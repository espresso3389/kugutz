#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
SRC_DIR="$ROOT_DIR/.dropbear-build/dropbear-src.patched"
PATCH_FILE="$ROOT_DIR/scripts/dropbear.patch"

if [[ ! -d "$SRC_DIR/.git" ]]; then
  echo "Expected git repo at $SRC_DIR" >&2
  exit 1
fi

echo "Generating patch at $PATCH_FILE"
git -C "$SRC_DIR" diff --patch > "$PATCH_FILE"

echo "Resetting working tree to 'orig'"
git -C "$SRC_DIR" reset --hard orig
git -C "$SRC_DIR" clean -fd

echo "Done."
