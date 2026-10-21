# Implementation plans

Generated 2026-09-08; extended 2026-09-21. Execute in order unless dependencies say otherwise.

| Plan | Title | Priority | Effort | Depends on | Status |
|---|---|---|---|---|---|
| 001 | Implement booking details and configuration pages | P1 | L | — | IN PROGRESS |
| 002 | Booking acceptance test plan | — | — | 001 | EXISTING |
| 004 | Recurring booking events | — | L | 001, 006, 008 | TODO |
| 005 | Generalise TableList filtering across API v2 relationships and runtime fields | P1 | L | `dea38d23f` | DONE |
| 006 | Notify instrument owners about bookings and cancellations | P1 | L | — | DONE |

## Dependency notes

- Plan 005 uses the committed API v2/TableList baseline `dea38d23f`. It adds picker metadata,
  bounded restoration, catalog validation, and acceptance coverage while preserving the existing
  relationship and runtime-field query compilers.
- Plan 006 uses the current booking baseline `bb3d6dbb0`; it is independent of the TableList work and
  covers RSpace notifications, not calendar subscriptions.

- Plan 004 was revised on 2026-09-30 after a review against the booking stack at `90c0e11bb`. It
  depends on plan 006 and the subscription notifications (`.claude/plans/008-*`), because series
  actions must batch subscriber notifications, and on the cancellation lifecycle (reasons and
  restore), which has landed. The timezone, feature flag and prototype questions are decided; its
  remaining v1 contract defaults still need product confirmation.

## Status values

`TODO`, `IN PROGRESS`, `DONE`, `BLOCKED` with a one-line reason, or `EXISTING` for plans authored
before this index was reconciled.
