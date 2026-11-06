#define _GNU_SOURCE

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <poll.h>
#include <signal.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/inotify.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#ifndef BITS_PER_LONG
#define BITS_PER_LONG (sizeof(unsigned long) * 8)
#endif

#define BIT_WORD(bit) ((bit) / BITS_PER_LONG)
#define BIT_MASK(bit) (1UL << ((bit) % BITS_PER_LONG))

#define ARRAY_SIZE(a) (sizeof(a) / sizeof((a)[0]))

#define REMAP_MODE_DISABLED 0
#define REMAP_MODE_NORMAL 1
#define REMAP_MODE_SWAPPED 2
#define REMAP_MODE_SWAP_ONLY 3
#define DEFAULT_LONG_PRESS_TIMEOUT_MS 500
#define PAGE_UP_LONG_PRESS_SIGNAL_KEY KEY_F11
#define PAGE_DOWN_LONG_PRESS_SIGNAL_KEY KEY_F12

#define TOUCH_REFRESH_SIGNAL_KEY KEY_F10
#define TOUCH_TAP_MAX_MS 500
#define TOUCH_TRIGGER_MAX 1000
#define UINPUT_DEVICE_NAME "rp_page_key_remap"

#define STATE_PROTOCOL_VERSION 3
#define APP_PACKAGE_NAME "com.hidsquid.refreshpaper"
#define TICK_INTERVAL_MS 5000

#define LOG_ERR(...) log_msg(__VA_ARGS__)

static volatile sig_atomic_t g_stop = 0;

/* Raw content of the state file as written by the app. */
typedef struct {
    bool present;
    bool parsed;
    int version;
    int pid;
    int mode;
    bool page_up_long;
    bool page_down_long;
    int timeout_ms;
    int touch_trigger;
} raw_state_t;

/* State the daemon actually acts on, after validity checks. */
typedef struct {
    int remap_mode;
    bool page_up_long_press_enabled;
    bool page_down_long_press_enabled;
    int long_press_timeout_ms;
    int touch_trigger;  /* 0 = off, N = refresh every N counted touches */
} remap_state_t;

typedef struct {
    bool active;
    bool long_press_triggered;
    int short_press_code;
    uint16_t signal_key_code;
    int64_t deadline_ms;
} pending_page_key_t;

typedef struct {
    int source_fd;
    int uinput_fd;
    int inotify_fd;
    int watch_wd;
    bool grabbed;
    bool grab_failed;
    bool last_desired;
    bool watch_fail_logged;
    bool dropping;  /* SYN_DROPPED seen: discard events until the next SYN_REPORT */
    bool event_clock_monotonic;  /* source fd timestamps use CLOCK_MONOTONIC */

    /* Touchscreen (read-only, never grabbed); open only while touch_trigger > 0. */
    int touch_fd;
    bool touch_failed;           /* last open failed or fd errored: retry on tick only */
    bool touch_dropping;         /* SYN_DROPPED seen on the touch fd */
    bool touch_clock_monotonic;
    int touch_trigger;           /* trigger the counter belongs to */
    int touch_count;
    int64_t touch_down_ms;       /* BTN_TOUCH down time, -1 = none */

    char state_dir[256];
    char state_path[300];

    raw_state_t raw;
    remap_state_t eff;
    const char *eff_reason;
    char last_state_log[256];

    bool phys_down[KEY_MAX + 1];
    int phys_down_count;
    uint16_t out_code[KEY_MAX + 1];   /* output code emitted at DOWN, 0 = none */
    bool uinput_down[KEY_MAX + 1];    /* keys currently pressed on the uinput side */

    pending_page_key_t pending_up;
    pending_page_key_t pending_down;
} daemon_t;

static daemon_t g;

static void refresh_effective(void);

static void log_msg(const char *fmt, ...) __attribute__((format(printf, 1, 2)));

static void log_msg(const char *fmt, ...) {
    char msg[512];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(msg, sizeof(msg), fmt, ap);
    va_end(ap);

    char ts[32] = "0000-00-00 00:00:00";
    time_t t = time(NULL);
    struct tm tmv;
    if (localtime_r(&t, &tmv) != NULL) {
        strftime(ts, sizeof(ts), "%Y-%m-%d %H:%M:%S", &tmv);
    }
    fprintf(stderr, "%s page_key_remapd: %s\n", ts, msg);
    fflush(stderr);
}

static void handle_signal(int sig) {
    (void)sig;
    g_stop = 1;
}

static bool test_bit(const unsigned long *array, size_t array_len, int bit) {
    if (bit < 0) return false;
    size_t word = BIT_WORD((size_t)bit);
    if (word >= array_len) return false;
    return (array[word] & BIT_MASK((size_t)bit)) != 0;
}

static bool has_page_keys(const unsigned long *key_bits, size_t key_words) {
    return test_bit(key_bits, key_words, KEY_PAGEUP) &&
           test_bit(key_bits, key_words, KEY_PAGEDOWN);
}

static bool looks_like_gpio_keys(const char *name) {
    return name != NULL && strstr(name, "gpio_keys") != NULL;
}

static int read_key_bits(int fd, unsigned long *out_bits, size_t out_words) {
    memset(out_bits, 0, out_words * sizeof(unsigned long));
    size_t bytes = out_words * sizeof(unsigned long);
    int ret = ioctl(fd, EVIOCGBIT(EV_KEY, bytes), out_bits);
    if (ret < 0) return -1;
    return 0;
}

