# Conversation history and knowledge view

AgentShell keeps all completed task records in a private Room database backed by
SQLite in credential-protected app storage. Room provides compile-time DAO query
validation and explicit v1→v2→v3→v4→v5 migrations. New records contain the user's bounded prompt,
the full generated reply (all blocks), result title, timestamp, task state and IDs.
Older records retain their titles and are labeled incomplete, never fabricated.
The dedicated
knowledge screen uses a virtualized list to render every conversation and every
semantic relation instead of truncating the view, and offers an explicit delete-all
action. Existing bounded SharedPreferences history is migrated on first open.

The semantic view is an interactive mind-map canvas rather than a fixed list. It
draws every entity and labeled relationship, supports two-finger pan/zoom from
0.35× to 4×, provides accessible zoom/reset buttons, and keeps the detailed evidence
cards below it. Search covers node names/types, relation predicates, and source
evidence, highlights every match, and cycles through matches. Tapping a node edits
its name/type; long-press dragging stores its density-independent position in Room,
so custom layouts survive navigation and restarts. Only visible nodes and relevant
edges are painted while the complete graph remains stored and searchable.
Relationship cards edit the predicate and target or delete that edge; original
evidence remains visible. Edge deletion and delete-all require confirmation.

Cloud history sharing is off by default. The optional, process-session-only switch
shares up to six recent turn excerpts and twelve confirmed facts, within a 12,000
character JSON budget. Changing the endpoint resets consent. Memory is labeled
untrusted data, not authorization. Local `回顾上次任务` and `我的偏好` queries work
without a model. Recall is bounded lexical matching, not comprehensive semantic
retrieval; the complete history remains stored locally. Each new prompt is automatically
processed by an offline explicit-fact extractor. When the user has enabled a remote
model endpoint, that same current prompt is sent in a second structured extraction
request for broader people, relationship, preference, project, place, and long-term
fact coverage. Old history is never sent in bulk. Clearing application data or using
**清除全部** removes conversations, tasks, entities, relations, and provenance.
Deleting/correcting an exact relation suppresses later extraction of that triple
and excludes affected raw turns from model context. User-corrected facts are marked;
their old evidence stays local. Semantic paraphrases/aliases are not deduplicated.

Every relation stores its source turn, an exact evidence substring, confidence, and
confirmation state. Explicit local matches are marked **原文明示**. Broader model
extractions are stored and displayed as **模型候选**; evidence not found verbatim in
the source prompt is rejected. Candidate facts are visible memory, but they cannot
authorize capabilities or silently become trusted policy. Notification-derived
content remains excluded.

The Shell's task journal records state before capability dispatch. Restarts cancel
unfinished planning/confirmation and mark interrupted dispatch as **结果未知**;
they never restore tokens or automatically repeat an action. Late completions
cannot overwrite cancelled/terminal tasks or recreate cleared history. This is
honest recovery reporting, not a transactional rollback of Android side effects.
