#!/usr/bin/env bash
# 사용법: magisk/build_module.sh <서명된 release APK>
# 데몬 빌드 -> APK 포함 -> build/magisk/RefreshPaper-<version>.zip
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "usage: $0 <signed-release-apk>" >&2
  exit 1
fi

APK="$1"
[ -f "$APK" ] || { echo "APK not found: $APK" >&2; exit 1; }
APK="$(cd "$(dirname "$APK")" && pwd)/$(basename "$APK")"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODULE="$ROOT/magisk/module"

"$ROOT/daemon/build.sh"

[ -f "$MODULE/bin/page_key_remapd" ] || { echo "daemon binary missing: $MODULE/bin/page_key_remapd" >&2; exit 1; }

find_aapt2() {
  local sdk_root=""
  if [ -n "${ANDROID_HOME:-}" ] && [ -d "${ANDROID_HOME}" ]; then
    sdk_root="$ANDROID_HOME"
  elif [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "${ANDROID_SDK_ROOT}" ]; then
    sdk_root="$ANDROID_SDK_ROOT"
  elif [ -f "$ROOT/local.properties" ]; then
    sdk_root="$(sed -n 's/^sdk.dir=//p' "$ROOT/local.properties" | sed 's#\\:#:#g')"
  fi
  [ -n "$sdk_root" ] && [ -d "$sdk_root/build-tools" ] || return 1
  local latest
  latest="$(ls -d "$sdk_root"/build-tools/* 2>/dev/null | sort -V | tail -n 1 || true)"
  [ -n "$latest" ] && [ -x "$latest/aapt2" ] || return 1
  echo "$latest/aapt2"
}

AAPT2="$(find_aapt2 || true)"
[ -n "$AAPT2" ] || { echo "aapt2 not found. Set ANDROID_HOME/ANDROID_SDK_ROOT or sdk.dir in local.properties (build-tools required)." >&2; exit 1; }

BADGING="$("$AAPT2" dump badging "$APK")" || { echo "aapt2 dump badging failed: $APK" >&2; exit 1; }
PACKAGE_LINE="$(printf '%s\n' "$BADGING" | grep -m1 '^package:' || true)"
VERSION="$(printf '%s' "$PACKAGE_LINE" | sed -n "s/.* versionName='\([^']*\)'.*/\1/p")"
VERSION_CODE="$(printf '%s' "$PACKAGE_LINE" | sed -n "s/.* versionCode='\([0-9]*\)'.*/\1/p")"
[ -n "$VERSION" ] && [ -n "$VERSION_CODE" ] || { echo "cannot read versionName/versionCode from APK: $APK" >&2; exit 1; }

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
cp -R "$MODULE/." "$STAGE/"
mkdir -p "$STAGE/system/priv-app/RefreshPaper"
cp "$APK" "$STAGE/system/priv-app/RefreshPaper/RefreshPaper.apk"

sed -e "s/^version=.*/version=$VERSION/" -e "s/^versionCode=.*/versionCode=$VERSION_CODE/" \
  "$MODULE/module.prop" > "$STAGE/module.prop"

OUT_DIR="$ROOT/build/magisk"
OUT="$OUT_DIR/RefreshPaper-$VERSION.zip"
mkdir -p "$OUT_DIR"
rm -f "$OUT"
(cd "$STAGE" && zip -r -X "$OUT" . -x '*.gitkeep' -x '*.DS_Store' >/dev/null)

echo "$OUT"
