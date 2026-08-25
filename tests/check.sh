#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

grep -q 'name: "AgentShell"' "${project_root}/apps/AgentShell/Android.bp"
grep -q 'PRODUCT_PACKAGES' "${project_root}/product/agentos_cf_x86_64.mk"
grep -q 'AgentShell' "${project_root}/product/agentos_cf_x86_64.mk"
grep -q 'android.intent.category.HOME' "${project_root}/apps/AgentShell/AndroidManifest.xml"

echo "AgentOS platform checks passed"
