#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

grep -q 'name: "AgentShell"' "${project_root}/apps/AgentShell/Android.bp"
grep -q 'srcs: \["src/\*\*/\*.kt"\]' "${project_root}/apps/AgentShell/Android.bp"
grep -q 'PRODUCT_PACKAGES' "${project_root}/product/agentos_cf_x86_64.mk"
grep -q 'AgentShell' "${project_root}/product/agentos_cf_x86_64.mk"
grep -q 'AgentCapabilityService' "${project_root}/product/agentos_cf_x86_64.mk"
grep -q 'android.intent.category.HOME' "${project_root}/apps/AgentShell/AndroidManifest.xml"
grep -q 'USE_CAPABILITY_BROKER' "${project_root}/apps/AgentShell/AndroidManifest.xml"
grep -q 'system.time.read' "${project_root}/libraries/CapabilityCore/src/com/agentos/capability/core/Capabilities.kt"
grep -q 'class CapabilityBroker' "${project_root}/libraries/CapabilityCore/src/com/agentos/capability/core/CapabilityBroker.kt"
grep -q 'interface IAgentCapabilityService' "${project_root}/libraries/CapabilityApi/src/main/aidl/com/agentos/capability/api/IAgentCapabilityService.aidl"
grep -q 'android:protectionLevel="signature"' "${project_root}/services/AgentCapabilityService/AndroidManifest.xml"
grep -q 'Binder.getCallingUid' "${project_root}/services/AgentCapabilityService/src/com/agentos/capability/service/AgentCapabilityService.kt"
grep -q 'com.agentos.capability domain=agent_capability_service' "${project_root}/sepolicy/private/seapp_contexts"
grep -q 'class OpenAiCompatiblePlanner' "${project_root}/apps/AgentShell/src/com/agentos/shell/OpenAiCompatiblePlanner.kt"
grep -q 'class GeneratedUiParser' "${project_root}/apps/AgentShell/src/com/agentos/shell/GeneratedUiParser.kt"
python3 -m json.tool "${project_root}/schemas/generated-ui.schema.json" >/dev/null

echo "AgentOS platform checks passed"
