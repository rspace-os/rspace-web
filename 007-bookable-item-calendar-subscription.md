# Plan 007: Add calendar subscription management to the merged bookable item page

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving on. If a STOP condition
> occurs, stop and report it. Do not improvise. When done, update this plan's row
> in `plans/README.md` unless a reviewer says they maintain the index.
>
> **Drift check, run first**:
> `git diff --stat 37e013af4..HEAD -- src/main/java/com/researchspace/booking/service/BookingCalendarManager.java src/main/java/com/researchspace/booking/service/BookingCalendarManagerImpl.java src/main/java/com/researchspace/api/v2/controller src/main/java/com/researchspace/api/v2/openapi src/main/java/com/researchspace/webapp/controller src/main/resources/bundles src/main/webapp/WEB-INF/sitemesh3.xml src/main/webapp/ui/src/modules/booking/pages/bookable-items src/main/webapp/ui/src/modules/booking/prototypes/MergedBookableItemPage.prototype.stories.tsx src/main/webapp/ui/src/modules/common/i18n/locales/en-US/booking.json src/main/webapp/ui/src/modules/common/i18n/resources.d.ts src/test/java/com/researchspace/booking/service src/test/java/com/researchspace/api/v2 src/test/java/com/researchspace/webapp/controller DevDocs/DeveloperNotes`
> Plans 005 and 006 must both be DONE first. Compare changed in-scope files with
> Current state. Also run `git status --short` and identify operator-approved
> concurrent changes before editing. A change to the one-time token contract,
> merged page header, feed privacy rules, or public-route security is a STOP
> condition until this plan is reconciled.

## Status

- **Priority**: P1
- **Effort**: L
- **Risk**: HIGH
- **Depends on**: `plans/006-bookable-item-audit-trail.md`, which depends on Plan 005
- **Category**: security
- **Planned at**: commit `37e013af4`, 2026-08-27

## Why this matters

The branch already stores hashed subscription credentials and generates
privacy-shaped iCalendar feeds, but users cannot create, replace, or revoke a
subscription. There is also no public controller that serves the feed. The
selected merged-page prototype supplies the right placement, a small popover
beside the Enabled badge, but its assumption that opening the popover can fetch
an existing raw link conflicts with the server's security contract.

This plan completes the existing backend and adapts that popover to the real
one-time reveal lifecycle. It does not add another page, another database
model, or a second calendar generator.

## Current state

### The credential and feed domain already exist

`CONTEXT.md:326-333` defines the terms this feature must use:

```text
Calendar subscription: one user's revocable, read-only external calendar feed
for one booking configuration.

Subscription link: the bearer URL revealed when a calendar subscription is
created or replaced. It grants the owner's current privacy-shaped view of that
bookable item's events.
```

`RSDEV-1187-booking-design.md:99-107` fixes the security contract:

```text
Use a random bearer token in each calendar feed URL. Store only a hash of the
token. Permit the owner to revoke or rotate the token.

Serve calendar feeds outside the API-key and OAuth route group. Resolve the
token to its owner and scope. Apply the owner's current permission when the
service builds the feed.

Return the raw token only when the service creates it. Never log the token.
```

Do not rename the concepts to calendar integration, calendar export, or shared
calendar. Do not place a raw token or its hash in an audit event.

### The manager implements most of the behavior

`BookingCalendarManager.java:11-71` already exposes:

```java
record Status(boolean active, Date updatedAt) {}
record Created(Status status, String subscriptionUrl) {}

Status status(Long configurationId, User subject, User actor);
Created createOrRotate(Long configurationId, User subject, User actor);
void revoke(Long configurationId, User subject, User actor);
FeedResult feed(String rawToken, Locale locale, Date refreshedAt);
```

`BookingCalendarManagerImpl.java:116-187` already:

- requires an active personal caller for management operations;
- checks the Booking feature and current configuration readability for status
  and create/replace;
- locks the configuration before creating or replacing;
- generates 32 random bytes as a 43-character base64url token;
- stores only `CryptoUtils.hashToken(rawToken)`;
- returns the raw subscription URL only from `createOrRotate`;
- logs subscription, configuration, actor, and subject identifiers, not the
  raw token;
- requires the configuration to exist for revoke, then removes only the
  caller's row and stays idempotent when that row is already absent.

`BookingCalendarManagerImpl.java:226-275` resolves a token hash, checks that
the owner remains active and Booking-enabled, asks `TimeSlotBookingManager` for
the owner's current readable calendar source, and applies event and byte caps.
It maps capacity exhaustion to `AtCapacity` and size or generation failures to
`Oversized`. The final broad `RuntimeException` catch currently returns
`NotFound`, which makes a backend outage look like a missing credential. Change
that catch to log a generic warning without the token and return the same
service-unavailable result used for generation failure. Do not change a real
missing, inactive, disabled, or unreadable subscription from concealed
`NotFound`.

Keep the existing lifecycle when Booking is disabled globally or for one user.
Do not bulk-delete or revoke stored hashes. A feed request for a disabled or
inactive owner still returns concealed 404 with no events because current state
is checked on every refresh. Retaining the unusable hash allows the existing
permission model to decide whether it becomes usable again after re-enablement.

The entity, DAO, hash-only uniqueness constraints, Liquibase change, feed
generator, feed limits, and generator tests already exist. Reuse them.

### No controllers expose the manager

There is no authenticated management controller and no anonymous `.ics`
delivery controller. Add these exact HTTP contracts:

| Method | Path | Success response |
|---|---|---|
| GET | `/api/v2/booking-configurations/{configurationId}/calendar-subscription` | 200 status document, never a URL |
| POST | `/api/v2/booking-configurations/{configurationId}/calendar-subscription` | 200 created/replaced document with a URL revealed once |
| DELETE | `/api/v2/booking-configurations/{configurationId}/calendar-subscription` | 204 with no body |
| GET | `/public/booking/calendars/feed.ics?token={token}` | 200 iCalendar bytes for a valid available feed |

