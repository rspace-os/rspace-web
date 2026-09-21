# Booking permissions

Booking configurations derive permissions from their target Inventory item.
The item's owner and a direct sysadmin subject manage scheduling and events.
Other Inventory editors can book and manage their own eligible bookings;
full-read users can view and subscribe. Limited read does not grant Booking
access. Run-as requests use the effective subject's permissions.

The requester can still read their own past and future bookings after losing
item access. The response redacts the item and configuration relationship;
the UI displays "Unknown item". This does not permit editing, cancellation,
item-property filtering, audit access or reading other users' bookings.

The generic REST API v2 access routes and editable component remain in place.
Booking access documents report `inherited: true`, derived caller capabilities
and no independent assignments. The access component explains the Inventory
source and renders read-only. The server rejects assignment, ownership and
leave operations for inherited access. Independently managed resources keep
their existing edit behavior.

The migration permits a null `BookingConfiguration.resourceAccess_id`. New
configurations do not populate independent grants. Legacy rows remain for
migration/audit purposes but must never authorize Booking operations. Deploy
all application nodes together; an old node would still use the stale grants.
Do not roll back to independent ACL authorization without reconciling Inventory
permission changes. Destructive cleanup of legacy rows is a later migration.

Booking writes hold a database permission fence until commit. A separate
`READ_COMMITTED` transaction loads the subject and target permission facts,
so initialized Hibernate memberships from an older transaction cannot authorize
a write after revocation. Instrument and configuration locks still protect
scheduling and item state. Paths that need those locks acquire them before the
permission fence. Callers must commit new users and Inventory items before
creating their Booking configuration.

The fence currently locks the seeded `BookingConfigurationDefaults` row with
ID 1. This serializes Booking writes with permission-change listeners and
subscription creation. It uses an extra database connection for fresh reads.
Keep the lock order as authoritative permission mutation locks, then the
Inventory target and Booking configuration where applicable, then the fence,
then subscription rows. If contention becomes material, replace the global
fence with scoped locks only after auditing every permission-changing path.

Inventory and Booking currently have separate application entrypoints.
Inventory's Booking links perform a full page navigation, which creates a new
Booking query cache. Returning to an already open Booking tab uses normal
React Query refetching on focus. Changes made in another session are not pushed
to the UI; server authorization remains authoritative on every request.
