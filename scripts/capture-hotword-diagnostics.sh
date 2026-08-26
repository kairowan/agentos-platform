#!/usr/bin/env bash
set -euo pipefail

output_dir="${1:?usage: capture-hotword-diagnostics.sh OUTPUT_DIRECTORY}"
command -v adb >/dev/null || { echo "adb is required"; exit 1; }
[[ "$(adb get-state 2>/dev/null)" == "device" ]] || { echo "exactly one authorized device is required"; exit 1; }

mkdir -p "${output_dir}"
adb shell getprop >"${output_dir}/getprop.txt"
adb shell dumpsys voiceinteraction >"${output_dir}/voiceinteraction.txt"
adb shell dumpsys soundtrigger >"${output_dir}/soundtrigger.txt" 2>&1 || true
adb shell dumpsys audio >"${output_dir}/audio.txt"
adb shell dumpsys package com.agentos.voice >"${output_dir}/voice-package.txt"
adb logcat -d -v threadtime >"${output_dir}/logcat.txt"

echo "Hotword diagnostics captured in ${output_dir}"
