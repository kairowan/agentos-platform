# ADR 0002: Room-backed editable knowledge graph

## Status

Accepted

## Decision

AgentShell stores conversation turns, entities, and evidence-bearing relations in
Room over the existing private SQLite database. Database version 2 includes an
explicit migration from the original hand-written SQLite schema, so installing an
upgrade preserves existing history.

The history screen renders every stored entity and relation on one interactive
Compose canvas. Users can pan, zoom from 0.35x to 4x, rename/retype entities, edit
relation predicates and targets, or remove relations. Editing a relation keeps its
original source evidence and marks it as user-confirmed.

## Consequences

- DAO queries and schema changes receive compile-time validation.
- No server is required for local history or graph browsing.
- Large graphs remain complete and navigable, but automatic layout is currently a
  deterministic type-column layout rather than a force-directed layout.
- Raw source evidence remains available after semantic corrections for auditability.
