# Installed-app capability bridge

AgentOS keeps compatible Android applications as background capability providers.
It does not read their private databases, extract account tokens, intercept TLS, or
pretend that every application has a public API.

`AgentAppBridgeService` runs inside the existing capability-service sandbox and
provides a signature-protected AIDL boundary. Its first vertical slice supports:

- discovery of activities that explicitly advertise themselves as launchable;
- coarse video, reading, news, food, social, and fallback categories;
- one-time, 60-second confirmation tokens before opening another application;
- a maximum 200-node semantic snapshot from the optional accessibility bridge;
- generic click and scroll actions plus confirmed text input;
- confirmation for transaction-like clicks such as pay, order, send, publish,
  transfer, and delete;
- package, node path, class, and text revalidation before a queued action runs.

The accessibility service is never enabled silently. Open **应用能力桥**, choose
**配置应用语义桥**, and explicitly enable **AgentOS 应用语义桥**. After visiting
another app, AgentOS can display the most recently cached accessible page. Canvas,
video surfaces, protected windows, and WebViews that do not expose accessibility
semantics can legitimately produce an empty or incomplete snapshot.

## Security boundary

Only the single platform-signed AgentShell package can bind. Inputs and result
counts are bounded. App launches, text entry, and risky clicks use one-time tokens;
queued actions run only when the package and the original node path, class, and text
still match. Payment credentials, CAPTCHA solving, private storage access, hidden
network APIs, and automatic final payment remain outside this bridge.

## Adapter direction

The current category policy is intentionally small. App-specific adapters should
translate domain requests into the same generic launch/snapshot/action contract,
not receive new permissions. When an app exposes a standard deep link,
`ContentProvider`, share target, or `MediaSession`, that stable platform interface
should take precedence over semantic UI automation.
