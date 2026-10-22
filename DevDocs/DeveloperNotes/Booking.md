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

Booking tables are created with `openingHours` directly. Rebuild development
databases that used earlier stack schemas. See [booking database migrations](BookingDevelopment.md).
Audit snapshots use the same independent `openingHours` schedule as configuration reads.

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

The `timezone` field on `/api/v2/booking-configurations` is the item's scheduling timezone. Single
and bulk creates may set it to an IANA zone ID; blank or unknown zones receive `400 Bad Request`
from the entity validation. Creates that omit it are assigned the JVM-backed institution timezone.
It is immutable afterwards: patch and bulk-update requests that include it receive the collection
framework's normal `400 Bad Request` response. The Add bookable item form offers it as a searchable
select that defaults to the institution timezone; the edit form omits it.

## Booking windows and reconciliation

Booking creation requires a start strictly in the future and an end after the start.
Interval-changing edits use the same validation; edits to an already-started booking are
rejected as state transitions. Client draft and uncertain-write handling are documented in
[Booking frontend](BookingFrontend.md#booking-window-reconciliation).

## Booking Calendar query scope

`GET /api/v2/booking-catalogue/calendar` accepts `start` (inclusive), `end` (exclusive),
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

The `target` picker names `booking-instruments`, uses `globalId` identity and the `IN` prefix.
The shared picker metadata, traversal limits and runtime catalog allowlist rules are documented
in [Fields reached through a relationship](RestApiV2QueriesAndAccess.md#fields-reached-through-a-relationship).

## Audit formatting

See [Booking audit events](Logging.md#booking-audit-events) for snapshot rendering and historical
schedule handling.

## Scheduling error payloads

Some scheduling problems add RFC 9457 extension members. Each is present only on the codes that document it:

| Code | Status | Extension members |
| --- | --- | --- |
| `errors.api.v2.booking.overlap` | 409 | `conflict` |
| `errors.api.v2.booking.buffer` | 409 | `conflict`, `bufferBeforeMinutes`, `bufferAfterMinutes` |
| `errors.api.v2.booking.maximumDuration` | 400 | `maximumDurationMinutes` |

`conflict` is `{"id": 59, "kind": "BOOKING", "start": "2026-09-28T08:00:00Z", "end":
"2026-09-28T10:00:00Z"}`: the first event that blocks the requested interval, as UTC instants.
`kind` is `BOOKING` or `MAINTENANCE`. It never contains a purpose, requester, or other private
field, and the server does not format its times into `detail`. The server checks the requested
interval first; only when that is free does it check the interval widened by the item's buffers,
so `buffer` means that moving the booking slightly would resolve the conflict. Both checks apply to
create, time-changing edits, restoration, and maintenance. Double booking permits booking-on-booking
overlap; maintenance still conflicts with every booking and maintenance event.

## Booking catalogue availability quick filters

The server evaluates the All bookable items quick filters. Both catalogue calls accept the
catalogue's item filters (`q`, `target`, `where`, `type`, `location`, `capability`, `mine`) and the
caller's availability window:

| Parameter | Meaning |
| --- | --- |
| `start`, `end` | Today's window `[start, end)` as instants, at most two days long |
| `now` | The instant to classify; defaults to the server clock |

The client derives the window from today's date and the availability-window preference in its
display time zone, so the server applies no display wall-clock rule. `GET
/api/v2/booking-catalogue?availability=available-now|free-later-today` returns only the items in
that category, in catalogue order, with a matching `total`, and pages them like any other
request. `GET /api/v2/booking-catalogue/availability-counts` returns
`{ "availableNow": n, "freeLaterToday": m }` without rows. A missing window, a reversed or longer
window, or another `availability` value is a 400 response.

`BookingCurrentAvailability` classifies each item. Free time is the window's part inside the item's
opening hours, evaluated per date in its scheduling time zone through
`BookingSchedulingSettings.effectiveHours` (independent weekday entries, omitted weekdays closed, and a `24:00` close),
less every confirmed, non-deleted event widened by the item's buffers. Maintenance always counts;
bookings do unless the item allows double booking. Cancelled events never count. An item is
available now when `now` is free inside the window, and free later today when it is busy now and
free time follows before the window ends; before the window starts, any free time makes it free
later. The categories are exclusive, and a window collapsed by daylight saving matches neither.
Every event on a readable item is readable to the caller (`BookingEventReadAccess`), so the counts
disclose nothing the bookings collection does not. The manager reads the scope in keyset batches of
500 configurations, each with one event query, so requests never list the whole catalogue or bind more
than one batch of IDs.

## Location filters

Location is a single-target relationship with a global-ID prefix, so it publishes a `picker` like
`target` does. Clients derive the filter field, its operators, and its picker from metadata.

`booking-configurations` declares a filter-only `location` relationship to `containers`
(`IC` prefix). The Calendar event filter description declares it too, because the Calendar reuses
item filters as event filters. A bookable item's location is the immediate parent of its target
Instrument. Booking does not store the parent. `BookingLocationFilterManager` therefore rewrites each
`location` or `location.value` comparison in place, before the query compiles, into a
`target.value` comparison. The rewritten comparison lists the configured Instruments stored
directly in the named Containers that the caller can read, using Inventory's readable-Container
rule. The rewrite preserves the filter's Boolean structure. The relationship's ID property does not
exist, so a query path that skipped the rewrite fails instead of matching the wrong rows. The
following paths rewrite location filters:

- `booking-configurations` list, count, bulk update, and bulk archive
- `GET /api/v2/booking-catalogue`
- the Calendar resources (`where`) and events (`eventWhere`, and the combined `where` of
  `/api/v2/booking-calendar/events`)

| Filter | Meaning |
| --- | --- |
| `location==IC12`, `location=in=(…)` | Items stored directly in these readable Containers |
| `location!=IC12`, `location=out=(…)` | Readable items not stored in them |
| `location=exists=true` / `false` | Items with / without a readable immediate parent |

An unreadable Container contributes no items, exactly like one that does not exist, for rows and
totals. Workbenches share the Container ID space, so `IC<workbench ID>` names a workbench, while
`BE…` is rejected as an unsupported prefix. Configurations render `location: null`. Booking events render a `containers` reference at depth 0,
expanded through the container read policy at depth 1 or greater, or null when unreadable. The catalogue item's
`location` member carries the readable location.

A top-level AND location rule, as written by every filter builder,
does not list items. `BookingLocationFilterManager.resolveLocations(request, caller, targetPath)` turns
it into a correlated predicate from `BookingLocationQuery`, applied as a trusted restriction: the
target Instrument is readable, and it is (`==`, `=in=`, `=exists=true`) or is not (`!=`, `=out=`,
`=exists=false`) stored directly in a non-deleted parent the caller may read, among the named
Containers when the rule names some. Readability is the same readable-Container rule (the Container
itself, or a non-deleted child readable without role visibility). The predicate has no size limit
and matches the same rows as the listed form. Neither matches an unreadable target. Its
subquery and parameter names derive from `targetPath`, because the Calendar resources query can
hold an item rule and an event rule at once. It applies to:

- `GET /api/v2/booking-catalogue` and the item `where` of the Calendar resources, correlated
  through `bookingConfiguration.target`
- the Calendar resources `eventWhere`, correlated through the event subquery's
  `calendarEvent.bookingConfiguration.target`
- the combined `where` of `/api/v2/booking-calendar/events`, correlated through
  `booking.bookingConfiguration.target`

A location rule inside an OR group, and every `booking-configurations` list, count, bulk update and
bulk archive filter, still lists the matching items, and one that would bind more than 10,000 items
returns a complexity error. `BookingCatalogueControllerMVCIT` compares both forms of every operator
on all three endpoints, for the owner and for a principal investigator who can read an item only
through role visibility, inside a Container they cannot read.

The location picker searches `GET /api/v2/booking-catalogue/locations`. It lists readable parent
Containers and workbenches of active, enabled bookable items. `q` matches the name, or exactly
one `IC` or `BE` global ID. The repeatable `globalId` parameter, at most 100 values, restores saved
selections in one request. Unreadable and unknown IDs are omitted alike.

The catalogue's client filter mapping sends a supported top-level availability predicate as
the server's `availability` parameter with today's display-zone window. Contradictory categories
or an availability predicate inside an OR group cannot be restored as a server quick filter;
the client reports the unavailable rule and preserves the saved expression for explicit reset.

## Booking cancellation reasons

`PATCH /api/v2/bookings/{id}` accepts an optional `cancellationReason` with
`state: "CANCELLED"`. It is trimmed, blank becomes null, and the maximum length is
500 characters. A non-null normalized reason without cancellation is rejected with
`errors.api.v2.booking.cancellationReason.requiresCancel`. The API field validator
rejects overlong input with `errors.api.v2.booking.patch` and an `invalidParams`
entry for `cancellationReason`; the manager also enforces the limit with
`errors.api.v2.booking.cancellationReason.length`. Creation does not accept the field.

The readable field is nullable and redacted to null for BUSY readers. It has no query
capabilities. Clients with explicit field projections must include `cancellationReason`
to display it. For example, with the current version in `If-Match`:

```json
{ "state": "CANCELLED", "cancellationReason": "Instrument needs recalibration" }
```

Repeating a cancellation with the current version and the same normalized reason is a
no-op; a different reason returns a state-transition conflict.

A cancelled booking can be restored with `{ "state": "CONFIRMED" }` and its current version in
`If-Match`. Restoring re-books the same slot under today's rules. The start must still be in the future,
the bookable item must be enabled, and the slot must pass the current scheduling policy and be free
of other bookings and buffers (the usual `overlap` and `buffer` conflicts, with `conflict`). No other
field may change in the same request. The restore clears the cancellation reason, is audited as
`WRITE`, and notifies subscribers with a "restored" message. My Bookings uses it for Undo.
Replaying the original stale version after a successful cancellation uses the existing
concurrency error contract. Refresh to inspect the saved result rather than automatically
resubmitting with a newer version.

Maintenance cancellation stores and audits the reason but sends no notification.

## Typed scheduling increments

The time increment is stored as `slotGranularityMinutes` plus `incrementUnit`
(`MINUTES`, `HOURS` or `DAYS`) and `alignToIncrement`. The unit is persisted, so 24 hours
and 1 day stay distinct after a save. All arithmetic uses a typed rule. The
frontend's `domain/bookingIncrement.ts` and `BookingSchedulingSettings` on the server
implement the same contract, pinned by the shared vectors in
`src/test/resources/booking/incrementScenarios.json`, which both the JUnit and Vitest
suites read.

- Minutes and hours are elapsed time. A booking's elapsed length must be a positive whole
  multiple of the increment, so a 90-minute booking across the spring change still lasts
  90 minutes.
- Days are calendar days in the item's timezone and must be whole numbers;
  `slotGranularityMinutes` holds the day count times 1440 as a nominal value only. A Day
  booking ends at its starting local time, a whole number of increments of local
  dates later. Noon to noon may last 23, 24 or 25 hours. If that local time does
  not exist on the end date, the booking is rejected with
  `errors.api.v2.booking.dayEndMissing` instead of being moved through the gap. Day
  increments require every weekday to be open from 00:00 to 24:00.
- With "Start on clock intervals" on, the start's local minute of day must also be a
  multiple of the increment, counted from midnight in the item's timezone
  (`errors.api.v2.booking.alignment`). The end only follows the duration rule, so a
  17-minute aligned booking from 23:48 ends at 00:05.
- Alignment is effective only below 1440 minutes and never for Days. The stored flag is
  not rewritten, so returning to a shorter increment restores the user's choice.

The fallback for new configurations is 15 minutes, aligned. Maintenance is exempt from
the increment and alignment, as it is from opening hours and the maximum duration.

Settings validation checks the structure of the weekly schedule. An unaligned increment
must fit one continuous opening period; touching periods merge across midnight and
across Sunday into Monday. An aligned increment also needs a grid start inside such a
period with room for a whole increment, so a 60-minute aligned increment cannot fit
09:15 to 10:15. Bookings must still fit their dates' opening periods.

The maximum booking duration is always elapsed minutes. For minutes and hours it is 0
or a whole multiple of the increment. For Days it is 0 or at least the nominal increment
(`days × 1440`) with no divisibility check, because a calendar day across the autumn
change lasts 25 hours; a 1440-minute maximum therefore rejects that booking, and 1500
accepts it. The absolute cap is 527,040 elapsed minutes (8,784 hours), which some
366-calendar-day intervals exceed.

## Catalogue search

Booking catalogue text search matches instrument names, descriptions, readable immediate-parent
location names, and exact `IN` global IDs. Creation pickers send the required `capability` so
permission filtering happens before pagination and totals are calculated. Empty result sets
have no type facets. The administration collection also accepts exact instrument IDs in its
text search; invalid or out-of-range IDs remain ordinary name searches.
