# Plan 003: Show resource audit events in Booking

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving to the next step. If a
> STOP condition occurs, stop and report it. Do not improvise. When done, update
> the status row for this plan in `plans/README.md`.
>
> **Drift check, run first**:
> `git diff --stat 7e0e831c7..HEAD -- src/main/webapp/ui/src/modules/booking src/main/webapp/ui/src/modules/common/app/router.tsx src/main/webapp/ui/src/modules/common/i18n/locales/en-US/booking.json src/main/webapp/ui/src/modules/common/i18n/resources.d.ts src/main/java/com/researchspace/api/v2 src/main/java/com/researchspace/service/audit/search src/main/java/com/researchspace/model/booking src/main/java/com/researchspace/model/collection/ApiV2UserResource.java src/test/java/com/researchspace/api/v2 src/test/java/com/researchspace/service/audit/search src/main/resources/bundles`
> If an in-scope file changed, compare the excerpts below with the live code. A
> mismatch in route names, API response shapes, or access rules is a STOP
> condition.

## Status

- **Priority**: P1
- **Effort**: L
- **Risk**: HIGH
- **Depends on**: none
- **Category**: direction
- **Planned at**: commit `7e0e831c7`, 2026-08-25

## Why this matters

Booking users need a way to inspect audit records. The backend already exposes
paginated audit history for every readable REST API v2 resource. This plan adds
the missing Booking UI and strengthens that endpoint's pagination guarantees.
It does not create another audit store.

The page is available to all authenticated users. It uses the existing REST API
v2 authorization rules:

- Every authenticated user can read booking configurations and their permitted
  audit events.
- A system administrator can read every user resource. Another user can read
  only their own user resource.
- The audit search applies its existing actor-visibility restriction after it
  resolves the selected resource.

The frontend must not duplicate or broaden these rules. The backend remains the
authorization boundary.

The first version has deliberately narrow semantics:

- "Bookable item" means the selected `BookingConfiguration`. Its history shows
  changes to enablement, timezone, and scheduling policy.
- "User" means the selected `User` resource. Its history shows changes to that
  user account.
- It does not show every booking made by a user, every booking for an item, or
  history for a resource that has already been physically deleted. Those
  require a different searchable audit projection and must not be inferred from
  the current endpoints.

## Current state

### The sidebar separates common pages from Administration

`src/main/webapp/ui/src/modules/booking/pages/BookingPage.tsx:52-57`:

```tsx
{
  key: "administration",
  icon: SettingsIcon,
  children: [
    { key: "settings", link: <Link {...linkOptions({ to: "/booking/config/settings" })} /> },
    { key: "bookableItems", link: <Link {...linkOptions({ to: "/booking/config/bookable-items" })} /> },
  ],
},
```

`BookingSidebar` removes this group when
`currentUser.hasSysAdminRole` is false. Auditing is not an Administration child.
Add it as a top-level Booking item so every authenticated user can reach it.
Keep the Administration filtering unchanged.

### The router is assembled from route factories

`src/main/webapp/ui/src/modules/common/app/router.tsx:38-50` creates the Booking
route and adds each child factory. Add the audit child the same way. Existing
Booking child factories live in files such as
`src/main/webapp/ui/src/modules/booking/pages/bookable-items/routes.ts` and use
`createRoute({ getParentRoute, path, component })`.

### The audit backend already exists

`src/main/java/com/researchspace/api/v2/controller/ApiV2AuditController.java:42-69`
provides:

```java
@GetMapping("/{resource}/{id}/audit")
public ApiV2ListResult<ApiV2AuditEvent> list(...)

@GetMapping("/{resource}/{id}/audit/count")
public ApiV2CountResult count(...)
```

The list accepts `page`, `limit`, optional `dateFrom`, optional `dateTo`, and
optional `actions`. `ApiV2AuditEvent` contains `timestamp`, `username`,
`fullName`, `domain`, `action`, `description`, and `payload`.

`src/main/java/com/researchspace/api/v2/resource/ApiV2AuditLog.java:59-97`
first checks authentication and resource readability, then searches the
existing audit trail. `AuditTrailHandlerImpl` also limits a non-system-
administrator to events by users they can view. The endpoint limits one query
window to 183 days and returns 400 for an inverted or wider range.

Keep these authorization rules. Do not add a Booking controller, database
table, audit DAO, or Envers query.

### Both selected resource types are already compatible

- `BookingConfiguration.getAuditTrailIdentifier()` returns
  `booking-configurations:<id>`. The resource route is
  `/api/v2/booking-configurations/{id}/audit`.
- `User` inherits `getOidString()` with `@AuditTrailIdentifier`, yielding
  `US<id>`. The resource route is `/api/v2/users/{id}/audit`.
