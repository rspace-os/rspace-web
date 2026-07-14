# Booking and Scheduling on REST API v2: Architecture and Delivery Contract

This document defines native booking for RSpace Inventory. The first release supports instruments.
Quantity requests may follow later.

Booking is the first consumer of the generic REST API v2. The calendar UI is out of scope.

Source: epic RSDEV-1187. The `C*` labels refer to the review issues listed below.

> **Assumption:** RSpace will build booking itself. Work must not start until the epic closes the
> build-versus-adopt decision.
>
> **Funded scope:** Phases 1–3 deliver end-to-end booking on REST API v2. Build only the generic API
> features that booking needs. Phase 5 tests the design with a second resource.

---

## Decisions (the contract)

| # | Decision | Rationale |
|---|---|---|
| Stack | Use Blaze Persistence with the existing Hibernate DAO style. Use JPA `EntityManager`, not Spring Data or native `Session`. | Fits the current stack and eases a later Jakarta move. |
| Entity location | Keep booking entities in `rspace-web`, under `com.researchspace.model.booking`. Use one-way Inventory links. | Avoids a dependency loop with `rspace-core-model`. |
| Reservation shape | Start with concrete `TimeSlotBooking`. Add a shared base type only when a second subtype exists. | Avoids an unproven inheritance model. |
| Target | Store a typed instrument FK on `BookingConfiguration`. Allow only one configuration per target (C7). | Keeps FK integrity and gives each target one lock row. |
| Availability | Require explicit `AVAILABLE` and `CLOSED` RRULEs, with limits (C11). | An item is not free unless a rule says it is. |
| Recurrence | Store each booking occurrence as a row. Extend series to a rolling horizon (C9, C10). | One-off and recurring bookings use the same overlap query. |
| Concurrency | Lock the configuration row, recheck its version, then insert (C7, C8). | Works across application nodes. |
| Time | Store occurrences as UTC `Instant`. Store an IANA timezone on each rule and series. | Named zones handle daylight-saving changes. |
| Lifecycle | Use `CONFIRMED` and `CANCELLED`, plus soft delete. Derive completion from the end time. | No completion job is needed. |
| Permissions | Full inventory `READ` is required to book. Only the booker, item owner, or sysadmin may change a booking. | Visibility alone does not grant write access. |
| Privacy | Mask private fields as busy/free from the first read release. Use the same rule for feeds. | Prevents purpose and requester leaks. |
| Bookability | Store the enabled flag on `BookingConfiguration`. | Booking stays local to this repo. |
| API | Build booking on REST API v2 from the start. Add generic features only when booking needs them. | Phase 5 checks reuse with another resource. |
| Writes | Authorize every row. Bulk writes are capped, ordered, rechecked, and atomic (C5). | No write path may bypass authorization. |
| Audit | Use Envers for row history and audit events for user actions (C12). | Together they show both prior state and the actor. |
| iCal | Use revocable, hashed feed tokens. Upgrade ical4j before adding recurrence (C6, C14). | Calendar clients cannot send API auth headers. |

---

## Review resolution key

| Ref | Concern retained in this contract |
|---|---|
| C1 | Limit generic-v2 delivery risk by growing it only through concrete consumers. |
| C3 | Apply read authorization before count, pagination, and projection. |
| C4 | Require full inventory `READ`, not `LIMITED_READ`, for booking and private details. |
| C5 | Make bulk writes capped, authorized per row, deterministic, and atomic. |
| C6 | Authenticate calendar subscriptions with revocable, hashed feed tokens. |
| C7 | Guarantee one configuration/lock row per target. |
| C8 | Detect every concurrent configuration input change before insert. |
| C9 | Route horizon materialisation through the same per-target lock path. |
| C10 | Persist and notify skipped recurring occurrences. |
| C11 | Bound RRULE expansion and distinguish malformed from oversized input. |
| C12 | Define Envers relation modes and actor-aware event auditing. |
| C13 | Specify indexes for overlap and recurrence hot paths. |
| C14 | Upgrade and verify ical4j before recurrence implementation. |

---