static int open_event_device(const char *path) {
    return open(path, O_RDONLY | O_NONBLOCK | O_CLOEXEC);
}

static int64_t now_monotonic_ms(void) {
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) != 0) {
        return 0;
    }
    return ((int64_t)ts.tv_sec * 1000LL) + (ts.tv_nsec / 1000000LL);
}

/*
 * Event timestamp in ms on the monotonic clock. Falls back to "now" when the
 * source fd could not be switched to CLOCK_MONOTONIC (old behavior).
 */
static int64_t event_time_ms(const struct input_event *ev, bool monotonic) {
    if (!monotonic) {
        return now_monotonic_ms();
    }
#ifdef input_event_sec
    return ((int64_t)ev->input_event_sec * 1000LL) + (ev->input_event_usec / 1000);
#else
    return ((int64_t)ev->time.tv_sec * 1000LL) + (ev->time.tv_usec / 1000);
#endif
}

static int find_source_device(char *out_path, size_t out_path_len, char *out_name, size_t out_name_len) {
    DIR *dir = opendir("/dev/input");
    if (!dir) {
        LOG_ERR("failed to open /dev/input: %s", strerror(errno));
        return -1;
    }

    struct dirent *ent;
    int found = -1;

    while ((ent = readdir(dir)) != NULL) {
        if (strncmp(ent->d_name, "event", 5) != 0) continue;

        char path[256];
        snprintf(path, sizeof(path), "/dev/input/%s", ent->d_name);
        int fd = open_event_device(path);
        if (fd < 0) continue;

        char name[128] = {0};
        if (ioctl(fd, EVIOCGNAME(sizeof(name)), name) < 0) {
            close(fd);
            continue;
        }

        unsigned long key_bits[(KEY_MAX / BITS_PER_LONG) + 1];
        if (read_key_bits(fd, key_bits, ARRAY_SIZE(key_bits)) < 0) {
            close(fd);
            continue;
        }

        bool candidate = looks_like_gpio_keys(name) && has_page_keys(key_bits, ARRAY_SIZE(key_bits));
        close(fd);
        if (!candidate) continue;

        snprintf(out_path, out_path_len, "%s", path);
        snprintf(out_name, out_name_len, "%s", name);
        found = 0;
        break;
    }

    closedir(dir);
    if (found != 0) {
        LOG_ERR("no gpio_keys device with PAGEUP/PAGEDOWN found");
    }
    return found;
}

static int emit_event(int fd, uint16_t type, uint16_t code, int32_t value) {
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    if (gettimeofday(&ev.time, NULL) != 0) {
        return -1;
    }
    ev.type = type;
    ev.code = code;
    ev.value = value;
    ssize_t written = write(fd, &ev, sizeof(ev));
    return written == (ssize_t)sizeof(ev) ? 0 : -1;
}

static int emit_key(int fd, uint16_t code, int32_t value) {
    if (emit_event(fd, EV_KEY, code, value) < 0 ||
        emit_event(fd, EV_SYN, SYN_REPORT, 0) < 0) {
        return -1;
    }
    return 0;
}

static int emit_key_tap(int fd, uint16_t code) {
    if (emit_key(fd, code, 1) < 0 || emit_key(fd, code, 0) < 0) {
        return -1;
    }
    return 0;
}

static int setup_uinput_from_source(const unsigned long *source_key_bits, size_t source_key_words) {
    int ufd = open("/dev/uinput", O_WRONLY | O_NONBLOCK | O_CLOEXEC);
    if (ufd < 0) {
        LOG_ERR("failed to open /dev/uinput: %s", strerror(errno));
        return -1;
    }

    if (ioctl(ufd, UI_SET_EVBIT, EV_KEY) < 0 ||
        ioctl(ufd, UI_SET_EVBIT, EV_SYN) < 0) {
        LOG_ERR("failed to set uinput ev bits: %s", strerror(errno));
        close(ufd);
        return -1;
    }

    // Pass through every key from the source device.
    for (int code = 0; code <= KEY_MAX; code++) {
        if (!test_bit(source_key_bits, source_key_words, code)) continue;
        if (ioctl(ufd, UI_SET_KEYBIT, code) < 0) {
            LOG_ERR("failed to enable key %d: %s", code, strerror(errno));
            close(ufd);
            return -1;
        }
    }

    // Ensure mapped destination keys and synthetic long-press signal keys are available.
    if (ioctl(ufd, UI_SET_KEYBIT, KEY_VOLUMEUP) < 0 ||
        ioctl(ufd, UI_SET_KEYBIT, KEY_VOLUMEDOWN) < 0 ||
        ioctl(ufd, UI_SET_KEYBIT, PAGE_UP_LONG_PRESS_SIGNAL_KEY) < 0 ||
        ioctl(ufd, UI_SET_KEYBIT, PAGE_DOWN_LONG_PRESS_SIGNAL_KEY) < 0 ||
        ioctl(ufd, UI_SET_KEYBIT, TOUCH_REFRESH_SIGNAL_KEY) < 0) {
        LOG_ERR("failed to enable synthetic key bits: %s", strerror(errno));
        close(ufd);
        return -1;
    }

    struct uinput_setup usetup;
    memset(&usetup, 0, sizeof(usetup));
    snprintf(usetup.name, UINPUT_MAX_NAME_SIZE, UINPUT_DEVICE_NAME);
    usetup.id.bustype = BUS_VIRTUAL;
    usetup.id.vendor = 0x1209;
    usetup.id.product = 0x4001;
    usetup.id.version = 1;

    if (ioctl(ufd, UI_DEV_SETUP, &usetup) < 0) {
        LOG_ERR("UI_DEV_SETUP failed: %s", strerror(errno));
        close(ufd);
        return -1;
    }

    if (ioctl(ufd, UI_DEV_CREATE) < 0) {
        LOG_ERR("UI_DEV_CREATE failed: %s", strerror(errno));
        close(ufd);
        return -1;
    }

    return ufd;
}