The authenticated response shapes are fixed for this plan:

```json
{"active":false,"updatedAt":null}
```

```json
{
  "active":true,
  "updatedAt":"2026-08-27T12:00:00.000+00:00",
  "subscriptionUrl":"https://rspace.example/public/booking/calendars/feed.ics?token=<token>"
}
```

GET returns only `active` and `updatedAt`, including when active. POST returns
the three fields above. Do not add the URL, token, hash, owner, or configuration
ID to the status response.

The nested `BookingCalendarNotFoundException` is already the manager's generic
missing signal and the missing-or-unreadable signal for status and create. Map
it through `ApiV2ControllerAdvice` to the existing localized 404
`ApiV2Problem`. Keep `AuthorizationException` mapped to 403. Preserve revoke's
ability to remove the caller's row after they lose read access to a still-live
configuration. That lets an owner invalidate a credential which could become
usable again if access returns. The existing API v2 authentication interceptor
already applies private no-store caching. Do not add a second authentication or
cache filter.

### The public feed needs an explicit transport contract

`src/main/webapp/WEB-INF/security.xml:39-46` already maps `/public/**` to
anonymous access. Keep that mapping unchanged. The feed controller must reject
anything except exactly one `token` query parameter containing 43 base64url
characters before it calls the manager. Use the fixed path
`/public/booking/calendars/feed.ics`; the `.ics` suffix is never part of the
token.

This placement keeps the bearer out of `HttpServletRequest.getRequestURI()`, so
`PerformanceLoggingInterceptor` cannot put it in slow-request logs. It does not
make the subscription URL tokenless. A calendar client must present the bearer
on every refresh. Redirecting once to a tokenless URL would require a cookie,
session, or second persistent bearer alias; URL fragments never reach the
server, and calendar clients do not run browser history APIs. Therefore
"stripping" in this plan means removing/redacting the query parameter from
application, container, proxy, observability, and test logs. Do not promise to
remove it from the URL stored by Google, Outlook, or another calendar client.

`LoggingInterceptor` logs request parameters for `/**`, so annotate the feed
method with `@IgnoreInLoggingInterceptor(ignoreAll = true)`. The slow-request
interceptor logs only `getRequestURI()` and is safe with the fixed path. Before
release, verify that the supported servlet-container and reverse-proxy access
log formats either omit query strings or redact `token` for this exact route.
Document the required deployment setting. If any supported deployment must log
the raw query string and cannot redact it, stop rather than ship a bearer URL
that is copied into operational logs.

Map manager results as follows:

| Manager result | HTTP response |
|---|---|
| `Available` | 200, `text/calendar;charset=UTF-8`, `Content-Disposition: inline; filename="rspace-bookings.ics"` |
| `NotFound` | 404 with an empty body |
| `AtCapacity` | 503 with `Retry-After: 30` and an empty body |
| `Oversized` or generation unavailable | 503 with an empty body |

Every response, including 404 and 503, has `Cache-Control: private, no-store`.
Do not put the token in response bodies, exception messages, application or
access logs, metrics, analytics, referrers, or test names. Set a restrictive
`Referrer-Policy: no-referrer` header as defense in depth even though calendar
clients are not browsers.

`sitemesh3.xml:70-80` decorates broad public paths with `externalPages.jsp`.
Add a context-prefix-safe exclude for
`*/public/booking/calendars/feed.ics` before that decorator mapping. Without it,
SiteMesh can wrap calendar bytes in HTML.

### The prototype placement survives, but its fetch lifecycle does not

After Plans 005 and 006, `BookableItemPage` has Bookings, Details, and Audit log
tabs. The identity header uses `InventoryItem idPlacement="title"`, followed by
the Enabled or Disabled badge. Put an `Add to calendar` trigger beside that
badge for every authenticated user who can read the item. This action is not a
sysadmin edit operation.

`MergedBookableItemPage.prototype.stories.tsx:743-935` supplies the selected
popover presentation and already uses the installed Font Awesome Google and
Microsoft icons plus the shared Base UI popover. Keep that placement and reuse
those dependencies. Reject these prototype assumptions:

- Opening the popover mints or recovers a raw URL.
- An active subscription's URL can be fetched later.
- There are only loading and ready states.

The real popover has these states:

1. Status loading.
2. Status error with Retry.
3. Inactive, with a deliberate Generate link action.
4. Newly generated or replaced, with a one-time warning, Google Calendar,
   Outlook, `webcal://`, and Copy link actions.
5. Active after a reload, with the created or replaced date, Replace link, and
   Revoke link actions, but no raw URL.
6. Replace and revoke confirmations inside the popover.
7. Mutation failure with a safe retry path.

Opening the popover runs GET only. POST runs only after Generate or confirmed
Replace. DELETE runs only after confirmed Revoke. Disable mutation buttons
while a request is pending and do not automatically retry POST or DELETE.

Keep a newly returned URL only in component-local memory. Do not put it in the
React Query status cache, localStorage, sessionStorage, router search state,
analytics, console output, or error text. Clear the old local URL before a
Replace request, because the old credential may be invalid by the time the
response arrives. Clear it after Revoke and when the component unmounts.

### Applicable repository conventions

- Concrete REST API v2 operations use a controller and `ApiV2Caller`, as in
  `BookingSettingsController`.
- REST API v2 failures use localized `ApiV2Problem` responses.
- Frontend response bodies use Valibot and `parseOrThrow`; non-2xx API v2
  responses use `parseApiV2Problem`.
- English text uses semantic keys in the Booking catalog.
- Unit tests use MSW and semantic React Testing Library queries.
- Browser Mode coverage reuses the story, MSW handlers, and page object created
  by Plan 005. Do not create a second page harness.