- `ApiV2UserResource.OWN_ROW_UNLESS_SYSADMIN` lets a system administrator read
  every user and otherwise restricts the caller to their own row. The user
  selector must show only rows returned by that endpoint.
- `ApiV2BookingConfigurationResource` allows authenticated reads and sysadmin
  writes. Its audit history is therefore available to authenticated users,
  subject to the audit actor-visibility rule.

The backend integration test
`src/test/java/com/researchspace/api/v2/contract/ApiV2RelationshipContractMVCIT.java:335-350`
already proves that a booking configuration's audit and count routes answer.
`src/test/java/com/researchspace/api/v2/controller/ApiV2AuditControllerTest.java:62-92`
proves the user audit route.

### Existing frontend patterns to reuse

- `BookableItemPicker` already returns `configurationId`, target Global ID,
  name, timezone, and scheduling policy. `loadBookableItems` already accepts an
  `includeDisabled` argument, but the component does not expose it.
- `BookableItemPicker.tsx:161-204` is the model for a server-backed Base UI
  combobox with debounced input, React Query, loading/error handling, and
  accessible labels.
- `bookingTime.ts` and `all-bookable-items/calendarDate.ts` already provide
  Temporal-backed calendar-date parsing, `addCalendarDays`, `zonedDayBounds`,
  and `localToday`. Reuse them instead of adding another date parser.
- `BookingEventList.tsx` is the model for a non-suspending React Query list with
  loading, error, empty, rows, and previous/next pagination states.
- Parse network responses with Valibot, `parseOrThrow`, and `v2ListEnvelope`.
- Send REST API v2 requests with `bookingApiV2Headers(token)` and get the token
  from `useOauthTokenQuery({ useRestApiV2: true })`.
- Use the primitives under `src/modules/common/ui/`. Do not add a component
  library.

### Product and audit constraints

`RSDEV-1187-booking-design.md:86-95` says to use Envers for earlier row values,
audit-trail events for actions and actors, and to publish events only after
commit. This page reads audit-trail events. It is not a version-history or row
diff viewer.

The payload is a recorded snapshot of audit properties, not a field-level diff.
Label it "Recorded values". Do not call the payload "Changes".

### Security and reliability contract

This is a security-grade viewer of the existing audit store. For one selected
resource, date range, authorization context, and result snapshot, it must not
silently omit, duplicate, or reorder distinct events while the user paginates.

The existing audit store is the trust boundary. This plan does not make the log
files tamper-evident and does not change their retention policy. If the product
requires detection of filesystem tampering, immutable retention, or proof for
an external compliance regime, stop and write a separate audit-storage plan.

The viewer and endpoint must provide these guarantees:

1. The server chooses a snapshot timestamp for the first page. It uses the
   earlier of the requested upper bound and the server's current time.
2. The first response returns that timestamp. Every later page request sends it
   back. Newer events cannot move an existing result to another page.
3. Distinct events have a deterministic order. Timestamp descending is the
   primary order. A canonical event key is the tie-breaker.
4. The canonical key covers timestamp, actor identifiers, domain, action,
   description, and the authorized payload in a fixed serialization. It is an
   ordering and response identity key, not a claim of tamper evidence.
5. Exact duplicate events remain separate results. Their relative order does
   not matter because their displayed and canonical values are identical.
6. The endpoint fails the request if an eligible audit file cannot be read or
   parsed. It must not return a partial page with HTTP 200.
7. The response includes the snapshot timestamp and a result-set fingerprint.
   Later pages send both values. If the authorized result set at that snapshot
   no longer has the same fingerprint, the endpoint returns a conflict response
   that tells the client to restart from page 1.
8. The fingerprint covers the complete ordered result set, including repeated
   canonical keys. It detects retention, late writes, actor-visibility changes,
   or log rotation that changes the result set. A revoked resource read still
   returns the existing authorization result.
9. The page shows "Results as of" and provides Refresh. Refresh discards the
   snapshot and starts again at page 1.

The endpoint continues to return only fields allowed by existing resource and
actor authorization. Booking configuration payloads, audit actor names, and
plain-text descriptions are approved for callers who pass those checks. User
email remains subject to the existing user-resource row policy. Omitting an
approved field from the table is a presentation choice, not a confidentiality
control.

## Commands you will need

Run all commands from the repository root. Do not insert a standalone `--`
after a pnpm script name.

