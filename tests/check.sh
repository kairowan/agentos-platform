#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

grep -q 'name: "AgentShell"' "${project_root}/apps/AgentShell/Android.bp"
grep -q 'srcs: \["src/\*\*/\*.kt"\]' "${project_root}/apps/AgentShell/Android.bp"
grep -q 'PRODUCT_PACKAGES' "${project_root}/product/agentos_cf_x86_64.mk"
grep -q 'AgentShell' "${project_root}/product/agentos_cf_x86_64.mk"
grep -q 'AgentCapabilityService' "${project_root}/product/agentos_cf_x86_64.mk"
grep -q 'AgentVoiceService' "${project_root}/product/agentos_cf_x86_64.mk"
grep -q 'AgentMediaService' "${project_root}/product/agentos_cf_x86_64.mk"
grep -q 'PRODUCT_PACKAGE_OVERLAYS' "${project_root}/product/agentos_cf_x86_64.mk"
grep -q 'android.intent.category.HOME' "${project_root}/apps/AgentShell/AndroidManifest.xml"
grep -q 'USE_CAPABILITY_BROKER' "${project_root}/apps/AgentShell/AndroidManifest.xml"
grep -q 'system.time.read' "${project_root}/libraries/CapabilityCore/src/com/agentos/capability/core/Capabilities.kt"
grep -q 'class CapabilityBroker' "${project_root}/libraries/CapabilityCore/src/com/agentos/capability/core/CapabilityBroker.kt"
grep -q 'interface IAgentCapabilityService' "${project_root}/libraries/CapabilityApi/src/main/aidl/com/agentos/capability/api/IAgentCapabilityService.aidl"
grep -q 'oneway interface IAgentEventListener' "${project_root}/libraries/CapabilityApi/src/main/aidl/com/agentos/capability/api/IAgentEventListener.aidl"
grep -q 'android:protectionLevel="signature"' "${project_root}/services/AgentCapabilityService/AndroidManifest.xml"
grep -q 'BIND_NOTIFICATION_LISTENER_SERVICE' "${project_root}/services/AgentCapabilityService/AndroidManifest.xml"
grep -q 'BIND_ACCESSIBILITY_SERVICE' "${project_root}/services/AgentCapabilityService/AndroidManifest.xml"
grep -q 'interface IAgentAppBridgeService' "${project_root}/libraries/CapabilityApi/src/main/aidl/com/agentos/capability/api/IAgentAppBridgeService.aidl"
grep -q 'Binder.getCallingUid' "${project_root}/services/AgentCapabilityService/src/com/agentos/capability/service/AgentAppBridgeService.kt"
grep -q 'MAX_NODES = 200' "${project_root}/services/AgentCapabilityService/src/com/agentos/capability/service/AgentAppAccessibilityService.kt"
! grep -q 'android.permission.RECORD_AUDIO' "${project_root}/apps/AgentShell/AndroidManifest.xml"
grep -q 'android.permission.RECORD_AUDIO' "${project_root}/services/AgentVoiceService/AndroidManifest.xml"
grep -q 'android:foregroundServiceType="camera|microphone"' "${project_root}/services/AgentMediaService/AndroidManifest.xml"
grep -q 'interface IAgentMediaService' "${project_root}/libraries/CapabilityApi/src/main/aidl/com/agentos/capability/api/IAgentMediaService.aidl"
grep -q 'Binder.getCallingUid' "${project_root}/services/AgentMediaService/src/com/agentos/media/AgentMediaService.kt"
grep -q 'SurfaceView' "${project_root}/apps/AgentShell/src/com/agentos/shell/MediaWorkspace.kt"
grep -q 'fun AgentHomeScreen' "${project_root}/apps/AgentShell/src/com/agentos/shell/AgentHome.kt"
grep -q 'fun AgentBackdrop' "${project_root}/apps/AgentShell/src/com/agentos/shell/AgentDesign.kt"
grep -q 'object AppAdapterCatalog' "${project_root}/services/AgentCapabilityService/src/com/agentos/capability/service/AppBridgePolicy.kt"
grep -q 'BIND_HOTWORD_DETECTION_SERVICE' "${project_root}/services/AgentVoiceService/AndroidManifest.xml"
grep -q 'createAlwaysOnHotwordDetector' "${project_root}/services/AgentVoiceService/src/com/agentos/voice/AgentVoiceInteractionService.java"
grep -q 'createOnDeviceSpeechRecognizer' "${project_root}/services/AgentVoiceService/src/com/agentos/voice/AgentVoiceInteractionSession.java"
grep -q 'EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L' "${project_root}/services/AgentVoiceService/src/com/agentos/voice/AgentVoiceInteractionSession.java"
grep -q 'MAX_LISTENING_MILLIS = 10_000L' "${project_root}/services/AgentVoiceService/src/com/agentos/voice/AgentVoiceInteractionSession.java"
grep -q 'ACTION_INTERRUPT_OUTPUT' "${project_root}/services/AgentVoiceService/src/com/agentos/voice/AgentVoiceInteractionService.java"
grep -q 'class LocalConversationHistory' "${project_root}/apps/AgentShell/src/com/agentos/shell/ConversationHistory.kt"
grep -q 'abstract class AgentKnowledgeDatabase : RoomDatabase' "${project_root}/apps/AgentShell/src/com/agentos/shell/ConversationHistory.kt"
grep -q 'MIGRATION_1_2' "${project_root}/apps/AgentShell/src/com/agentos/shell/ConversationHistory.kt"
grep -q 'MIGRATION_2_3' "${project_root}/apps/AgentShell/src/com/agentos/shell/ConversationHistory.kt"
grep -q 'tableName = "node_positions"' "${project_root}/apps/AgentShell/src/com/agentos/shell/ConversationHistory.kt"
grep -q 'InteractiveKnowledgeGraph' "${project_root}/apps/AgentShell/src/com/agentos/shell/KnowledgeGraphCanvas.kt"
grep -q 'transformable(transformState)' "${project_root}/apps/AgentShell/src/com/agentos/shell/KnowledgeGraphCanvas.kt"
grep -q 'detectDragGesturesAfterLongPress' "${project_root}/apps/AgentShell/src/com/agentos/shell/KnowledgeGraphCanvas.kt"
grep -q 'class ModelKnowledgeExtractor' "${project_root}/apps/AgentShell/src/com/agentos/shell/KnowledgeExtractor.kt"
grep -q 'state.history.asReversed()' "${project_root}/apps/AgentShell/src/com/agentos/shell/MainActivity.kt"
grep -q 'config_forceVoiceInteractionServicePackage' "${project_root}/overlay/frameworks/base/core/res/res/values/config.xml"
grep -q 'com.agentos.voice domain=agent_voice_service' "${project_root}/sepolicy/private/seapp_contexts"
grep -q 'Binder.getCallingUid' "${project_root}/services/AgentCapabilityService/src/com/agentos/capability/service/AgentCapabilityService.kt"
grep -q 'com.agentos.capability domain=agent_capability_service' "${project_root}/sepolicy/private/seapp_contexts"
grep -q 'class OpenAiCompatiblePlanner' "${project_root}/apps/AgentShell/src/com/agentos/shell/OpenAiCompatiblePlanner.kt"
grep -q 'class GeneratedUiParser' "${project_root}/apps/AgentShell/src/com/agentos/shell/GeneratedUiParser.kt"
python3 -m json.tool "${project_root}/schemas/generated-ui.schema.json" >/dev/null
bash -n "${project_root}/scripts/capture-hotword-diagnostics.sh"

for preview in home-v2 app-bridge-v2 camera-v2 knowledge-v2; do
  test -s "${project_root}/docs/images/ui-v2/${preview}.png"
done

if command -v sha256sum >/dev/null 2>&1; then
  (cd "${project_root}" && sha256sum --check docs/images/ui-v2/ui-preview.sha256)
else
  (cd "${project_root}" && shasum -a 256 --check docs/images/ui-v2/ui-preview.sha256)
fi

echo "AgentOS platform checks passed"
