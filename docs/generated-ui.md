# Generated UI v1

AgentOS renders a bounded interface description instead of compiling model output
as an APK. The machine-readable contract is `schemas/generated-ui.schema.json`.

Version 1 supports three block types:

- `paragraph`: non-interactive explanatory text
- `fact`: a label/value result returned by a capability
- `action`: a visible prompt that starts another agent turn

Unknown fields and block types are rejected. Text lengths and total block count are
bounded by the schema. An action contains a prompt, not an Android Intent or Binder
handle, so it re-enters policy evaluation before anything executes.

The Kotlin renderer currently consumes the equivalent typed model directly. JSON
deserialization will be introduced with schema validation at the model-process IPC
boundary, where untrusted data first enters the trusted shell.