| Purpose | Command | Expected on success |
|---|---|---|
| Load router guidance | `pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core` | exit 0 |
| Load navigation guidance | `pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/navigation` | exit 0 |
| Focused frontend tests | `pnpm test src/modules/booking/pages/audit/__tests__/audit.test.ts src/modules/booking/pages/audit/__tests__/AuditPage.test.tsx src/modules/booking/pages/__tests__/BookingPage.test.tsx src/modules/booking/components/__tests__/BookableItemPicker.test.tsx` | all tests pass |
| Focused backend unit tests | `mvn test -Dtest=ApiV2AuditControllerTest,ApiV2AuditLogTest,ApiV2ControllerAdviceTest,ApiV2OpenApiGeneratorTest,AuditTrailHandlerImplTest,BasicLogQuerySearcherTest,LogFileTrackerTest -Dfast=true` | build success |
| Authorization contracts | `mvn test -Dtest=ApiV2AccessContractMVCIT,ApiV2RelationshipContractMVCIT` | build success |
| Extract English keys | `pnpm run i18n:extract --sync-primary` | exit 0; only intended English catalog changes |
| Generate i18n types | `pnpm run i18n:types` | exit 0 |
| Validate i18n | `pnpm run i18n:lint` | exit 0 |
| Type check | `pnpm tsc` | exit 0, no TypeScript errors |
| Lint | `pnpm lint` | exit 0, no Biome errors |

## Suggested executor toolkit

- Use the `react-testing-library` skill, if available, for the page and picker
  tests.
- Keep the `ponytail` skill active. Extend the existing generic audit endpoint
  instead of creating a Booking-specific audit service.
- Do not use the Browser Mode skill for this plan. The requested behavior is
  covered by React Testing Library and MSW without running the full application.

## Scope

### In scope

- `src/main/webapp/ui/src/modules/booking/pages/BookingPage.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/__tests__/BookingPage.test.tsx`
- `src/main/webapp/ui/src/modules/booking/components/BookableItemPicker.tsx`
- `src/main/webapp/ui/src/modules/booking/components/__tests__/BookableItemPicker.test.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/audit/audit.ts` (create)
- `src/main/webapp/ui/src/modules/booking/pages/audit/AuditPage.tsx` (create)
- `src/main/webapp/ui/src/modules/booking/pages/audit/routes.ts` (create)
- `src/main/webapp/ui/src/modules/booking/pages/audit/__tests__/audit.test.ts` (create)
- `src/main/webapp/ui/src/modules/booking/pages/audit/__tests__/AuditPage.test.tsx` (create)
- `src/main/webapp/ui/src/modules/common/app/router.tsx`
- `src/main/webapp/ui/src/modules/common/i18n/locales/en-US/booking.json`
- `src/main/webapp/ui/src/modules/common/i18n/resources.d.ts`
- `src/main/java/com/researchspace/api/v2/controller/ApiV2AuditController.java`
- `src/main/java/com/researchspace/api/v2/controller/ApiV2ControllerAdvice.java`
- `src/main/java/com/researchspace/api/v2/controller/ApiV2AuditSnapshotConflictException.java` (create)
- `src/main/java/com/researchspace/api/v2/model/ApiV2AuditEvent.java`
- `src/main/java/com/researchspace/api/v2/model/ApiV2AuditQuery.java`
- `src/main/java/com/researchspace/api/v2/model/ApiV2AuditPage.java` (create)
- `src/main/java/com/researchspace/api/v2/resource/ApiV2AuditLog.java`
- `src/main/java/com/researchspace/api/v2/openapi/ApiV2OpenApiGenerator.java`
- `src/main/java/com/researchspace/service/audit/search/AuditTrailSearchResult.java`
- `src/main/java/com/researchspace/service/audit/search/AuditTrailSearchPage.java` (create)
- `src/main/java/com/researchspace/service/audit/search/AuditTrailSearchException.java` (create)
- `src/main/java/com/researchspace/service/audit/search/AuditTrailHandler.java`
- `src/main/java/com/researchspace/service/audit/search/AuditTrailHandlerImpl.java`
- `src/main/java/com/researchspace/service/audit/search/IAuditTrailSearch.java`
- `src/main/java/com/researchspace/service/audit/search/BasicLogQuerySearcher.java`
- `src/main/java/com/researchspace/service/audit/search/LogFileTracker.java`
- `src/test/java/com/researchspace/api/v2/controller/ApiV2AuditControllerTest.java`
- `src/test/java/com/researchspace/api/v2/controller/ApiV2ControllerAdviceTest.java`
- `src/test/java/com/researchspace/api/v2/resource/ApiV2AuditLogTest.java`
- `src/test/java/com/researchspace/api/v2/contract/ApiV2AccessContractMVCIT.java`
- `src/test/java/com/researchspace/api/v2/contract/ApiV2RelationshipContractMVCIT.java`
- `src/test/java/com/researchspace/service/audit/search/BasicLogQuerySearcherTest.java` (create)
- `src/test/java/com/researchspace/service/audit/search/LogFileTrackerTest.java` (create)
- `src/test/java/com/researchspace/service/audit/search/AuditTrailHandlerImplTest.java`
- `src/test/java/com/researchspace/api/v2/openapi/ApiV2OpenApiGeneratorTest.java`
- the applicable user-facing error bundle under `src/main/resources/bundles/`
- `plans/README.md`, plan metadata and final status only

