#!/usr/bin/env bash
set -euo pipefail

serial=""
timeout_s="300"
poll_s="0.8"

usage() {
  cat <<'USAGE'
Usage: adb_auto_allow.sh [--serial SERIAL] [--timeout SECONDS] [--poll SECONDS]

Watches the current Android UI and taps an "Allow/許可" style button when a
permission dialog is visible.

Notes:
- This is best-effort and depends on device language/OS variant.
- It can also tap "While using the app" style buttons.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) serial="${2:-}"; shift 2 ;;
    --timeout) timeout_s="${2:-}"; shift 2 ;;
    --poll) poll_s="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown_arg: $1" >&2; usage; exit 2 ;;
  esac
done

adb_base=(adb)
if [[ -n "$serial" ]]; then
  adb_base+=( -s "$serial" )
fi

now_ms() {
  python3 - <<'PY'
import time
print(int(time.time() * 1000))
PY
}

deadline_ms=$(( $(now_ms) + (timeout_s * 1000) ))

dump_ui() {
  # Some devices return a "UI hierchary dumped" string to stdout; ignore.
  "${adb_base[@]}" shell uiautomator dump /sdcard/uidump.xml >/dev/null 2>&1 || return 1
  "${adb_base[@]}" shell cat /sdcard/uidump.xml 2>/dev/null || return 1
}

center_of_bounds() {
  # Input like: [l,t][r,b]
  local b="$1"
  python3 - "$b" <<'PY'
import re,sys
s=sys.argv[1]
m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", s)
if not m:
  sys.exit(2)
l,t,r,b=map(int,m.groups())
print((l+r)//2, (t+b)//2)
PY
}

pick_allow_bounds() {
  # Prefer the "strongest" allow option first.
  # Matches both text and content-desc (some OEM dialogs use one or the other).
  local xml="$1"
  python3 -c '
import re,sys
xml=sys.stdin.read()

def find_bounds(patterns):
  for pat in patterns:
    m=re.search(pat, xml, flags=re.IGNORECASE)
    if m:
      return m.group(1)
  return None

candidate_patterns = [
  r"(?:text|content-desc)=\"Allow only while using the app\"[^>]*bounds=\"([^\"]+)\"",
  r"(?:text|content-desc)=\"While using the app\"[^>]*bounds=\"([^\"]+)\"",
  r"(?:text|content-desc)=\"Allow\"[^>]*bounds=\"([^\"]+)\"",
  r"(?:text|content-desc)=\"OK\"[^>]*bounds=\"([^\"]+)\"",
  r"(?:text|content-desc)=\"使用中のみ許可\"[^>]*bounds=\"([^\"]+)\"",
  r"(?:text|content-desc)=\"許可\"[^>]*bounds=\"([^\"]+)\"",
  r"(?:text|content-desc)=\"OK\"[^>]*bounds=\"([^\"]+)\"",
]

b=find_bounds(candidate_patterns)
if b:
  sys.stdout.write(b)
' <<<"$xml"
}

while [[ $(now_ms) -lt $deadline_ms ]]; do
  xml="$(dump_ui || true)"
  if [[ -n "$xml" ]]; then
    bounds="$(pick_allow_bounds "$xml" || true)"
    if [[ -n "$bounds" ]]; then
      read -r x y < <(center_of_bounds "$bounds")
      "${adb_base[@]}" shell input tap "$x" "$y" >/dev/null 2>&1 || true
      echo "tapped_allow bounds=$bounds x=$x y=$y"
      exit 0
    fi
  fi
  sleep "$poll_s"
done

echo "timeout_no_dialog"
exit 1
