# Capability Broker

The model and generated UI have no Android authority. They may request one typed
`CapabilityId`; the Broker resolves that ID to a system-owned implementation.

## Decisions

- `READ_ONLY`: execute immediately and record the result.
- `REQUIRES_CONFIRMATION`: issue an opaque, one-time token and wait for the trusted UI.
- `BLOCKED`: deny without calling the implementation.

Approval tokens are stored only in the Broker, removed before execution, and cannot
be reused. Exceptions become generic failures so implementation details do not leak
into model context. Every decision is appended to a bounded audit log without prompt
text, model output, arguments, or API credentials.

## Process boundary

Starting with v0.3, `AgentShell` and `AgentCapabilityService` are separate APKs,
processes, UIDs, and proposed SELinux domains. The shell has no capability
implementation and talks only through `IAgentCapabilityService`.

The service is protected by a signature permission and then independently checks the
Binder calling UID. Authorization succeeds only when that UID maps to exactly
`com.agentos.shell` and its signing certificate matches the service. Unknown package,
signature mismatch, and shared-UID ambiguity fail closed in runnable JVM tests.

The policy in `sepolicy/private` assigns the two packages separate domains and grants
only Binder communication between them. Full policy compilation and denial-log
verification remain gated on the first complete AOSP image build.

## Incoming events

The Broker process also owns `AgentNotificationListenerService`. Android grants that
listener access only after explicit user approval. It accepts message-category
notifications, rejects ongoing notifications and group summaries, bounds all text,
and sends the sanitized event to the Shell through a one-way AIDL callback.

Incoming notification content is not added to a remote-model prompt automatically.
Sending a reply is deliberately not implemented yet; it will require a separate
typed capability and a trusted one-time confirmation.