- Run root pnpm scripts without a standalone `--` separator.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Existing feed baseline | `mvn test -Dtest=BookingCalendarFeedGeneratorTest -Dfast=true` | BUILD SUCCESS |
| Manager tests | `mvn test -Dtest=BookingCalendarManagerTest,BookingCalendarFeedGeneratorTest -Dfast=true` | BUILD SUCCESS |
| Controller unit contracts | `mvn test -Dtest=BookingCalendarFeedControllerTest,LoggingInterceptorTest,PerformanceLoggingInterceptorTest,ApiV2ControllerAdviceTest,ApiV2OpenApiGeneratorTest -Dfast=true` | BUILD SUCCESS |
| Controller integration | `mvn test -Dtest=BookingCalendarSubscriptionControllerMVCIT,BookingCalendarFeedControllerMVCIT` | BUILD SUCCESS |
| Calendar client and component | `pnpm test src/modules/booking/pages/bookable-items/__tests__/bookableItemCalendarSubscription.test.ts src/modules/booking/pages/bookable-items/__tests__/CalendarSubscriptionPopover.test.tsx src/modules/booking/pages/bookable-items/__tests__/BookableItemPage.test.tsx` | all tests pass |
| Browser inner loop | `VITEST_BROWSERS=chromium pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx` | all tests pass in Chromium |
| Browser final | `pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx` | all tests pass in Chromium, Firefox, and WebKit |
| Extract English keys | `pnpm run i18n:extract --sync-primary` | exit 0; only intended English changes |
| Generate i18n types | `pnpm run i18n:types` | exit 0 |
| Validate i18n | `pnpm run i18n:lint` | exit 0 |
| Type check | `pnpm tsc` | exit 0, no TypeScript errors |
| Lint | `pnpm lint` | exit 0, no Biome errors |
| Dev Stack status | `./docker/dev/rspace-dev ps` | reports this worktree's stack and URL |
| Dev Stack boot | `./docker/dev/rspace-dev up` | full DB, backend, and frontend stack starts |

## Suggested executor toolkit

- Use `react-testing-library` for the popover component tests.
- Use `rspace-browser-tests` for the three-engine Browser Mode spec.
- Use `rspace-dev-stack` for the final live verification. The originating task
  authorizes starting the full stack.
- Use Playwright MCP, as requested by the operator, for live browser
  verification against the Dev Stack. Do not substitute standalone Playwright
  or the in-app preview for this acceptance step.
- Keep `ponytail` active. Reuse the manager, generator, DAO, entity, Base UI
  popover, installed icons, existing page story, and existing page object.

## Scope

### In scope

- `src/main/java/com/researchspace/booking/service/BookingCalendarManager.java`
- `src/main/java/com/researchspace/booking/service/BookingCalendarManagerImpl.java`
- `src/main/java/com/researchspace/api/v2/controller/BookingCalendarSubscriptionController.java` (create)
- `src/main/java/com/researchspace/api/v2/controller/ApiV2ControllerAdvice.java`
- `src/main/java/com/researchspace/api/v2/openapi/ApiV2OpenApiGenerator.java`
- `src/main/java/com/researchspace/webapp/controller/BookingCalendarFeedController.java` (create)
- the applicable API v2 error bundle under `src/main/resources/bundles/`
- `src/main/webapp/WEB-INF/sitemesh3.xml`
- the applicable deployment/access-logging note under `DevDocs/DeveloperNotes/`
- `src/test/java/com/researchspace/booking/service/BookingCalendarManagerTest.java` (create)
- `src/test/java/com/researchspace/api/v2/controller/BookingCalendarSubscriptionControllerMVCIT.java` (create)
- `src/test/java/com/researchspace/api/v2/controller/ApiV2ControllerAdviceTest.java`
- `src/test/java/com/researchspace/api/v2/openapi/ApiV2OpenApiGeneratorTest.java`
- `src/test/java/com/researchspace/webapp/controller/BookingCalendarFeedControllerTest.java` (create)
- `src/test/java/com/researchspace/webapp/controller/BookingCalendarFeedControllerMVCIT.java` (create)
- `src/test/java/com/researchspace/webapp/controller/LoggingInterceptorTest.java`
- `src/test/java/com/researchspace/webapp/controller/PerformanceLoggingInterceptorTest.java`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/bookableItemCalendarSubscription.ts` (create)
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/CalendarSubscriptionPopover.tsx` (create)
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookableItemPage.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/__tests__/bookableItemCalendarSubscription.test.ts` (create)
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/__tests__/CalendarSubscriptionPopover.test.tsx` (create)
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/__tests__/BookableItemPage.test.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookableItemPage.story.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/pageObjects/BookableItemPage.ts`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/mocks/bookableItemsMocks.ts`
- `src/main/webapp/ui/src/modules/common/i18n/locales/en-US/booking.json`
- `src/main/webapp/ui/src/modules/common/i18n/resources.d.ts`
- `src/main/webapp/ui/src/modules/booking/prototypes/MergedBookableItemPage.prototype.stories.tsx` (delete after parity)
- `plans/README.md`, status only after implementation

### Out of scope

- `BookableItemCalendarSubscription`, its DAO, its Liquibase change, or another
  subscription table.
- `BookingCalendarFeedGenerator`, iCalendar event semantics, privacy shaping,
  recurrence, or booking visibility rules.
- `src/main/webapp/WEB-INF/security.xml`. `/public/**` is already anonymous.
- One-off booking downloads and `BookingCalendarManager.download`.
- A sysadmin reset endpoint or UI for `resetForConfiguration`.
- Bulk token invalidation or deletion when Booking or a user is disabled.
- A Calendar tab, a global calendar settings page, or a per-user list of every
  subscription.
- Recovering or displaying a raw URL from GET.
- Sending subscription URLs by email or putting them in notifications.
- Auditing token creation, replacement, revocation, raw tokens, or token hashes.
- A new store, dependency, dialog, icon package, generic subscription
  framework, or changes to the shared popover component.