## Domain model

Put all entities in `rspace-web`, under `com.researchspace.model.booking`. Register them in
`hibernate.cfg.xml`.

Use the existing soft-delete fields and timestamps. Use `java.time.Instant` for new time fields.

### `BookingConfiguration` — per bookable item
- `id`
- `instrument_id`: nullable `@ManyToOne Instrument`; instruments are the only target in the first release
- `getTarget()` and `setTarget(InventoryRecord)` resolve the typed target
- `UNIQUE(instrument_id)` and exactly-one-target validation (C7)
- concurrent enablement returns 409 and may be retried
- `enabled : boolean` — the bookability toggle
- `timeZone : String` (IANA `ZoneId`) — per-item default tz
- `configurationVersion : long`; increment it when `enabled`, `timeZone`, the target, or an
  `AvailabilityRule` changes (C8)
- embedded `BookingEditInfo` with created/modified users and times
- *(future: minDuration/maxDuration/maxAdvance)*
- Lock this row `FOR UPDATE` when creating bookings or extending the horizon.

### `TimeSlotBooking` — concrete cut-1 booking entity
- `id`, `requester : @ManyToOne User`
- mandatory `bookingConfiguration : @ManyToOne BookingConfiguration`; map its FK with
  `updatable=false`
- To move a booking, cancel it and create a new authorized booking.
- `startTime : Instant` (UTC), `endTime : Instant` (UTC), `series : @ManyToOne BookingSeries` (nullable; null = one-off)
- `status : enum {CONFIRMED, CANCELLED}`, `purpose : String`, soft-delete + timestamps
- embedded `BookingEditInfo`
- nullable `cancelledBy`, `cancelledDate`, and `cancellationReason`
- Overlap checks query these materialized occurrence rows.

### `BookingSeries` — recurrence master (materialise-on-write)
- `id`, `requester`, `bookingConfiguration`
- embedded `RecurrenceDefinition` (`rrule`, `dtStart`, `tzId`, `durationMinutes`)
- `purpose`, `status`, and soft-delete fields
- `materializedUntil : Instant`; mark this horizon watermark `@NotAudited`
- embedded `BookingEditInfo`
- `1..* → TimeSlotBooking`

### `BookingSeriesSkippedOccurrence` — **new (C10)**; skipped materialisations
- `id`, `series : @ManyToOne BookingSeries`
- `slotStart : Instant`, `slotEnd : Instant`
- `reason : enum {OVERLAP, OUTSIDE_WINDOW, CLOSED, OTHER}`
- `conflictingBookingId : Long` (nullable), `bookingConfiguration`
- `notificationState`, timestamps
- Records occurrences that could not be inserted, so they can be reported and notified.
- Append only. Do not Envers-audit it. Publish an audit event for each insert.

### `AvailabilityRule` — per item; windows + closures (**expand-on-read**, never materialised)
- `id`, `bookingConfiguration`, embedded `RecurrenceDefinition`, `kind : enum {AVAILABLE, CLOSED}`, soft-delete
- embedded `BookingEditInfo`
- Any insert/update/delete **bumps `BookingConfiguration.configurationVersion`**.

### `RecurrenceDefinition` — shared recurrence value
- `@Embeddable` value used by `BookingSeries`, `AvailabilityRule`, and their request DTOs
- `rrule : String` (RFC-5545), `dtStart`, `tzId : String`, `durationMinutes : int`
- owns parsing, timezone checks, and duration checks
- managers apply their own horizon and occurrence limits

### `CalendarFeedToken` — **new (C6)**; `.ics` URL auth
- `id`, `owner : @ManyToOne User`, `tokenHash : String`; never store the raw token
- `scope : enum {USER_BOOKINGS, INSTRUMENT}` + typed nullable `instrument : @ManyToOne Instrument`
- database `CHECK`: `USER_BOOKINGS` requires no instrument; `INSTRUMENT` requires one
- `revoked : boolean`, `createdDate`, `lastUsedDate`, `expiryDate` (nullable)
- The token, not the API auth interceptor, identifies the feed owner and scope.
- Do not Envers-audit this table. Polling updates `lastUsedDate`; issue and revoke actions use audit events.

