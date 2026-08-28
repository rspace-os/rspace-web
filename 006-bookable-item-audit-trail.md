# Plan 006: Make the item audit trail stable, complete, and recoverable

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving on. If a STOP condition
> occurs, stop and report it. Do not improvise. When done, update this plan's row
> in `plans/README.md` unless a reviewer says they maintain the index.
>
> **Drift check, run first**:
> `git diff --stat 37e013af4..HEAD -- src/main/webapp/ui/src/modules/booking/pages/bookable-items src/main/webapp/ui/src/modules/common/i18n/locales/en-US/booking.json src/main/webapp/ui/src/modules/common/i18n/resources.d.ts src/main/java/com/researchspace/api/v2 src/main/java/com/researchspace/service/audit/search/AuditTrailActorVisibility.java src/main/java/com/researchspace/service/audit/search/AuditTrailHandlerImpl.java src/test/java/com/researchspace/api/v2 src/test/java/com/researchspace/service/audit/search/AuditTrailActorVisibilityTest.java src/test/java/com/researchspace/service/audit/search/AuditTrailHandlerImplTest.java src/main/resources/bundles DevDocs/DeveloperNotes src/main/webapp/ui/src/modules/booking/prototypes/AuditLogViews.prototype.stories.tsx`
> Plan 005 must be DONE first. Compare changed files with Current state. A change
> to endpoint authorization, audit storage, or the item-local Audit log tab is a
> STOP condition until this plan is reconciled.

## Status

- **Priority**: P1
- **Effort**: L
- **Risk**: HIGH
- **Depends on**: `plans/005-merged-bookable-item-page.md`
- **Category**: security
- **Planned at**: commit `37e013af4`, 2026-08-27

## Why this matters

The item-local Audit log tab already reads the generic REST API v2 audit route,
but its page sequence can drift while events arrive or old logs rotate. Its row
IDs are positions, equal timestamps have no deterministic tie-breaker, and the
file searcher catches parse errors and returns whatever partial results it had
already collected. That is not a trustworthy audit viewer.

This plan strengthens the REST API v2 endpoint and updates the existing item
tab. Its strict file reader is isolated from the legacy Activity API and MVC
audit page. It does not create another audit page or another audit store.

## Current state

### The correct product location already exists

After Plan 005, `BookableItemPage` has Bookings, Details, and Audit log tabs at
`/booking/bookable-items/$globalId`. The Audit log panel mounts
`BookableItemAuditLog` with the numeric configuration ID. Keep that local
meaning: this is history for one `BookingConfiguration`, not every reservation
for the instrument and not every action performed by a user.

Existing Plan 003 proposed `/booking/auditing`, resource pickers, and user audit
history. That proposal is superseded by this item-local design. Do not add a
sidebar item, user picker, global feed, or second audit route.

### The backend endpoint already applies the important access checks

`src/main/java/com/researchspace/api/v2/controller/ApiV2AuditController.java:42-52`
delegates every registered resource audit route to `ApiV2AuditLog`:

```java
@GetMapping("/{resource}/{id}/audit")
public ApiV2ListResult<ApiV2AuditEvent> list(...) throws BindException {
  throwBindExceptionIfErrors(errors);
  return auditLog.search(requireResource(resource), id, query, subject(caller));
}
```

`ApiV2AuditLog:59-97` requires authentication, calls
`resource.requireReadableForAudit`, restricts the date window to 183 days, and
passes the subject through `AuditTrailHandler`. The handler then limits a
non-sysadmin to events by users they may view. Preserve both resource readability
and actor visibility. Do not move either rule into the frontend.

The payload mapping at `ApiV2AuditLog:154-181` already filters recorded values
through the resource's readable fields before returning them. Any event identity
or result fingerprint must use that authorized payload, not the raw log payload.

### Pagination identity is positional and unstable

`src/main/webapp/ui/src/modules/booking/pages/bookable-items/bookableItemAudit.ts:19-54`
states that the API has no event ID and derives one from the response position:

```ts
export type AuditRow = AuditEvent & { rowId: string };
...
rows: result.docs.map((event, index) => ({
  ...event,
  rowId: `${result.pagingCounter + index}`,
})),
```

The backend orders only by timestamp descending. Two events in the same
millisecond have no deterministic secondary order. A new event can shift every
later page, while retention can remove rows between requests.

### Read failures currently return partial success

`BasicLogQuerySearcher.java:104-125` wraps the whole file loop in one catch:

```java
try {
  for (File logFile : logs) {
    ...
    hits.add(logline);
  }
} catch (IOException | ParseException e) {
  log.warn("parsing problem", e);
}
```

The method then sorts and returns `hits`, so an unreadable or malformed eligible
file produces HTTP 200 with missing events.

`LogFileTracker.java:121-131` also catches failures while reading historic file
date ranges and continues. `LogLineParser.parseAll()` silently skips every
nonblank line that does not match its regex, including a malformed middle line
between valid first and last lines. These classes also back the legacy
`ActivityApiController` and `AuditTrailController`; changing their error
contract globally is unnecessary risk. Keep them source-compatible and give
REST API v2 a strict reader.

### The UI has the right base presentation but no snapshot recovery