### Out of scope

- Database, Liquibase, Envers, audit event publication, log format, retention,
  and audit storage.
- The legacy `/audit/auditing` JSP, JavaScript, controller, CSV download, and
  Administration menu.
- A global Booking audit feed.
- Booking events grouped by bookable item.
- Booking activity performed by, or on behalf of, a selected user.
- Audit history for deleted booking configurations or deleted users. The
  resource-specific endpoint resolves the current resource before searching.
- Field-level diffs or restoration from Envers.
- CSV export, sorting, and multi-resource comparison.
- New dependencies or a new common table abstraction.
- Changing the existing REST API v2 resource or audit actor authorization
  policies.
- Refactoring authorization for the existing Booking settings and bookable-item
  routes.

## Git workflow

- Suggested branch: `feat/booking-auditing`
- Keep commits aligned with logical work: reliable audit pagination, access
  contracts, data client and page, route and translations, then verification.
- Match the repository's plain imperative commit style, for example
  `Implement booking item details page`.
- Do not push or open a pull request unless instructed.

## Steps

### Step 1: Characterize the existing endpoints

Before edits, run the focused backend audit tests and authorization contracts.
Then inspect the OpenAPI document and cited classes to confirm these response,
query, and access rules have not drifted:

- `GET /api/v2/booking-configurations/{id}/audit`
- `GET /api/v2/users/{id}/audit`
- query: `page`, `limit`, `dateFrom`, `dateTo`.
- response: the standard one-based v2 list envelope containing
  `ApiV2AuditEvent` documents.
- an authenticated user can read an existing booking configuration audit.
- a user can read their own user audit. Another user's audit returns the
  existing concealed 404 response.
- a system administrator can read another user's audit.
- an anonymous caller receives 401.

Add missing characterization cases before changing the pagination contract. In
particular, test an existing booking configuration with an ordinary user's API
key. A 404 test against a nonexistent ID does not characterize authorization.
Do not change production code if a characterization test fails.

**Verify**:
`mvn test -Dtest=ApiV2AuditControllerTest,ApiV2AuditLogTest,AuditTrailHandlerImplTest -Dfast=true`
and
`mvn test -Dtest=ApiV2AccessContractMVCIT,ApiV2RelationshipContractMVCIT` ->
BUILD SUCCESS.

### Step 2: Make audit pagination snapshot-stable

Strengthen the existing generic audit endpoint. Do not create a Booking-
specific service.

1. Add optional `snapshotAt` and `snapshotFingerprint` fields to
   `ApiV2AuditQuery`. They must be both absent or both present. Reject a future
   `snapshotAt`. Accept only a 64-character lowercase hexadecimal fingerprint.
2. Add `ApiV2AuditPage<T>`. Preserve the standard list-envelope fields and add
   required `snapshotAt` and `snapshotFingerprint` fields.
3. On a request without snapshot fields, choose `snapshotAt` from the server
   clock. Use the earlier of that instant and `dateTo` as the effective upper
   bound. Never trust the browser clock as the snapshot source.
4. Add a snapshot-search method to `AuditTrailHandler`. It must apply the
   existing actor-visibility restriction and return every matching event in the
   bounded resource and date query. Keep the existing paged method for legacy
   callers. The API v2 audit endpoint will paginate the stable mapped result.
5. In `ApiV2AuditLog`, apply the existing resource-field allowlist before any
   response identity is calculated. Give each authorized mapped event a
   canonical key. Use a fixed UTF-8 serialization of timestamp, username, full
   name, domain, action, description, and authorized payload with recursively
   sorted map keys. Hash it with SHA-256 and expose the lowercase hexadecimal
   value as `eventId`.
6. Sort the complete mapped result by timestamp descending and then `eventId`
   ascending. Slice the requested page only after this sort. Preserve exact
   duplicate events as separate results. Do not use filesystem iteration order
   or a log filename as a tie-breaker.
7. Add `AuditTrailSearchPage`, or an equivalently small return type, for the
   bounded snapshot search. Keep existing callers source-compatible where
   practical. Do not expose a hash of fields that the caller cannot read.
8. Compute `snapshotFingerprint` over the complete ordered sequence of event
   IDs. Use a length prefix or delimiter that cannot create ambiguous
   concatenations. Repeated IDs must appear repeatedly in the digest input.
9. On later page requests, re-run authorization and the bounded search at the
   supplied `snapshotAt`. Compare the complete result fingerprint before
   returning rows. If it differs, return HTTP 409 with localized text that tells
   the caller to refresh the audit results. Do not return a page from a changed
   result set.