static int map_key_code(int code, int mode) {
    if (mode == REMAP_MODE_SWAP_ONLY) {
        if (code == KEY_PAGEUP) return KEY_PAGEDOWN;
        if (code == KEY_PAGEDOWN) return KEY_PAGEUP;
        return code;
    }

    if (mode == REMAP_MODE_SWAPPED) {
        if (code == KEY_PAGEUP) return KEY_VOLUMEDOWN;
        if (code == KEY_PAGEDOWN) return KEY_VOLUMEUP;
        return code;
    }

    if (code == KEY_PAGEUP) return KEY_VOLUMEUP;
    if (code == KEY_PAGEDOWN) return KEY_VOLUMEDOWN;
    return code;
}

/* ---------- touch refresh ---------- */

/* Finds the direct-input touchscreen (INPUT_PROP_DIRECT, BTN_TOUCH, ABS_MT_POSITION_X). */
static int find_touch_device(char *out_path, size_t out_path_len, char *out_name, size_t out_name_len) {
    DIR *dir = opendir("/dev/input");
    if (!dir) return -1;

    struct dirent *ent;
    int found = -1;

    while ((ent = readdir(dir)) != NULL) {
        if (strncmp(ent->d_name, "event", 5) != 0) continue;

        char path[256];
        snprintf(path, sizeof(path), "/dev/input/%s", ent->d_name);
        int fd = open_event_device(path);
        if (fd < 0) continue;

        char name[128] = {0};
        if (ioctl(fd, EVIOCGNAME(sizeof(name)), name) < 0 ||
            looks_like_gpio_keys(name) || strcmp(name, UINPUT_DEVICE_NAME) == 0) {
            close(fd);
            continue;
        }

        unsigned long props[(INPUT_PROP_MAX / BITS_PER_LONG) + 1] = {0};
        unsigned long key_bits[(KEY_MAX / BITS_PER_LONG) + 1];
        unsigned long abs_bits[(ABS_MAX / BITS_PER_LONG) + 1] = {0};
        bool candidate = false;
        if (ioctl(fd, EVIOCGPROP(sizeof(props)), props) >= 0 &&
            read_key_bits(fd, key_bits, ARRAY_SIZE(key_bits)) == 0 &&
            ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(abs_bits)), abs_bits) >= 0) {
            candidate = test_bit(props, ARRAY_SIZE(props), INPUT_PROP_DIRECT) &&
                        test_bit(key_bits, ARRAY_SIZE(key_bits), BTN_TOUCH) &&
                        test_bit(abs_bits, ARRAY_SIZE(abs_bits), ABS_MT_POSITION_X);
        }
        close(fd);
        if (!candidate) continue;

        snprintf(out_path, out_path_len, "%s", path);
        snprintf(out_name, out_name_len, "%s", name);
        found = 0;
        break;
    }

    closedir(dir);
    return found;
}

static void close_touch(void) {
    if (g.touch_fd >= 0) {
        close(g.touch_fd);
        g.touch_fd = -1;
        LOG_ERR("touch device closed");
    }
    g.touch_dropping = false;
    g.touch_down_ms = -1;
    g.touch_count = 0;
}

static void open_touch(void) {
    char path[256] = {0};
    char name[128] = {0};

    if (find_touch_device(path, sizeof(path), name, sizeof(name)) != 0) {
        if (!g.touch_failed) {
            LOG_ERR("touch device not found (retrying every %ds)", TICK_INTERVAL_MS / 1000);
        }
        g.touch_failed = true;
        return;
    }

    int fd = open_event_device(path);
    if (fd < 0) {
        if (!g.touch_failed) {
            LOG_ERR("failed to open touch device %s: %s (retrying every %ds)",
                    path, strerror(errno), TICK_INTERVAL_MS / 1000);
        }
        g.touch_failed = true;
        return;
    }

    int clk = CLOCK_MONOTONIC;
    g.touch_clock_monotonic = ioctl(fd, EVIOCSCLOCKID, &clk) >= 0;
    if (!g.touch_clock_monotonic) {
        LOG_ERR("touch EVIOCSCLOCKID failed: %s (using receive time)", strerror(errno));
    }

    g.touch_fd = fd;
    g.touch_failed = false;
    g.touch_dropping = false;
    g.touch_down_ms = -1;  /* a touch already down at open is ignored: no recorded down */
    g.touch_count = 0;
    LOG_ERR("touch device: %s (%s)", path, name);
}

/*
 * Opens the touchscreen while touch_trigger > 0 and closes it otherwise.
 * Also resets the counter when the trigger value changes. Failed opens are
 * retried only from the tick (is_retry).
 */
