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
access; the transaction closes before the audit-file scan.
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
and no independent assignments. The read-only access component explains the Inventory
source. The server rejects assignment, ownership and
leave operations for inherited access. Independently managed resources keep
their existing edit behavior.

The migration permits a null `BookingConfiguration.resourceAccess_id`. New
configurations do not populate independent grants. Legacy rows remain for
migration/audit purposes but must never authorize Booking operations. Deploy
all application nodes together; an old node would still use the stale grants.
Do not roll back to independent ACL authorization without reconciling Inventory
permission changes. Destructive cleanup of legacy rows is a later migration.

Booking writes take a shared lock on the singleton calendar permission gate
before target, configuration and subscription locks. Permission-source writers
take its exclusive lock through `BookingCalendarRevocationCoordinator`. An outer
writer entry point must register with the coordinator before its first query,
lock, DML statement or possible Hibernate autoflush. A transaction must not try
to upgrade a shared gate to exclusive. The phase guard rejects registration
after revocation finalization starts. It cannot detect every first permission
mutation inside a transaction callback. A first-ever permission mutation
inside a transaction callback is therefore structurally prohibited.

The coordinator coalesces affected users and instruments. Before commit it
flushes the source changes, evaluates current scalar account, role, membership,
community, sharing and target rows with locking reads, deletes item links whose
owners have lost read access, and flushes the deletions. A failed read,
lock or deletion rolls back the whole source transaction. Configuration state
and feature availability are reversible and do not permanently revoke a token;
an actual loss of inherited read access does. Mixed source-to-item create or
rotate calls remain in the same transaction and use the same current facts.
User-wide link creation and rotation are rejected under an active or suspended
gate because they retain their independent `READ_COMMITTED` transaction.

Inventory and Booking currently have separate application entrypoints.
Inventory's Booking links use full page navigation, creating a new
Booking query cache. Returning to an already open Booking tab uses normal
React Query refetching on focus. Changes made in another session are not pushed
to the UI; server authorization remains authoritative on every request.

## Settings route access

The Settings route checks the current subject's sysadmin capability before fetching its
protected document. Client draft stability and refresh behavior are described in
See [Booking frontend](BookingFrontend.md#settings-access-and-draft-stability).

Creation and lifecycle rules are in [Booking](Booking.md#configuration-lifecycle);
maintenance exemptions are in [Scheduling](Booking.md#scheduling).

Cancellation and restoration are in [Booking cancellation reasons](Booking.md#booking-cancellation-reasons).
