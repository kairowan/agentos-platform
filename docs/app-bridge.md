# Installed-app capability bridge

AgentOS keeps compatible Android applications as background capability providers.
It does not read their private databases, extract account tokens, intercept TLS, or
pretend that every application has a public API.

`AgentAppBridgeService` runs inside the existing capability-service sandbox and
provides a signature-protected AIDL boundary. Its first vertical slice supports:

- discovery of activities that explicitly advertise themselves as launchable;
- a shared adapter catalog for short video, long video, reading, news, food,
  social, shopping, music, maps, and a safe generic fallback;
- one-time, 60-second confirmation tokens before opening another application;
- a maximum 200-node semantic snapshot from the optional accessibility bridge;
- explicit confirmation for **every** click and text input, independent of label;
- only the two explicit scroll actions bypass confirmation;
- package, node path, class, and text revalidation before a queued action runs.

The accessibility service is never enabled silently. Open **应用能力桥**, choose
**配置应用语义桥**, and explicitly enable **AgentOS 应用语义桥**. After visiting
another app, AgentOS can display the most recently cached accessible page. Canvas,
video surfaces, protected windows, and WebViews that do not expose accessibility
semantics can legitimately produce an empty or incomplete snapshot.

## Security boundary

Only the single platform-signed AgentShell package can bind. Inputs and result
counts are bounded. App launches, text entry, and all clicks use one-time tokens;
queued actions run only when the package and the original node path, class, and text
still match. Payment credentials, CAPTCHA solving, private storage access, hidden
network APIs, and automatic final payment remain outside this bridge.

Password nodes and Android 14+ nodes marked `accessibilityDataSensitive` (including
their subtrees) are excluded. Editable fields are represented by a placeholder,
not their existing contents. This is not a general detector for sensitive text in
apps that fail to mark their nodes. Locked-device reads/actions are rejected.

Queued actions have a separate five-second, single-use execution authorization.
Cancellation, a replacement request, service interruption/unbinding or process
restart invalidates it, including callbacks already posted to the main thread.
The workspace's **停止待执行操作** clears queued work; it cannot undo an action
that already ran. `STATUS_QUEUED` means queued, **not** verified completion. There
is no automatic retry and no universal downstream business-result verification.

## Adapter direction

The catalog assigns each recognized provider a category and declared UI-level
capabilities, while the generic fallback keeps unknown launchable apps usable.
Future package-specific navigation rules translate domain requests into the same
generic launch/snapshot/action contract, not new permissions. When an app exposes a standard deep link,
`ContentProvider`, share target, or `MediaSession`, that stable platform interface
should take precedence over semantic UI automation.