static void apply_touch(bool is_retry) {
    int trigger = g.eff.touch_trigger;
    if (trigger != g.touch_trigger) {
        g.touch_trigger = trigger;
        g.touch_count = 0;
    }

    if (trigger == 0) {
        close_touch();
        g.touch_failed = false;
        return;
    }
    if (g.touch_fd >= 0) return;
    if (g.touch_failed && !is_retry) return;
    open_touch();
}

static void handle_touch_event(const struct input_event *ev) {
    if (ev->type != EV_KEY || ev->code != BTN_TOUCH) return;

    int64_t at_ms = event_time_ms(ev, g.touch_clock_monotonic);
    if (ev->value == 1) {
        g.touch_down_ms = at_ms;
    } else if (ev->value == 0) {
        bool is_tap = g.touch_down_ms >= 0 && at_ms - g.touch_down_ms <= TOUCH_TAP_MAX_MS;
        g.touch_down_ms = -1;
        if (!is_tap || g.eff.touch_trigger <= 0) return;

        g.touch_count++;
        if (g.touch_count >= g.eff.touch_trigger) {
            g.touch_count = 0;
            /* The app may have died without clearing the state file; re-check before emitting. */
            refresh_effective();
            if (g.eff.touch_trigger <= 0) return;  /* may have closed the touch fd */
            if (emit_key_tap(g.uinput_fd, TOUCH_REFRESH_SIGNAL_KEY) < 0) {
                LOG_ERR("failed to emit touch refresh signal: %s", strerror(errno));
            }
        }
    }
}

/* Reads everything queued on the touch fd. Read errors close it; the tick retries. */
static void drain_touch(void) {
    struct input_event evs[32];

    while (g.touch_fd >= 0) {
        ssize_t n = read(g.touch_fd, evs, sizeof(evs));
        if (n < 0) {
            if (errno == EINTR) continue;
            if (errno == EAGAIN) return;
            LOG_ERR("touch read failed: %s", strerror(errno));
            close_touch();
            g.touch_failed = true;
            return;
        }
        if (n == 0) return;

        size_t count = (size_t)n / sizeof(evs[0]);
        for (size_t i = 0; i < count; i++) {
            const struct input_event *ev = &evs[i];

            if (ev->type == EV_SYN && ev->code == SYN_DROPPED) {
                g.touch_dropping = true;
                continue;
            }
            if (g.touch_dropping) {
                /* Discard up to and including the next SYN_REPORT; an up may be lost. */
                if (ev->type == EV_SYN && ev->code == SYN_REPORT) {
                    g.touch_dropping = false;
                    g.touch_down_ms = -1;
                }
                continue;
            }
            handle_touch_event(ev);
            if (g.touch_fd < 0) break;  /* closed by refresh_effective(); drop the rest */
        }
    }
}

/* ---------- state file ---------- */