- Changes to Plans 005 or 006 behavior.

## Git workflow

- Suggested branch: `feat/bookable-item-calendar-subscription`
- Commit manager characterization and transport controllers first, then the
  frontend lifecycle, then browser and live verification cleanup.
- Match the repository's imperative commit style.
- Do not push or open a pull request unless instructed.

## Steps

### Step 1: Characterize and tighten the existing manager

Run the existing feed-generator baseline first. Add
`BookingCalendarManagerTest` in the manager's package so it can use the
package-private constructor and deterministic token supplier. Mock the DAO,
configuration DAO, booking manager, feature flag manager, resource registry,
generator, limits, and property holder. Use enabled users with explicit IDs and
do not place a sample raw token in a test display name or failure message.

Cover these contracts:

- status requires subject and actor to be the same active user;
- status returns inactive without exposing a URL when no row exists;
- missing or unreadable configuration uses `BookingCalendarNotFoundException`;
- create stores a 64-character hash, never the raw token, and returns the raw
  URL exactly once, using the fixed `feed.ics` path and percent-encoded `token`
  query parameter;
- replace updates the existing row and hash rather than inserting a second row;
- revoke removes only the caller's row and is safe when no row exists;
- inactive or disabled owners and no current readable calendar source produce
  `NotFound` without deleting the stored subscription row;
- a valid feed uses the owner's current source and locale;
- capacity exhaustion produces `AtCapacity`;
- size and generation failures produce `Oversized`;
- an unexpected DAO or source failure logs no token and produces the same
  service-unavailable result as generation failure, not `NotFound`.

Add Javadoc to the manager interface methods while touching it. Keep the
existing records and method signatures. In the implementation, change the URL
builder to the fixed query-token form and the broad runtime-failure mapping
described in Current state. Do not weaken any
personal-caller, active-user, feature, resource-readability, hashing, locking,
or safety-limit check.

**Verify**:
`mvn test -Dtest=BookingCalendarManagerTest,BookingCalendarFeedGeneratorTest -Dfast=true`
reports BUILD SUCCESS.

### Step 2: Add the authenticated management controller

Create `BookingCalendarSubscriptionController` under the REST API v2 controller
package. Use `ApiV2Caller` from the request attribute and pass both
`caller.subject()` and `caller.actor()` to the manager. Do not derive a user
from the path or request body.

Implement the exact GET, POST, and DELETE paths and response documents from
Current state:

1. GET maps `Status` to `active` and `updatedAt` only.
2. POST maps `Created.status()` and `subscriptionUrl()` to the one-time response.
3. DELETE calls revoke and returns 204 with no body.
4. Add source-level OpenAPI operation IDs, schemas, security responses, and a
   warning that POST replaces any active link and returns the new link only in
   that response.
5. Do not log request or response objects.

Add an advice handler for `BookingCalendarNotFoundException` that returns the
existing localized 404 problem. Keep 403 for personal-caller or feature
failures. Do not distinguish missing from unreadable in the body.

Create `BookingCalendarSubscriptionControllerMVCIT` using
`@ApiV2WebIntegrationTest` and the real manager, database, API keys, feature
flag, readable configuration, and subscription row. Model setup and cleanup on
`BookingSettingsControllerMVCIT` and the Booking manager integration fixtures.
Cover:

- anonymous management is 401;
- delegated or wrong-subject access is 403;
- GET and POST conceal missing and unreadable configurations as the same 404
  problem;
- DELETE returns 204 for a still-live configuration the caller can no longer
  read and removes only that caller's existing row;
- GET inactive contains no URL field;
- POST returns an HTTPS URL on a normal deployment, marks status active, and
  sends private no-store caching;
- a later GET remains active but contains no URL field;
- a second POST returns a different URL and invalidates the first token hash;
- DELETE returns 204 and a later GET is inactive;
- Booking-disabled access is 403;
- POST and DELETE do not create audit payloads containing token material.

The repository's OpenAPI generator is catalog-driven and does not discover
concrete controller annotations. Extend `ApiV2OpenApiGenerator` with the exact
management path, GET/POST/DELETE operations, status and one-time-created
schemas, authentication, 404/403 responses, and private no-store response
headers. The GET schema must not contain `subscriptionUrl`. Do not put the
public feed route in REST API v2 docs.

**Verify**:

```text
mvn test -Dtest=ApiV2ControllerAdviceTest,ApiV2OpenApiGeneratorTest -Dfast=true
mvn test -Dtest=BookingCalendarSubscriptionControllerMVCIT
```

Both commands report BUILD SUCCESS. The generated OpenAPI contains the three
management operations, and the GET schema has no subscription URL property.

### Step 3: Add anonymous feed delivery without decoration

Create `BookingCalendarFeedController` in the web controller package. Match
only `/public/booking/calendars/feed.ics`. Require exactly one `token` query
parameter and validate exactly 43 base64url characters before calling the
manager. A missing, repeated, blank, or malformed token returns 404 and never
hashes or queries it. Annotate the method with
`@IgnoreInLoggingInterceptor(ignoreAll = true)`.

Call `manager.feed(token, locale, new Date())` and map its sealed result using
the response table in Current state. Use one private helper for the common
private no-store and `Referrer-Policy: no-referrer` headers. Keep the fixed
30-second `Retry-After` as a controller constant, not a deployment property. Do
not return `ApiV2Problem` bodies from this non-API feed endpoint.

Change the manager's generic runtime-failure result, if not completed in Step
1, so an internal outage reaches the controller's 503 branch. Log only the
exception class and safe subscription or configuration IDs when available.
Never log `rawToken`, the complete request target, or a hash.

Add `*/public/booking/calendars/feed.ics` as a SiteMesh exclude before the broad
public decorator mapping. Do not change `security.xml`; its existing
`/public/** = anon` rule is the intended security boundary.

