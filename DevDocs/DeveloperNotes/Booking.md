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
configuration-edit capability, held by the target Inventory item's
owner or a sysadmin acting as themselves, as described in [Booking permissions](BookingPermissions.md).
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

Booking derives authorization from current Inventory permissions; see
[Booking permissions](BookingPermissions.md) for capabilities, requester retention,
run-as, access migration and transaction locking.

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

The instrument owner and a direct sysadmin subject hold the maintenance capability;
Inventory editors and readers do not.

Booking target references name the Inventory `instruments` resource, with stored kind
`INSTRUMENT` and global-ID prefix `IN`. Inventory read access and soft deletion apply
when resolving those references. A caller with booking access but no Inventory access
receives a null target. Expanded reads select the instrument fields they need with
`fields[instruments]`; parent locations expand through `parentContainer` to `containers`.

Booking settings GET responses (including `/admin`) return an `ETag`. PATCH
`/api/v2/booking-settings/admin` requires that tag in `If-Match`, with no body
version: missing tags return 428, malformed tags 400, and stale tags 412.
Successful PATCH responses include the updated tag. Shared conditional-request
parsing accepts known compression-proxy tag alterations.

When Booking is disabled, settings writes and configuration and booking writes
return 404, as do the feature-specific directory and catalogue reads.

The bounded `booking-configuration-targets` and `booking-settings/access-grantees`
lookups return `{docs: [...]}` without paging metadata. Catalogue lists retain
`docs` and their existing paging members.

## Scheduling timezone writes

The `timezone` field on `/api/v2/booking-configurations` is now read-only. It remains available in
GET responses as the item's scheduling timezone, but create, patch, and bulk requests that include
it receive the collection framework's normal `400 Bad Request` response. Public creates assign the
JVM-backed institution timezone. This is an immediate breaking change; internal Java manager
commands retain their timezone fields for fixtures and other trusted scheduling workflows.

## Booking windows and reconciliation

Booking creation requires a start strictly in the future and an end after the start.
Interval-changing edits use the same validation; edits to an already-started booking are
rejected as state transitions. Client draft and uncertain-write handling are documented in
[Booking frontend](BookingFrontend.md#booking-window-reconciliation).

## Booking Calendar query scope

`GET /api/v2/booking-catalogue/calendar` accepts `calendarStart`, `calendarEnd`,
`where` for item predicates, `eventWhere` for event predicates, `q`, `page`, and `limit`.
It resolves runtime fields through the same registered resource contexts as ordinary
collections. A correlated event EXISTS applies visibility, confirmed/nondeleted state,
half-open interval overlap and the complete event expression before resource counts and
pagination. Several matching events count the resource once.

`GET /api/v2/booking-calendar/events` accepts `start`, `end`, combined item/event `where`,
`q`, `page`, and `limit`. It applies the same mandatory event scope before pagination,
orders by start then ID, and renders the safe booking target projection. Both endpoints
require start before end and limit pages to 100 rows. Search matches name, exact inventory
ID, inventory-readable description, or visible booking purpose/requester name and username.
Description text is used only under the instrument read policy and is not added to the booking
target payload.

Calendar text search uses the compiler's `LikeEscaper` and `RsqlSqlFragments.like` helpers.
Instrument and location lookups also declare `!` as their SQL escape character to match
`LikeEscaper`. This makes `%`, `_`, and `!` literal search text. Calendar SQL lowercases both
operands for case-insensitive matching.

### Requester privacy

Booking filters people by the scalar `requesterId` on `bookings` and the Calendar events, and by
the Calendar's `bookedBy` text facet, which matches the requester's username, first name or last
name. There is no people relationship or picker. `requesterId` renders only for an event shown in
full, like `bookedBy` and `purpose`; a busy event renders `requesterId: null`.

Filtering on a requester must not reveal who booked an event that the caller sees as busy. An
event shows its requester when the caller can read its Inventory item or requested it.
`BookingRequesterFilters` pairs every comparison whose selector starts with `requester`
(`requesterId` and the Calendar's `requesterUsername`, `requesterFirstName` and
`requesterLastName`) with that rule, so a busy event never matches a requester comparison,
positive or negative. The caller's own events always satisfy the rule, so My Bookings'
`requesterId==<own ID>` is unchanged. `TimeSlotBookingResourceOperations` applies the guard to
list and count. `BookingCalendarQuery` applies it to the Calendar event filters, including the
`bookedBy` facet, and to the Calendar `q` search over purpose and requester names. The read policy
grants no busy-only rows today, and the guard does not depend on that.

### Booking target runtime fields

`booking-instruments` delegates to `instruments`: `target.customFields` supports filters and
projection; `target.extraFields` supports filters only. The catalogue preserves resolved runtime
bindings and trusted constraints while owning its paging, sorting and DTO projection. See
[relationship queries](RestApiV2QueriesAndAccess.md#relationships) for the shared contract.