Availability rules expand when read. Booking series create rows because bookings need transactional
overlap checks and per-occurrence edits.

**Hot-path indexes (C13; Phase 1):**
- overlap query: composite `(booking_configuration_id, status, deleted, start_time, end_time)`
- `BookingSeries` lookup by config; `TimeSlotBooking.series_id`; **all FK columns indexed**
- `CalendarFeedToken(tokenHash)`; `AvailabilityRule(booking_configuration_id, kind)`

### Booking validity + lock sequence
The slot must be inside an `AVAILABLE` window, outside all `CLOSED` periods, and free of confirmed,
active bookings. Do not expand RRULEs while holding the lock.

1. Read the version, enabled flag, timezone, target, and rules. Expand and validate outside the lock.
2. `SELECT ... FOR UPDATE` the config row.
3. Re-read those values. Reject disabled or retargeted items. Retry if an expansion input changed (C8).
4. Recheck overlap against confirmed rows, then insert.
5. Commit and release the lock.

The horizon job uses the same configuration lock, checks, and insert path (C9). Its job lock only
prevents two horizon jobs from running together.

### RRULE guardrails (C11)
Apply these limits to availability and booking recurrence:

- maximum query range, occurrence count, duration, and horizon
- clamp rules without `UNTIL` or `COUNT` to the horizon
- return 422 for a valid rule that exceeds a limit
- return 400 for a malformed rule

---

## Auditing (C12 expanded)

Booking uses both RSpace audit systems.

Envers stores earlier row values. `AuditTrailService` records actions for the sysadmin Audit Trail UI.
One cannot replace the other.

### Layer 1 — Envers row history

Audit `TimeSlotBooking`, `BookingSeries`, `AvailabilityRule`, and `BookingConfiguration`.

For `User` and `Instrument` relations, use
`@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)`. Booking audit tables must not
depend on audit tables from another repository.

Do not audit:

- `CalendarFeedToken`; polling changes `lastUsedDate` too often
- `BookingSeriesSkippedOccurrence`; it is already append-only audit data
- `BookingSeries.materializedUntil`; horizon updates do not represent user changes

If only `materializedUntil` changes, Envers must not create a revision.

`REVINFO` records when a change happened, but not who made it. Store the actor on each audited row
with embedded `BookingEditInfo`.

`TimeSlotBooking` also stores the cancellation user, time, and reason. Changing `REVINFO` for the
whole application is out of scope.

### Layer 2 — audit-trail events

Managers publish booking domain events. `BookingAuditTrail` listens after commit and creates
`GenericEvent` records.

Publish from managers so single and bulk writes use the same path. After-commit listeners ensure
rolled-back work is not logged.

Add `AuditDomain.BOOKING` and a non-person `SYSTEM` actor to `rspace-audit`. Consume the change through
a JitPack version bump. Never install it locally.

Annotate audited event objects with `@AuditTrailData`. Mark the stable id with
`@AuditTrailIdentifier`. Expose only non-sensitive report fields with `@AuditTrailProperty`.

An integration test must confirm that the saved domain is `BOOKING`, not `UNKNOWN`. Reuse existing
audit actions.

| Booking action | `AuditAction` | Actor | Description carries |
|---|---|---|---|
| Create one-off booking | `CREATE` | requester | instrument global id, slot |
| Create series | `CREATE` | requester | rrule, tzId, occurrence count |
| Modify occurrence/series | `WRITE` | modifier | changed fields summary |
| Cancel occurrence/series | `WRITE` | canceller | `cancelled`, reason, scope (occurrence/series) |
| Soft-delete | `DELETE` | actor | — |
| Horizon materialisation | `CREATE` | `SYSTEM` | `materialised by horizon job`, series id + requester — never attribute scheduler work to the requester |
| Skipped occurrence (C10) | `WRITE` on series | `SYSTEM` | skip reason, slot, conflicting booking id + requester |
| Config enable/disable, tz change | `WRITE` | actor | old → new |
| `AvailabilityRule` change | `WRITE` on config | actor | rule kind + rrule |
| Feed token issue / revoke | `CREATE` / `DELETE` | owner | token **id** + scope — never the raw token or its hash |

