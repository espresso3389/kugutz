#!/usr/bin/env bash
set -euo pipefail

NDK_DIR="${NDK_DIR:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "$NDK_DIR" ]]; then
  echo "NDK_DIR or ANDROID_NDK_ROOT must be set." >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="$ROOT_DIR/third_party/libusb"
JNI_LIBS_DIR="${JNI_LIBS_DIR:-$ROOT_DIR/app/android/app/src/main/jniLibs}"
ABIS="${ABIS:-arm64-v8a}"

if [[ ! -d "$SRC_DIR/android/jni" ]]; then
  echo "libusb submodule missing at $SRC_DIR" >&2
  exit 2
fi

NDK_BUILD="$NDK_DIR/ndk-build"

for ABI in $ABIS; do
  pushd "$SRC_DIR/android/jni" >/dev/null
  "$NDK_BUILD" APP_ABI="$ABI"
  popd >/dev/null
  mkdir -p "$JNI_LIBS_DIR/$ABI"
  # Keep the upstream filename we build, but also provide the common soname(s)
  # that Python packages like `pyusb` attempt to load via ctypes.
  # pyusb tries: usb-1.0 / libusb-1.0 / usb  => typically `libusb-1.0.so`.
  cp -a "$SRC_DIR/android/libs/$ABI/libusb1.0.so" "$JNI_LIBS_DIR/$ABI/" || true
  if [[ -f "$JNI_LIBS_DIR/$ABI/libusb1.0.so" ]]; then
    cp -a "$JNI_LIBS_DIR/$ABI/libusb1.0.so" "$JNI_LIBS_DIR/$ABI/libusb-1.0.so" || true
    cp -a "$JNI_LIBS_DIR/$ABI/libusb1.0.so" "$JNI_LIBS_DIR/$ABI/libusb.so" || true
  fi
done
