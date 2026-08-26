#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output_path="${1:-${project_root}/docs/images/ui-v2/thought-field-runtime-v1.png}"
preview_port="${AGENTOS_PREVIEW_PORT:-8765}"
preview_profile="$(mktemp -d)"
capture_path="${preview_profile}/thought-field.png"
browser_pid=""

if [[ -n "${CHROME_BIN:-}" ]]; then
  browser_bin="${CHROME_BIN}"
elif [[ -x "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" ]]; then
  browser_bin="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
elif command -v google-chrome >/dev/null 2>&1; then
  browser_bin="$(command -v google-chrome)"
else
  echo "Chrome is required to compile and capture the production WebGL/GLES shader" >&2
  exit 1
fi

python3 -m http.server "${preview_port}" --bind 127.0.0.1 --directory "${project_root}" >/dev/null 2>&1 &
server_pid=$!
cleanup() {
  if [[ -n "${browser_pid}" ]]; then
    kill "${browser_pid}" 2>/dev/null || true
    wait "${browser_pid}" 2>/dev/null || true
  fi
  kill "${server_pid}" 2>/dev/null || true
  wait "${server_pid}" 2>/dev/null || true
  rm -rf -- "${preview_profile}"
}
trap cleanup EXIT

for _ in {1..20}; do
  if curl --silent --fail "http://127.0.0.1:${preview_port}/scripts/thought-field-preview.html" >/dev/null; then
    break
  fi
  sleep 0.1
done

"${browser_bin}" \
  --headless=new \
  --no-first-run \
  --no-default-browser-check \
  --disable-background-networking \
  --disable-component-update \
  --hide-scrollbars \
  --use-angle=swiftshader \
  --enable-webgl \
  --enable-unsafe-swiftshader \
  --ignore-gpu-blocklist \
  --user-data-dir="${preview_profile}" \
  --window-size=944,2048 \
  --screenshot="${capture_path}" \
  "http://127.0.0.1:${preview_port}/scripts/thought-field-preview.html?time=12.5" \
  >/dev/null 2>&1 &
browser_pid=$!

for _ in {1..200}; do
  if [[ -s "${capture_path}" ]]; then
    sleep 0.2
    break
  fi
  if ! kill -0 "${browser_pid}" 2>/dev/null; then
    wait "${browser_pid}"
    echo "Chrome exited before producing the shader capture" >&2
    exit 1
  fi
  sleep 0.1
done

test -s "${capture_path}"
kill "${browser_pid}" 2>/dev/null || true
wait "${browser_pid}" 2>/dev/null || true
browser_pid=""
mv -f -- "${capture_path}" "${output_path}"
echo "Captured production shader: ${output_path}"