Do not create events for booking reads or feed fetches. `lastUsedDate` is enough for feed activity.

### What answers what

| Question | Source |
|---|---|
| Who cancelled/modified booking X and why? | Audit Trail UI (layer 2); `cancelledBy`/`cancellationReason` on the row |
| What did booking X look like before the edit? | Envers `_AUD` (layer 1) |
| Which occurrences never got created, and why? | `BookingSeriesSkippedOccurrence` + its layer-2 events |
| Is this feed token still in use? | `lastUsedDate` |
| Usage/billing reporting (deferred) | layer 2 events + `_AUD` cancellation history are the feedstock; no extra capture needed now |

Tests must cover every action above. Each mutation creates one event and, where required, one Envers
revision.

Also test that watermark-only updates and rollbacks create no audit record. A feed fetch should only
update `lastUsedDate`.

---

## Service / DAO layer

- **`BookingManager`**: create, change, and cancel bookings. Owns locking, version checks, overlap checks, and inserts. It is the v2 booking write handler.
- **`BookingConfigurationManager`**: enable booking, set timezones, and manage availability rules.
- **`AvailabilityManager`**: calculate free/busy time from rules, closures, and bookings.
- **`BookingHorizonManager`**: safely extend recurring series from a scheduled job (C9).
- **`CalendarFeedManager`**: issue, rotate, revoke, and resolve feed tokens (C6).
- **`BookingIcalGenerator`**: format `.ics` output. It is stateless and owns no transaction.
- **`BookingAuditTrail`**: turn committed domain events into audit events.
- **`BookingDao`**: run overlap, calendar-range, and keyset queries with JPA and Blaze.

Apply authorization in the DAO query, before paging (C3).

---

## REST API v2 (booking is the first consumer)

Build booking directly on the generic v2 engine. Do not build a separate booking API first.

Add only the generic features booking needs. Phase 5 tests reuse with a second resource.

### Generic v2 engine
- **Bootstrap:** create Blaze `CriteriaBuilderFactory` and `EntityViewManager` from `SessionFactory`.
- **`V2ResourceConfig<T>`:** define the view, write handler, authorization policy, and optional row-visibility policy for one resource.
- **`V2QueryManager`:** apply RSQL, authorization, projection, and paging in that order (C3).
- **`V2WriteManager`:** authorize each row, then call the resource write handler.
- **`V2Controller`:** provide list, get, create, patch, delete, count, query, and bulk endpoints.

Fields are denied by default. A field is exposed only when its view declares it. `@ApiField` controls
read, default inclusion, filter, sort, write, role, and visibility access.

Do not declare excluded fields. They must also be unavailable to `select`, filter, sort, and writes.

For role-based access, remove gated fields from the view and allow-lists. For row-based access, use
SQL `CASE` mappings from `RowVisibilityPolicy`; do not switch the view type per row.

A page may mix full and restricted rows. Users must not filter or sort by hidden raw fields.

Fail at startup if a computed field is filterable or sortable, an unwritable view allows writes, or
a role-gated field is missing from its view.

Bulk writes follow one transaction (C5):

1. Select at most `cap + 1` row ids and required aggregate ids. Reject an oversized batch.
2. Lock aggregate ids in order, then row ids in order.
3. Reload and authorize each row.
4. Recompute aggregate ids. Roll back and retry if they differ from the locked set.
5. Apply every change, or roll back the whole batch on any failure.

Rows that match after the snapshot are not part of the batch. Never rerun an unlocked `where` query.
Make aggregate ownership immutable where possible.
- **Success envelopes:** two OpenAPI 3.0 component schemas, selected with `oneOf`:
  - offset: `docs, totalDocs, limit, page, totalPages, hasNextPage, hasPrevPage, prevPage, nextPage`;
  - cursor: `docs, limit, hasNextPage, hasPrevPage, nextCursor, prevCursor`

