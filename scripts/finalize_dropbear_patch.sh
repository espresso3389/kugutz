#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
SRC_DIR="$ROOT_DIR/.dropbear-build/src"
PATCH_FILE="$ROOT_DIR/scripts/dropbear.patch"

if [[ ! -d "$SRC_DIR/.git" ]]; then
  echo "Expected git repo at $SRC_DIR" >&2
  exit 1
fi

echo "Generating patch at $PATCH_FILE"
git -C "$SRC_DIR" diff --patch > "$PATCH_FILE"

BASE_COMMIT=$(git -C "$SRC_DIR" rev-list --max-parents=0 HEAD | tail -n 1)
if [[ -z "$BASE_COMMIT" ]]; then
  echo "Unable to locate base commit in $SRC_DIR" >&2
  exit 1
fi

echo "Resetting working tree to base commit $BASE_COMMIT"
git -C "$SRC_DIR" reset --hard "$BASE_COMMIT"
git -C "$SRC_DIR" clean -fd

echo "Done."
