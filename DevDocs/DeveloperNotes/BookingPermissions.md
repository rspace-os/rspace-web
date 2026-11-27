# Booking permissions

Booking configurations derive permissions from their target Inventory item.
The item's owner and a direct sysadmin subject manage scheduling and events.
Other Inventory editors can book and manage their own eligible bookings;
full-read users can view and subscribe. Limited read does not grant Booking
access. Run-as requests use the effective subject's permissions.

The requester can still read their own past and future bookings after losing
item access. The response redacts the item and configuration relationship;
the UI displays "Unknown item". This permits no editing, cancellation,
item-property filtering, audit access or reading other users' bookings.
There is no departure marker. Requester-only access grants no configuration,
calendar, access-management or subscription permission.

Audit permission checks use a read transaction to resolve current Inventory
access; the subsequent audit-file scan does not hold that transaction open.
After permanent configuration deletion, a sysadmin can use the audit endpoint
through the last retained Envers revision while the target Inventory item is
still available. Former owners cannot use this route to recover deleted data,
and ordinary configuration reads continue to return 404. Audit results retain
the existing completed-day snapshot boundary, so today's changes appear after
the next UTC day boundary. Booking and configuration audits both include events
by people outside the viewer's user directory: the Booking audit capability has
already authorized the viewer, and people who book an item often share no group
with its managers.

The generic REST API v2 access routes and editable component remain in place.
Booking access documents report `inherited: true`, derived caller capabilities
and no independent assignments. `InventoryAccessReadOnly` explains the Inventory source and renders read-only. The server rejects assignment, ownership and
leave operations for inherited access. Independently managed resources keep
their existing edit behavior.

The migration permits a null `BookingConfiguration.resourceAccess_id`. New
configurations do not populate independent grants. Legacy rows remain for
migration/audit purposes but must never authorize Booking operations. Deploy
all application nodes together; an old node would still use the stale grants.
Do not roll back to independent ACL authorization without reconciling Inventory
permission changes. Destructive cleanup of legacy rows is a later migration.

Booking writes take a shared lock on the singleton calendar permission gate
before target, configuration and subscription locks. Bulk configuration archive first
locks all instrument targets in id order, then their owners in id order, then configurations
in id order. Cancellations and notification selection follow while those locks are held. Permission-source writers
take its exclusive lock through `BookingCalendarRevocationCoordinator`. An outer
writer entry point must register with the coordinator before its first query,
lock, DML statement or possible Hibernate autoflush. A transaction must not try
to upgrade a shared gate to exclusive. The phase guard rejects registration
after revocation finalization starts, but it cannot detect every first permission
mutation made inside a transaction callback. A first-ever permission mutation
inside a transaction callback is therefore structurally prohibited.

The coordinator coalesces affected users and instruments. Before commit it
flushes the source changes, evaluates current scalar account, role, membership,
community, sharing and target rows with locking reads, deletes item links whose
owners have actually lost read access, and flushes the deletions. A failed read,
lock or deletion rolls back the whole source transaction. Configuration state
and feature availability are reversible and do not permanently revoke a token;
an actual loss of inherited read access does. Mixed source-to-item create or
rotate calls remain in the same transaction and use the same current facts.
User-wide link creation and rotation are rejected under an active or suspended
gate because they retain their independent `READ_COMMITTED` transaction.

## Permission-neutral entry points

`UserManager.recordLoginSuccess` persists login history, resets failure counters,
and applies the existing lockout policy's expiry unlock without registering a
revocation. `recordLoginFailure` evaluates `DefaultLockoutPolicy` in a neutral
transaction and conditionally writes the count, first-failure timestamp and lock
state, guarded by all previously read policy inputs. A null timestamp uses
`IS NULL`. Guard conflicts retry in fresh transactions up to three attempts.
A predicted new lock, or exhausted retries, starts a separate transaction that
registers before loading and locking the account, then re-evaluates the policy.
An expiry unlock and failures against an already locked account normally need no
gate. Admin unlock and general account saves keep their existing registration.
Login bookkeeping rejects invocation beneath an active or suspended gate.
The two public login entry points use `NOT_SUPPORTED` transaction advice; their
helper owns the fresh transactions. They do not retain an unused outer connection
while borrowing the connection used for bookkeeping.

`GroupManager.setPublicationAllowed` accepts a group id and boolean, loads and
requires both PI membership and WRITE permission in its service transaction, and updates only publicationAllowed.
It does not call the general group writer. All twelve group registrations retain
the exclusive gate and full `affectAll` sweep, including the other group settings.

Instrument PATCH registers before its first database access when the request
supplies `sharingMode` or `sharedWith`, including an empty list or unchanged value.
Content-only PATCH skips registration. This decision never compares persisted
state. Every PATCH then calls `InstrumentDaoHibernateImpl.lockById` before loading edit
state, including content-only PATCH. The DAO flushes pending edits, evicts the cached
instrument and reloads its root columns with locking HQL, without locking eager associations. This serializes content writes with sharing and owner changes, so an
ungated content write cannot replay stale permission or location fields. Deletion, restoration and owner transfer remain gated. Preference and
change-email saves retain the general user-save gate.

Inventory and Booking currently have separate application entrypoints.
Inventory's Booking links perform a full page navigation, which creates a new
Booking query cache. Returning to an already open Booking tab uses normal
React Query refetching on focus. Changes made in another session are not pushed
to the UI; server authorization remains authoritative on every request.

Creation and lifecycle rules are in [Booking](Booking.md#configuration-lifecycle);
maintenance exemptions are in [Scheduling](Booking.md#scheduling).

Cancellation and restoration are in [Booking cancellation reasons](Booking.md#booking-cancellation-reasons).

Personal notification subscription PUT takes the shared gate before instrument locks.
Its final access check uses the existing scalar current-access DAO in the caller's
transaction and connection. Account, role, membership, community and sharing checks
therefore see current committed rows even when the caller already has a REPEATABLE
READ snapshot or cached entities. The gate holds revocation until the write commits.
Feature availability remains reversible. Its final check also uses shared locking scalar
reads of the baseline and personal override, retaining deployment override precedence.

With `innodb_snapshot_isolation=ON`, a personal notification subscription PUT
joining an older snapshot can briefly return 409 when its current access read
is rejected with MariaDB error 1020. It fails closed: the subscription is not
changed. Retrying in a fresh transaction rechecks current permissions and
returns 404 if access has been revoked.