Cursor fields are nullable. Cursor pages do not include a total. Append immutable `id` to the sort
order and encode the full sort tuple. Reject cursors created for another resource or sort.
- **Error envelope**: see [Error shape](#error-shape-i18n).

### Booking resources
- `/api/inventory/v2/bookings`, `/availability-rules`, `/booking-configurations` — standard v2 resources backed by the engine.
- Serve `.ics` feeds outside header-authenticated `/api/**`, or explicitly allow their token auth.
- Full inventory `READ` is required to book (C4). Only the booker, owner, or sysadmin may change it.

### Per-instrument iCal subscription links
Each bookable instrument has a read-only subscription URL for calendar clients.

- **Issue:** from the instrument view, `POST /api/inventory/v2/booking-configurations/{id}/calendar-feeds`
  creates an instrument-scoped token owned by the requester. Return the raw token once and store only
  its hash.
- **Subscribe:** `GET /calendar/instrument/{instrumentId}.ics?token=<raw>` — served **outside**
  `/api/**`. Resolve the token, reject revoked or expired tokens, and update `lastUsedDate`.
- **Contents:** one `VEVENT` per `CONFIRMED`, non-deleted `TimeSlotBooking` in a bounded forward
  window. Each event has a stable booking UID and `LAST-MODIFIED`. Closed periods are omitted by default.
- **Privacy:** use the token owner's current permission. Show full details or only time and `Busy`.
- **Lifecycle:** list, revoke, and rotate feeds. Revocation is immediate. Use a short `max-age` and
  `Cache-Control: private`.
- **Security:** the URL token is a bearer credential. Use one token per feed, with revocation and
  optional expiry. Never use an API key.

A personal feed uses the same design with `scope=USER_BOOKINGS` and `requester = owner`.

### Error shape (i18n)

Extend the existing `ApiError` format. Resolve every user-facing message from a bundle key.

Each error contains a stable code and a server-localized message:

```json
{
  "status": 409,
  "errorId": "b3f1c2a4-2e90-4e2b-9a0c-1d2e3f4a5b6c",
  "timestamp": "2026-07-01T10:15:00Z",
  "errors": [
    {
      "code": "booking.series.conflict",
      "message": "Booking series rejected: 2 occurrence(s) conflict with existing bookings.",
      "field": "recurrence",
      "args": [2],
      "details": {
        "conflicts": [
          { "slotStart": "2026-07-14T12:00:00Z", "slotEnd": "2026-07-14T13:00:00Z",
            "reason": "OVERLAP", "conflictingBookingId": "BK1044" }
        ]
      }
    }
  ]
}
```

Rules:

- `code` is a stable, untranslated key from the Inventory message bundle.
- `message` is resolved with the request locale. Clients may instead localize `code` and `args`.
- `args` is an ordered array of strings, numbers, or booleans. Bundle templates use `{0}`, `{1}`, etc.
- `details` is structured, untranslated data. Treat its schema as an opaque object.
- Omit optional `field`, `args`, and `details`; do not return them as `null`.
- `field` uses dotted paths such as `recurrence` or `target.id`.
- One response may contain several errors, including one per failed bulk row.

Sample bundle entries (`bundles/inventory/ApplicationResources.properties` or an `inventory`-scoped bundle):

```properties
booking.series.conflict=Booking series rejected: {0} occurrence(s) conflict with existing bookings.
booking.outside_window=Requested time is outside the instrument's available hours.
booking.rrule.too_large=Recurrence exceeds the allowed limit of {0} occurrences.
booking.not_bookable=This item is not bookable.
booking.forbidden=You do not have permission to modify this booking.
```

#### Schema compatibility (Standard Schema / valibot / zod / arktype)

The OpenAPI schema is the source of truth. Generate the frontend validator from it; do not maintain a
second schema by hand.

The generated validator may use Valibot, Zod, or ArkType through Standard Schema.

Keep the schema easy to generate:

- use a fixed envelope and one error-item shape
- keep `code` as an open string; publish a separate generated union if clients need one
- use a flat scalar array for `args` and an opaque object for `details`
- omit optional values instead of returning `null`
- use OpenAPI 3.0 `oneOf`, not JSON Schema type arrays

Equivalent JSON Schema (OpenAPI component) and the validator it generates:

```jsonc
// components.schemas.ApiErrorEnvelope
{
  "type": "object",
  "required": ["status", "errorId", "timestamp", "errors"],
  "properties": {
    "status":    { "type": "integer" },
    "errorId":   { "type": "string" },
    "timestamp": { "type": "string", "format": "date-time" },
    "errors": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["code", "message"],
        "properties": {
          "code":    { "type": "string" },
          "message": { "type": "string" },
          "field":   { "type": "string" },
          "args":    { "type": "array", "items": {
                         "oneOf": [
                           { "type": "string" },
                           { "type": "number" },
                           { "type": "boolean" }
                         ]
                       } },
          "details": { "type": "object", "additionalProperties": true }
        }
      }
    }
  }
}
```

```ts
// generated (valibot) — identical inference under zod/arktype via Standard Schema
const ApiErrorItem = v.object({
  code:    v.string(),
  message: v.string(),
  field:   v.optional(v.string()),
  args:    v.optional(v.array(v.union([v.string(), v.number(), v.boolean()]))),
  details: v.optional(v.record(v.string(), v.unknown())),
});
const ApiErrorEnvelope = v.object({
  status:    v.number(),
  errorId:   v.string(),
  timestamp: v.pipe(v.string(), v.isoTimestamp()),
  errors:    v.array(ApiErrorItem),
});
```

### Worked API example (for review)

These examples show filtering, field selection, paging, privacy, recurrence errors, and calendar feeds.

**1. List instrument IC42's confirmed July bookings (read).**

```
GET /api/inventory/v2/bookings
    ?filter=target.id==42;status==CONFIRMED;startTime>=2026-07-01T00:00:00Z;startTime<=2026-08-01T00:00:00Z
    &select=id,startTime,endTime,status,purpose,requester.username
    &sort=startTime&limit=20&page=1
Header: apiKey: <key>
```

RSQL supports `== != > >= < <= =in= =out= =like=`. `;` means AND and `,` means OR. The
`BookingView` allow-list controls selectable, filterable, and sortable fields.

`200 OK` — `docs` shaped by `select` (full projection; caller has full READ on IC42):

```json
{
  "docs": [
    { "id": "BK1021", "startTime": "2026-07-07T13:00:00Z", "endTime": "2026-07-07T14:00:00Z",
      "status": "CONFIRMED", "purpose": "Confocal imaging - project X",
      "requester": { "username": "asmith" } },
    { "id": "BK1044", "startTime": "2026-07-09T09:30:00Z", "endTime": "2026-07-09T11:00:00Z",
      "status": "CONFIRMED", "purpose": "Training", "requester": { "username": "bcheng" } }
  ],
  "totalDocs": 37, "limit": 20, "page": 1, "totalPages": 2,
  "hasPrevPage": false, "hasNextPage": true, "prevPage": null, "nextPage": 2
}
```

The query masks each row in SQL. Users without full read get `Busy` and no requester or purpose.
Authorized users get full details.

Mask before paging so totals stay correct. Hidden fields must not be filterable or sortable (C3).

For deep pages, use `cursor`; `nextCursor` replaces `nextPage`. Send long filters to `POST /query`.

**2. Create a recurring booking (write → `BookingManager`).**

```
POST /api/inventory/v2/bookings
{
  "targetId": 42,
  "purpose": "Confocal imaging - project X",
  "recurrence": { "rrule": "FREQ=WEEKLY;BYDAY=TU;COUNT=20",
                  "dtStart": "2026-07-07T14:00:00", "tzId": "Europe/Amsterdam",
                  "durationMinutes": 60 }
}
```

`201 Created` returns the series and its materialized occurrences. In this timezone, occurrences
through Oct 20 are `12:00Z`; Oct 27 and later are `13:00Z`.

```json
{ "seriesId": "BS204", "rrule": "FREQ=WEEKLY;BYDAY=TU;COUNT=20", "tzId": "Europe/Amsterdam",
  "materializedUntil": "2026-11-17T13:00:00Z", "occurrenceCount": 20, "status": "CONFIRMED" }
```

Reject the whole series if any occurrence clashes. Return 409 with `booking.series.conflict`.

Return 403 for forbidden creates, 422 for oversized recurrence, and 400 for malformed RRULEs (C11).

**3. Issue + subscribe to the instrument's iCal feed.**

```
POST /api/inventory/v2/booking-configurations/7/calendar-feeds      → 201
{ "url": "https://rspace.example.org/calendar/instrument/42.ics?token=9f3c...e1",
  "token": "9f3c...e1", "scope": "INSTRUMENT" }       (raw token shown once)

GET /calendar/instrument/42.ics?token=9f3c...e1                     → 200 text/calendar
BEGIN:VCALENDAR
BEGIN:VEVENT
UID:BK1021@rspace
DTSTART:20260707T130000Z
DTEND:20260707T140000Z
SUMMARY:Confocal imaging - project X
LAST-MODIFIED:20260612T101500Z
END:VEVENT
END:VCALENDAR
```

(Summaries collapse to `Busy` when the feed owner lacks full read.)

---

## Dependencies (pom.xml; subject to add-deps policy + 7-day release-age block)

- Blaze Persistence: core, entity-view, and Hibernate 5.6 modules. Use `javax`, not Jakarta artifacts.
- `rsql-parser`. Add QueryDSL only if Phase 0 proves it is useful.
- ical4j: upgrade first and recheck all transitive exclusions (C14).
- `rspace-audit`: add `AuditDomain.BOOKING` and the `SYSTEM` actor through a JitPack version bump.

Never install the audit library into the local Maven repository.

---

## Spring 6 / newer-version outlook (checked; do not pull forward)

Do not move to Spring 6 or Jakarta for this feature. That is an application-wide migration.

Blaze already provides keyset paging on the current stack. Hibernate 6 and Spring Data add nothing
needed here. Upgrade ical4j independently.

Use JPA `EntityManager` and Blaze so a later Jakarta migration is mainly an artifact and import change.

---

## Delivery roadmap (booking + v2 co-developed)

- **Phase 0 — Spikes.**
  - Bootstrap Blaze and choose updatable views or manager handlers.
  - Prove mixed-permission SQL masking before building the projection API.
  - Upgrade ical4j and test RRULE expansion (C14).
  - Keep QueryDSL only if it improves direct RSQL-to-Blaze mapping.
- **Phase 1 — persistence and reads (funded).** Add the v2 read engine, booking entities, Liquibase,
  Envers, indexes, paging, privacy, errors, and OpenAPI 3.0 schemas.
  - Return 401 for missing or invalid credentials and 403 for forbidden actions.
  - Apply authorization and row masking before paging (C3).
  - Add booking audit metadata and `AuditDomain.BOOKING`.
- **Phase 2 — recurrence (funded).** Add availability expansion, booking series, horizon work,
  skipped occurrences, and locked create/cancel operations (C9–C11).
- **Phase 3 — writes and feeds (funded).** Add generic writes, atomic bulk operations, booking
  changes, personal and instrument feeds, and series edits (C4–C6).
- **Phase 4 — hardening.** Test privacy, reporting, query performance, dependencies, and OpenAPI code generation.
- **Phase 5 — second consumer.** Move maintenance onto v2. Reuse controller validation and keep
  persisted-state checks in `MaintenanceManager`. Fix any booking-specific API assumptions.

Each phase needs unit tests for interval and recurrence logic. Add integration tests for locking,
authorization, horizon work, localized errors, auditing, and feed tokens.

---

## Deferred (captured, not built)

- Quantity requests and a shared reservation base type
- approval workflows
- "this and future" series edits
- partial acceptance of conflicting series
- maintenance or performer scheduling
- usage reports and billing
- external or guest booking
- per-item duration and advance-booking rules
- partial-overlap handling for recurring bookings
