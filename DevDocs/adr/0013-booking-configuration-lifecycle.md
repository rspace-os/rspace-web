---
status: accepted
---

# Model booking configuration archiving as lifecycle state

Booking configuration archiving uses `ACTIVE` and `ARCHIVED` states rather than a deletion flag,
archive-only routes, or a separate archive capability. Archiving and restoring use the existing
configuration-edit permission. The current permission rules are documented in [Booking](../DeveloperNotes/Booking.md).

Ordinary `DELETE` remains a retry-safe archive command even for an already archived configuration.
Permanent deletion uses the same item URI only when the caller explicitly requests it. Inferring
permanent deletion from a second ordinary `DELETE` was rejected because a retry must not become
destructive. Permanent deletion is singular, requires a system administrator acting as themselves,
removes live operational data, and retains audit history.

Separate archive routes, Owner-only archive permission, `enabled=false`, a parallel `deleted` flag,
delegated permanent deletion, and bulk permanent deletion were rejected. They either duplicate the
existing lifecycle/edit contract, conflate availability with archival, or weaken the explicit
destructive-action boundary.