static void read_raw_state(raw_state_t *out) {
    memset(out, 0, sizeof(*out));
    out->timeout_ms = DEFAULT_LONG_PRESS_TIMEOUT_MS;

    int fd = open(g.state_path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return;

    char buf[128];
    ssize_t n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (n <= 0) return;
    buf[n] = '\0';
    out->present = true;

    int v[7] = {0};
    int parsed = sscanf(buf, "%d %d %d %d %d %d %d", &v[0], &v[1], &v[2], &v[3], &v[4], &v[5], &v[6]);
    if (parsed < 1) return;
    out->version = v[0];
    if (v[0] != STATE_PROTOCOL_VERSION) return;
    if (parsed < 7) return;

    int mode = v[2];
    int timeout_ms = v[5];
    int touch_trigger = v[6];
    if (mode < REMAP_MODE_DISABLED || mode > REMAP_MODE_SWAP_ONLY) {
        mode = REMAP_MODE_DISABLED;
    }
    if (timeout_ms < 100 || timeout_ms > 5000) {
        timeout_ms = DEFAULT_LONG_PRESS_TIMEOUT_MS;
    }

    if (touch_trigger < 1 || touch_trigger > TOUCH_TRIGGER_MAX) {
        touch_trigger = 0;
    }

    out->parsed = true;
    out->pid = v[1];
    out->mode = mode;
    out->page_up_long = v[3] != 0;
    out->page_down_long = v[4] != 0;
    out->timeout_ms = timeout_ms;
    out->touch_trigger = touch_trigger;
}

static bool app_pid_valid(int pid) {
    if (pid <= 0) return false;
    if (kill((pid_t)pid, 0) != 0 && errno != EPERM) return false;

    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/cmdline", pid);
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return false;
    char buf[sizeof(APP_PACKAGE_NAME) + 8];
    ssize_t n = read(fd, buf, sizeof(buf));
    close(fd);
    size_t want = sizeof(APP_PACKAGE_NAME) - 1;
    if (n < (ssize_t)want) return false;
    return memcmp(buf, APP_PACKAGE_NAME, want) == 0;
}

/* Recomputes the effective state from the cached raw state and a fresh pid check. */
static void refresh_effective(void) {
    remap_state_t eff = {
        .remap_mode = REMAP_MODE_DISABLED,
        .page_up_long_press_enabled = false,
        .page_down_long_press_enabled = false,
        .long_press_timeout_ms = DEFAULT_LONG_PRESS_TIMEOUT_MS,
        .touch_trigger = 0,
    };
    const char *reason;

    if (!g.raw.present) {
        reason = "state file missing";
    } else if (g.raw.version != STATE_PROTOCOL_VERSION) {
        reason = g.raw.parsed || g.raw.version != 0 ? "unsupported version" : "state file unparseable";
    } else if (!g.raw.parsed) {
        reason = "state file unparseable";
    } else if (!app_pid_valid(g.raw.pid)) {
        reason = "app process not running";
    } else {
        eff.remap_mode = g.raw.mode;
        eff.page_up_long_press_enabled = g.raw.page_up_long;
        eff.page_down_long_press_enabled = g.raw.page_down_long;
        eff.long_press_timeout_ms = g.raw.timeout_ms;
        eff.touch_trigger = g.raw.touch_trigger;
        reason = "ok";
    }

    g.eff = eff;
    g.eff_reason = reason;

    char line[256];
    if (g.raw.parsed) {
        snprintf(line, sizeof(line),
                 "state: pid=%d mode=%d up_long=%d down_long=%d timeout=%dms touch=%d -> effective mode=%d up_long=%d down_long=%d touch=%d (%s)",
                 g.raw.pid, g.raw.mode, g.raw.page_up_long, g.raw.page_down_long, g.raw.timeout_ms,
                 g.raw.touch_trigger, eff.remap_mode, eff.page_up_long_press_enabled,
                 eff.page_down_long_press_enabled, eff.touch_trigger, reason);
    } else {
        snprintf(line, sizeof(line), "state: unusable -> effective disabled (%s)", reason);
    }
    if (strcmp(line, g.last_state_log) != 0) {
        snprintf(g.last_state_log, sizeof(g.last_state_log), "%s", line);
        LOG_ERR("%s", line);
    }

    apply_touch(false);
}

static void reload_state(void) {
    read_raw_state(&g.raw);
    refresh_effective();
}

static bool desired_grab(void) {
    return g.eff.remap_mode != REMAP_MODE_DISABLED ||
           g.eff.page_up_long_press_enabled ||
           g.eff.page_down_long_press_enabled;
}

/* ---------- inotify ---------- */

static void try_add_watch(void) {
    if (g.inotify_fd < 0 || g.watch_wd >= 0) return;

    int wd = inotify_add_watch(g.inotify_fd, g.state_dir,
                               IN_CLOSE_WRITE | IN_MOVED_TO | IN_DELETE_SELF);
    if (wd < 0) {
        if (!g.watch_fail_logged) {
            LOG_ERR("state dir %s not watchable: %s (retrying every %ds)",
                    g.state_dir, strerror(errno), TICK_INTERVAL_MS / 1000);
            g.watch_fail_logged = true;
        }
        return;
    }
    g.watch_wd = wd;
    g.watch_fail_logged = false;
    LOG_ERR("watching state dir %s", g.state_dir);
    reload_state();
}

static void drop_watch(void) {
    g.watch_wd = -1;
    g.watch_fail_logged = false;
}

static void handle_inotify(void) {
    char buf[4096] __attribute__((aligned(__alignof__(struct inotify_event))));
    bool reload = false;

    for (;;) {
        ssize_t n = read(g.inotify_fd, buf, sizeof(buf));
        if (n < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (n == 0) break;

        for (char *p = buf; p < buf + n;) {
            struct inotify_event *ie = (struct inotify_event *)p;
            if (ie->mask & IN_Q_OVERFLOW) {
                reload = true;
            }
            if (ie->mask & (IN_DELETE_SELF | IN_IGNORED)) {
                if (g.watch_wd >= 0) {
                    LOG_ERR("state dir watch removed");
                }
                drop_watch();
                reload = true;
            } else if (ie->len > 0 && strcmp(ie->name, "state") == 0) {
                reload = true;
            }
            p += sizeof(struct inotify_event) + ie->len;
        }
    }

    if (reload) reload_state();
}

/* ---------- grab handling ---------- */

static pending_page_key_t *pending_key_for_code(int code) {
    if (code == KEY_PAGEUP) return &g.pending_up;
    if (code == KEY_PAGEDOWN) return &g.pending_down;
    return NULL;
}

static bool any_pending(void) {
    return g.pending_up.active || g.pending_down.active;
}

static void release_uinput_keys(void) {
    bool any = false;
    for (int code = 0; code <= KEY_MAX; code++) {
        if (!g.uinput_down[code]) continue;
        emit_key(g.uinput_fd, (uint16_t)code, 0);
        g.uinput_down[code] = false;
        any = true;
    }
    if (any) LOG_ERR("released keys still pressed on uinput device");
}

static void do_ungrab(void) {
    release_uinput_keys();
    if (ioctl(g.source_fd, EVIOCGRAB, 0) < 0) {
        LOG_ERR("EVIOCGRAB release failed: %s", strerror(errno));
    }
    g.grabbed = false;
    LOG_ERR("ungrabbed source device");
}

/* Applies grab/ungrab if wanted and safe; otherwise defers (called again after each event). */
static void apply_grab(bool is_retry) {
    bool desired = desired_grab();
    if (desired != g.last_desired) {
        g.last_desired = desired;
        g.grab_failed = false;
    }
    if (desired == g.grabbed) return;
    if (g.phys_down_count > 0 || any_pending()) return;  /* defer */

    if (desired) {
        if (g.grab_failed && !is_retry) return;
        if (ioctl(g.source_fd, EVIOCGRAB, 1) < 0) {
            if (!g.grab_failed) {
                LOG_ERR("EVIOCGRAB failed: %s (will retry every %ds)",
                        strerror(errno), TICK_INTERVAL_MS / 1000);
            }
            g.grab_failed = true;
            return;
        }
        g.grab_failed = false;
        g.grabbed = true;
        LOG_ERR("grabbed source device");
    } else {
        do_ungrab();
    }
}

/* ---------- long press ---------- */

static int process_due_long_press(pending_page_key_t *pending, int64_t at_ms) {
    if (!pending->active || pending->long_press_triggered || at_ms < pending->deadline_ms) {
        return 0;
    }

    if (emit_key_tap(g.uinput_fd, pending->signal_key_code) < 0) {
        return -1;
    }
    pending->long_press_triggered = true;
    return 0;
}

/* Fires pending long presses whose deadline is <= at_ms (a point on the event timeline). */
static int process_due_long_presses(int64_t at_ms) {
    if (process_due_long_press(&g.pending_up, at_ms) < 0) {
        LOG_ERR("failed to emit page-up long press signal: %s", strerror(errno));
        return -1;
    }
    if (process_due_long_press(&g.pending_down, at_ms) < 0) {
        LOG_ERR("failed to emit page-down long press signal: %s", strerror(errno));
        return -1;
    }
    return 0;
}

/* Returns ms until the nearest long-press deadline, or -1 if none. */
static int64_t nearest_deadline_ms(void) {
    int64_t nearest = -1;
    if (g.pending_up.active && !g.pending_up.long_press_triggered) {
        nearest = g.pending_up.deadline_ms;
    }
    if (g.pending_down.active && !g.pending_down.long_press_triggered &&
        (nearest < 0 || g.pending_down.deadline_ms < nearest)) {
        nearest = g.pending_down.deadline_ms;
    }
    return nearest;
}

/* ---------- event handling ---------- */

static void sync_phys_from_device(void) {
    unsigned char bits[(KEY_MAX / 8) + 1];
    memset(bits, 0, sizeof(bits));
    if (ioctl(g.source_fd, EVIOCGKEY(sizeof(bits)), bits) < 0) return;

    int count = 0;
    for (int code = 0; code <= KEY_MAX; code++) {
        bool down = (bits[code / 8] & (1 << (code % 8))) != 0;
        g.phys_down[code] = down;
        if (down) count++;
    }
    g.phys_down_count = count;
}

/*
 * Called once the SYN_DROPPED packet has been fully discarded. Key-ups may have
 * been lost, so reconcile the virtual side with the physical state. Safe when not
 * grabbed: nothing was emitted, so out_code/uinput_down are empty.
 */
static void resync_after_drop(void) {
    sync_phys_from_device();

    /* Release time is unknown: cancel instead of risking a page turn or F11/F12. */
    memset(&g.pending_up, 0, sizeof(g.pending_up));
    memset(&g.pending_down, 0, sizeof(g.pending_down));

    for (int code = 0; code <= KEY_MAX; code++) {
        uint16_t out = g.out_code[code];
        if (out == 0 || g.phys_down[code]) continue;  /* still held: keep out_code */
        if (g.uinput_down[out]) {
            emit_key(g.uinput_fd, out, 0);
            g.uinput_down[out] = false;
        }
        g.out_code[code] = 0;
    }
    LOG_ERR("input events dropped (SYN_DROPPED); resynced key state");
}

static void track_physical(int code, int value) {
    if (value == 1 && !g.phys_down[code]) {
        g.phys_down[code] = true;
        g.phys_down_count++;
    } else if (value == 0 && g.phys_down[code]) {
        g.phys_down[code] = false;
        g.phys_down_count--;
    }
}

static int handle_key_event(int code, int value, int64_t event_ms) {
    track_physical(code, value);

    /* Not grabbed: Android receives the keys directly. Only tracking above. */
    if (!g.grabbed) return 0;

    if (value == 1) {
        /* Cheap liveness check so a dead app disables remap for this press. */
        refresh_effective();
    }

    pending_page_key_t *pending = pending_key_for_code(code);
    bool long_press_enabled = false;
    if (code == KEY_PAGEUP) {
        long_press_enabled = g.eff.page_up_long_press_enabled;
    } else if (code == KEY_PAGEDOWN) {
        long_press_enabled = g.eff.page_down_long_press_enabled;
    }

    if (pending != NULL && (pending->active || long_press_enabled)) {
        if (value == 1) {
            if (!pending->active) {
                pending->active = true;
                pending->long_press_triggered = false;
                pending->short_press_code = g.eff.remap_mode == REMAP_MODE_DISABLED
                    ? code
                    : map_key_code(code, g.eff.remap_mode);
                pending->signal_key_code = (uint16_t)(
                    code == KEY_PAGEUP
                        ? PAGE_UP_LONG_PRESS_SIGNAL_KEY
                        : PAGE_DOWN_LONG_PRESS_SIGNAL_KEY
                );
                pending->deadline_ms = event_ms + g.eff.long_press_timeout_ms;
            }
            return 0;
        }

        if (pending->active) {
            if (value == 0) {
                bool long_press_triggered = pending->long_press_triggered;
                int short_press_code = pending->short_press_code;
                memset(pending, 0, sizeof(*pending));

                if (!long_press_triggered) {
                    if (emit_key_tap(g.uinput_fd, (uint16_t)short_press_code) < 0) {
                        LOG_ERR("failed to emit short page key press: %s", strerror(errno));
                        return -1;
                    }
                }
            }
            return 0;  /* repeat (2) while pending: swallow */
        }
        /* No held press to resolve. Fall through to normal emission. */
    }

    uint16_t out;
    if (value == 1) {
        out = (uint16_t)(g.eff.remap_mode == REMAP_MODE_DISABLED
            ? code
            : map_key_code(code, g.eff.remap_mode));
        g.out_code[code] = out;
    } else {
        out = g.out_code[code];
        if (out == 0) return 0;  /* press was not ours (e.g. started before grab) */
    }

    if (emit_key(g.uinput_fd, out, value) < 0) {
        LOG_ERR("failed to emit event: %s", strerror(errno));
        return -1;
    }
    if (value == 1) {
        g.uinput_down[out] = true;
    } else if (value == 0) {
        g.uinput_down[out] = false;
        g.out_code[code] = 0;
    }
    return 0;
}

/* Reads everything currently queued on the source fd. Returns -1 on fatal error. */
static int drain_source(void) {
    struct input_event evs[32];

    for (;;) {
        ssize_t n = read(g.source_fd, evs, sizeof(evs));
        if (n < 0) {
            if (errno == EINTR) continue;
            if (errno == EAGAIN) return 0;
            LOG_ERR("read failed: %s", strerror(errno));
            return -1;
        }
        if (n == 0) return 0;

        size_t count = (size_t)n / sizeof(evs[0]);
        for (size_t i = 0; i < count; i++) {
            const struct input_event *ev = &evs[i];

            if (ev->type == EV_SYN && ev->code == SYN_DROPPED) {
                g.dropping = true;
                continue;
            }
            if (g.dropping) {
                /* Discard everything up to and including the next SYN_REPORT. */
                if (ev->type == EV_SYN && ev->code == SYN_REPORT) {
                    g.dropping = false;
                    resync_after_drop();
                }
                continue;
            }
            if (ev->type != EV_KEY || ev->code > KEY_MAX) continue;
            if (ev->value < 0 || ev->value > 2) continue;

            /* Resolve long presses in event order: only those due by this event's time. */
            int64_t event_ms = event_time_ms(ev, g.event_clock_monotonic);
            if (process_due_long_presses(event_ms) < 0) return -1;
            if (handle_key_event(ev->code, ev->value, event_ms) < 0) return -1;
        }
    }
}

/* ---------- main loop ---------- */

static int main_loop(void) {
    int64_t next_tick_ms = now_monotonic_ms() + TICK_INTERVAL_MS;

    reload_state();
    try_add_watch();

    while (!g_stop) {
        apply_grab(false);

        int64_t now_ms = now_monotonic_ms();
        bool need_tick = g.grabbed || g.watch_wd < 0 || (desired_grab() && !g.grabbed) ||
                         (g.eff.touch_trigger > 0 && g.touch_fd < 0) || g.touch_fd >= 0;
        if (!need_tick) next_tick_ms = now_ms + TICK_INTERVAL_MS;

        int64_t timeout = -1;
        int64_t deadline = nearest_deadline_ms();
        if (deadline >= 0) {
            timeout = deadline > now_ms ? deadline - now_ms : 0;
        }
        if (need_tick) {
            int64_t t = next_tick_ms > now_ms ? next_tick_ms - now_ms : 0;
            if (timeout < 0 || t < timeout) timeout = t;
        }

        struct pollfd pfds[3];
        nfds_t nfds = 0;
        int inotify_idx = -1;
        int touch_idx = -1;
        memset(pfds, 0, sizeof(pfds));
        pfds[nfds].fd = g.source_fd;
        pfds[nfds].events = POLLIN;
        nfds++;
        if (g.inotify_fd >= 0) {
            inotify_idx = (int)nfds;
            pfds[nfds].fd = g.inotify_fd;
            pfds[nfds].events = POLLIN;
            nfds++;
        }
        if (g.touch_fd >= 0) {
            touch_idx = (int)nfds;
            pfds[nfds].fd = g.touch_fd;
            pfds[nfds].events = POLLIN;
            nfds++;
        }

        int poll_rc = poll(pfds, nfds, (int)timeout);
        if (poll_rc < 0) {
            if (errno == EINTR) continue;
            LOG_ERR("poll failed: %s", strerror(errno));
            return -1;
        }

        if (poll_rc > 0) {
            if (pfds[0].revents & (POLLERR | POLLHUP | POLLNVAL)) {
                LOG_ERR("source device error (revents=0x%x)", pfds[0].revents);
                return -1;
            }
            if (touch_idx >= 0 && pfds[touch_idx].fd == g.touch_fd &&
                (pfds[touch_idx].revents & (POLLERR | POLLHUP | POLLNVAL))) {
                LOG_ERR("touch device error (revents=0x%x)", pfds[touch_idx].revents);
                close_touch();
                g.touch_failed = true;  /* the tick retries */
            }
        }

        /*
         * Drain the source to EAGAIN (also on timeout, to close the race with
         * events queued meanwhile). Long presses are decided by event timestamps
         * there; only once the queue is empty is firing by "now" correct.
         */
        if (drain_source() < 0) return -1;
        if (poll_rc > 0 && inotify_idx >= 0 && (pfds[inotify_idx].revents & POLLIN)) {
            handle_inotify();
        }
        drain_touch();
        if (process_due_long_presses(now_monotonic_ms()) < 0) return -1;

        if (need_tick && now_monotonic_ms() >= next_tick_ms) {
            next_tick_ms = now_monotonic_ms() + TICK_INTERVAL_MS;
            if (g.watch_wd < 0) {
                try_add_watch();  /* reloads on success */
                /* still no watch (e.g. inotify unavailable): poll state */
                if (g.watch_wd < 0) reload_state();
            } else if (g.grabbed || g.touch_fd >= 0) {
                reload_state();
            }
            /*
             * Another client may have held the grab while a key went up, leaving
             * phys_down stale. EVIOCGKEY reports the true state. Not while grabbed:
             * we see every event then, and resyncing would desync out_code tracking.
             */
            if (!g.grabbed) sync_phys_from_device();
            apply_grab(true);
            apply_touch(true);
        }
    }

    return 0;
}

static void shutdown_daemon(void) {
    release_uinput_keys();
    if (g.grabbed) {
        if (ioctl(g.source_fd, EVIOCGRAB, 0) < 0) {
            LOG_ERR("EVIOCGRAB release failed: %s", strerror(errno));
        } else {
            LOG_ERR("ungrabbed source device");
        }
        g.grabbed = false;
    }
    if (ioctl(g.uinput_fd, UI_DEV_DESTROY) < 0) {
        LOG_ERR("UI_DEV_DESTROY failed: %s", strerror(errno));
    }
    close(g.uinput_fd);
    close(g.source_fd);
    if (g.touch_fd >= 0) close(g.touch_fd);
    if (g.inotify_fd >= 0) close(g.inotify_fd);
}

int main(int argc, char **argv) {
    char source_path[256] = {0};
    char source_name[128] = {0};
    const char *state_dir = NULL;

    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--device") == 0) {
            if (i + 1 >= argc) {
                LOG_ERR("missing value for --device");
                return 1;
            }
            snprintf(source_path, sizeof(source_path), "%s", argv[++i]);
            continue;
        }
        if (strcmp(argv[i], "--state-dir") == 0) {
            if (i + 1 >= argc) {
                LOG_ERR("missing value for --state-dir");
                return 1;
            }
            state_dir = argv[++i];
            continue;
        }
        LOG_ERR("unknown argument: %s", argv[i]);
        return 1;
    }

    if (state_dir == NULL || state_dir[0] == '\0') {
        LOG_ERR("--state-dir is required");
        return 1;
    }

    memset(&g, 0, sizeof(g));
    g.source_fd = g.uinput_fd = g.inotify_fd = g.watch_wd = -1;
    g.touch_fd = -1;
    g.touch_down_ms = -1;
    if (snprintf(g.state_dir, sizeof(g.state_dir), "%s", state_dir) >= (int)sizeof(g.state_dir) ||
        snprintf(g.state_path, sizeof(g.state_path), "%s/state", state_dir) >= (int)sizeof(g.state_path)) {
        LOG_ERR("--state-dir too long");
        return 1;
    }

    LOG_ERR("starting (pid %d, state dir %s)", (int)getpid(), g.state_dir);

    if (source_path[0] == '\0' &&
        find_source_device(source_path, sizeof(source_path), source_name, sizeof(source_name)) != 0) {
        return 2;
    }

    g.source_fd = open_event_device(source_path);
    if (g.source_fd < 0) {
        LOG_ERR("failed to open source device %s: %s", source_path, strerror(errno));
        return 3;
    }

    {
        int clk = CLOCK_MONOTONIC;
        if (ioctl(g.source_fd, EVIOCSCLOCKID, &clk) < 0) {
            LOG_ERR("EVIOCSCLOCKID failed: %s (using receive time for long presses)", strerror(errno));
        } else {
            g.event_clock_monotonic = true;
        }
    }

    if (source_name[0] == '\0') {
        ioctl(g.source_fd, EVIOCGNAME(sizeof(source_name)), source_name);
    }
    LOG_ERR("source device: %s (%s)", source_path, source_name[0] ? source_name : "unknown");

    unsigned long source_key_bits[(KEY_MAX / BITS_PER_LONG) + 1];
    if (read_key_bits(g.source_fd, source_key_bits, ARRAY_SIZE(source_key_bits)) < 0) {
        LOG_ERR("failed to read source key bits: %s", strerror(errno));
        close(g.source_fd);
        return 4;
    }

    if (!has_page_keys(source_key_bits, ARRAY_SIZE(source_key_bits))) {
        LOG_ERR("source device does not expose PAGEUP/PAGEDOWN");
        close(g.source_fd);
        return 5;
    }

    g.uinput_fd = setup_uinput_from_source(source_key_bits, ARRAY_SIZE(source_key_bits));
    if (g.uinput_fd < 0) {
        close(g.source_fd);
        return 7;
    }
    LOG_ERR("uinput device %s created", UINPUT_DEVICE_NAME);

    g.inotify_fd = inotify_init1(IN_NONBLOCK | IN_CLOEXEC);
    if (g.inotify_fd < 0) {
        LOG_ERR("inotify_init failed: %s (falling back to periodic checks)", strerror(errno));
    }

    sync_phys_from_device();

    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = handle_signal;
    sigemptyset(&sa.sa_mask);
    sigaction(SIGINT, &sa, NULL);
    sigaction(SIGTERM, &sa, NULL);

    int loop_rc = main_loop();

    if (loop_rc != 0) {
        LOG_ERR("fatal error in main loop, exiting");
    }
    shutdown_daemon();
    LOG_ERR("exit (%s)", loop_rc == 0 ? "signal" : "error");

    return loop_rc == 0 ? 0 : 8;
}
