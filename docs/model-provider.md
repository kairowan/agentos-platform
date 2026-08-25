# Model provider

AgentOS can use an OpenAI-compatible chat-completions endpoint as an optional,
unprivileged planner.

In the shell, open **模型连接**, enter:

1. an HTTPS chat-completions endpoint;
2. its model name;
3. an optional API key;
4. enable the remote endpoint.

The key exists only in process memory and is cleared when the process exits. AgentOS
does not persist or log it. Enabling the provider sends the user's prompt to that
provider, so users must review the provider's privacy terms.

Cleartext HTTP is rejected except for `localhost`, `127.0.0.1`, and `10.0.2.2`, which
support a developer-owned model on the device, host, or Android emulator host.

The response must be a single generated-UI v1 JSON object. Fenced Markdown, unknown
fields, unknown capabilities, invalid blocks, oversized content, non-success HTTP
responses, and timeouts are rejected. AgentOS then returns to its deterministic local
planner instead of giving the response additional authority.