In `BookingCalendarFeedControllerTest`, use a mocked manager and focused MockMvc
coverage for missing/repeated/malformed token rejection, every sealed result,
exact content type, inline filename, cache/referrer headers, Retry-After, and
empty error bodies. Verify that rejected tokens never call `feed`.

Add a focused interceptor regression using a generated 43-character token.
Exercise the annotated handler through `LoggingInterceptor` and force the slow
request branch in `PerformanceLoggingInterceptor`. Capture output and assert the
generated token appears nowhere; do not put it in assertion messages. The
generic logger should skip the request entirely, while the slow logger may log
only `/public/booking/calendars/feed.ics`.

Inspect and document each supported container/proxy access-log configuration.
Record the exact route-scoped query-redaction or query-omission requirement in
`DevDocs/DeveloperNotes`. This is a release prerequisite, not a controller unit
test substitute.

In `BookingCalendarFeedControllerMVCIT`, create a real configuration,
subscription, and confirmed booking. Request the generated URL without an API
key or session. Assert that the body starts with `BEGIN:VCALENDAR`, includes the
bookable item and permitted event fields, ends with `END:VCALENDAR`, and is not
wrapped in HTML. Replace and revoke the credential through the manager and
assert the old and revoked URLs return 404. Keep raw URLs in local test
variables only and never include them in assertion messages.

**Verify**:

```text
mvn test -Dtest=BookingCalendarFeedControllerTest,BookingCalendarManagerTest,LoggingInterceptorTest,PerformanceLoggingInterceptorTest -Dfast=true
mvn test -Dtest=BookingCalendarFeedControllerMVCIT
```

Both commands report BUILD SUCCESS. Also run:

```text
rg -n 'public/booking/calendars/feed\.ics' src/main/webapp/WEB-INF/sitemesh3.xml
rg -n '/public/\*\* = anon' src/main/webapp/WEB-INF/security.xml
```

The first command finds the new exclude before the public decorator. The second
finds the existing anonymous rule and `git diff` shows no `security.xml` change.

### Step 4: Add the typed frontend client

Create `bookableItemCalendarSubscription.ts` beside the existing bookable-item
clients. Define Valibot schemas and inferred types for the exact status and
created response shapes. Require `updatedAt` to be null for inactive status and
an ISO timestamp for active status. Require a valid HTTP or HTTPS URL in the
POST response because local Dev Stack links may use HTTP.

Export four small pieces:

- a stable query-key factory for one configuration's status;
- `fetchCalendarSubscriptionStatus` using GET;
- `createOrReplaceCalendarSubscription` using POST;
- `revokeCalendarSubscription` using DELETE.

All three requests use `bookingApiV2Headers`. Parse non-2xx responses with
`parseApiV2Problem`, parse JSON with `parseOrThrow`, and accept an AbortSignal
for GET. DELETE accepts only 204 and never tries to parse a body.

Keep calendar-app URL construction in this file as pure helpers. Convert only
an `http://` or `https://` prefix to `webcal://`. Percent-encode the complete
feed URL for Outlook and the complete webcal URL for Google's `cid` query.

Add pure tests for status and created validation, all three methods, 401/403/404
problem parsing, 204 revoke, wrong response shapes, application URLs containing
a context path, and app-link encoding. Assert that GET cannot return or retain a
subscription URL.

**Verify**:
`pnpm test src/modules/booking/pages/bookable-items/__tests__/bookableItemCalendarSubscription.test.ts`
passes.

### Step 5: Build the real lifecycle in the header popover

Create `CalendarSubscriptionPopover.tsx` and mount it in the merged page's
identity header beside the Enabled or Disabled badge. Pass only the numeric
configuration ID and OAuth token. Do not gate the trigger on sysadmin role.

Use one React Query status query with `enabled: open`. Opening the popover must
send GET only. Keep its status response in the normal query cache. Keep a newly
created URL in component-local state and give that state precedence over the
active-status presentation until the page reloads or the user revokes it.

Implement each state listed in Current state:

- Loading has an accessible status and spinner.
- GET failure has a concise message and Retry button.
- Inactive explains that the feed is read-only and has a Generate link button.
- Generated warns that the link is shown now and cannot be recovered later. It
  offers Google Calendar, Outlook, Other calendar apps via `webcal://`, and a
  read-only field with Copy link.
- Active shows the localized created or replaced date. It never shows an input
  or app shortcuts because the raw link is unavailable.
- Replace requires an inline confirmation that the current link will stop
  working. Clear any locally held URL before POST. A successful POST installs
  the new local URL and updates the status cache. Do not retry automatically.
- Revoke requires inline confirmation. A successful DELETE clears local URL
  state and writes the inactive status to the query cache.
- Mutation failure keeps the popover open. A failed POST explains that the
  outcome could not be confirmed and that running Replace again will issue a
  fresh link. Refetch status without claiming the old URL still works.
- Clipboard failure produces a visible error rather than a false Copied state.

The trigger is a semantic button with the visible label `Add to calendar` and
`aria-haspopup="dialog"`, `aria-expanded`, and `aria-controls` wired to the
popup. The popup is a non-modal `role="dialog"` and has stable
`aria-labelledby` and `aria-describedby`
relationships to its visible heading and concise description. It opens only
on button activation, not hover or focus, so WCAG 1.4.13 hover/focus content
does not apply.

Use semantic buttons, headings, descriptions, live status, and alert text. On
every open, deterministically focus the popup heading, made programmatically
focusable with `tabIndex={-1}`; the next Tab reaches the first available
action. Tab and Shift+Tab traverse the popup in DOM order without a keyboard
trap. Escape and an explicit Close button dismiss it and return focus to Add to
calendar. Replace and Revoke confirmations focus their warning and keep their
confirm/cancel actions next in keyboard order. Cancelling returns focus to the
Replace or Revoke action that opened the confirmation. Successful Replace
moves focus to the first revealed link action, Google Calendar; successful
Revoke moves it to Generate.