`BookableItemAuditLog.tsx` already has 7, 30, and 90-day presets, native date
inputs, a responsive `TableList`, and previous/next navigation. It currently:

- sends UTC day boundaries;
- applies no explicit empty, malformed, inverted, or over-wide validation;
- keys each query only by resource, range, and page;
- cannot show when the result set was fixed;
- cannot recover from a changed result set except as a generic error.

Keep the responsive table/card presentation and the UTC audit-day convention.
Add consistency and recovery without building a generic audit-table framework.

### Reliability contract

For one readable resource, authorized actor set, date/action filter, and daily
snapshot, the endpoint must provide these guarantees:

1. The first request selects `snapshotDate`, a UTC `YYYY-MM-DD` date. It is the
   latest UTC day whose final millisecond does not exceed the inclusive
   requested `dateTo` instant, capped before the current UTC day. For example, on
   2026-08-27 a range containing today is bounded through 2026-08-26. A range
   starting today can therefore be a valid empty snapshot.
2. Internally the reader uses an exact half-open interval
   `[dateFrom instant, snapshotDate + 1 day at 00:00Z)`. It does not pass the
   boundary through `AuditTrailSearchElement`, whose legacy date handling adds
   23:59:59 and is neither exact nor safe at subsecond/DST boundaries.
3. Later pages send both `snapshotDate` and `snapshotFingerprint` from the
   first response. The pair is all-or-nothing.
4. The complete authorized result set is sorted by timestamp descending and
   deterministic `eventId` ascending before slicing a page.
5. `eventId` is a SHA-256 identity over a fixed canonical serialization of the
   event's timestamp, actor fields, domain, action, description, and authorized
   payload. It is not a signature or proof that the source file was not changed.
6. Exact duplicate events remain separate results. They may share `eventId`.
7. `snapshotFingerprint` covers the complete ordered list of event IDs,
   including repeated IDs and their count.
8. If the same bounded query no longer produces the supplied fingerprint, the
   server returns 409. It never serves a mixed page sequence.
9. If an eligible audit file is malformed, unreadable, replaced during the
   bounded read, or cannot be certified after one retry, the endpoint returns a
   localized 503 problem response. It never returns a partial HTTP 200.
10. Collection stops at a configurable result ceiling plus one. Above the
    ceiling, the endpoint returns a localized 400 requiring a narrower range.
    The default ceiling is selected from the stress test in this plan, not
    guessed in advance.
11. The UI shows `Results through <date>`, offers Refresh, and gives a clear
    restart path after 409.

Call this a daily bounded-consistency snapshot. It strengthens pagination over
the mutable file store, but it is not a database transaction, immutable
archive, or tamper-evidence mechanism.

### Stable file-read protocol

The first page must not certify a path list that changes while it is being
opened. The REST v2 reader performs one complete attempt as follows:

1. Enumerate matching regular audit files and capture a manifest containing
   canonical path, file key/inode when the filesystem exposes one, byte size,
   and last-modified time. Reject duplicate canonical paths and non-regular
   files.
2. Determine candidate overlap using strict first/last timestamp parsing. Null,
   unrecognized, or invalid boundary lines are failures, not absent ranges.
3. Open each candidate and read no more than its recorded byte size. Parse
   incrementally and collect only events in the exact half-open interval.
4. Treat an unrecognized nonblank line, bad timestamp, unknown domain/action,
   malformed audit JSON, truncated eligible line, or I/O failure as fatal. The
   internal exception may contain the safe source filename and one-based line
   number, but never raw line content. Blank lines are ignored explicitly.
5. Re-stat the manifest after the scan. Replacement, truncation, identity
   change, or same-size modification invalidates the attempt. For size-only
   growth on the same file key, strictly parse the appended byte range: complete
   records at or after the exclusive boundary are harmless, while an eligible
   record, partial record, or another concurrent change invalidates the attempt.
   Discard an invalid attempt and retry once from a fresh manifest. Never
   combine hits from two attempts.
6. If the second attempt changes or cannot be read consistently, return 503.

Appending events after the exclusive daily boundary must not invalidate the
bounded result. Replacement, retention, or rollover that can affect eligible
bytes must. Later pages repeat the bounded scan and compare the result
fingerprint, so eligible retention or replacement becomes 409. If the protocol
cannot distinguish harmless appends from eligible changes on every supported
filesystem, use the 503/retry path; if it cannot produce a consistent attempt
at all, trigger the durable-snapshot STOP condition.

### Applicable repository conventions

- Backend user-facing error text belongs in the applicable bundle under
  `src/main/resources/bundles/`.
- REST v2 errors use `ApiV2Problem`; follow `ApiV2ControllerAdvice`.
- Do not change frozen `JacksonUtil`. Keep canonical serialization local to the
  audit resource and cover it with fixed tests.
- Frontend network responses use Valibot and `parseOrThrow`; API failures use
  the existing `parseApiV2Problem` helper.
- Frontend unit tests use MSW, shared `server`, semantic queries, and
  `expectAccessible`.
