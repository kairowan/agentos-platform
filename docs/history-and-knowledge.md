# Conversation history and knowledge view

AgentShell keeps at most 100 completed task records in its credential-protected app
storage. Each record contains the user's bounded prompt, the actual result title, a
timestamp, and a random identifier. The UI renders the newest 20 records as a
source-backed `goal -> result` mind map and offers an explicit local delete action.

History is not uploaded merely because a remote planner is configured. It is also
not injected into later model prompts in this implementation. Clearing application
data or using **清除本机历史** removes it.

This is intentionally a conversation map, not yet a semantic knowledge graph.
Automatically extracted people, preferences, relationships, or beliefs can be
wrong and can expose sensitive material. A future knowledge service must keep
provenance, let the model propose candidate memories, require user confirmation for
durable facts, support correction/deletion, and apply separate retention rules to
notification-derived content.