Expose `Copied` through a polite `role="status"` message. Mutation and
clipboard failures use assertive alerts without stealing focus from the active
control. Ensure the accessible names for Google Calendar, Outlook, Copy link,
Replace, and Revoke contain those visible labels; icons are supplementary.
App anchors opened in a new tab use `rel="noreferrer"`; the `webcal://` anchor
hands off to the registered local application. Use the existing Google and
Microsoft brand icons and `CalendarIcon` for the generic option.

Keep the popover within a 320px viewport with a width no larger than the
viewport minus its collision padding. Do not modify the shared popover or add a
mobile dialog. Keep the header wrapping behavior from Plan 005.

Add `CalendarSubscriptionPopover.test.tsx` with MSW and React Testing Library.
Cover every state and transition, GET-only open, one-time reveal, close and
reopen in the same mount, reload-style remount, Replace confirmation, old URL
clearing, Revoke, ambiguous POST failure, clipboard success and failure,
external link attributes, keyboard-only operation, focus return, confirmation
focus, dialog relationships, Tab/Shift+Tab order, Escape, visible-label names,
polite Copy status, assertive error announcement without focus theft, no
keyboard trap, and accessibility. Update the parent page test to prove the
trigger is present for ordinary and sysadmin users and that the status query
stays lazy until the popover opens.

**Verify**:
`pnpm test src/modules/booking/pages/bookable-items/__tests__/CalendarSubscriptionPopover.test.tsx src/modules/booking/pages/bookable-items/__tests__/BookableItemPage.test.tsx`
passes.

### Step 6: Extend Browser Mode coverage and retire the prototype

Extend the Plan 005 `BookableItemPage.story.tsx`, shared mock handlers, page
object, and Browser Mode spec. Do not create another story wrapper or fixture
set. Register status, create/replace, revoke, and feed handlers through the
shared MSW worker.

Cover:

- Opening Add to calendar sends GET and never POST.
- Inactive, loading, retry, generated, active-after-remount, replace warning,
  revoke warning, and mutation-error presentations.
- Generate reveals the app handoff buttons and read-only URL once.
- Remounting with active status cannot recover or display the URL.
- Replace returns a different URL and the old feed handler becomes 404.
- Revoke makes the replacement feed handler return 404.
- Ordinary readable users see and use the same subscription control.
- Keyboard-only users can open and close the popover, Generate, confirm Replace
  and Revoke, and cancel either confirmation. Closing returns focus to the
  trigger; mutations and cancellation move it to the specified relevant
  control. Assert the trigger/dialog relationships, deterministic heading
  focus, Tab/Shift+Tab order, Escape dismissal, polite Copy status, alert
  behavior without focus theft, and absence of a keyboard trap.
- At 320px, the header trigger and open popover do not create document-level
  horizontal overflow and all controls remain reachable.
- The inactive, generated, and active presentations pass the browser
  accessibility scan.

Treat this as explicit acceptance evidence for WCAG 2.2 SC 1.4.3, 1.4.4,
1.4.10, 1.4.11, 1.4.12, 2.1.1, 2.1.2, 2.4.3, 2.4.7, 2.4.11, 2.5.3,
2.5.8, 3.2.1, 4.1.2, and 4.1.3. Manually verify the trigger and every popup
state at default and 200% text
size, at 400% browser zoom/320 CSS px width, and with WCAG text-spacing
overrides (line-height 1.5, paragraph spacing 2 times the font size, letter
spacing 0.12em, and word spacing 0.16em). Repeat in light and dark themes and in
forced-colors or the platform high-contrast mode. Require text contrast of at
least 4.5:1 (3:1 for large text), non-text UI and focus-indicator contrast of
at least 3:1, no lost content/function or two-dimensional page scrolling,
visible focus that is not entirely obscured, and pointer targets of at least
24 by 24 CSS px or a documented WCAG spacing exception. For the practical AAA
goals in SC 2.4.12, 2.4.13, and 2.5.5, keep focus wholly unobscured, use a focus
indicator equivalent to a 2 CSS px perimeter with 3:1 state contrast, and
prefer 44 by 44 CSS px targets.

Perform a manual screen-reader pass with NVDA in Firefox or Chrome and
VoiceOver in Safari. Verify the trigger state, dialog name/description, initial
focus, status and alert announcements, app-link names, confirmation focus, and
dismissal/return focus. Record the browser, assistive technology, theme/mode,
and result. Automated axe/component checks are regression aids and do not by
themselves establish WCAG conformance.

Run Chromium during the inner loop, then all three engines. After unit and
browser parity, delete
`MergedBookableItemPage.prototype.stories.tsx`. Its selected page layout and
calendar placement now exist in production, and its automatic-link assumption
must not remain as a misleading example.

**Verify**:

```text
VITEST_BROWSERS=chromium pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx
pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx
rg -n 'MergedBookableItemPage|useCalendarLink|D1_CalendarPopover' src/main/webapp/ui/src
```

Both browser commands pass. The search returns no matches.

### Step 7: Extract translations and run the automated final gate

Use semantic keys below `booking:bookableItemDetails.calendarSubscription`.
Author English with literal `defaultValue`, run extraction with
`--sync-primary`, review the English diff, remove defaults, and regenerate
types. Never use `--sync-all`.

Run every automated command in Commands you will need. Review the complete diff
for raw token literals, accidental credential logging, security changes, new
dependencies, or files outside Scope.

**Verify**: every command below exits 0:

