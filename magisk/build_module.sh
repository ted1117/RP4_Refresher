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

VERSION="$(sed -n 's/^version=//p' "$MODULE/module.prop" | tr -d '\r' | head -n1)"
[ -n "$VERSION" ] || { echo "version not found in module.prop" >&2; exit 1; }

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
cp -R "$MODULE/." "$STAGE/"
mkdir -p "$STAGE/system/priv-app/RefreshPaper"
cp "$APK" "$STAGE/system/priv-app/RefreshPaper/RefreshPaper.apk"

OUT_DIR="$ROOT/build/magisk"
OUT="$OUT_DIR/RefreshPaper-$VERSION.zip"
mkdir -p "$OUT_DIR"
rm -f "$OUT"
(cd "$STAGE" && zip -r -X "$OUT" . -x '*.gitkeep' -x '*.DS_Store' >/dev/null)

echo "$OUT"
