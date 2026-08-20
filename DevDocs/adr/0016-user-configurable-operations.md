# 16. Stage 2: user-configurable operations, validated from the same definitions

Date: 2026-08-20

## Status

Accepted as direction; implementation unscheduled. Builds on DevDocs/adr/0006 (operations are
data) and, when implemented, replaces the config-copy mechanism of DevDocs/adr/0015.

## Context

DevDocs/adr/0006 anticipated an "end users configure their own operations" capability from
the start. DevDocs/adr/0015 gave the backend a verbatim copy of the checked-in
`operations_config.json` so the public API could enforce operation rules, explicitly
accepting the duplication as temporary. This ADR records, ahead of time, the decided
trajectory so stage-1 code is written to survive it.

## Decision

- Operation definitions move from a checked-in frontend file to server-owned,
  user-editable data. The storage design (database vs managed file), the editing
  surface, and who may edit are stage-2 design questions, deliberately not decided
  here.
- The backend serves the definitions to the frontend. The checked-in frontend copy
  and the backend resource copy are both deleted, along with the byte-equality drift
  test.
- Validation continues to be interpreted generically from the definition, by the
  same registry-and-validator shape DevDocs/adr/0015 builds; only the registry's source
  changes from a classpath resource to the user-editable store. The stage-1
  hard-coded rules are thereby removed without rewriting the validator.
- Definition schema validation (valibot on the frontend today, fail-fast parsing on
  the backend) becomes a write-time gate on the editing surface: an invalid
  definition is rejected at save time, never at operation time.

## Consequences

- The frontend/backend rule-drift class of bugs disappears structurally.
- New surface to design in stage 2: authorization for editing definitions, seeding
  the seven shipped operations as data, versioning semantics for in-flight
  remembered process values when a definition changes, and per-deployment scoping.
- Until then, DevDocs/adr/0015's copy plus drift test remains the operative mechanism.