```text
mvn test -Dtest=BookingCalendarManagerTest,BookingCalendarFeedGeneratorTest,BookingCalendarFeedControllerTest,LoggingInterceptorTest,PerformanceLoggingInterceptorTest,ApiV2ControllerAdviceTest,ApiV2OpenApiGeneratorTest -Dfast=true
mvn test -Dtest=BookingCalendarSubscriptionControllerMVCIT,BookingCalendarFeedControllerMVCIT
pnpm run i18n:types
pnpm run i18n:lint
pnpm tsc
pnpm lint
pnpm test src/modules/booking/pages/bookable-items/__tests__/bookableItemCalendarSubscription.test.ts src/modules/booking/pages/bookable-items/__tests__/CalendarSubscriptionPopover.test.tsx src/modules/booking/pages/bookable-items/__tests__/BookableItemPage.test.tsx
pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx
```

Also run:

```text
git diff --name-only
rg -n 'MergedBookableItemPage|useCalendarLink|D1_CalendarPopover' src/main/webapp/ui/src
```

The diff contains only Scope files and the prototype search returns no matches.

### Step 8: Verify the complete lifecycle in the Docker Dev Stack

This live gate is required in addition to mocked Browser Mode tests. Use the
`rspace-dev-stack` skill and Playwright MCP. Do not use standalone Playwright
or the in-app preview for this step.

1. Run `./docker/dev/rspace-dev ps`. Reuse this worktree's running stack when
   available. Otherwise run `./docker/dev/rspace-dev up` for the full database,
   backend, and frontend stack.
2. Watch `./docker/dev/rspace-dev logs app` until Jetty reports the app started.
   Read the application URL from `ps`.
3. With Playwright MCP, log in with an ordinary seeded user, enable Booking only
   if the existing test deployment requires it, and open a readable merged
   bookable item page. Create a bookable item through the normal UI first if the
   reusable database has none.
4. Navigate, take a safe accessibility snapshot, act on returned locators, and
   assert after each state change. Do not take a snapshot or screenshot while a
   raw subscription URL is visible because the automation artifact would log a
   bearer credential.
5. Open the popover and confirm no POST occurred before Generate. Generate a
   link. In one in-page Playwright evaluation, read the link from the DOM, fetch
   it anonymously, and return only the status, content type, and booleans for
   `BEGIN:VCALENDAR` and `END:VCALENDAR`. Never return the URL or token from the
   evaluation.
6. Exercise Replace and Revoke through the UI. Keep the old and new URLs only
   inside the in-page evaluation closure. Return only whether the URLs differ
   and the status sequence `200, 404, 200, 404`. Do not inspect or export broad
   network logs for these requests because request URLs contain bearer tokens.
7. Generate once more, reload the page, and confirm the active state shows its
   date but no URL field or app handoff links. Revoke it so the Dev Stack does
   not retain a live test credential.
8. Confirm the 320px presentation has no document-level horizontal overflow.
   Review console failures and unrelated network failures through a safe
   snapshot after the raw URL is gone. Check application logs for errors, but do
   not copy request paths containing calendar tokens into the report.
9. Run the final full-page and complete-process WCAG 2.2 AA acceptance pass
   across Bookings, Details read/edit/save/validation, Audit log filtering,
   pagination/Refresh/Restart, and the complete calendar subscription
   Generate/Copy/Replace/Revoke flow. Include keyboard-only operation; 200%
   text resizing; 400% zoom/320 CSS px reflow; the specified text-spacing
   overrides; light, dark, and forced-colors/high-contrast presentations;
   contrast and target-size measurements; visible and unobscured focus; NVDA
   with Firefox or Chrome; and VoiceOver with Safari. Confirm relationships and
   reading order in both table and responsive-card layouts. Do not expose the
   raw calendar URL in screenshots, snapshots, recordings, reports, or
   assistive-technology logs.
10. Record the non-secret result matrix, browser used, Dev Stack URL origin,
   screen reader/browser combinations, and any console or network failures. An
   axe result may be included as supporting evidence, never as the sole claim
   of conformance. Ask the operator whether to run
   `./docker/dev/rspace-dev down`; do not run `down` without approval and never
   run `nuke` as part of this plan.

**Verify**:

- Generate produces a 200 `text/calendar` response with a complete VCALENDAR.
- Reload shows active status without recovering the raw URL.
- Replace yields a different working URL and the prior URL returns 404.
- Revoke makes the replacement URL return 404.
- The popover fits at 320px.
- The complete merged page and its Bookings, Details, Audit log, editing, and
  calendar-subscription processes meet the recorded WCAG 2.2 AA acceptance
  matrix; practical AAA focus and target-size improvements are recorded.
- No relevant browser console, network, backend, or SiteMesh errors remain.
- No screenshot, log excerpt, tool response, plan update, or test name contains
  a raw token or subscription URL.

## Test plan

- `BookingCalendarManagerTest` owns hashing, personal-caller checks, current
  permissions, one-time URL creation, rotation, revocation, safety limits, and
  unavailable-result mapping.
- `BookingCalendarSubscriptionControllerMVCIT` owns real REST API v2
  authentication, concealed 404s, response shapes, no-store caching, and the
  management lifecycle.
- `BookingCalendarFeedControllerTest` owns malformed token rejection and exact
  response mapping without database setup.
- The interceptor tests own proof that neither generic nor slow application
  logging emits the query bearer; deployment documentation owns proxy/container
  query redaction.
- `BookingCalendarFeedControllerMVCIT` owns anonymous full-stack MVC delivery,
  real iCalendar bytes, owner-shaped data, replacement, revocation, and no HTML
  decoration.
- The pure frontend client test owns schemas, problem parsing, request methods,
  and calendar-app URL encoding.
- The popover unit test owns state transitions, mutation ambiguity, clipboard
  behavior, dialog relationships, visible-label names, keyboard focus,
  status/error announcement, and semantic accessibility.
- The existing page test owns placement, role independence, and lazy status
  loading.
- The existing Browser Mode story, page object, and spec own real browser
  interaction, narrow layout, all three engines, and accessibility.
