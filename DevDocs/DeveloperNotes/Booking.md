# Booking

A booking configuration makes one Inventory instrument bookable. It stores its scheduling
timezone and rules, copied from instance defaults at creation. Later defaults changes do not
alter existing configurations. A time-slot booking stores a half-open UTC interval, requester,
optional purpose, state and audit data. Intervals touching at an endpoint do not overlap.
Cancellation changes `CONFIRMED` to `CANCELLED` without soft-deleting the row; cancelled
events remain readable and do not occupy confirmed-only calendars or overlap checks.

Booking's REST API v2 resources use the shared collection, query, version-precondition and
problem-details contracts in [Collections](RestApiV2Collections.md) and
[Queries and access](RestApiV2QueriesAndAccess.md). `bookingEnabled` gates the feature;
see [Feature flags](FeatureFlags.md).

## Configuration lifecycle

`POST /api/v2/booking-configurations` and
`POST /api/v2/booking-configurations/bulk` permit creation only by the target
Inventory instrument's owner or a sysadmin subject. Inventory read or edit
access alone is insufficient. Run-as uses the impersonated user's permissions,
with the originating sysadmin retained as audit actor. Bulk creation checks
every target before saving; a mixed authorized/unauthorized batch is rejected
without saving any configuration.

Booking configurations use `ACTIVE` and `ARCHIVED` states. Archiving and restoring use the
configuration-edit capability, held by Booking Owners, Managers and implicit sysadmin Owners.
There is no separate archive capability or deletion flag.

Ordinary `DELETE /api/v2/booking-configurations/{id}` archives the configuration.
Repeating it on an archived configuration is a no-op when the supplied version is
current; it never becomes a permanent delete. Restore with a state-only
`PATCH` containing `{"state": "ACTIVE"}` before changing other settings.

Archiving cancels every future confirmed booking and maintenance event in the
same transaction. The events remain readable as cancelled rows without soft deletion.

`DELETE /api/v2/booking-configurations/{id}?permanent=true` permanently deletes
one configuration in either lifecycle state. Only a sysadmin acting as themselves
may do this. It removes live operational data and retains audit history. Bulk
permanent deletion is not supported.

Booking settings have a separate sysadmin-only grantee directory at
`GET /api/v2/booking-settings/access-grantees` for creation defaults.

## Access

Booking uses independent resource-role assignments. A configuration's access governs its
events, calendar, audit history and subscription eligibility, and grants no Inventory access.
Every reader of a configuration sees full event details; there is no busy-only Viewer role.
The generic assignment and effective-role rules are in
[Security and permissions](SecurityAndPermissions.md#resource-role-permissions).

| Role | Booking capabilities |
| --- | --- |
| Owner | Configuration, all events, role assignments, archive and restore |
| Manager | Configuration, all events, Manager/Booker/Viewer assignments, archive and restore; no Owner changes |
| Booker | Create bookings and change or cancel their own eligible bookings |
| Viewer | Read the configuration/calendar and create a personal calendar subscription |

At least one persisted Owner assignment to a user or supported group must remain.
Disabled users, deleted-group snapshots and groups without enabled members still count
structurally but grant no effective access. Their assignments remain removable and auditable.
Implicit sysadmin Owner access does not satisfy the persisted-owner invariant.
Run-as uses the represented subject's rights; the original actor supplies audit attribution.
Controllers pass both identities into services, which do not inspect Shiro or thread-local identity.

Creation copies default sharing: All users, selected users/groups, or nobody beyond the
creator, who becomes Owner. A new instance starts with All users. The All users audience
also covers subsequently created accounts. The highest direct, group or audience role wins;
a weaker direct role cannot reduce it. Leaving removes only one's direct assignment,
preserves group/audience access, and cannot remove the final persisted Owner.

The capability-protected access directory offers ordinary Owners and Managers their groups
and fellow group members. Sysadmins can select any active user or valid lab, collaboration
or project group. Communities are not selectable. The settings directory is sysadmin-only.
The implicit sysadmin Owner role permits repairing configurations with no effective persisted
Owner. After losing a role, a requester retains read-only access to their own past and future
bookings through the requester relationship, with no departure marker or configuration,
calendar, audit, assignment or subscription rights.

## Scheduling

Booking configurations and institution defaults expose one `openingHours` array.
Each row has an integral ISO `dayOfWeek` from 1 for Monday to 7 for Sunday, a
`start` in `HH:mm`, and an exclusive `end` in `HH:mm` or `24:00`. The start must
precede the end. Schedules contain one to seven unique weekdays and return in
weekday order. Omitted weekdays are closed. Initial defaults contain seven independent
`00:00` to `24:00` entries. To close all days, disable bookings; an empty week is invalid.

See [`BookingOpeningHoursCodec`](../../src/main/java/com/researchspace/model/booking/BookingOpeningHoursCodec.java)
and [`ApiV2BookingConfigurationResource`](../../src/main/java/com/researchspace/model/booking/ApiV2BookingConfigurationResource.java)
for schema declarations.

A supplied schedule replaces the whole week. Omission on create copies the
complete institution schedule; later defaults changes leave existing items
unchanged. Omission on update preserves the current schedule. Collection writes
reject explicit null, while the singleton PATCH treats null as unchanged.

Booking policy resolves each weekday in the scheduling timezone. Coverage is
end-exclusive and can join consecutive intervals at midnight. Maintenance keeps
its existing exemptions from opening hours and maximum duration. Changing the schedule advances the configuration version and requires
`EDIT_CONFIGURATION`. Creation and time edits validate the locked current configuration,
serializing concurrent settings changes. Existing bookings are not moved or cancelled.

Apply the additive independent-hours migration before running this application.
Legacy columns remain for migration and historical data; application code no
longer reads or writes them. See [booking database migrations](BookingDevelopment.md).

Buffers before and after confirmed bookings can differ. A maximum booking duration of `0`
disables the item limit; the absolute 527,040-minute (366-day) elapsed-time cap still applies.
Double booking permits booking-on-booking overlap, while maintenance conflicts with every
booking and maintenance event. Maintenance requires `CREATE_BLOCKOUT`, exposed as
`capabilities.canCreateBlockout`, and whole-minute endpoints. It bypasses opening hours,
closed weekdays, increment/alignment and the item maximum, but retains the absolute cap.

Booking Owners and Managers hold the maintenance capability.

Booking target references name the Inventory `instruments` resource, with stored kind
`INSTRUMENT` and global-ID prefix `IN`. Inventory read access and soft deletion apply
when resolving those references. A caller with booking access but no Inventory access
receives a null target. Expanded reads select the instrument fields they need with
`fields[instruments]`; parent locations expand through `parentContainer` to `containers`.