- Run commands from the repository root without a standalone pnpm separator.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Backend unit tests | `mvn test -Dtest=ApiV2AuditControllerTest,ApiV2AuditLogTest,ApiV2AuditStrictSearchTest,ApiV2ControllerAdviceTest,ApiV2OpenApiGeneratorTest,AuditTrailActorVisibilityTest,AuditTrailHandlerImplTest -Dfast=true` | BUILD SUCCESS |
| Stress workload | `mvn test -Dtest=ApiV2AuditSearchStressTest -Dfast=true -Daudit.stress=true` | workload completes and prints the measurement table |
| Authorization contracts | `mvn test -Dtest=ApiV2AccessContractMVCIT,ApiV2RelationshipContractMVCIT` | BUILD SUCCESS |
| Audit client tests | `pnpm test src/modules/booking/pages/bookable-items/__tests__/bookableItemAudit.test.ts` | all tests pass |
| Audit component tests | `pnpm test src/modules/booking/pages/bookable-items/__tests__/BookableItemAuditLog.test.tsx src/modules/booking/pages/bookable-items/__tests__/BookableItemPage.test.tsx` | all tests pass |
| Browser inner loop | `VITEST_BROWSERS=chromium pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx` | audit cases pass in Chromium |
| Browser final | `pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx` | all tests pass in Chromium, Firefox, and WebKit |
| Extract English keys | `pnpm run i18n:extract --sync-primary` | exit 0; only intended English changes |
| Generate i18n types | `pnpm run i18n:types` | exit 0 |
| Validate i18n | `pnpm run i18n:lint` | exit 0 |
| Type check | `pnpm tsc` | exit 0, no TypeScript errors |
| Lint | `pnpm lint` | exit 0, no Biome errors |

## Suggested executor toolkit

- Use the `react-testing-library` skill for `BookableItemAuditLog.test.tsx`.
- Use the `rspace-browser-tests` skill when extending
  `BookableItemPage.spec.tsx`. Reuse its story, page object, and MSW handlers.
- Keep the `ponytail` skill active. Extend the generic REST API v2 audit route
  already used by the tab. Isolate strict parsing behind one v2 search service;
  do not create a Booking-specific audit service or change legacy searchers.

## Scope

### In scope

- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/bookableItemAudit.ts`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookableItemAuditLog.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/__tests__/bookableItemAudit.test.ts` (create)
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/__tests__/BookableItemAuditLog.test.tsx` (create)
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/__tests__/BookableItemPage.test.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookableItemPage.story.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/pageObjects/BookableItemPage.ts`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/mocks/bookableItemsMocks.ts`
- `src/main/webapp/ui/src/modules/common/i18n/locales/en-US/booking.json`
- `src/main/webapp/ui/src/modules/common/i18n/resources.d.ts`
- `src/main/java/com/researchspace/api/v2/controller/ApiV2AuditController.java`
- `src/main/java/com/researchspace/api/v2/controller/ApiV2ControllerAdvice.java`
- `src/main/java/com/researchspace/api/v2/controller/ApiV2AuditSnapshotConflictException.java` (create)
- `src/main/java/com/researchspace/api/v2/controller/ApiV2AuditUnavailableException.java` (create)
- `src/main/java/com/researchspace/api/v2/model/ApiV2AuditEvent.java`
- `src/main/java/com/researchspace/api/v2/model/ApiV2AuditQuery.java`
- `src/main/java/com/researchspace/api/v2/model/ApiV2AuditPage.java` (create)
- `src/main/java/com/researchspace/api/v2/resource/ApiV2AuditLog.java`
- `src/main/java/com/researchspace/api/v2/resource/ApiV2AuditStrictSearch.java` (create; keep manifest/parser helpers package-private or nested)
- `src/main/java/com/researchspace/api/v2/openapi/ApiV2OpenApiGenerator.java`
- `src/main/java/com/researchspace/service/audit/search/AuditTrailActorVisibility.java` (create)
- `src/main/java/com/researchspace/service/audit/search/AuditTrailHandlerImpl.java`
- `src/test/java/com/researchspace/api/v2/controller/ApiV2AuditControllerTest.java`
- `src/test/java/com/researchspace/api/v2/controller/ApiV2ControllerAdviceTest.java`
- `src/test/java/com/researchspace/api/v2/resource/ApiV2AuditLogTest.java`
- `src/test/java/com/researchspace/api/v2/resource/ApiV2AuditStrictSearchTest.java` (create)
- `src/test/java/com/researchspace/api/v2/resource/ApiV2AuditSearchStressTest.java` (create)
- `src/test/java/com/researchspace/api/v2/openapi/ApiV2OpenApiGeneratorTest.java`
- `src/test/java/com/researchspace/api/v2/contract/ApiV2RelationshipContractMVCIT.java`
- `src/test/java/com/researchspace/service/audit/search/AuditTrailActorVisibilityTest.java` (create)
- `src/test/java/com/researchspace/service/audit/search/AuditTrailHandlerImplTest.java`
- the applicable API v2 error bundle under `src/main/resources/bundles/`
- a focused result-ceiling note under `DevDocs/DeveloperNotes/`
- `src/main/webapp/ui/src/modules/booking/prototypes/AuditLogViews.prototype.stories.tsx` (delete after parity)
- `plans/README.md`, status only after implementation

### Out of scope

- A `/booking/auditing` route, Booking sidebar link, resource picker, or user
  audit view.
- Booking events grouped by instrument. This endpoint audits the selected
  `BookingConfiguration` resource.
- Audit history for physically deleted configurations. The generic endpoint
  first resolves a current readable resource.
- Database, Liquibase, Envers, audit event publication, log format, retention,
  or immutable storage.
- Field-level diffs, restoration, CSV export, sorting controls, and free-text
  search.
- Changes to resource readability or audit actor-visibility rules.
- Behavior changes to `BasicLogQuerySearcher`, `LogFileTracker`,
  `LogLineParser`, the v1 Activity API, or the legacy MVC audit controller.
- Calendar-subscription behavior from Plan 007.
- Deleting the merged-page prototype. Its calendar decision remains pending.
- New dependencies or a generic audit React framework.

## Git workflow

- Suggested branch: `feat/bookable-item-audit-snapshots`
- Commit reliable file reads first, then the snapshot response contract, then
  frontend recovery and prototype cleanup.
- Match the repository's imperative commit style.
- Do not push or open a pull request unless instructed.

## Steps

### Step 1: Characterize current authorization and errors

Before production edits, extend the existing backend tests only where the
current contract lacks proof:

- anonymous audit list returns 401;
- an authenticated ordinary user can read a readable booking configuration's
  audit trail;
- unreadable or missing resource returns the existing concealed 404;
- actor visibility remains applied by `AuditTrailHandlerImpl`;
- a supplied inverted or wider-than-183-day range returns 400;
- authenticated audit responses carry `Cache-Control: private, no-store`.

Use the real REST API v2 contract fixtures for authorization. Do not mock the
permission result in those tests. If current authorization differs from Current
state, stop rather than changing it as part of pagination work.

**Verify**:

```text
mvn test -Dtest=ApiV2AuditControllerTest,ApiV2AuditLogTest,AuditTrailHandlerImplTest -Dfast=true
mvn test -Dtest=ApiV2AccessContractMVCIT,ApiV2RelationshipContractMVCIT
```

Both commands report BUILD SUCCESS.

### Step 2: Isolate authorization and strict file reads for REST API v2

Extract the current `AuditTrailHandlerImpl.configureUserRestriction` logic into
one injected `AuditTrailActorVisibility` service. Keep its inputs and result
semantics unchanged, and make both the legacy handler and the new v2 searcher
delegate to it. Characterization tests must prove sysadmin visibility,
ordinary-user viewable-user intersection, inclusion of the caller, and an empty
authorized set. Do not copy permission logic into `ApiV2AuditLog`.

Create `ApiV2AuditStrictSearch` for this REST v2 endpoint. It owns the stable
file-read protocol in Current state and returns an unsliced authorized result
sequence or a typed unavailable signal. Keep its strict parser and manifest
records package-private or nested unless another v2 caller actually needs them.
Stream lines rather than loading whole files.

The strict parser must fail on:

- an unrecognized nonblank line, including a malformed line between valid first
  and last lines;
- an invalid timestamp, unknown domain/action, malformed audit JSON, or
  truncated eligible record;
- an unreadable file or a manifest mismatch that persists after one retry.

Its internal exception includes only a safe filename and one-based line number,
never the raw line. Blank lines are explicitly accepted and skipped. Keep exact
duplicate events. Leave `BasicLogQuerySearcher`, `LogFileTracker`, and
`LogLineParser` unchanged so legacy consumers retain their current behavior.

Add temporary-file tests for a malformed first, middle, and last line; bad
timestamp; unknown enum; malformed JSON; blank lines; unreadable input; a valid
file followed by a failed file; harmless post-boundary append; eligible append;
replacement with the same filename; truncation; rollover between enumeration
and open; one successful retry; and two unstable attempts. No failure may
return accumulated hits.

**Verify**:
`mvn test -Dtest=ApiV2AuditStrictSearchTest,AuditTrailActorVisibilityTest,AuditTrailHandlerImplTest -Dfast=true`
reports BUILD SUCCESS, and `git diff` shows no change to the three legacy file
search classes.

### Step 3: Add the daily snapshot response

Extend the existing generic REST API v2 endpoint. Do not add a Booking
controller.

1. Add optional `snapshotDate` and `snapshotFingerprint` query fields to
   `ApiV2AuditQuery`. Validate that both are absent or both present. Parse the
   date strictly as UTC `YYYY-MM-DD`, require a 64-character lowercase
   hexadecimal fingerprint, and reject a snapshot whose final millisecond
   exceeds the latest completed UTC boundary or the inclusive requested
   `dateTo` instant.
2. Add `eventId` to `ApiV2AuditEvent`.
3. Add `ApiV2AuditPage<T>`, preserving every field from `ApiV2ListResult` and
   adding required `snapshotDate` and `snapshotFingerprint` fields.
4. On the first request, select the daily boundary exactly as specified in the
   Reliability contract. Inject `Clock` so UTC midnight behavior is fixed in
   tests. Reproduce the current 183-day default-window behavior for omitted
   endpoints with exact instants rather than passing the query through
   `DateRangeRestrictor` or `AuditTrailSearchElement`.
5. Call `ApiV2AuditStrictSearch` with the exact half-open interval and the actor
   restriction from the shared visibility service. Do not use the legacy
   get-all pagination mode or its inclusive date mutation.
6. Stop streaming and return localized 400 problem code
   `errors.api.v2.audit.results.tooMany` as soon as the strict search observes
   configured ceiling + 1 authorized matches. Do not finish building the list.
7. Map readable fields before identity calculation. Canonically serialize the
   mapped event with UTF-8, fixed field order, recursively sorted map keys, and
   explicit nulls. Hash it with SHA-256 to produce lowercase `eventId`.
8. Sort the bounded list by timestamp descending, then `eventId` ascending.
   Do not use `distinct`, sets, filenames, or filesystem iteration order.
9. Calculate `snapshotFingerprint` over the complete ordered sequence. Use a
   tested unambiguous encoding so repeated IDs and their count change the hash.
10. If a later request's recalculated fingerprint differs, throw
    `ApiV2AuditSnapshotConflictException` and map it to localized HTTP 409
    problem code `errors.api.v2.audit.snapshot.changed`.
11. Slice the requested one-based page only after sorting and fingerprinting.
12. Translate strict read failures to localized HTTP 503 problem code
    `errors.api.v2.audit.unavailable` through
    `ApiV2AuditUnavailableException` and the controller advice.
13. Keep `/audit/count` source-compatible and free of snapshot parameters. It
    may use the same bounded strict search but preserves its response shape and
    result ceiling.

Do not describe either hash as tamper evidence. Do not change `JacksonUtil`.

Add fixed tests for canonical map ordering, same-millisecond ordering, exact
duplicates, UTC daily selection, a historical requested To date, a today-only
empty snapshot, exact exclusion of events at the next midnight, fingerprint
agreement, 409 after eligible retention/replacement, harmless next-day events,
malformed/one-sided/future snapshot input, ceiling and ceiling + 1, 503 without
a partial body, readable-field filtering, and unchanged authorization.

Extend `ApiV2RelationshipContractMVCIT` with one real Spring round trip. The
first request must bind the date range and return `snapshotDate`, a lowercase
64-character fingerprint, `eventId`, pagination fields, and private no-store
caching. A later-page request sends the returned pair unchanged and succeeds.
Also send malformed and one-sided snapshot parameters through Spring MVC and
assert the localized 400 problem. Keep filesystem race injection in unit tests;
the integration test proves binding, advice, serialization, and security-filter
wiring without manipulating deployment logs.

**Verify**:
`mvn test -Dtest=ApiV2AuditControllerTest,ApiV2AuditLogTest,ApiV2AuditStrictSearchTest,ApiV2ControllerAdviceTest,AuditTrailActorVisibilityTest,AuditTrailHandlerImplTest -Dfast=true`
reports BUILD SUCCESS.

### Step 4: Measure and set the result ceiling

Before choosing the default ceiling, add an opt-in
`ApiV2AuditSearchStressTest` that generates representative temporary audit logs
with 1,000, 5,000, 10,000, 25,000, and 50,000 authorized matching events. Run
first-page collection and later-page fingerprint verification at concurrency
1, 4, and 8. Include realistic payload sizes, duplicate timestamps, irrelevant
events, and multiple rolled files.

Record for each case the JDK/JVM heap, CPU count, storage type, elapsed-time
distribution after warm-up, throughput, and peak additional heap. Use existing
JDK/Maven facilities; do not add JMH or another dependency. Select the largest
count that, with a 2x safety margin, stays below both half the configured HTTP
request timeout and the smaller of 64 MiB or 5% of maximum heap per concurrent
request. If no tested count meets that rule at concurrency 8, trigger the
storage-design STOP condition.

Put the measurements, chosen default, configuration property, and reasoning in
a focused `DevDocs/DeveloperNotes` note. Wire that property into the strict
search with a positive bounded default. Keep exact unit tests at the configured
ceiling and ceiling + 1; timing values are recorded evidence, not brittle CI
assertions.

**Verify**:

```text
mvn test -Dtest=ApiV2AuditSearchStressTest -Dfast=true -Daudit.stress=true
mvn test -Dtest=ApiV2AuditLogTest,ApiV2AuditStrictSearchTest -Dfast=true
```

The stress run completes, the developer note contains its non-secret result
table, and the boundary tests pass.

### Step 5: Document the generic contract in OpenAPI

Update the generated audit operation to include `snapshotDate` and
`snapshotFingerprint` on list requests, the `ApiV2AuditPage` 200 schema, and 409
and 503 responses. Also document the 400 result-ceiling problem. Document:

- one-based pagination;
- the 183-day range limit;
- both snapshot fields must be sent together after page one;
- snapshot granularity is one completed UTC day and the effective interval is
  half-open;
- `eventId` is deterministic response identity, not a source-log signature;
- 409 means restart the result set;
- 503 means the server refused to return partial audit data;
- an over-ceiling result requires a narrower date range.

Keep the count operation free of pagination and snapshot parameters.

**Verify**:
`mvn test -Dtest=ApiV2OpenApiGeneratorTest -Dfast=true`
reports BUILD SUCCESS and asserts the exact parameter/response set.

### Step 6: Update the typed audit client

In `bookableItemAudit.ts`:

1. Add `eventId` to the event schema.
2. Add a schema for the audit page with snapshot metadata.
3. Accept an optional snapshot pair in
   `fetchBookingConfigurationAudit`. Send neither field for a new result set and
   both for later pages.
4. Parse failures through `parseApiV2Problem` so the component can distinguish
   409 and 503 by status and problem code.
5. Return `snapshotDate`, `snapshotFingerprint`, `hasPrevPage`, and
   `hasNextPage` with the page rows.
6. Build the TableList row ID from `eventId` plus the occurrence number for that
   same ID on the current page. Exact duplicates must render separately.
7. Export pure UTC date-range helpers. Presets labeled Last 7, 30, and 90 days
   use inclusive calendar dates, so their starts are today minus 6, 29, and 89
   days. Validate real ISO calendar dates, From not after To, and no more than
   183 elapsed 24-hour days. Keep the source-compatible ISO date-time query
   format: send From at UTC midnight and To one millisecond before the next UTC
   midnight. The v2 backend derives UTC calendar dates from those values and
   applies its own exact exclusive boundary; it must not pass To through the
   legacy 23:59:59 mutation.

Keep page zero-based inside the React component and convert to one-based only at
the HTTP boundary, as the existing client does.

Create `bookableItemAudit.test.ts` for URL parameters, headers, parsing,
zero/one-based conversion, snapshot round trips, duplicate row keys, UTC date
boundaries, leap dates, inverted ranges, and the exact 183-day boundary.

**Verify**:
`pnpm test src/modules/booking/pages/bookable-items/__tests__/bookableItemAudit.test.ts`
passes.

### Step 7: Add snapshot and recovery states to the existing tab

Update `BookableItemAuditLog.tsx`, keeping the current TableList:

- Keep draft dates separate from the applied range.
- Show a localized validation error and issue no request when either date is
  blank/malformed, From is after To, or the converted window exceeds 183 days.
- Reset page and snapshot whenever a preset or valid custom range is applied.
- Store the snapshot returned by the first successful page and send it on every
  later page request, including navigation back to an earlier page.
- Include resource ID, applied dates, page, snapshot values, and a local Refresh
  generation in stable React Query keys. A new result set must not reuse a page
  from an older snapshot.
- Show localized `Results through <date>` from the server value. If the applied
  range starts after that completed day, explain that the stable daily snapshot
  is empty rather than implying there are no same-day events.
- Add Refresh. It discards the snapshot, returns to page one, increments the
  local result generation, and starts a fresh request.
- On 409, do not render the conflicting page. Show an accessible warning and a
  Restart button that performs the same reset as Refresh.
- On 503, show a specific audit-unavailable state. Keep generic errors generic.
- On the result-ceiling problem, ask the user to choose a narrower range and do
  not render a partial table.
- Drive Previous and Next from server `hasPrevPage` and `hasNextPage`, not only
  local arithmetic.
- When Apply rejects a range, set `aria-invalid="true"` on the exact From or To
  inputs that are wrong and associate localized error and correction text with
  each through `aria-describedby`. Identify whether From, To, or both require
  correction, preserve the entered values, and focus the first invalid field;
  do not move focus to a generic validation alert.
- Use a polite `role="status"` region for initial loading, page loading,
  Refresh progress, and updated result counts/dates. Use `role="alert"` for
  validation-independent request failures, conflict, unavailable, and ceiling
  errors. Refresh keeps keyboard focus on the Refresh button and announces the
  updated results without moving focus.
- On 409, focus the conflict warning so Restart is the next relevant action.
  When Restart removes the conflict UI and the replacement page has loaded,
  move focus to a programmatically focusable audit-results heading.
- Preserve programmatic column/header relationships and a logical reading
  order in both responsive table and card presentations. Give Previous and
  Next descriptive names that include their destination page, expose their
  disabled states, and announce the current page and total/known result
  context through the status region.
- Keep date controls, presets, TableList, and navigation usable at narrow page
  widths.

Add `BookableItemAuditLog.test.tsx` with MSW coverage for initial load, presets,
valid and invalid custom dates, later-page snapshot parameters, Refresh, 409
restart, 503 unavailable, result-ceiling refusal, exact duplicates,
keyboard-only operation, focus movement, and accessibility. Use semantic role
and label queries against i18n keys. Keep the parent page test proving the audit
request is lazy until its tab opens.

When parity is complete, delete
`AuditLogViews.prototype.stories.tsx`. Its selected item presentation now lives
in production; its separate global/user variants are intentionally rejected by
this plan. Keep `MergedBookableItemPage.prototype.stories.tsx` for Plan 007.

**Verify**:
`pnpm test src/modules/booking/pages/bookable-items/__tests__/bookableItemAudit.test.ts src/modules/booking/pages/bookable-items/__tests__/BookableItemAuditLog.test.tsx src/modules/booking/pages/bookable-items/__tests__/BookableItemPage.test.tsx`
passes.

### Step 8: Add real-browser audit coverage

Extend the Plan 005 `BookableItemPage.story.tsx`, shared mock handlers, page
object, and `BookableItemPage.spec.tsx`. Do not add another story wrapper,
fixture set, or browser spec. Register audit responses through the shared MSW
worker, keep locators semantic, keep assertions out of the page object, and use
`expect.poll` for post-navigation request assertions.

Cover:

- Opening Audit log makes the first request lazily and shows the returned
  `Results through` date and rows.
- The native date inputs and presets apply correctly in Chromium, Firefox, and
  WebKit. Keyboard-only users can apply a range, paginate, Refresh, and Restart.
- Next sends the exact snapshot pair returned by page one. Refresh and Restart
  discard it before requesting a new result set.
- Invalid input, 409 conflict, 503 unavailable, and result-ceiling responses
  show their distinct accessible states without rendering stale rows. Assert
  the field-specific validation relationships and validation/conflict focus
  behavior from Step 7.
- Loading, pagination, Refresh, and restarted results use status semantics;
  actual failures use alerts. Refresh retains focus, Restart moves focus to the
  results heading, and pagination exposes descriptive names, disabled state,
  and the current-page announcement.
- At 320px, the date controls, presets, responsive table/card rows, and page
  navigation remain reachable without document-level horizontal overflow.
- The normal table, validation, conflict, and unavailable presentations pass
  the browser accessibility scan.

Treat this as explicit acceptance evidence for WCAG 2.2 SC 1.4.3, 1.4.4,
1.4.10, 1.4.11, 1.4.12, 2.1.1, 2.1.2, 2.4.3, 2.4.7, 2.4.11, 2.5.8,
3.3.1, 3.3.2, 3.3.3, 4.1.2, and 4.1.3. Manually verify the affected audit flow
at default and 200% text size, at 400%
browser zoom/320 CSS px width, and with WCAG text-spacing overrides
(line-height 1.5, paragraph spacing 2 times the font size, letter spacing
0.12em, and word spacing 0.16em). Repeat representative normal, validation,
conflict, and unavailable states in light and dark themes and in forced-colors
or the platform high-contrast mode. Require text contrast of at least 4.5:1
(3:1 for large text), non-text UI and focus-indicator contrast of at least
3:1, no loss of content/function or two-dimensional page scrolling, visible
focus that is not entirely obscured, and pointer targets of at least 24 by 24
CSS px or a documented WCAG spacing exception. For the practical AAA goals in
SC 2.4.12, 2.4.13, and 2.5.5, keep focus wholly unobscured and use a focus
indicator equivalent to a 2 CSS px perimeter
with 3:1 state contrast, and prefer 44 by 44 CSS px targets.

Perform a manual screen-reader pass with NVDA in Firefox or Chrome and
VoiceOver in Safari. Verify date labels and errors, Results through, table/card
relationships and reading order, page state, loading/Refresh announcements,
conflict recovery, and unavailable errors. Record the browser, assistive
technology, theme/mode, and result. Automated axe/component checks are useful
regressions but do not by themselves establish WCAG conformance. Plan 007 owns
the final integrated full-page and complete-process check.

Use Chromium for the inner loop, then run all three engines. Run the audit
browser cases two additional times in Chromium to catch stale MSW handlers or
snapshot races.

**Verify**:

```text
VITEST_BROWSERS=chromium pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx
VITEST_BROWSERS=chromium pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx
VITEST_BROWSERS=chromium pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx
pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx
```

Every run passes; the final command passes in Chromium, Firefox, and WebKit.

### Step 9: Extract translations and run the final gate

Use semantic keys below `booking:bookableItemDetails.audit` for Results through,
Refresh, Restart, validation, conflict, and unavailable states. Author with
literal `defaultValue`, extract English with `--sync-primary`, remove the
defaults, and regenerate types. Never use `--sync-all`.

Run every command in Commands you will need. Review the diff for accidental
changes to audit storage, authorization, or Plan 007 files.

**Verify**: every command below exits 0:

```text
mvn test -Dtest=ApiV2AuditControllerTest,ApiV2AuditLogTest,ApiV2AuditStrictSearchTest,ApiV2ControllerAdviceTest,ApiV2OpenApiGeneratorTest,AuditTrailActorVisibilityTest,AuditTrailHandlerImplTest -Dfast=true
mvn test -Dtest=ApiV2AccessContractMVCIT,ApiV2RelationshipContractMVCIT
pnpm run i18n:types
pnpm run i18n:lint
pnpm tsc
pnpm lint
pnpm test src/modules/booking/pages/bookable-items/__tests__/bookableItemAudit.test.ts src/modules/booking/pages/bookable-items/__tests__/BookableItemAuditLog.test.tsx src/modules/booking/pages/bookable-items/__tests__/BookableItemPage.test.tsx
pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx
```

`rg -n "AuditLogViews" src/main/webapp/ui/src` returns no matches.

## Test plan

- Backend resource tests own canonical identities, deterministic sort,
  duplicates, snapshot selection, fingerprint conflicts, and payload filtering.
- REST v2 strict-search tests own manifest retries, rollover, malformed middle
  lines, unreadable files, the result ceiling, and proof that no partial result
  escapes. Legacy searcher tests remain unchanged.
- Controller/advice tests own ceiling 400, snapshot 409, unavailable 503,
  response shape, and no-store headers.
- The opt-in stress workload and developer note own the measured default result
  ceiling and its runtime/heap evidence.
- REST contract tests own real authentication, resource readability, and actor
  visibility, plus Spring binding and serialization of the snapshot round trip.
- Pure frontend tests own URL construction, schemas, date boundaries, and row
  identity.
- Component tests own user interaction, snapshot reuse, conflict recovery,
  unavailable state, field-specific error semantics, status/alert
  announcements, pagination names/state, keyboard focus, and accessibility.
- The parent page test continues to own lazy mounting of the Audit log tab.
- The existing Browser Mode story, page object, mocks, and spec own native date
  behavior, responsive layout, keyboard flow, focus, three-engine coverage, and
  real-browser accessibility. They are extended, not duplicated.
- Manual visual and assistive-technology checks own contrast, text resize,
  zoom/reflow, text spacing, forced colors, target size, focus
  visibility/obscuring, responsive reading order, NVDA, and VoiceOver.
  Automated scans alone do not prove WCAG conformance.

## Done criteria

- [ ] The item-local Audit log remains the only new Booking audit UI.
- [ ] The first page returns a server-selected completed UTC `snapshotDate` and
  a complete result fingerprint over the exact half-open daily interval.
- [ ] Every later page sends the same snapshot pair.
- [ ] Events sort by timestamp descending and deterministic event ID.
- [ ] Exact duplicates remain visible as separate rows.
- [ ] A changed result set returns 409 and the UI offers a clean restart.
- [ ] An unreadable, unstable, or malformed eligible log, including a malformed
  middle line, returns 503, never partial 200.
- [ ] Strict behavior is confined to REST API v2; the Activity API and legacy
  MVC audit endpoint retain their existing behavior.
- [ ] Stress tests at 1,000 through 50,000 matches and concurrency 1, 4, and 8
  select and document a configurable ceiling; ceiling + 1 stops streaming and
  returns an explicit narrow-the-range error.
- [ ] Resource and actor authorization rules are unchanged and contract-tested.
- [ ] Date inputs reject blank, malformed, inverted, and over-wide ranges before
  sending a request, identify the exact invalid fields, provide associated
  correction guidance, and focus the first invalid field.
- [ ] Results through and Refresh are visible and tested.
- [ ] The snapshot contract completes a real authenticated Spring MVC round
  trip, including query binding, response serialization, and no-store caching.
- [ ] Audit controls work by keyboard with deliberate validation, conflict, and
  post-refresh focus behavior; Refresh retains focus and Restart focuses the
  results heading.
- [ ] Status, alert, table/card, and pagination semantics are tested, including
  current/disabled page state and responsive reading order.
- [ ] The audit UI fits at 320px and passes Chromium, Firefox, WebKit, and
  browser accessibility checks.
- [ ] WCAG 2.2 AA checks cover contrast, non-text contrast, 200% text resize,
  400% zoom/320px reflow, text spacing, visible/unobscured focus, 24px targets,
  forced colors, light/dark themes, NVDA, and VoiceOver, with AAA focus and
  44px target improvements applied where practical.
- [ ] OpenAPI documents the response and error contract.
- [ ] The global/user audit proposal remains rejected and its prototype is
  deleted after item parity.
- [ ] Backend tests, authorization contracts, focused frontend tests,
  three-engine Browser Mode, i18n lint, TypeScript, and Biome pass.
- [ ] No database, Envers, retention, or audit-publication file changed.
- [ ] The plan status in `plans/README.md` is DONE.

## STOP conditions

Stop and report instead of improvising if:

- Product intent requires a global audit page, user-resource picker, or booking
  actions grouped by user.
- The item audit must include `TimeSlotBooking` creates, edits, or cancellations
  rather than only the selected configuration's resource events.
- Deleted configurations must remain auditable without resolving a live
  resource.
- Existing resource readability or actor-visibility behavior differs from
  Current state or must be broadened.
- The two-attempt manifest and bounded-read protocol cannot produce a
  consistent daily snapshot on a supported filesystem. Report the exact race
  and propose a durable snapshot store; do not weaken the 503 contract.
- Exact duplicate events are declared invalid and should be deduplicated.
- No tested result count satisfies the documented request-time and heap budget
  at concurrency 8. Report measurements before changing storage, limits, or
  adding another index.
- A focused verification command fails twice after a reasonable correction.
- Implementation requires a file listed as out of scope.

## Maintenance notes

- Keep canonical serialization versioned by tests. A serialization change will
  invalidate active snapshots and must continue to produce a clear 409 restart.
- `eventId` and `snapshotFingerprint` are consistency values, not signatures.
- Keep identity calculation after field filtering. Hashing raw payloads can leak
  hidden-field changes through response identities.
- Keep exact duplicates in both the fingerprint and UI. A set is incorrect.
- If the backend range limit changes, update frontend validation and boundary
  tests in the same change.
- If product later asks for booking activity rather than configuration history,
  define that projection separately. Do not stretch this resource endpoint.