10. Change `BasicLogQuerySearcher` and `LogFileTracker` to fail closed. If a
    candidate audit file cannot be classified, or an eligible file cannot be
    read or parsed, propagate a specific audit-search exception. Map it to HTTP
    503 with localized user-facing text. Never log and continue with a partial
    result. Log the exception internally without exposing filesystem paths or
    raw log content in the API response.
11. Map snapshot conflicts and unavailable searches in
    `ApiV2ControllerAdvice`. Add both problem responses to the generated OpenAPI
    document and cover them in controller-advice and OpenAPI tests. Use semantic
    bundle keys under `errors.api.v2.audit.*` for an invalid snapshot, a changed
    snapshot, and unavailable audit data.
12. Keep the existing 183-day limit, resource readability check, actor-
    visibility restriction, search-audit event, and private no-store response
    header.

Add focused tests for canonical serialization, stable ordering of distinct
events with the same millisecond timestamp, repeated identical events,
snapshot upper-bound selection, fingerprint agreement, a late or removed event
causing 409, unreadable and malformed files causing 503, and unchanged
authorization behavior. Assert that an event after `snapshotAt` does not change
later pages. Keep a contract assertion for `Cache-Control: private, no-store`.

Update the OpenAPI audit response and query documentation. Document that
`eventId` is deterministic identity for display and ordering. It is not a
cryptographic proof that the source log was not changed.

**Verify**:
`mvn test -Dtest=ApiV2AuditControllerTest,ApiV2AuditLogTest,ApiV2ControllerAdviceTest,ApiV2OpenApiGeneratorTest,AuditTrailHandlerImplTest,BasicLogQuerySearcherTest,LogFileTrackerTest -Dfast=true`
-> BUILD SUCCESS.

### Step 3: Add the typed audit client and date-range helpers

Create `pages/audit/audit.ts` with only the data needed by this page:

1. Define an `AuditScope` union of `"bookableItem" | "user"`.
2. Define Valibot schemas and inferred types for:
   - an audit event, including `eventId`;
   - the audit page, including `snapshotAt` and `snapshotFingerprint`;
   - a user option containing `id`, `username`, `firstName`, and `lastName`;
   - the standard v2 user-list envelope.
3. Keep `domain` and `action` as validated strings. The backend owns the enum
   and the page should not fail when a later release adds an enum value.
4. Add
   `loadAuditEvents({ scope, id, page, dateFrom, dateTo, snapshot }, token, signal)`.
   It must choose `booking-configurations` or `users` from the scope, request a
   fixed limit of 20, send ISO date-time boundaries, use
   `bookingApiV2Headers`, and parse with `parseOrThrow`. Omit both snapshot
   fields on page 1 of a new result set. Send both values returned by page 1 on
   every later request. Use the existing `parseApiV2Problem` helper for failed
   responses so the page can distinguish snapshot conflict, unavailable audit
   data, and other failures by status and problem code.
5. Add `loadAuditUsers(term, token, signal)`. Request only the four option
   fields. Search username, first name, and last name with the existing typed
   RSQL serializer. Combine the three `contains` comparisons with `or`, omit
   `where` for a blank term, and return the first 20 rows in the backend's
   default `username,id` order. Do not download the whole user collection.
6. Add small pure helpers for a default 182-calendar-date window and validation.
   Build the default with the existing `localToday()` and
   `addCalendarDays(today, -181)`. For requests, use
   `Temporal.Now.timeZoneId()` with the existing `zonedDayBounds`: send the
   selected From day's start and one millisecond before the selected To day's
   exclusive end. A range is valid only when both dates parse, From is not after
   To, and the elapsed duration between those instants is no more than
   `183 * 24` hours. Use elapsed duration because a daylight-saving transition
   can make a calendar-date count differ from the backend's exact
   `Duration.ofDays(183)` limit. Do not parse `YYYY-MM-DD` with
   `new Date(value)`, which treats it as UTC rather than the browser's local
   calendar date.
7. Export stable React Query key factories. A new first-page key includes scope,
   selected ID, both dates, page 1, and a local refresh generation. It does not
   change when page 1 returns its snapshot. Later-page keys also include the
   returned snapshot values and page number. This avoids a second page-1 request
   when the component stores the server snapshot.

Do not add an API client class, repository interface, or generic audit hook.

Add `audit.test.ts` for URL selection, one-based pagination, headers, parsing,
snapshot round trips, query-key stability, user search, local-day boundary
conversion, inverted ranges, and the 183-day limit. Include a user-search term
such as `a\";id==*` and prove it remains one escaped RSQL value. Test the
maximum accepted search length and the rejected response.

**Verify**:
`pnpm test src/modules/booking/pages/audit/__tests__/audit.test.ts` -> all new
tests pass.

