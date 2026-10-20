---
status: accepted
---

# Model booking configuration archiving as lifecycle state

Booking configuration archiving uses an `ACTIVE` or `ARCHIVED` state on the
configuration rather than a deletion flag, archive-only routes, or a separate
archive capability. Owner and Manager may archive and restore through their
existing configuration-edit permission. Ordinary DELETE remains the archive
command and is retry-safe even when the configuration is already Archived.

Permanent deletion uses the same resource URI only when a caller explicitly
requests it. It is available for one configuration at a time, regardless of
lifecycle state, to a system administrator acting as themselves. It removes
live operational data but retains audit history. Inferring permanent deletion
from a second ordinary DELETE was rejected because retrying the same request
must not become destructive. Separate archive routes, Owner-only archive permission,
`enabled=false`, a parallel `deleted` flag, delegated permanent deletion, and
bulk permanent deletion were also rejected.

Archiving cancels every future confirmed booking and maintenance event on the
configuration in the same transaction. Those events remain readable as
cancelled rows; archiving does not soft-delete them.
