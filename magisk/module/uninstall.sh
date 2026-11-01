#!/system/bin/sh

MODDIR=${0%/*}
SQLITE3="$MODDIR/bin/sqlite3"
DB="/data/adb/lspd/config/modules_config.db"
PKG="moe.silicon.android.rp4posed"
LOG="/data/local/tmp/refreshpaper_uninstall.log"

# 데몬 로그, 감시 루프 pid 파일 정리
rm -f /data/local/tmp/page_key_remapd.log /dev/page_key_remapd.supervisor.pid

echo "=== uninstall start ===" >> "$LOG"

# sqlite3 존재 확인
if [ ! -x "$SQLITE3" ]; then
  echo "sqlite3 not found: $SQLITE3" >> "$LOG"
  exit 0
fi

# DB 존재 확인
if [ ! -f "$DB" ]; then
  echo "DB not found: $DB" >> "$LOG"
  exit 0
fi

# 대상 모듈 존재 여부 확인
EXISTS=$("$SQLITE3" "$DB" \
  "SELECT COUNT(*) FROM modules WHERE module_pkg_name='$PKG';" 2>/dev/null)

if [ "$EXISTS" = "1" ]; then
  BEFORE=$("$SQLITE3" "$DB" \
    "SELECT enabled FROM modules WHERE module_pkg_name='$PKG';" 2>/dev/null)

  echo "before enabled=$BEFORE" >> "$LOG"

  # 강제 enable
  "$SQLITE3" "$DB" \
    "UPDATE modules SET enabled=1 WHERE module_pkg_name='$PKG';" 2>>"$LOG"

  AFTER=$("$SQLITE3" "$DB" \
    "SELECT enabled FROM modules WHERE module_pkg_name='$PKG';" 2>/dev/null)

  echo "after enabled=$AFTER" >> "$LOG"
else
  echo "module not found: $PKG" >> "$LOG"
fi

echo "=== uninstall end ===" >> "$LOG"
exit 0