### Step 4: Let users pick disabled bookable items

Expose an optional `includeDisabled?: boolean` prop on `BookableItemPicker`,
defaulting to `false`. Thread it through `loadAllBookableItems` to the existing
`loadBookableItems(..., includeDisabled)` argument. Include the boolean in the
React Query key so enabled-only and all-item results never share cache entries.

Fix the existing `loadBookableItems` filter construction as part of that small
change. When `includeDisabled` is true and a target or name filter exists, send
only that target or name filter. When it is true and the search is blank, omit
the `where` parameter entirely. The current ternary falls back to
`enabled==true` in the blank-search case, so merely passing the boolean through
would still hide disabled items when the picker first opens.

The audit page will pass `includeDisabled`. Existing booking forms must retain
their enabled-only behavior without caller changes.

Extend `BookableItemPicker.test.tsx` with three checks:

- the default component query still includes `enabled==true`;
- `includeDisabled` omits only the enabled predicate while preserving a typed
  name or target filter;
- `includeDisabled` with a blank search omits `where` and returns disabled
  options.

**Verify**:
`pnpm test src/modules/booking/components/__tests__/BookableItemPicker.test.tsx`
-> existing and new tests pass.

### Step 5: Build the audit page

Create `AuditPage.tsx`. Keep page state local. The route does not need URL
search parameters in this first version.

The page must contain:

1. A level-one heading and a short description that says this is resource
   history, not booking activity by user.
2. A labelled fieldset for the resource type with Bookable item and User
   choices. Use the existing radio-group primitive or native radios.
3. A selector for the chosen type:
   - Bookable item uses `BookableItemPicker` with `includeDisabled` and stores
     its `configurationId`.
   - User uses a small server-backed Base UI combobox in this file. Follow
     `BookableItemPicker` for debouncing, accessible labels, empty results, and
     request errors. Display `First Last (username)`, falling back to username
     when the name is blank. Do not filter options by a frontend role check.
     The users endpoint returns every row to a system administrator and only
     the caller's row to another user.
4. Native `input type="date"` controls for From and To. Initialize them to the
   current local date and 181 days earlier, a safe 182-calendar-date window even
   when it crosses a daylight-saving transition. Show a localized validation
   error and disable the event query when the range is blank, inverted, or its
   converted ISO boundaries exceed 183 elapsed days.
5. Reset the event page to 1, discard the snapshot, and advance the local result
   generation when scope, selected resource, or date range changes. Clear the
   selected resource when scope changes.
6. Do not request audit events until a resource is selected and the dates are
   valid.
7. Render loading, request failure, no-selection, and no-events states with the
   existing `Spinner`, `Alert`, and `Empty` primitives.
8. Render results in the existing semantic table primitives. Columns:
   - Time, formatted with `Intl.DateTimeFormat(i18n.language, ...)` and wrapped
     in `<time dateTime={event.timestamp}>`;
   - Changed by, using a trimmed nonblank `fullName` or falling back to
     `username`, with username as secondary text when a full name exists;
   - Action, rendered as localized text, with an untranslated safe fallback for
     a future action value;
   - Recorded values.
   Use `eventId` with the duplicate occurrence index as the React row key.
   Exact duplicate events share an event ID and must still render as separate
   rows.
9. Recorded values are a compact `<dl>`, not raw JSON. For booking
   configurations, render only the known setting keys already present in
   `booking.json`. For users, render only `username` and `email`. Omit `id` and
   `target`, because the selected resource already identifies the subject.
   Render booleans and durations with existing localized yes/no and minute
   messages. If no known payload value or description exists, show a localized
   "No recorded values" message.
10. If `description` starts with the existing `subject=<username>` delegation
    marker, translate it as an "On behalf of" line. Otherwise render a nonblank
    description as plain text.
11. Show the server-provided snapshot timestamp as a localized "Results as of"
    value. Add a Refresh button that discards the snapshot, advances the result
    generation, returns to page 1, and starts a new query.
12. If a later page receives the snapshot-conflict response, do not merge or
    display it. Show an accessible warning and a button that restarts at page 1.
13. Add a previous/next `<nav>` using existing buttons. Disable buttons from
   `hasPrevPage` and `hasNextPage`; show `page` and `totalPages`. Keep the API's
   one-based page numbering throughout.

Do not make every row expandable, add column sorting, or build a reusable audit
table. There is one page and one presentation.

**Verify**:
`pnpm test src/modules/booking/pages/audit/__tests__/AuditPage.test.tsx` -> all
page tests pass.

### Step 6: Add the route and sidebar entry

Create `pages/audit/routes.ts` with `createAuditRoute(parent)` at `/auditing`,
rendering `AuditPage`.

In `BookingPage.tsx`:

