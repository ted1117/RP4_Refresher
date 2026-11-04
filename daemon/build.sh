#!/usr/bin/env bash
# Builds the page key remap daemon (arm64 only) into the Magisk module's bin/ directory.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SRC="$SCRIPT_DIR/page_key_remapd.c"
OUT_DIR="$PROJECT_ROOT/magisk/module/bin"
OUT_BIN="$OUT_DIR/page_key_remapd"
API_LEVEL="${API_LEVEL:-29}"
TRIPLE="aarch64-linux-android"

find_ndk_root() {
  if [[ -n "${ANDROID_NDK_HOME:-}" && -d "${ANDROID_NDK_HOME}" ]]; then
    echo "$ANDROID_NDK_HOME"
    return 0
  fi

  if [[ -n "${ANDROID_NDK_ROOT:-}" && -d "${ANDROID_NDK_ROOT}" ]]; then
    echo "$ANDROID_NDK_ROOT"
    return 0
  fi

  local sdk_root=""
  if [[ -n "${ANDROID_SDK_ROOT:-}" && -d "${ANDROID_SDK_ROOT}" ]]; then
    sdk_root="$ANDROID_SDK_ROOT"
  elif [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}" ]]; then
    sdk_root="$ANDROID_HOME"
  elif [[ -f "$PROJECT_ROOT/local.properties" ]]; then
    sdk_root="$(sed -n 's/^sdk.dir=//p' "$PROJECT_ROOT/local.properties" | sed 's#\\:#:#g')"
  fi

  if [[ -n "$sdk_root" && -d "$sdk_root/ndk" ]]; then
    local latest
    latest="$(ls -d "$sdk_root"/ndk/* 2>/dev/null | sort -V | tail -n 1 || true)"
    if [[ -n "$latest" && -d "$latest" ]]; then
      echo "$latest"
      return 0
    fi
  fi

  return 1
}

NDK_ROOT="$(find_ndk_root || true)"
if [[ -z "$NDK_ROOT" ]]; then
  echo "NDK not found. Set ANDROID_NDK_HOME or install NDK in Android SDK." >&2
  exit 1
fi

TOOLCHAIN_BASE="$NDK_ROOT/toolchains/llvm/prebuilt"
HOST_TAG="$(ls -1 "$TOOLCHAIN_BASE" | head -n 1)"
if [[ -z "$HOST_TAG" || ! -d "$TOOLCHAIN_BASE/$HOST_TAG" ]]; then
  echo "No prebuilt toolchain found under $TOOLCHAIN_BASE" >&2
  exit 1
fi
TOOLCHAIN="$TOOLCHAIN_BASE/$HOST_TAG/bin"

CLANG="$TOOLCHAIN/${TRIPLE}${API_LEVEL}-clang"
STRIP_BIN="$TOOLCHAIN/llvm-strip"
if [[ ! -x "$CLANG" ]]; then
  echo "Compiler not found: $CLANG" >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
echo "Building arm64-v8a -> $OUT_BIN"
"$CLANG" -O2 -Wall -Wextra "$SRC" -o "$OUT_BIN"
if [[ -x "$STRIP_BIN" ]]; then
  "$STRIP_BIN" "$OUT_BIN" || true
fi
chmod 755 "$OUT_BIN"

echo "Done. $OUT_BIN"
