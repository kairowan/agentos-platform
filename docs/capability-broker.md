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

The v0.2 Broker is in-process so the policy can be exercised on ordinary Android and
free CI. Its public contract and tests are the migration point for the future AIDL
service running outside the model process. The AIDL migration remains open because it
must be tested in a complete AOSP build with SELinux domains.