- import an appropriate Lucide audit/history icon;
- add `{ key: "auditing", icon, link: <Link ... to="/booking/auditing" /> }`
  as a top-level item outside Administration;
- add `auditing` to the translated `labels` object.

In the common app router, import and add `createAuditRoute(bookingRouteBase)`
beside the other Booking routes.

Update `BookingPage.test.tsx` to assert:

- a system administrator sees an Auditing link to `/booking/auditing`;
- an ordinary user also sees the link;
- collapsing or removing Administration does not hide Auditing;
- direct navigation by an ordinary user renders the audit page.

Run the two TanStack Intent guidance commands before these edits, as required by
the repository instructions.

**Verify**:
`pnpm test src/modules/booking/pages/__tests__/BookingPage.test.tsx src/modules/booking/pages/audit/__tests__/AuditPage.test.tsx`
-> all tests pass and no route type errors occur.

### Step 7: Complete the page tests

Use MSW and the shared `server` from `@/__tests__/mswServer`. Follow the test
setup in `BookingPage.test.tsx` and the user-centric query rules in the root
`AGENTS.md`.

Cover these page behaviors in `AuditPage.test.tsx`:

- initial no-selection state and valid default date window;
- selecting a bookable item calls
  `/api/v2/booking-configurations/{configurationId}/audit` with page, limit,
  dateFrom, and dateTo;
- disabled bookable items are available to the audit picker;
- opening the item picker before typing does not send `enabled==true` when
  `includeDisabled` is set;
- switching to User clears the bookable item and does not reuse its audit query;
- user search calls `/api/v2/users` with a sparse fieldset and RSQL term;
- an over-limit user search shows an accessible request error and does not
  broaden the query;
- selecting a user calls `/api/v2/users/{id}/audit`;
- an ordinary user sees only the user options returned by the authorized API
  response, while a system administrator can receive multiple users;
- loading, network error, empty result, and populated result states;
- the populated table shows localized actor, action, timestamp markup, known
  payload values, and the translated delegation marker;
- HTML-like actor, description, and payload values render as text and do not
  create DOM elements or event handlers;
- unknown payload keys are not rendered;
- previous and next pagination request the correct one-based page;
- page 1 stores the returned snapshot and later pages send it;
- a new event after the snapshot does not move events between pages;
- changing target or dates resets page to 1 and discards the snapshot;
- Refresh discards the snapshot and displays the new result set;
- a snapshot conflict never displays the conflicting page and offers restart;
- invalid and over-wide ranges show an accessible error and issue no audit
  request;
- `expectAccessible(container)` passes in the no-selection and populated states.

Do not assert internal hook state, class names, or component implementation.

**Verify**:
`pnpm test src/modules/booking/pages/audit/__tests__/audit.test.ts src/modules/booking/pages/audit/__tests__/AuditPage.test.tsx src/modules/booking/pages/__tests__/BookingPage.test.tsx src/modules/booking/components/__tests__/BookableItemPicker.test.tsx`
-> all focused tests pass.

### Step 8: Extract translations and run final checks

While authoring, wrap every user-facing string in `t()` with a literal English
`defaultValue`. Use semantic keys under `booking:auditing` and
`booking:sidebar.auditing`.

Run `pnpm run i18n:extract --sync-primary`, review the English catalog diff,
remove every `defaultValue`, then run the type and lint generators. Never use
`--sync-all`.

Suggested translation groups under `auditing`:

- `title`, `description`, `scope.legend`, `scope.bookableItem`, `scope.user`;
- `selectors.*` for labels, placeholders, empty, and request failures;
- `dates.from`, `dates.to`, and range validation;
- `states.*` for selection, loading, empty, request failure, snapshot conflict,
  and unavailable audit data;
- `columns.*`, `actions.*`, `values.*`, `delegation`, and `pagination.*`.

Then run the backend tests, TypeScript, i18n lint, Biome, and the focused
frontend test set.

**Verify**:

```text
mvn test -Dtest=ApiV2AuditControllerTest,ApiV2AuditLogTest,ApiV2ControllerAdviceTest,ApiV2OpenApiGeneratorTest,AuditTrailHandlerImplTest,BasicLogQuerySearcherTest,LogFileTrackerTest -Dfast=true
mvn test -Dtest=ApiV2AccessContractMVCIT,ApiV2RelationshipContractMVCIT
pnpm run i18n:types
pnpm run i18n:lint
pnpm tsc
pnpm lint
pnpm test src/modules/booking/pages/audit/__tests__/audit.test.ts src/modules/booking/pages/audit/__tests__/AuditPage.test.tsx src/modules/booking/pages/__tests__/BookingPage.test.tsx src/modules/booking/components/__tests__/BookableItemPicker.test.tsx
```

Every command exits 0. `rg -n "defaultValue" src/main/webapp/ui/src/modules/booking/pages/audit`
returns no matches.

