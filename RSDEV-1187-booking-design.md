# RSDEV-1187 — Booking & Scheduling on REST API v2: Domain Model + Implementation Plan

Backend design for native booking/scheduling of RSpace Inventory objects (instruments first;
biobank quantity-requests later), built as the **first consumer of a new generic REST API v2**.
Frontend (calendar UI) is **out of scope** here.

Source: epic RSDEV-1187. Decisions came from design grilling and plan review (resolutions at the end).

> **Assumption:** this plan takes the **build-native** branch. The epic still lists build-native vs
> adopt-open-source as open; this document assumes build-native is chosen.
>
> **Scope & funding:** booking and the **generic v2 API are co-developed** — booking is v2's first
> consumer and is built on the framework from Phase 1 (supersedes review comment C1). To contain the
> delivery risk C1 raised, the v2 framework is grown **only as far as booking exercises it** each
> phase (no speculative surface), and a **second consumer in Phase 5** validates that the abstraction
> generalises. The fundable basic build (tender core G2.1.4) is Phases 1–3: booking end-to-end on the
> v2 read+write engine.

---

## Decisions (the contract)

| # | Decision | Rationale |
|---|---|---|
| Stack | **blaze-persistence yes; Spring Data no.** Keep the Hibernate DAO style, but call JPA `EntityManager` from the existing `SessionFactory`, not native `Session`. | blaze fits. Spring Data is EOL-on-Spring-5 and foreign here. `EntityManager` also eases the later Spring 6/jakarta move. |
| Entity location | **Local in `rspace-web`** (`com.researchspace.model.booking`), registered in this repo's `hibernate.cfg.xml`. Inventory association is **unidirectional**. | Avoids the jitpack loop; core-model stays unchanged. Clustermarket is only weak precedent: those are unaudited JSON cache tables, not audited/permissioned/user-owned domain objects. |
| Inheritance | Abstract **`Reservation`**, **`@Inheritance(SINGLE_TABLE)`** + discriminator. `TimeSlotBooking` now; `QuantityRequest` defined but deferred; more subtypes allowed. | Matches inventory-side convention. Shared reservation engine for future approval/notification paths. |
| Target ref | **Typed nullable FK** (`InventoryRecordConnectedEntity` pattern), **instrument only in cut 1**, extensible `getTarget()`. No `@Any`. Stored on config row, **unique per target (C7)**. | `InventoryRecord` is multi-table, so no single polymorphic FK. Typed FKs keep FK integrity for overlap checks. |
| Availability | **Explicit** availability with **full RRULE** for windows + closures, under guardrails (C11). | Chosen over free-by-default. |
| Recurring bookings | **Materialised occurrences**: `BookingSeries` creates `TimeSlotBooking` rows to a rolling horizon. The job uses the same per-item lock + insert path as user creation (C9). Create-time conflict rejects the series; horizon-time conflict skips, records, and notifies (C10). Edits: this occurrence + whole series; this-and-future deferred. | One row type means one overlap query for one-off and recurring bookings. |
| Concurrency | **`SELECT ... FOR UPDATE`** on the config row, then validate and insert. Requires unique config-per-target (C7) and availability-version recheck (C8). Multi-node safe. | MariaDB has no exclusion constraint; JVM locks do not work across nodes. |
| Time | **Per-item timezone**; explicit **TZID per series/rule**; persisted occurrences are UTC `Instant`; display via `SessionTimeZoneUtils`. | RRULE recurrence needs a named zone for DST. |
| Lifecycle | `CONFIRMED`/`CANCELLED` + soft-delete. Completed is derived (`end < now`). Enum can grow later. | No transition job needed. |
| Permissions | **Book** = full inventory `READ`, not `LIMITED_READ`. **Cancel/modify** = booker, item-owner, or sysadmin only; PI/lab-admin/community-admin visibility does not grant mutation. Booking never grants item `WRITE`. Busy/free privacy later via a second projection. | Reuses `InventoryPermissionUtils` full-read checks and keeps mutation scope conservative. |
| Bookability | Toggle on local `BookingConfiguration` row (also the locked row), not a core-model column. | `InstrumentEntity` has no such field; booking stays local. |
| API | **Booking is built on the generic v2 API from the start** (it is v2's pilot consumer). Shape: `RSQL` → predicates → blaze entity views (`select`/`depth`, full/busy) → keyset/offset → Payload-style envelope, with an **i18n error envelope**. Framework grown only as booking needs it; second consumer validates generality (Phase 5). | Single unified surface; co-development chosen over extract-later (supersedes C1). |
| Writes | Generic CRUD requires mandatory per-row `AuthorizationPolicy.assert`; no bypass. Read authz is in-query (C3). Handler is pluggable: default updatable view; booking uses `BookingManager`. Bulk-by-`where`: preflight count, hard cap, authz before mutation, defined transaction semantics, reject batch if any row unauthorized (C5). | Safe only with mandatory per-row authz. |
| Audit | **Envers `@Audited`** on booking entities, with explicit relation `targetAuditMode` (C12). | Booking actions must be auditable/reportable. |
| iCal | Read-only `.ics` feeds use **hashed URL feed tokens**, not header auth (C6). Upgrade ical4j to the latest suitable release. | Calendar clients cannot send `apiKey`/Bearer headers. Only ~1 current call site uses ical4j, so migration is small; recurrence code starts on a current API (C14). |

---

## Domain model

All entities live in `rspace-web`, package `com.researchspace.model.booking`, and are registered in
`hibernate.cfg.xml`. Use RSpace soft-delete (`deleted`, `deletedDate`) and timestamps. New time
fields use `java.time.Instant` (Hibernate 5.6 maps it natively).

### `BookingConfiguration` — per bookable item
- `id`
- target FK: `instrument_id` (`@ManyToOne Instrument`, nullable) — typed-FK pattern, cut 1 = instrument only; `getTarget()/setTarget(InventoryRecord)` resolver
- **`UNIQUE(instrument_id)`** + exactly-one-target validation **(C7)**. Without this, two config rows can split the `FOR UPDATE` lock. Concurrent enablement → 409 + retry.
- `enabled : boolean` — the bookability toggle
- `timeZone : String` (IANA `ZoneId`) — per-item default tz
- `availabilityVersion : long` — bumped on any `AvailabilityRule` change (C8); checked under lock
- *(future: minDuration/maxDuration/maxAdvance)*
- **Locked `FOR UPDATE` during booking creation and horizon materialisation.**

### `Reservation` (abstract) — `@Inheritance(SINGLE_TABLE)`, `@DiscriminatorColumn("reservation_type")`
- `id`, `requester : @ManyToOne User`, `bookingConfiguration : @ManyToOne BookingConfiguration` (mandatory)
- `status : enum {CONFIRMED, CANCELLED}`, `purpose : String`, soft-delete + timestamps

### `TimeSlotBooking extends Reservation` — `@DiscriminatorValue("TIME_SLOT")`
- `startTime : Instant` (UTC), `endTime : Instant` (UTC), `series : @ManyToOne BookingSeries` (nullable; null = one-off)
- **The materialised occurrence row. Overlap detection queries these.**

### `QuantityRequest extends Reservation` — `@DiscriminatorValue("QUANTITY")` — *defined, impl deferred*
- `quantity : BigDecimal`, `unit`, `aliquots` … (stub)

### `BookingSeries` — recurrence master (materialise-on-write)
- `id`, `requester`, `bookingConfiguration`
- `rrule : String` (RFC-5545), `dtStart`, `tzId : String` (default from config tz), `durationMinutes : int`
- `purpose`, `materializedUntil : Instant` (horizon watermark), `status`, soft-delete
- `1..* → TimeSlotBooking`

### `BookingSeriesSkippedOccurrence` — **new (C10)**; skipped materialisations
- `id`, `series : @ManyToOne BookingSeries`
- `slotStart : Instant`, `slotEnd : Instant`
- `reason : enum {OVERLAP, OUTSIDE_WINDOW, CLOSED, OTHER}`
- `conflictingBookingId : Long` (nullable), `bookingConfiguration`
- `notificationState`, timestamps
- Needed because Envers cannot audit an occurrence that was never inserted, but skips must be reportable + notifiable.

### `AvailabilityRule` — per item; windows + closures (**expand-on-read**, never materialised)
- `id`, `bookingConfiguration`, `rrule`, `dtStart`, `tzId`, `durationMinutes`, `kind : enum {AVAILABLE, CLOSED}`, soft-delete
- Any insert/update/delete **bumps `BookingConfiguration.availabilityVersion`**.

### `CalendarFeedToken` — **new (C6)**; `.ics` URL auth
- `id`, `owner : @ManyToOne User`, `tokenHash : String` (store a hash, never the raw token)
- `scope : enum {USER_BOOKINGS, INSTRUMENT}` + `scopeTargetId` (nullable, e.g. instrument)
- `revoked : boolean`, `createdDate`, `lastUsedDate`, `expiryDate` (nullable)
- Feeds resolve the subject from the token, not the API auth interceptor.

**Two recurrence strategies:** `AvailabilityRule` expands on read (ical4j) because it is read-mostly.
`BookingSeries` materialises rows because bookings need transactional overlap integrity and
per-occurrence edits.

**Hot-path indexes (C13; Phase 1):**
- overlap query: composite `(booking_configuration_id, status, deleted, start_time, end_time)`
- `BookingSeries` lookup by config; `TimeSlotBooking.series_id`; **all FK columns indexed**
- `CalendarFeedToken(tokenHash)`; `AvailabilityRule(booking_configuration_id, kind)`

### Booking validity + lock sequence
Validity = inside an `AVAILABLE` window, outside every `CLOSED` period, and no overlap with a
`CONFIRMED`, non-deleted `TimeSlotBooking`. Do **not** expand RRULE under the lock:

1. Read `availabilityVersion`; compute candidates + window/closure validity **outside** the lock (ical4j expansion is CPU-bound).
2. `SELECT ... FOR UPDATE` the config row.
3. **Under the lock:** verify `availabilityVersion` is unchanged **(C8)**, otherwise release + retry; re-check overlap against `CONFIRMED` rows; insert.
4. Release.

The horizon job uses the **same** per-config lock + recheck + insert path **(C9)**. Its job-level
lock only prevents job-vs-job collisions, not job-vs-user races.

### RRULE guardrails (C11)
Apply to availability expansion and series materialisation:
- max query range (e.g. 12 months), expanded occurrences per request, occurrence duration, and horizon.
- unbounded rules (`RRULE` with no `UNTIL`/`COUNT`) are clamped to the horizon; never expand open-ended.
- valid RFC-5545 that exceeds limits → **422** with a clear message; malformed rules → **400**.

---

## Service / DAO layer

- **`BookingManager`** (`*Manager` → AOP-transactional): `createBooking` (single + series materialisation), `cancel` (occurrence/series), `modify`. Owns lock/version-recheck/overlap/insert. Entities `@Audited`. Registered as the v2 write handler for bookings.
- **`BookingConfigurationManager`**: toggle bookability, set tz, manage `AvailabilityRule`s (bumps `availabilityVersion`), enforce unique-config-per-target.
- **`AvailabilityService`**: free/busy for `[from,to]` = expanded `AvailabilityRule`s (ical4j, guardrailed) − closures − confirmed bookings.
- **`BookingHorizonService`** + `@Scheduled` job: multi-node-safe via DB lock row; rolls materialisation forward through the per-item lock path (C9).
- **`CalendarFeedService`**: issue/revoke/rotate `CalendarFeedToken`s; resolve `.ics` requests by token (C6).
- **`BookingIcalService`**: ical4j `.ics` generation.
- **`BookingDao`/`BookingDaoImpl`**: Hibernate DAO via JPA `EntityManager` (unwrapped from current `Session`) + blaze. Handles overlap, calendar-range, and my-bookings keyset queries. **Authorization predicate is in-query (C3)**, never post-paging.

---

## REST API v2 (booking is the first consumer)

Booking is built directly on the generic v2 engine; there is no separate "booking-specific then
generalise" track. The engine is grown only as booking exercises it, and a second consumer (Phase 5)
proves it generalises.

### Generic v2 engine
- **Bootstrap** `@Configuration`: blaze `CriteriaBuilderFactory` + `EntityViewManager` from `SessionFactory` (Hibernate 5.6 exposes it as `EntityManagerFactory`).
- **`V2ResourceConfig<T>`** per resource: read entity-view(s), write handler, `AuthorizationPolicy<T>`. Booking registers `BookingView`/`BookingBusyView`, `BookingManager`, and the booking authz policy.
- **Field policy (generic include/exclude).** The blaze entity view is a **default-deny field allow-list**: a field is exposed only if declared on the view, and each declared attribute carries an `@ApiField` policy reflected once at startup into the projection, `select` allow-list, RSQL filterable set, sort set, writable set, and OpenAPI schema. `@ApiField` capabilities (safe defaults): `read` (true), `defaultInclude` (true; `false` = opt-in via `select`/`depth`, e.g. the `me` navigation block), `filter` (false), `sort` (false), `write` (false), and `roles`/`visibility` (a per-request gate).
  - **Exclude a field** → don't declare it (or `read=false`): invisible on the wire, in `select`, `filter`, `sort`, and writes. (This is how `canUserLoginNow`/`activeNow` are kept off the maintenance API.)
  - **Role-gated visibility** (`config` public vs sysadmin) → `roles=`; the framework strips gated fields from the projection + allow-lists per caller. **Per-row** visibility (users connected/unconnected, booking full/busy) → register multiple views + a selector, since a static role gate can't express per-row.
  - **Fail-fast at boot:** `filter`/`sort` on a non-queryable (computed/virtual) attribute → error (prevents the virtual-field filtering trap); `write` on a non-updatable view → error; a `roles`-gated field must exist on its view.
- **`V2QueryService`** (read): `RSQL → predicate → blaze → entity-view projection → PagedList/keyset`, with the **authorization predicate injected before count/pagination/projection (C3)**. Filterable/selectable/sortable fields = the entity-view allow-list.
- **`V2WriteService`** (write): create/update/delete (single + bulk); **mandatory per-row `AuthorizationPolicy.assert`, no bypass**; pluggable handler (booking → `BookingManager`). Bulk-by-`where`: preflight count, hard cap, authz before mutation, defined transaction semantics, reject batch if any row unauthorized (C5).
- **`V2Controller`** base (Spring MVC, building on the inventory API conventions): `GET` list/`{id}`, `POST`, `PATCH/{id}`, `DELETE/{id}`, `GET /count`, `POST /query` (method-override for oversized filters), bulk by `where`.
- **Success envelope**: `docs, totalDocs, limit, page, totalPages, hasNextPage, hasPrevPage, prevPage, nextPage`.
- **Error envelope**: see [Error shape](#error-shape-i18n).

### Booking resources
- `/api/inventory/v2/bookings`, `/availability-rules`, `/booking-configurations` — standard v2 resources backed by the engine.
- `.ics` feed endpoints are served **outside header-auth `/api/**`** (or explicitly token-whitelisted) and authenticated by `CalendarFeedToken`, not `apiKey`/Bearer.
- Booking requires full inventory `READ` to book (not `LIMITED_READ`, C4); cancel/modify = booker/owner/sysadmin only.

### Per-instrument iCal subscription links
Each bookable instrument exposes a read-only **subscription URL** for calendar clients
(Google/Outlook/Apple), superseding the epic note that RSpace already generates per-instrument
calendar URLs.

- **Issue:** from the instrument view, `POST /api/inventory/v2/booking-configurations/{id}/calendar-feeds`
  creates a `CalendarFeedToken`: `scope=INSTRUMENT`, `scopeTargetId=<instrument>`, owner = requester.
  Return the **raw token once**; store only its hash. Response includes the subscription URL.
- **Subscribe:** `GET /calendar/instrument/{instrumentId}.ics?token=<raw>` — served **outside**
  header-auth `/api/**`. `CalendarFeedService` resolves token → owner + scope, rejects revoked/expired
  tokens, and stamps `lastUsedDate`.
- **Contents:** one `VEVENT` per `CONFIRMED`, non-deleted `TimeSlotBooking` in a bounded forward
  window (reuse RRULE horizon, C11). Each event has stable `UID` (booking global id) and
  `LAST-MODIFIED`. `CLOSED` periods may be emitted as busy/blocking events, off by default.
- **Privacy:** projection follows the **token owner's** instrument permission: full view (booker +
  purpose) or busy/free (time + "Busy"). Feeds must not expose more than the owner can see in-app.
- **Lifecycle:** `GET` list / `DELETE` revoke / rotate on the `calendar-feeds` sub-resource.
  Revocation is immediate. Use short `max-age` and `Cache-Control: private`.
- **Caveat:** feed URLs carry a bearer-equivalent query token. Use per-feed tokens, not API keys,
  with revocation and optional expiry.

A per-user "all my bookings" feed (`scope=USER_BOOKINGS`) uses the same mechanism; only the query
changes (`requester = owner`).

### Error shape (i18n)

Aligns with the existing `com.researchspace.apiutils.ApiError` model raised by `ApiControllerAdvice`,
extended so every user-facing string is **resolved from a message-bundle key**, per the repo i18n rule
(no hard-coded user-facing strings). Each error carries a stable machine `code` (the bundle key) plus
a server-rendered localised `message`:

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
      "args": { "conflictCount": 2 },
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

i18n contract:
- **`code`** is a stable message-bundle key under `src/main/resources/bundles/inventory/` (machine-readable, never translated). Clients branch on it.
- **`message`** is resolved server-side via the injected `MessageSource.getMessage(code, args, locale)` in the request locale (`Accept-Language` / user setting). A client that localises itself uses `code` + `args` and ignores `message`.
- **`args`** are named parameters for interpolation in the properties file (e.g. `{conflictCount}`), so translators control word order. Constrained to a **flat map of scalars** (`string | number | boolean`) so it maps to a single JSON-Schema `additionalProperties` rule and a `v.record(...)` — no nested objects here.
- **`details`** is structured, non-localised machine data (e.g. the conflicting slots) for the UI to render specifics; it is never a translated string. Typed as an **opaque object** (`unknown`/`additionalProperties: true`) so the envelope schema stays code-agnostic.
- **Optionality:** absent fields are **omitted, never `null`** (`field`, `args`, `details` are optional); `null` is reserved for genuinely-nullable values (e.g. the success envelope's `prevPage`). This keeps `v.optional(...)` vs `v.nullable(...)` unambiguous.
- **`field` uses dotted path notation** matching the client schema's `path` (`recurrence`, `target.id`). This lets the FE fold server errors into valibot's `flatten()` output (`{ root, nested }`) with no key translation — a small `apiErrorsToFlatten(envelope)` adapter (or an async `rawCheck` re-emitting them as valibot issues) merges server and client validation into one error channel.
- One request can return multiple `errors` (e.g. bulk-write per-row failures), each with its own `code`/`args`/`field`.

Sample bundle entries (`bundles/inventory/ApplicationResources.properties` or an `inventory`-scoped bundle):

```properties
booking.series.conflict=Booking series rejected: {conflictCount} occurrence(s) conflict with existing bookings.
booking.outside_window=Requested time is outside the instrument's available hours.
booking.rrule.too_large=Recurrence exceeds the allowed limit of {maxOccurrences} occurrences.
booking.not_bookable=This item is not bookable.
booking.forbidden=You do not have permission to modify this booking.
```

#### Schema compatibility (Standard Schema / valibot / zod / arktype)

The error envelope is designed to **round-trip through JSON Schema** so the frontend can *generate* a
validator rather than hand-write one. The **OpenAPI spec is the single source of truth**: the v2
success and error envelopes are published as OpenAPI component schemas, and the FE generates a
[Standard Schema](https://standardschema.dev/)-compatible validator (valibot, zod, or arktype — all
implement the same interface) via tooling such as `openapi-to-zod` / valibot's JSON-Schema package /
`@hey-api/openapi-ts`. No FE schema is maintained by hand.

Constraints that keep it generatable across all three libraries:
- **Fixed-shape envelope**, no dynamic top-level keys; `errors` is a homogeneous array.
- **`code` is an open `string`**, not a closed `enum`, in the response schema — so a new bundle key never fails validation. A *separate* generated `BookingErrorCode` union/`v.picklist` is published for clients that want exhaustive handling, decoupled from the wire schema.
- **`args` = flat scalar map**, **`details` = opaque object**, **optional = omitted not null** (above) — the three rules that most often break naive codegen.

Equivalent JSON Schema (OpenAPI component) and the validator it generates:

```jsonc
// components.schemas.ApiErrorEnvelope
{
  "type": "object",
  "required": ["status", "errorId", "errors"],
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
          "args":    { "type": "object",
                       "additionalProperties": { "type": ["string", "number", "boolean"] } },
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
  args:    v.optional(v.record(v.string(), v.union([v.string(), v.number(), v.boolean()]))),
  details: v.optional(v.record(v.string(), v.unknown())),
});
const ApiErrorEnvelope = v.object({
  status:    v.number(),
  errorId:   v.string(),
  timestamp: v.optional(v.pipe(v.string(), v.isoTimestamp())),
  errors:    v.array(ApiErrorItem),
});
```

### Worked API example (for review)

Shows the agreed shape: RSQL filter, sparse `select`, `sort`, Payload-style envelope,
full-vs-busy projection, recurring create conflict handling, the i18n error envelope, and iCal feed.

**1. List instrument IC42's confirmed July bookings (read).**

```
GET /api/inventory/v2/bookings
    ?filter=target.id==42;status==CONFIRMED;startTime>=2026-07-01T00:00:00Z;startTime<=2026-08-01T00:00:00Z
    &select=id,startTime,endTime,status,purpose,requester.username
    &sort=startTime&limit=20&page=1
Header: apiKey: <key>
```

RSQL operators (prefer the symbolic comparison operators over the `=gt=`/`=ge=`/`=lt=`/`=le=` aliases; both parse): `==  !=  >  >=  <  <=  =in=  =out=  =like=`, `;`=AND, `,`=OR. Filterable,
selectable, and sortable fields come from the `BookingView` allow-list.

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

Same request from a visibility-only user returns **busy** projection (`BookingBusyView`):
`requester`/`purpose` absent and summary = `"Busy"`. Read authz is *in-query* (C3), so totals and
pages stay correct.

For deep pages, `&cursor=<keyset>` uses blaze keyset pagination (`nextCursor` replaces `nextPage`).
Oversized filters use `POST /api/inventory/v2/bookings/query` with `{ "filter": "...",
"select": [...], "sort": "-startTime", "limit": 50 }` (method override).

**2. Create a recurring booking (write → `BookingManager`).**

```
POST /api/inventory/v2/bookings
{
  "targetId": 42,
  "purpose": "Confocal imaging - project X",
  "recurrence": { "rrule": "FREQ=WEEKLY;BYDAY=TU;COUNT=10",
                  "dtStart": "2026-07-07T14:00:00", "tzId": "Europe/Amsterdam",
                  "durationMinutes": 60 }
}
```

`201 Created` — series + materialised occurrences. DST example: the Oct 27 occurrence is `13:00Z`,
the rest `12:00Z`, because `14:00 Europe/Amsterdam` crosses DST:

```json
{ "seriesId": "BS204", "rrule": "FREQ=WEEKLY;BYDAY=TU;COUNT=10", "tzId": "Europe/Amsterdam",
  "materializedUntil": "2026-09-08T12:00:00Z", "occurrenceCount": 10, "status": "CONFIRMED" }
```

If any occurrence clashes, reject the whole series — `409 Conflict` in the i18n error envelope
(`code: booking.series.conflict`, see [Error shape](#error-shape-i18n)). Unauthorized create →
`403` (`booking.forbidden`). Unbounded/oversized RRULE → `422` (`booking.rrule.too_large`); malformed
RRULE → `400` (C11).

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

- blaze-persistence: `core-api/impl`, `entity-view-api/impl`, `integration-hibernate-5.6`, plus querydsl/jackson/spring integrations only if Phase 0 keeps them. Use javax artifacts, **not** `-jakarta`.
- `rsql-parser`; QueryDSL **only if Phase 0 confirms it earns its place** (see Phase 0).
- ical4j: **upgrade to the latest suitable release (C14).** Code migration is small (~1 production call site + one test). Main risk is **transitive-dependency convergence**; the POM already has ical4j exclusions that must be re-derived. New recurrence code targets the upgraded API.

---

## Spring 6 / newer-version outlook (checked; do not pull forward)

Do **not** move to Spring 6 / jakarta for this feature; it is a whole-app migration and adds little:
- **blaze-persistence**: javax and `-jakarta` artifacts are parallel builds; Hibernate 6.2 adds nothing booking-relevant over 5.6.
- **Hibernate 6 / Spring Data JPA 3.x**: Scroll/keyset pagination is already covered by blaze on Spring 5; AOT/native is irrelevant; Spring Data was rejected.
- **ical4j 4.x**: useful, already planned, and Spring-independent (Java 11+, not jakarta).
- **QueryDSL**: maintained OpenFeign 7.x is jakarta-side, which supports the Phase 0 lean toward direct RSQL → blaze `CriteriaBuilder` on Spring 5.
- **Spring 6**: `ProblemDetail`/observability are nice, but hand-rollable and not booking-critical.

**Hedge:** booking uses JPA `EntityManager` + blaze, so later Spring 6 is mostly dependency/import
swap (`-jakarta`, `javax→jakarta`), not architectural rework.

---

## Phased plan (booking + v2 co-developed)

- **Phase 0 — Spikes.**
  - Bootstrap blaze from `SessionFactory`; decide which entities can use blaze **updatable views** vs `*Manager` overrides.
  - **ical4j upgrade (C14):** choose version, re-derive exclusions, migrate the existing call site/test, confirm needed RRULE expansion.
  - Check whether maintained `RSQL → QueryDSL` exists without Spring Data. If not, **drop QueryDSL** and use `RSQL → blaze CriteriaBuilder` directly. Test entity-view-over-`BlazeJPAQuery` only if QueryDSL survives.
- **Phase 1 — v2 read engine + booking persistence & read (funded).** v2 bootstrap, `V2ResourceConfig`/`V2QueryService`, RSQL→blaze, entity views, pagination, success + **i18n error envelope published as OpenAPI component schemas** (the source of truth for FE valibot/zod codegen). Booking entities (`BookingConfiguration` unique-per-target, `availabilityVersion`, `CalendarFeedToken`, `BookingSeriesSkippedOccurrence`), Liquibase (`changeLog-RSDEV-1187.xml`, `context="run"`, Envers `_AUD`, job lock table, hot-path indexes C13), `BookingDao`, and read endpoints (`GET`/list/`count`/`query`) with in-query authz (C3) + full-vs-busy projection. Explicit Envers relation audit modes (C12).
- **Phase 2 — Recurrence (funded).** `AvailabilityRule` expand-on-read + guardrails (C11); `BookingSeries` materialisation; horizon job through per-item lock path (C9); skipped-occurrence records (C10); `BookingManager` create/cancel with lock + version recheck + overlap.
- **Phase 3 — v2 write engine + booking writes + iCal (funded).** `V2WriteService` with mandatory per-row authz, pluggable handler (booking → `BookingManager`), bulk-by-`where` (C5); full-READ gating (C4); per-instrument and per-user `.ics` feeds with `CalendarFeedToken` auth (C6); my-bookings keyset; occurrence/series edits.
- **Phase 4 — Hardening.** Privacy projections, reporting hooks, OpenAPI spec for v2.
- **Phase 5 — Second v2 consumer (validate generality).** Port a second inventory resource onto v2 to confirm the abstraction holds; fix leaks found.

Tests per phase: pure-unit interval/RRULE/overlap/guardrail math; `*IT` (`MVCTestBase` /
`RealTransactionSpringTestBase`) for lock + version recheck, per-row authz, horizon job, error-envelope
localisation, and feed-token auth.

---

## Deferred (captured, not built)

QuantityRequest (biobank) impl · approval workflow (PENDING/APPROVED) · this-and-future series edits ·
partial-accept of conflicting series · maintenance/performer scheduling · usage reporting + billing ·
external/guest booking · busy/free privacy projection (designed-for) · per-item booking rules
(min/max duration, advance window) · recurring-booking partial-overlap handling.
