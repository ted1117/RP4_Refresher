#!/system/bin/sh

MODDIR=${0%/*}
BIN="$MODDIR/bin/page_key_remapd"
STATE_DIR="/data/user_de/0/com.hidsquid.refreshpaper/files/page_key_remap"
LOG="/data/local/tmp/page_key_remapd.log"
SUPPID="/dev/page_key_remapd.supervisor.pid"
LEGACY_BIN="/data/local/tmp/page_key_remapd"
LEGACY_PID="/data/local/tmp/page_key_remapd.pid"

log() {
  echo "$(date '+%Y-%m-%d %H:%M:%S') [service] $*" >> "$LOG"
}

# 단일 인스턴스: 이미 감시 루프가 살아 있으면 종료
if [ -f "$SUPPID" ]; then
  OLD=$(cat "$SUPPID" 2>/dev/null)
  if [ -n "$OLD" ] && kill -0 "$OLD" 2>/dev/null; then
    # pid 재사용 방지: cmdline이 이 스크립트일 때만 실행 중으로 본다
    OLDCMD=$(tr '\0' ' ' < "/proc/$OLD/cmdline" 2>/dev/null)
    case "$OLDCMD" in
      *service.sh*|*"$MODDIR"*) exit 0 ;;
    esac
  fi
fi

# 예전 방식(앱이 띄운 데몬) 정리
for P in $(pidof page_key_remapd 2>/dev/null); do
  # /data/local/tmp 의 바이너리만 대상 (모듈 데몬은 건드리지 않음)
  EXE=$(readlink "/proc/$P/exe" 2>/dev/null)
  CMD=$(tr '\0' ' ' < "/proc/$P/cmdline" 2>/dev/null)
  case "$EXE" in
    "$LEGACY_BIN"*) kill "$P" 2>/dev/null; continue ;;
  esac
  case "$CMD" in
    "$LEGACY_BIN"|"$LEGACY_BIN "*) kill "$P" 2>/dev/null ;;
  esac
done
rm -f "$LEGACY_BIN" "$LEGACY_PID"

# 부팅 완료 대기
while [ "$(getprop sys.boot_completed)" != "1" ]; do
  sleep 2
done

# 로그가 1MB를 넘으면 비움
if [ -f "$LOG" ]; then
  SIZE=$(wc -c < "$LOG" 2>/dev/null)
  [ -n "$SIZE" ] && [ "$SIZE" -gt 1048576 ] && : > "$LOG"
fi

# 감시 루프 (백그라운드)
(
  BACKOFF=1
  while true; do
    START=$(date +%s)
    log "start: $BIN"
    "$BIN" --state-dir "$STATE_DIR" >> "$LOG" 2>&1
    CODE=$?
    RUNTIME=$(( $(date +%s) - START ))
    if [ "$RUNTIME" -ge 60 ]; then
      BACKOFF=1
    fi
    log "exit code=$CODE runtime=${RUNTIME}s, ${BACKOFF}s 후 재시작"
    sleep "$BACKOFF"
    if [ "$RUNTIME" -lt 60 ]; then
      BACKOFF=$(( BACKOFF * 2 ))
      [ "$BACKOFF" -gt 300 ] && BACKOFF=300
    fi
  done
) &
echo $! > "$SUPPID"

# LSPosed 매니저 열기 요청 처리 (백그라운드)
# 앱은 su가 없어서 매니저를 직접 못 띄운다. 요청 파일이 생기면 root인 이 스크립트가 대신 띄운다.
# 요청 파일은 앱의 files/ 폴더에 둔다 (전용 하위 폴더는 첫 요청 전에 없어서 감시를 놓쳤음).
# 참고: files/ 에 건 inotify는 page_key_remap/ 하위 폴더 안의 이벤트는 알려주지 않는다.
# 하위 폴더 항목 자체의 이벤트만 오는데, 요청 파일 존재 여부만 확인하므로 무해하다.
REQ_DIR="/data/user_de/0/com.hidsquid.refreshpaper/files"
REQ_FILE="$REQ_DIR/launch_lsposed"
OLD_REQ_DIR="$REQ_DIR/root_request"

handle_lsposed() {
  # 앱은 파일이 사라지면 처리된 것으로 보므로 가장 먼저 지운다
  rm -f "$REQ_FILE"
  log "lsposed launch requested"
  am start -c org.lsposed.manager.LAUNCH_MANAGER com.android.shell/.BugreportWarningActivity >> "$LOG" 2>&1
  log "lsposed launch exit code=$?"
}

(
  # 예전 버전이 쓰던 하위 폴더는 더 이상 쓰지 않으므로 한 번 지운다
  [ -d "$OLD_REQ_DIR" ] && rm -rf "$OLD_REQ_DIR"
  while true; do
    if [ ! -d "$REQ_DIR" ]; then
      sleep 1
      continue
    fi
    if [ -e "$REQ_FILE" ]; then
      MTIME=$(stat -c %Y "$REQ_FILE" 2>/dev/null)
      NOW=$(date +%s)
      if [ -n "$MTIME" ] && [ $(( NOW - MTIME )) -gt 10 ]; then
        # 재부팅 전에 남은 오래된 요청은 실행하지 않고 지운다
        rm -f "$REQ_FILE"
        log "stale lsposed request removed"
      else
        handle_lsposed
      fi
    fi
    # 일부 toybox inotifyd는 마스크 첫 글자를 무시함 (그래서 w를 두 번)
    # 또 m/y(옮겨 들어옴/나감) 글자가 뒤바뀌고 이벤트의 파일 이름도 믿을 수 없어서,
    # 이벤트 종류·이름은 보지 않고 요청 파일이 있는지만 확인함 (rename은 원자적)
    inotifyd - "$REQ_DIR:wwmy" | while read -r ev dir file; do
      [ -e "$REQ_FILE" ] && handle_lsposed
    done
    # 폴더가 사라지는 등으로 inotifyd가 끝나면 다시 시도
    sleep 1
  done
) &
exit 0