## Test plan

- Pure data-client tests live in `pages/audit/__tests__/audit.test.ts`.
- Component and interaction tests live in
  `pages/audit/__tests__/AuditPage.test.tsx`.
- Sidebar regression coverage stays in the existing `BookingPage.test.tsx`.
- Picker regression coverage stays in the existing
  `BookableItemPicker.test.tsx`.
- Backend unit tests cover snapshot construction, canonical event IDs,
  deterministic ordering, fingerprint validation, and fail-closed file errors.
- REST API v2 contract tests cover anonymous, ordinary-user, self, other-user,
  system-administrator, and readable booking-configuration access.
- Use direct `render` and `within` imports from `@testing-library/react`,
  `userEvent.setup()`, semantic jest-dom assertions, and `expectAccessible`.
- Do not mock authorization in the contract tests. Use real API keys and
  existing resources.

## Done criteria

- [ ] Every authenticated Booking user sees an Auditing link at
  `/booking/auditing`.
- [ ] An authenticated user can select a current or disabled bookable item and
  see the events permitted by existing resource and actor authorization.
- [ ] A system administrator can search for a current user and see that user's
  resource audit records.
- [ ] Another user can select and inspect only their own user resource.
- [ ] The page supports any window of at most 183 elapsed days, including past
  windows, and rejects invalid ranges before a request.
- [ ] Pagination uses a server-selected snapshot, deterministic event IDs and
  ordering, and a complete result-set fingerprint.
- [ ] A changed snapshot produces a conflict and restart path. It never produces
  a silently mixed page sequence.
- [ ] An unreadable or malformed eligible audit file produces an explicit
  unavailable response. It never produces a partial HTTP 200 response.
- [ ] Audit requests are parsed with Valibot and cancelled through the React
  Query signal.
- [ ] Loading, error, empty, populated, and paginated states are tested.
- [ ] Recorded values never claim to be field-level diffs and unknown payload
  keys are not rendered.
- [ ] No audit storage, log format, legacy audit screen, or dependency file
  changed.
- [ ] Focused frontend, backend unit, and authorization contract tests pass.
- [ ] `pnpm run i18n:lint`, `pnpm tsc`, and `pnpm lint` exit 0.
- [ ] `git status --short` shows no modified source files outside the in-scope
  list.
- [ ] The plan status in `plans/README.md` is DONE.

## STOP conditions

Stop and report instead of improvising if:

- Product intent says "user audit" means booking actions performed by, or on
  behalf of, the user. `/api/v2/users/{id}/audit` does not mean that.
- Product intent says "bookable item audit" must include `TimeSlotBooking`
  creation, edits, and cancellations. The configuration endpoint does not group
  booking events by target.
- Deleted resources must remain selectable or auditable. The generic route
  requires the current resource to resolve before searching.
- The page must merge bookable-item and user events into one chronological feed.
- Existing REST API v2 resource readability or audit actor visibility differs
  from Current state or must change for this feature.
- Product security requires tamper-evident storage, immutable retention, or
  external compliance proof. This plan only strengthens the viewer over the
  existing audit store.
- Stable snapshot pagination requires changing the audit log format or adding a
  durable audit table. Write a separate storage plan instead.
- The generic endpoint names, one-based page numbering, or 183-day restriction
  differ from Current state.
- A route change requires a new router context or a change to the root route's
  authentication model.
- A focused verification command fails twice after a reasonable correction.
- Implementation appears to require a file listed as out of scope.

Any of the first four product conditions changes the meaning of the requested
audit data. Write a separate plan rather than stretching this one.

## Maintenance notes

- Reviewers should verify the two meanings in Why this matters against the
  product wording. The API names look generic enough to invite the wrong
  interpretation.
- The audit payload is an allowlisted snapshot. When a new auditable REST field
  is added, decide explicitly whether this page should render it.
- Treat `eventId` and `snapshotFingerprint` as deterministic consistency
  values. Do not describe them as signatures or tamper evidence.
- Keep canonical event serialization versioned and covered by fixed test
  vectors. A serialization change invalidates active snapshots and must produce
  a clear restart response.
- Keep the snapshot timestamp, fingerprint, selected resource, date range, and
  authorization context aligned. Never reuse a snapshot after one changes.
- Keep the 183-elapsed-day UI validation aligned with
  `ApiV2AuditLog.MAX_SEARCH_RANGE`. If the backend changes the limit, change the
  page helper and its boundary tests in the same pull request.
- If deleted-resource history becomes a requirement, the backend needs a
  sysadmin-authorized query that does not rely on resolving a live resource.
- If booking activity by item or user becomes a requirement, first define a
  stable Booking audit domain and safe target/requester projection. Do not
  repurpose user-resource audit history.
