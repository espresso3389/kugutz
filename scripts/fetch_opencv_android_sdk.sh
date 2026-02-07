#!/usr/bin/env bash
set -euo pipefail

# Optional helper: fetch OpenCV Android SDK and copy `libopencv_java4.so` into jniLibs/.
#
# This is intentionally opt-in to avoid network fetches during normal builds.
#
# Usage:
#   OPENCV_ANDROID_SDK_URL="https://.../OpenCV-android-sdk-4.x.y.zip" \
#   ABIS="arm64-v8a armeabi-v7a x86 x86_64" \
#   ./scripts/fetch_opencv_android_sdk.sh

URL="${OPENCV_ANDROID_SDK_URL:-}"
if [[ -z "$URL" ]]; then
  echo "fetch_opencv_android_sdk: OPENCV_ANDROID_SDK_URL not set; skipping."
  exit 0
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST_JNI_LIBS_DIR="${JNI_LIBS_DIR:-$ROOT_DIR/app/android/app/src/main/jniLibs}"
ABIS="${ABIS:-arm64-v8a}"

WORK_DIR="$ROOT_DIR/third_party/opencv-android-sdk"
ZIP_PATH="$WORK_DIR/opencv-android-sdk.zip"
EXTRACT_DIR="$WORK_DIR/extracted"

mkdir -p "$WORK_DIR"

echo "fetch_opencv_android_sdk: downloading $URL"
curl -L --fail --retry 3 --retry-delay 2 -o "$ZIP_PATH" "$URL"

rm -rf "$EXTRACT_DIR"
mkdir -p "$EXTRACT_DIR"
unzip -q "$ZIP_PATH" -d "$EXTRACT_DIR"

# The zip layout varies; try to locate the SDK root.
SDK_NATIVE_LIBS="$(find "$EXTRACT_DIR" -type d -path '*/sdk/native/libs' | head -n 1 || true)"
if [[ -z "$SDK_NATIVE_LIBS" ]]; then
  echo "fetch_opencv_android_sdk: could not find sdk/native/libs in archive" >&2
  exit 2
fi

for ABI in $ABIS; do
  SRC_SO="$SDK_NATIVE_LIBS/$ABI/libopencv_java4.so"
  if [[ ! -f "$SRC_SO" ]]; then
    echo "fetch_opencv_android_sdk: missing $SRC_SO (ABI=$ABI), skipping this ABI" >&2
    continue
  fi
  mkdir -p "$DEST_JNI_LIBS_DIR/$ABI"
  cp -a "$SRC_SO" "$DEST_JNI_LIBS_DIR/$ABI/"
  echo "fetch_opencv_android_sdk: installed $DEST_JNI_LIBS_DIR/$ABI/libopencv_java4.so"
done

