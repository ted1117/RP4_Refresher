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
if [ -f "$LEGACY_PID" ]; then
  OLD=$(cat "$LEGACY_PID" 2>/dev/null)
  [ -n "$OLD" ] && kill "$OLD" 2>/dev/null
fi
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
exit 0