- Playwright MCP against the Docker Dev Stack owns the final real API, database,
  security filter, SiteMesh, browser acceptance path, and full-page,
  complete-process WCAG 2.2 AA verification. Manual checks own contrast, text
  resize, zoom/reflow, text spacing, forced colors, target size, focus
  visibility/obscuring, NVDA, and VoiceOver; automated scans alone do not prove
  conformance.

## Done criteria

- [ ] The management API exposes exact GET, POST, and DELETE contracts at the
  configured booking-configuration path.
- [ ] GET never returns a subscription URL, token, or hash.
- [ ] POST returns the new URL once and does not retry automatically.
- [ ] DELETE revokes only the caller's subscription and returns 204.
- [ ] GET and POST conceal missing and unreadable management targets as the
  same 404; DELETE still lets the caller revoke their row on a live target they
  can no longer read.
- [ ] The public controller uses the fixed
  `/public/booking/calendars/feed.ics` path; only one validated 43-character
  base64url `token` query value reaches the manager.
- [ ] Available feeds return undecorated UTF-8 iCalendar with inline filename
  and private no-store caching.
- [ ] Missing feeds return 404; capacity, size, and generation outages return
  503; capacity includes `Retry-After: 30`.
- [ ] The feed applies the owner's current active state, Booking feature, read
  permissions, and privacy-shaped event view.
- [ ] Opening the popover sends GET only and cannot mint or recover a raw URL.
- [ ] Generate, one-time reveal, active-after-reload, Replace, Revoke, loading,
  and error states are implemented and tested.
- [ ] Raw URLs exist only in the POST response and transient component or test
  memory. They do not enter storage, route state, logs, audit, screenshots, or
  broad automation artifacts.
- [ ] Application-log tests contain no raw token, and supported container/proxy
  access logs omit or redact the route's query string as documented.
- [ ] Disabling Booking or an owner leaves stored hashes untouched, while feed
  requests return concealed 404 with no events.
- [ ] The trigger sits beside the Enabled badge and works for ordinary readable
  users as well as sysadmins.
- [ ] The popover fits at 320px and passes accessibility checks.
- [ ] The popover lifecycle works by keyboard. Close returns focus to its
  trigger; confirmation cancellation and successful Replace/Revoke move focus
  to the next relevant in-popover control.
- [ ] The trigger and dialog expose stable labels, descriptions, expanded and
  control relationships; every open focuses the heading; Tab/Shift+Tab and
  Escape work without a trap; Copy uses polite status and failures use alerts
  without stealing focus.
- [ ] WCAG 2.2 AA checks cover contrast, non-text contrast, 200% text resize,
  400% zoom/320px reflow, text spacing, visible/unobscured focus, 24px targets,
  forced colors, light/dark themes, NVDA, and VoiceOver, with AAA focus and
  44px target improvements applied where practical.
- [ ] The Docker Dev Stack gate records a final full-page and complete-process
  accessibility pass across Bookings, Details, Audit log, editing, and calendar
  subscription; automated scans are not the sole conformance evidence.
- [ ] Manager, controller, MVC integration, frontend unit, three-engine Browser
  Mode, i18n, TypeScript, and Biome checks pass.
- [ ] Docker Dev Stack verification through Playwright MCP proves generate,
  reload, replace, old-link invalidation, new-link delivery, and revoke.
- [ ] The merged-page prototype has no remaining matches.
- [ ] No entity, DAO, Liquibase, security XML, generator, new dependency, new
  store, Calendar tab, download endpoint, or reset endpoint was added.
- [ ] The plan status in `plans/README.md` is DONE.

## STOP conditions

Stop and report instead of improvising if:

- Plans 005 or 006 are not DONE, or their merged header and Audit log tab differ
  from this plan's Current state.
- Product intent requires status GET to recover a raw URL. That conflicts with
  the documented hash-only, one-time reveal contract.
- The public feed must require API key, OAuth, or session authentication.
- Token creation, replacement, or revocation must enter the audit trail.
- A raw token or token hash must be stored outside the existing hash-only
  subscription row.
- A readable ordinary user must not manage their own subscription.
- Feed generation requires changes to privacy shaping, booking visibility,
  recurrence, generator semantics, the database schema, or the DAO.
- SiteMesh cannot exclude the public feed without changing global public-page
  decoration behavior.
- The existing `/public/** = anon` rule no longer exists or a broader security
  migration owns `security.xml` concurrently.
- A supported container, proxy, or observability pipeline cannot omit or redact
  the `token` query parameter for this route.
- The OpenAPI generator cannot discover a concrete controller without a
  codebase-wide generator redesign.
- Playwright MCP is unavailable for the required live gate. Do not silently
  substitute another browser driver.
- A focused verification command fails twice after a reasonable correction.
- Implementation requires a file listed as out of scope.

## Maintenance notes

- Treat every subscription URL as a password. Do not add it to support logs,
  telemetry, screenshots, query caches, or issue reports.
- The query parameter cannot be removed from the calendar client's stored URL:
  it is the recurring credential. The fixed path plus application and
  deployment log redaction is the selected meaning of stripping it.
- A user who loses read permission keeps the stored hash, but feed generation
  returns concealed 404 because the manager rechecks current permissions. If
  access returns, the same credential may work again unless the user replaced
  or revoked it. Preserve that documented current-permission behavior.
- POST is deliberately create-or-replace. A lost response can leave the caller
  unable to recover the newly issued URL. The safe recovery is another explicit
  Replace, not a GET that reveals stored credentials.
- Keep the fixed token pattern aligned with 32 random bytes encoded base64url.
  If token generation changes, update controller validation and tests together.
- Keep disabled-feature and disabled-user rows. Current permission checks make
  those feeds return concealed 404; bulk invalidation is not part of this plan.
- Keep SiteMesh exclusion and public controller tests together. An HTML
  decorator on `.ics` is a production-breaking regression.
- One-off booking downloads and sysadmin reset already exist at the manager
  level but remain intentionally unexposed.
