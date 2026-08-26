# Conversation history and knowledge view

AgentShell keeps all completed task records in a private Room database backed by
SQLite in credential-protected app storage. Room provides compile-time DAO query
validation and an explicit migration from the earlier hand-written schema. Each record contains the user's bounded prompt,
the actual result title, a timestamp, and a random identifier. The dedicated
knowledge screen uses a virtualized list to render every conversation and every
semantic relation instead of truncating the view, and offers an explicit delete-all
action. Existing bounded SharedPreferences history is migrated on first open.

The semantic view is an interactive mind-map canvas rather than a fixed list. It
draws every entity and labeled relationship, supports two-finger pan/zoom from
0.35× to 4×, provides accessible zoom/reset buttons, and keeps the detailed evidence
cards below it. Tapping a node edits its name/type. Relationship cards edit the
predicate and target or delete that edge; original evidence remains visible.

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
