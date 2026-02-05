#!/usr/bin/env bash
set -euo pipefail

NDK_DIR="${NDK_DIR:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "$NDK_DIR" ]]; then
  echo "NDK_DIR or ANDROID_NDK_ROOT must be set." >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIBUSB_DIR="$ROOT_DIR/third_party/libusb"
SRC_DIR="$ROOT_DIR/third_party/libuvc"
OUT_ROOT="$ROOT_DIR/third_party/libuvc/build-android"
JNI_LIBS_DIR="${JNI_LIBS_DIR:-$ROOT_DIR/app/android/app/src/main/jniLibs}"
API="${ANDROID_API:-21}"
ABIS="${ABIS:-arm64-v8a}"

if [[ ! -d "$SRC_DIR" ]]; then
  echo "libuvc submodule missing at $SRC_DIR" >&2
  exit 2
fi

TOOLCHAIN_FILE="$NDK_DIR/build/cmake/android.toolchain.cmake"

for ABI in $ABIS; do
  LIBUSB_BUILD="$ROOT_DIR/third_party/libusb/android/libs/$ABI"
  LIBUSB_INCLUDE="$LIBUSB_DIR/libusb"
  LIBUSB_LIB="$LIBUSB_BUILD/libusb1.0.so"
  if [[ ! -f "$LIBUSB_LIB" ]]; then
    echo "libusb build not found for $ABI. Run build_libusb_android.sh first." >&2
    exit 3
  fi

  BUILD_DIR="$OUT_ROOT/$ABI"
  PKG_DIR="$BUILD_DIR/pkgconfig"
  mkdir -p "$PKG_DIR"
  cat > "$PKG_DIR/libusb-1.0.pc" <<EOF
prefix=$LIBUSB_BUILD
exec_prefix=\${prefix}
libdir=$LIBUSB_BUILD
includedir=$LIBUSB_INCLUDE

Name: libusb-1.0
Description: libusb-1.0
Version: 1.0
Libs: -L\${libdir} -lusb1.0
Cflags: -I\${includedir}
EOF

  export PKG_CONFIG_PATH="$PKG_DIR"
  export PKG_CONFIG_LIBDIR="$PKG_DIR"

  cmake -S "$SRC_DIR" -B "$BUILD_DIR" \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM="android-$API" \
    -DBUILD_SHARED_LIBS=ON \
    -DLibUSB_INCLUDE_DIRS="$LIBUSB_INCLUDE" \
    -DLibUSB_LIBDIR="$LIBUSB_BUILD" \
    -DLibUSB_LIBRARY="$LIBUSB_LIB"
  cmake --build "$BUILD_DIR" --config Release -j"$(nproc)"

  mkdir -p "$JNI_LIBS_DIR/$ABI"
  cp -a "$BUILD_DIR/libuvc.so" "$JNI_LIBS_DIR/$ABI/" || true
done
