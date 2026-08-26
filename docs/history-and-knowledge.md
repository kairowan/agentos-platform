# Conversation history and knowledge view

AgentShell keeps all completed task records in a private SQLite database in
credential-protected app storage. Each record contains the user's bounded prompt,
the actual result title, a timestamp, and a random identifier. The dedicated
knowledge screen uses a virtualized list to render every conversation and every
semantic relation instead of truncating the view, and offers an explicit delete-all
action. Existing bounded SharedPreferences history is migrated on first open.

History is not injected into later model prompts. Each new prompt is automatically
processed by an offline explicit-fact extractor. When the user has enabled a remote
model endpoint, that same current prompt is sent in a second structured extraction
request for broader people, relationship, preference, project, place, and long-term
fact coverage. Old history is never sent in bulk. Clearing application data or using
**清除全部** removes conversations, entities, relations, and provenance.

Every relation stores its source turn, an exact evidence substring, confidence, and
confirmation state. Explicit local matches are marked **原文明示**. Broader model
extractions are stored and displayed as **模型候选**; evidence not found verbatim in
the source prompt is rejected. Candidate facts are visible memory, but they cannot
authorize capabilities or silently become trusted policy. Notification-derived
content remains excluded.
