#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

grep -q 'name: "AgentShell"' "${project_root}/apps/AgentShell/Android.bp"
grep -q 'srcs: \["src/\*\*/\*.kt"\]' "${project_root}/apps/AgentShell/Android.bp"
grep -q 'PRODUCT_PACKAGES' "${project_root}/product/agentos_cf_x86_64.mk"
grep -q 'AgentShell' "${project_root}/product/agentos_cf_x86_64.mk"
grep -q 'android.intent.category.HOME' "${project_root}/apps/AgentShell/AndroidManifest.xml"
grep -q 'system.time.read' "${project_root}/apps/AgentShell/src/com/agentos/shell/Capabilities.kt"
python3 -m json.tool "${project_root}/schemas/generated-ui.schema.json" >/dev/null

echo "AgentOS platform checks passed"
