# Plan 008: Add global and per-user Booking display preferences

> **Executor instructions**: Read this plan fully before changing code. Follow
> the steps in order, run every verification command, and confirm the expected
> result before continuing. Preserve unrelated and user-authored work. Never run
> a Maven `install` or deploy goal. If a STOP condition occurs, stop and report
> it. Do not improvise. When done, update this plan's row in
> `plans/README.md` unless a reviewer says they maintain the index.

## Status

- **Priority**: P1
- **Effort**: L
- **Risk**: HIGH
- **Depends on**: Plan 007, which depends on Plans 005 and 006
- **Category**: direction
- **Planned at**: commit `37e013af4b`, 2026-08-28, with the current dirty
  Booking worktree present

## Drift check

This plan was written against uncommitted Booking work from Plans 005 through
007. Do not expect a clean diff. Before implementation, run:

```bash
git rev-parse --short=10 HEAD
git status --short
test -f src/main/java/com/researchspace/model/booking/BookingDisplaySettings.java || true
test -f src/main/webapp/ui/src/modules/booking/components/AvailabilityBar.tsx
test -f src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookableItemPage.tsx
git diff --stat 37e013af4b -- \
  src/main/java/com/researchspace/model/booking \
  src/main/java/com/researchspace/model/preference/Preference.java \
  src/main/java/com/researchspace/booking \
  src/main/java/com/researchspace/api/v2/controller \
  src/main/resources/sqlUpdates \
  src/main/resources/bundles \
  src/main/webapp/ui/src/modules/booking \
  src/main/webapp/ui/src/modules/common/app/router.tsx \
  src/main/webapp/ui/src/modules/common/i18n \
  src/test/java/com/researchspace/booking \
  src/test/java/com/researchspace/api/v2
```

Expected: HEAD is `37e013af4b` or contains it, the two existing frontend files
exist, and the diff contains the in-progress Booking work. Plan 007 must be
DONE before this plan starts because both plans touch the merged item page,
calendar data, REST v2 documentation, and browser fixtures. Compare the live
interfaces with the excerpts below. A material mismatch is a STOP condition.

## Why this matters

Booking currently mixes three different ideas: the viewer's browser timezone,
the timezone used to present a booking, and the timezone stored on a bookable
item for enforcing its opening hours. Pages also disagree about how much of a
day to display. This produces inconsistent dates, timelines, availability
bars, and “Now” markers.

This plan adds one global Booking display default and one account-level user
override. The resolved preference becomes the single interface used by every
Booking page. It also removes per-item timezone writes from the public UI and
REST API without removing the internal scheduling timezone from the domain
model.

## Fixed product decisions

### Keep presentation timezone separate from scheduling timezone

Use these terms consistently in code, API descriptions, labels, and tests:

- **Display timezone**: the viewer's effective timezone. It controls displayed
  dates and times, route “today” values, wall-clock booking inputs, availability
  window bounds, and the “Now” position.
- **Scheduling timezone**: `BookingConfiguration.timeZone`. It remains an IANA
  zone and controls item opening hours, slot granularity checks around local
  dates, closure calculation, and iCalendar feed timezone metadata.
- **Institution timezone**: the running JVM's default timezone, obtained from
  `Clock.systemDefaultZone().getZone()`. Booking code must not override the JVM
  timezone.

The display preference must not change an existing item's scheduling rules.
Do not rewrite existing `BookingConfiguration.timeZone` values. Keep the
entity field, audit property, getter/setter, manager `Create`/`Patch` fields,
booking response timezone, calendar source timezone, and scheduling-policy use
in the codebase.

Remove only the public write seam:

- `timezone` remains readable on `/api/v2/booking-configurations` and booking
  responses so older configurations and scheduling facts remain observable.
- Mark it `createAccess = NEVER` and `updateAccess = NEVER`, remove
  `requiredOnCreate`, and describe it as the read-only scheduling timezone.
- Public REST v2 create operations inject the institution timezone. Public
  update and bulk-update operations cannot change it.
- Requests that still send `timezone` on create, patch, or bulk operations
  receive the collection framework's normal 400 response for a non-writable
  property. This is an intentional breaking API change and must appear in the
  REST documentation/release note supplied with the change.
- Internal Java callers, including fixture setup, may continue to pass a
  scheduling timezone to `BookingConfigurationManager.Create` or `Patch`.

### Global and user preference behavior

The global default lives in the existing `BookingConfigurationDefaults`
singleton and has these values on migration:

| Setting | Wire representation | Initial global value | Validation |
| --- | --- | --- | --- |
| Availability start | `HH:mm` string | `08:00` | `00:00` through `23:59` |
| Availability end | `HH:mm` string | `18:00` | `00:01` through `24:00` |
| Timezone mode | `BROWSER`, `INSTITUTION`, or `CUSTOM` | `BROWSER` | enum value |
| Custom timezone | nullable IANA zone string | `null` | required only for `CUSTOM`; validate with `ZoneId.of` |

An availability window is a same-day display interval. Start must be earlier
than end. `00:00` to `24:00` is valid; overnight windows such as `18:00` to
`08:00` are not part of this iteration.

The three user-facing timezone choices are exactly:

1. Use Browser Timezone
2. Use Institution Timezone
3. Use Custom Timezone

`BROWSER` stores the mode, not the browser's current IANA identifier. The same
account may therefore display in a different zone on another device. If the
browser does not report a valid IANA zone, resolve `BROWSER` to the institution
timezone. `CUSTOM` stores the selected IANA identifier.

A user with no stored override inherits the complete current global document.
Saving the user page writes one complete explicit document. Later global
changes continue to affect users who have never saved or who reset, but do not
replace explicit user choices. “Reset to global defaults” deletes the logical
override and immediately re-resolves the current global values.

The preferences apply account-wide to every React Booking route, across
browsers and sessions. They do not alter the legacy JSP/session timezone or
date rendering elsewhere in RSpace.

### Alignment with legacy timezone behavior

`TimezoneAdjusterImpl` normally puts the browser zone into the user's session.
When that value is missing or session lookup fails,
`SessionTimeZoneUtils.getUserTimezone()` falls back to
`DateTimeZone.getDefault()`. Several other non-Booking paths also use
`ZoneId.systemDefault()`, `TimeZone.getDefault()`, or
`Clock.systemDefaultZone()`.

Using `Clock.systemDefaultZone()` for Booking's Institution option agrees with
those process-default fallback paths. It does not force legacy pages to use
the institution option: a valid legacy browser/session timezone still wins for
that user. Do not set `user.timezone`, call `TimeZone.setDefault`, or otherwise
mutate the process timezone from Booking code.

### REST v2 user preference contract

Use a concrete subordinate singleton route, matching the existing concrete
`/api/v2/users/me` pattern documented in
`DevDocs/DeveloperNotes/RestApiV2Collections.md`:

```text
GET    /api/v2/users/me/booking-preferences
PUT    /api/v2/users/me/booking-preferences
DELETE /api/v2/users/me/booking-preferences
```

Do not register this as a collection resource. It has no client-selected user
id, pagination, filter, or relationship.

The GET response is a typed, self-describing document:

```json
{
  "availabilityWindowStart": "08:00",
  "availabilityWindowEnd": "18:00",
  "timezoneMode": "BROWSER",
  "customTimezone": null,
  "institutionTimezone": "Europe/Berlin",
  "overridden": false
}
```

PUT accepts the first four fields as one replacement document and returns the
same response shape with `overridden: true`. It ignores no unknown fields.
DELETE returns 204 and makes the next GET resolve the current global document
with `overridden: false`. All three methods require an authenticated personal
or run-as caller, use `caller.subject()` as preference owner, retain
`caller.actor()` for any audit/log context, and require the Booking feature.

Use the normal REST v2 problem response and externalized validation messages.
OpenAPI must document conditional `customTimezone` validation and the fact
that `institutionTimezone` and `overridden` are response-only.

### Persistence decision

Reuse `UserPreference`; do not create a second preference table or put these
values into `UI_JSON_SETTINGS`.

Append, and only append, this enum value after `UI_JSON_SETTINGS` in
`src/main/java/com/researchspace/model/preference/Preference.java`:

```java
BOOKING_DISPLAY_PREFERENCES(
    "", SettingsType.TEXT, PreferenceCategory.UI, "Booking display preferences")
```

The enum ordinal is stored in the database, so moving or inserting values is
data corruption. Store one versioned JSON document in this preference value,
for example:

```json
{
  "version": 1,
  "availabilityWindowStart": "09:00",
  "availabilityWindowEnd": "17:00",
  "timezoneMode": "CUSTOM",
  "customTimezone": "America/New_York"
}
```

Hide serialization, schema versioning, validation, global fallback, and
`UserManager` access behind a Booking-specific manager. This is the deep
module seam. Controllers and React code must not know the preference enum,
ordinal, or raw JSON representation. A blank value means no override. If an
existing value is corrupt or from an unsupported version, log a WARN without
the raw JSON and return the current global defaults; do not break Booking.

Do not use `src/main/webapp/ui/src/hooks/api/useUiPreference.tsx`: it exposes a
generic legacy Ajax endpoint and updates the shared `UI_JSON_SETTINGS` blob
with a client-side read/modify/write cycle. The dedicated REST document gives
this feature an atomic, validated, owned write.

### Frontend resolver contract

Create one small frontend module that owns transport validation, React Query
keys and mutations, browser-zone detection, and preference resolution. Its
public hook returns only this stable shape:

```ts
type ResolvedBookingDisplayPreferences = {
  availabilityWindow: {
    start: string;
    end: string;
    startMinute: number;
    endMinute: number;
  };
  timeZone: string;
  timezoneMode: "BROWSER" | "INSTITUTION" | "CUSTOM";
  institutionTimezone: string;
  overridden: boolean;
};
```

Use React Query for the server document. Do not mirror it into Zustand,
context, module globals, or local storage. All Booking pages consume the same
query key, so a successful PUT/DELETE can replace or invalidate one cache
entry and update every route.

```text
BookingConfigurationDefaults + optional UserPreference + browser zone
                                |
                                v
             booking display preference resolver
                                |
          +----------+----------+----------+----------+
          |          |          |          |          |
       Calendar   All items   Forms   My bookings   Item page
```

## Current state

### Global Booking settings already have a singleton and optimistic locking

`src/main/java/com/researchspace/model/booking/BookingConfigurationDefaults.java`
is an audited singleton. It currently contains creation-time scheduling
defaults and a `@Version` field. Its current class comment is:

```java
/** Singleton creation-time defaults copied into each new booking configuration. */
```

Update that description because the new display fields are global runtime
fallbacks, not values copied to bookable items.

`src/main/java/com/researchspace/api/v2/controller/BookingSettingsController.java`
maps the singleton at `/api/v2/booking-settings`. GET is available to Booking
users; PATCH requires sysadmin through the manager and supplies
`configurationVersion`. Preserve that optimistic concurrency behavior and add
the display defaults to the same GET/PATCH document.

`BookingSchedulingSettings` is the existing deep module for item scheduling.
Do not add display validation to it. Add a parallel `BookingDisplaySettings`
record/value module with constants, patch/merge support, entity mapping, and
cross-field validation so the controller and manager do not duplicate rules.

### Item timezone currently performs three jobs

`src/main/java/com/researchspace/model/booking/BookingConfiguration.java`
stores a required, audited `timeZone` and validates it with `ZoneId.of`.
`src/main/java/com/researchspace/model/booking/ApiV2BookingConfigurationResource.java`
currently exposes it as required and writable:

```java
@ApiV2ResourceField(
        property = "timeZone",
        requiredOnCreate = true,
        maxLength = 255,
        description = "IANA time-zone identifier used for booking windows.")
    String timezone
```

`BookingConfigurationResourceOperations.create()` and `patch()` read the
public `timezone` field into the internal manager command. The frontend schema
and list/edit forms do the same in
`src/main/webapp/ui/src/modules/booking/pages/bookable-items/bookingConfiguration.ts`.

The field cannot be deleted. These live uses are intentionally retained:

- `BookingSchedulingPolicyImpl` evaluates opening hours in
  `configuration.getTimeZone()`.
- `TimeSlotBooking.getTimeZone()` and booking REST responses derive it from
  the configuration.
- `TimeSlotBookingManager` and `BookingCalendarManager` use it for calendar
  source metadata and `X-WR-TIMEZONE`.
- Availability closure generation uses it to map item-local opening hours to
  absolute instants.

### Booking pages currently disagree about display time

- `CalendarPage.tsx` resolves the browser timezone directly.
- `AllBookableItemsPage.tsx` separately resolves the browser timezone, but
  builds each row from `zonedDayBounds(date, row.timezone)`.
- `BookingEventsCalendar.tsx` hard-codes a 07:00 to 19:00 timeline.
- `calendarAvailability.ts` assumes one item-local day per row.
- `MyBookingsPage` formats each booking in `booking.timezone` and exposes a
  timezone list column.
- `BookingForm` and `ZonedBookingWindowFields` resolve wall-clock values in the
  selected item's scheduling timezone.
- `BookingPage.tsx`, calendar controls, all-items controls, and route
  validators use browser-based `localToday()` independently.
- `AvailabilityBar.tsx` receives both item and user zones, but positions “Now”
  against a 24-hour denominator rather than a configured display interval.

The implementation must replace these independent decisions with the shared
resolved preference while keeping the scheduling timezone available wherever
business-rule calculation needs it.

### An older general timezone path exists outside Booking

`src/main/java/com/researchspace/auth/TimezoneAdjusterImpl.java`,
`src/main/java/com/researchspace/session/SessionTimeZoneUtils.java`, JSP header
logic, and `DevDocs/DeveloperNotes/Datetime.md` describe the legacy
browser/session timezone stored under `com_rs_timezone`. That mechanism serves
legacy pages. `SessionTimeZoneUtils.getUserTimezone()` falls back to
`DateTimeZone.getDefault()`, which agrees with the JVM-backed Institution
timezone when no session timezone exists. A valid session timezone may still
differ. Do not write the Booking preference into the session attribute and do
not change non-Booking formatting in this iteration.

## Commands you will need

Run frontend commands from the repository root. Never insert a standalone `--`
after a pnpm script name.

| Purpose | Command | Expected on success |
| --- | --- | --- |
| Load route guidance before router edits | `pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core` | exit 0; guidance is printed |
| Load search-param guidance | `pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/search-params` | exit 0; guidance is printed |
| Backend unit tests | `mvn test -Dtest=BookingTimeConfigTest,BookingDisplaySettingsTest,BookingDisplayPreferencesManagerTest,BookingConfigurationDefaultsManagerTest,BookingConfigurationManagerTest,BookingConfigurationResourceOperationsTest,ApiV2BookingConfigurationResourceTest -Dfast=true` | exit 0; all selected tests pass |
| Backend MVC/contract tests | `mvn test -Dtest=BookingSettingsControllerMVCIT,BookingDisplayPreferencesControllerMVCIT,ApiV2OpenApiContractMVCIT` | exit 0; all selected tests pass |
| Frontend unit tests | `pnpm test src/modules/booking/domain/__tests__/bookingDisplayPreferences.test.ts src/modules/booking/domain/__tests__/bookingTime.test.ts src/modules/booking/components/AvailabilityBar.test.tsx src/modules/booking/pages/bookable-items/__tests__/BookingSettingsPage.test.tsx src/modules/booking/pages/preferences/__tests__/BookingPreferencesPage.test.tsx src/modules/booking/pages/calendar/__tests__/CalendarPage.test.tsx src/modules/booking/pages/all-bookable-items/AllBookableItemsPage.test.tsx src/modules/booking/pages/bookings/__tests__/BookingForm.test.tsx src/modules/booking/pages/my-bookings/__tests__/MyBookingsPage.test.tsx` | exit 0; all selected tests pass |
| Browser test | `VITEST_BROWSERS=chromium pnpm test-browser src/modules/booking/pages/all-bookable-items/BookingPages.spec.tsx src/modules/booking/pages/preferences/BookingPreferencesPage.spec.tsx` | exit 0 in Chromium |
| Extract English | `pnpm run i18n:extract --sync-primary` | exit 0; reviewed `booking.json` diff only contains intended keys |
| Generate i18n types | `pnpm run i18n:types` | exit 0 |
| Check i18n | `pnpm run i18n:lint` | exit 0 |
| Typecheck | `pnpm tsc` | exit 0; no TypeScript errors |
| Lint | `pnpm lint` | exit 0; no new findings |
| Compile package | `mvn package -DskipTests=true` | exit 0; no local repository install occurs |

Use the `react-testing-library` skill when changing `*.test.tsx` files and the
`rspace-browser-tests` skill when creating or changing `*.spec.tsx` files, if
those skills are available to the executor.

## Scope

### In scope

Backend production and persistence:

- `src/main/resources/sqlUpdates/liquibase-master.xml`
- `src/main/resources/sqlUpdates/changeLog-rsdev-1187-booking-display-preferences.xml` (create)
- `src/main/java/com/researchspace/model/booking/BookingConfigurationDefaults.java`
- `src/main/java/com/researchspace/model/booking/BookingDisplaySettings.java` (create)
- `src/main/java/com/researchspace/model/booking/BookingTimezoneMode.java` (create)
- `src/main/java/com/researchspace/model/booking/ApiV2BookingConfigurationResource.java`
- `src/main/java/com/researchspace/model/preference/Preference.java`
- `src/main/java/com/researchspace/booking/config/BookingTimeConfig.java` (create)
- `src/main/java/com/researchspace/booking/service/BookingConfigurationDefaultsManager.java`
- `src/main/java/com/researchspace/booking/service/BookingConfigurationDefaultsManagerImpl.java`
- `src/main/java/com/researchspace/booking/service/BookingDisplayPreferencesManager.java` (create)
- `src/main/java/com/researchspace/booking/service/BookingDisplayPreferencesManagerImpl.java` (create)
- `src/main/java/com/researchspace/booking/api/v2/BookingConfigurationResourceOperations.java`
- `src/main/java/com/researchspace/api/v2/controller/BookingSettingsController.java`
- `src/main/java/com/researchspace/api/v2/controller/BookingDisplayPreferencesController.java` (create)
- the relevant validation keys in `src/main/resources/bundles/`
- `DevDocs/DeveloperNotes/RestApiV2Collections.md`
- a short API compatibility note in the existing Booking developer notes, or
  `DevDocs/DeveloperNotes/BookingDisplayPreferences.md` if no Booking note is
  present (create)

Frontend production:

- `src/main/webapp/ui/src/modules/common/app/router.tsx`
- `src/main/webapp/ui/src/modules/booking/domain/bookingDisplayPreferences.ts` (create)
- `src/main/webapp/ui/src/modules/booking/domain/bookingTime.ts`
- `src/main/webapp/ui/src/modules/booking/pages/BookingPage.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/preferences/BookingPreferencesPage.tsx` (create)
- `src/main/webapp/ui/src/modules/booking/pages/preferences/routes.ts` (create)
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookingSettingsPage.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/bookingConfiguration.ts`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookableItemsPage.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/AddBookableItemPage.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookableItemPage.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/schedulingSettings.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/calendar/CalendarPage.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/calendar/BookingEventsCalendar.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/calendar/calendarAvailability.ts`
- `src/main/webapp/ui/src/modules/booking/pages/calendar/routes.ts`
- `src/main/webapp/ui/src/modules/booking/pages/all-bookable-items/AllBookableItemsPage.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/all-bookable-items/availabilityQuickFilters.ts`
- `src/main/webapp/ui/src/modules/booking/pages/all-bookable-items/routes.ts`
- `src/main/webapp/ui/src/modules/booking/components/AvailabilityBar.tsx`
- `src/main/webapp/ui/src/modules/booking/components/DayTimeline.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookings/AddBookingPage.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookings/EditBookingPage.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookings/BookingForm.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookings/ZonedBookingWindowFields.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/my-bookings/MyBookingsPage.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/my-bookings/bookingList.tsx`
- the item event-list formatter owned by `BookableItemPage`, if it is still in
  a separate file when execution starts
- `src/main/webapp/ui/src/modules/common/i18n/locales/en-US/booking.json`
- `src/main/webapp/ui/src/modules/common/i18n/resources.d.ts`

Tests, stories, fixtures, and page objects adjacent to the production files
above are in scope. Create these named tests if they do not exist:

- `src/test/java/com/researchspace/model/booking/BookingDisplaySettingsTest.java`
- `src/test/java/com/researchspace/booking/config/BookingTimeConfigTest.java`
- `src/test/java/com/researchspace/booking/service/BookingDisplayPreferencesManagerTest.java`
- `src/test/java/com/researchspace/api/v2/controller/BookingDisplayPreferencesControllerMVCIT.java`
- `src/main/webapp/ui/src/modules/booking/domain/__tests__/bookingDisplayPreferences.test.ts`
- `src/main/webapp/ui/src/modules/booking/pages/preferences/__tests__/BookingPreferencesPage.test.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/preferences/BookingPreferencesPage.story.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/preferences/BookingPreferencesPage.spec.tsx`

### Out of scope

- Removing `BookingConfiguration.timeZone`, its database/audit columns, its
  internal setter, or its manager command fields.
- Rewriting existing item timezones or changing existing opening-hour meaning.
- Changing iCalendar feed timezone semantics or subscription tokens.
- Changing the legacy `com_rs_timezone` session preference, its JVM-default
  fallback, or any non-Booking RSpace page.
- Changing the process-wide default timezone from Booking code.
- Adding overnight availability windows, per-item display windows, or a second
  availability window within one day.
- Creating a generic preferences framework, a new preference table, or another
  generic key/value REST endpoint.
- Storing these settings in `UI_JSON_SETTINGS`, Zustand, local storage, or a
  module-level frontend store.
- Adding dependencies.

## Git workflow

- Start only after Plan 007 is DONE and the operator-approved dirty work is
  understood.
- Use a dedicated branch such as `advisor/008-booking-display-preferences` if
  the current work is not already on its delivery branch.
- Commit by logical backend/frontend/testing unit. Follow the repository's
  existing imperative commit style.
- Do not push or open a PR unless the operator asks.

## Steps

### Step 1: Add the global display settings as a separate domain module

Create `BookingTimezoneMode` and `BookingDisplaySettings`. Keep wire/persisted
time values as canonical `HH:mm` strings because `24:00` cannot be represented
by `LocalTime`. The value module must own:

- constants for `08:00`, `18:00`, and default mode `BROWSER`;
- `from(BookingConfigurationDefaults)` and `applyTo(...)`;
- a nullable patch/merge shape used by the existing PATCH endpoint;
- canonical format validation, start-before-end validation, enum/custom-zone
  cross-field validation, and `ZoneId.of` validation;
- no dependency on controllers or persistence services.

Add the four fields to `BookingConfigurationDefaults` with audited properties
and Bean Validation delegating to this value module. Change the entity comment
and Booking settings endpoint descriptions from “copied into future bookable
items” to “global Booking defaults”, while leaving the scheduling-field
behavior unchanged.

Create the Liquibase changelog and include it immediately after the existing
Booking changelogs and before the final recurring/custom includes. Add the four
columns to both `BookingConfigurationDefaults` and
`BookingConfigurationDefaults_AUD`. Populate the live singleton with the
initial values before adding non-null constraints. Do not add these display
columns to `BookingConfiguration`.

Extend `BookingConfigurationDefaultsManager.updateDefaults` so one locked,
version-checked write merges both `BookingSchedulingSettings.Patch` and
`BookingDisplaySettings.Patch`, validates both, increments the existing
version, and emits the existing audit event once. Extend
`BookingSettingsController.SettingsDocument` and `SettingsPatch` with the four
fields. `institutionTimezone` is also returned read-only, obtained from the
qualified clock introduced in Step 2.

Tests must cover migration defaults through entity/manager fixtures, exact
boundary values, `24:00`, equal/reversed/overnight windows, invalid IANA zones,
CUSTOM without a zone, and non-CUSTOM with a supplied custom zone. Normalize
the latter to null rather than retaining a dormant custom choice.

**Verify**:

```bash
mvn test -Dtest=BookingDisplaySettingsTest,BookingConfigurationDefaultsManagerTest,BookingSettingsControllerMVCIT -Dfast=true
```

Expected: exit 0 and every selected test passes. If the MVC test is classified
as a Spring test in the live build and is skipped by `-Dfast=true`, rerun that
class without `-Dfast=true` before continuing.

### Step 2: Configure the institution timezone and build the user preference route

Create `BookingTimeConfig` with a named `bookingInstitutionClock` bean returning
`Clock.systemDefaultZone()`. Inject this bean with `@Qualifier` everywhere. Do
not make it an unqualified global `Clock` because REST v2 audit code already
has a different clock. This qualified bean is the seam for production and test
clocks; callers must not resolve the JVM timezone independently.

Add `BookingTimeConfigTest`. Set the JVM default to a non-UTC zone such as
`Pacific/Auckland`, construct the bean, and prove its zone matches the JVM
default. Restore the original default in a `finally` block so the test cannot
leak process state.

Create `BookingDisplayPreferencesManager` as the only public Java interface to
the stored preference. It should expose operations equivalent to:

```java
ResolvedBookingDisplayPreferences get(User subject, User actor);
ResolvedBookingDisplayPreferences replace(
    BookingDisplaySettings settings, User subject, User actor);
void reset(User subject, User actor);
```

The implementation uses `UserManager`, `ObjectMapper`,
`BookingConfigurationDefaultsManager`, and the institution clock. Append
`BOOKING_DISPLAY_PREFERENCES` to the preference enum. Serialize the versioned
JSON record atomically through `UserManager.setPreference`. A reset writes the
empty default value through that supported interface unless the live
`UserManager` already exposes a safe delete operation; do not add direct DAO
access from the service.

Validate both incoming and deserialized values with the same
`BookingDisplaySettings` rules. Log malformed/unsupported stored preferences at
WARN with user id/username and version if parseable, never the raw JSON. Return
current global values with `overridden: false` after fallback. Ensure cache
behavior in `UserManager` is exercised so a replace/reset is visible on the
next GET without logout.

Add the concrete controller at
`/api/v2/users/me/booking-preferences`, its Bean Validation, feature check,
externalized messages, and OpenAPI annotations. Add the route to the concrete
route table in `RestApiV2Collections.md`. Test identity ownership, run-as
subject ownership, unauthenticated/disabled-Booking access, valid replacement,
validation errors, reset, corrupt JSON fallback, and global-default changes
after reset.

**Verify**:

```bash
mvn test -Dtest=BookingTimeConfigTest,BookingDisplayPreferencesManagerTest -Dfast=true
mvn test -Dtest=BookingDisplayPreferencesControllerMVCIT,ApiV2OpenApiContractMVCIT
```

Expected: both commands exit 0; the generated OpenAPI contains GET, PUT, and
DELETE at the exact route with the documented schema.

### Step 3: Close the public per-item timezone write seam

Update `ApiV2BookingConfigurationResource.timezone` to be read-only on create
and update. Remove it from create examples. Inject the qualified institution
clock into `BookingConfigurationResourceOperations` and use its zone id in
single and bulk creates. Do not pass a timezone value in the public patch.
Leave the manager's internal `Create`/`Patch` records and application logic
unchanged.

Update resource, relationship-contract, manager, and operation tests:

- create succeeds without `timezone` and stores the fixed institution-clock
  zone;
- create, bulk create, patch, and bulk patch reject a supplied `timezone`;
- response projections can still request and read `timezone`;
- direct manager create/patch tests still prove that internal Java callers can
  set or change a scheduling timezone;
- existing configurations are not mutated by global or user display changes.

Remove timezone from the Add Bookable Item and Edit Bookable Item field lists,
input/update schemas, request bodies, searchable/default table columns, and
their fixtures. Keep it in the response schema and projections wherever
scheduling calculations require it. If an item details view shows opening
hours, retain a read-only “Scheduling timezone” fact beside them so legacy
items in another zone are understandable.

Do not remove timezone from booking response schemas, picker option schemas,
calendar source models, availability row scheduling data, or feed generation.

**Verify**:

```bash
mvn test -Dtest=BookingConfigurationManagerTest,BookingConfigurationResourceOperationsTest,ApiV2BookingConfigurationResourceTest -Dfast=true
pnpm test src/modules/booking/pages/bookable-items/__tests__/BookableItemsPage.test.tsx src/modules/booking/pages/bookable-items/__tests__/BookableItemPage.test.tsx
```

Expected: both commands exit 0. Tests show that the public transport rejects
timezone writes while the domain manager still supports them.

### Step 4: Add the frontend preference module and both settings pages

Create `bookingDisplayPreferences.ts` with a strict Valibot response/input
schema, fetch/PUT/DELETE functions, stable React Query keys, mutations, browser
timezone adapter, pure resolver, and `todayInTimeZone(zone)` helper. Add tests
for all three modes, invalid/missing browser zones, 24:00 minute conversion,
malformed responses, and query-cache update/reset behavior.

Create `/booking/preferences` for every Booking user. The page contains:

- start and end `<input type="time">` controls;
- a named radio group with the three fixed choices;
- the resolved browser zone in the Browser label and institution zone in the
  Institution label;
- an IANA timezone combobox enabled only for Custom;
- Save and Reset to global defaults actions;
- dirty, pending, success, server validation, and network error states;
- an explanation that the window and zone affect display, not an item's
  opening hours.

Build timezone options from `Intl.supportedValuesOf("timeZone")`, but always
include the current browser, institution, custom, and `UTC` identifiers if the
runtime list omits them or uses aliases. Do not add a dependency.

Add a Preferences sidebar item outside Administration. Extend the sysadmin
`BookingSettingsPage` with a clearly separate “Booking display defaults”
section that reuses the same field component and the same validation language.
Do not label `openingStart`/`openingEnd` as the availability window; those
remain scheduling defaults for new items.

Before editing `router.tsx` or Booking route search schemas, run both TanStack
Intent commands from “Commands you will need” and follow the loaded guidance.

Wrap all English strings with semantic i18n keys. Author with temporary
`defaultValue`, extract with `--sync-primary`, review the catalog, remove the
defaults, then regenerate types.

**Verify**:

```bash
pnpm test src/modules/booking/domain/__tests__/bookingDisplayPreferences.test.ts src/modules/booking/pages/preferences/__tests__/BookingPreferencesPage.test.tsx src/modules/booking/pages/bookable-items/__tests__/BookingSettingsPage.test.tsx src/modules/booking/pages/__tests__/BookingPage.test.tsx
pnpm tsc
```

Expected: both commands exit 0. Unit tests prove that PUT/DELETE update the
shared query cache and that the page is available without sysadmin role.

### Step 5: Make the preference authoritative on every read-only Booking view

Thread `useBookingDisplayPreferences()` through the Calendar, All Bookable
Items, My Bookings, and merged item page. Remove local calls to
`Intl.DateTimeFormat().resolvedOptions().timeZone` from pages. Keep browser
detection inside the resolver only.

Calendar changes:

- use the effective display timezone for event slicing, labels, agenda
  formatting, “today”, and query envelopes;
- pass availability start/end into `BookingEventsCalendar` and `DayTimeline`;
- replace the fixed 07:00 to 19:00 day/resource timeline domain;
- show one small page-level effective timezone label instead of repeating it
  per event.

All Bookable Items changes:

- create one absolute display interval from the selected date, preferred
  timezone, and preferred start/end;
- use that same interval for every row so bars and “Now” lines align;
- continue using each row's scheduling timezone only to generate opening-hour
  closures;
- show the display timezone once at page level, not beneath each bar;
- make quick filters use the same interval.

`calendarAvailability.ts` currently assumes one item-local date. Change its
deep interface to accept the shared absolute display interval. For each item,
enumerate all scheduling-zone local dates that overlap that interval, generate
opening-hour blockouts for those dates, and clip bookings, buffers, and
closures to the display interval. A large zone difference or DST transition
can span two scheduling dates; do not assume it always spans one.

`AvailabilityBar` receives an absolute interval plus the display timezone.
Compute segment and “Now” percentages against
`endInstant - startInstant`, not 1,440 wall-clock minutes. If now is before or
after the configured window, clamp the red marker to the nearest edge so it
remains visible and give it an accessible label saying that current time is
before or after the displayed window. Inside the window, every row must have
the same red-line position.

My Bookings and item event lists format start/end in the preferred display
timezone. Remove timezone from the default/list column configuration, but keep
the read-only scheduling timezone available in details or policy text where it
explains opening hours.

Make route date defaults preference-aware. Prefer optional `date` search
params during initial route parsing; after the preference query resolves, each
page uses `search.date ?? todayInTimeZone(preference.timeZone)` and normalizes
future navigation to that date. The sidebar and Today buttons use
`todayInTimeZone`. Do not retain browser-local `localToday()` calls on Booking
routes.

**Verify**:

```bash
pnpm test src/modules/booking/domain/__tests__/bookingTime.test.ts src/modules/booking/components/AvailabilityBar.test.tsx src/modules/booking/pages/calendar/__tests__/CalendarPage.test.tsx src/modules/booking/pages/all-bookable-items/AllBookableItemsPage.test.tsx src/modules/booking/pages/all-bookable-items/availabilityQuickFilters.test.ts src/modules/booking/pages/my-bookings/__tests__/MyBookingsPage.test.tsx
```

Expected: exit 0. Tests include two items whose scheduling zones differ by at
least eight hours, a preferred zone different from both, a DST transition, a
display interval that overlaps two item-local dates, and before/inside/after
“Now” positions.

### Step 6: Separate display input from scheduling-policy validation in booking forms

Booking form wall clocks must use the preferred display timezone, including
initial values, editing existing bookings, repeated-hour choices, and DST gap
errors. Keep the selected item's scheduling timezone as a separate prop for
client policy preview.

Refactor `ZonedBookingWindowFields` and the pure date/time helpers so they:

1. resolve the user's date/time input in the display timezone to absolute
   instants;
2. render those instants back in the display timezone;
3. validate the resulting instants against opening hours and granularity in
   the item's scheduling timezone;
4. submit only instants to the API, leaving the backend as final authority.

Do not silently reinterpret an existing booking's wall clock when the user
changes their preference. On edit, derive the draft from stored instants in
the current display timezone. If policy help includes opening hours, label them
with the read-only scheduling timezone rather than implying they use the
display zone.

Test these named cases:

- display and scheduling zones match;
- Berlin display with New York scheduling and the inverse;
- display-zone spring gap rejects a nonexistent wall clock;
- display-zone autumn overlap presents both valid instants;
- the selected instant crosses a scheduling-zone local date boundary;
- edit after changing display preference preserves the stored instants until
  the user changes the fields;
- server policy rejection still displays the externalized error.

**Verify**:

```bash
pnpm test src/modules/booking/pages/bookings/__tests__/ZonedBookingWindowFields.test.tsx src/modules/booking/pages/bookings/__tests__/BookingForm.test.tsx src/modules/booking/pages/bookings/__tests__/AddBookingPage.test.tsx src/modules/booking/pages/bookings/__tests__/EditBookingPage.test.tsx
```

Expected: exit 0 and each named cross-zone/DST case passes.

### Step 7: Complete contracts, fixtures, browser coverage, and live-stack verification

Update all MSW handlers, stories, page objects, test fixtures, projections, and
generated i18n types affected by the new preference request and the removal of
public item-timezone writes. Preference fixtures should cover Browser,
Institution, Custom, and inherited global modes. Avoid a global permissive MSW
handler; each test/story should declare the state it needs.

Create a Browser Mode story and page object for the new preference page. The
browser test must prove that a custom preference survives a reload and affects
at least Calendar and All Bookable Items without another save. Extend the
existing Booking pages browser test to prove that differently zoned items have
the same availability domain and “Now” x-position.

Run the i18n extraction sequence, all focused tests, TypeScript, lint, and
backend package compilation from “Commands you will need”. Then use the live
Docker dev stack already running for this worktree. Do not nuke it.

Manual scenarios at `http://localhost:8097`:

1. As sysadmin, set global window to 08:00 to 18:00 and mode Browser.
2. As a user with no override, confirm Preferences shows inherited values and
   Calendar and All Bookable Items use the same window.
3. Set Institution mode while the browser zone differs; reload and confirm all
   Booking pages use the institution zone.
4. Set Custom to `America/New_York`; reload, visit Calendar, All Bookable Items,
   My Bookings, one item page, Add Booking, and Edit Booking, and confirm dates
   and times agree.
5. On `/booking/all-items?date=2026-08-28`, confirm items with different stored
   scheduling zones share one bar domain and one red “Now” position. Confirm
   the marker is visible at an edge when current time is outside 08:00 to 18:00.
6. Reset. Confirm the current global values return without logout.
7. As sysadmin, create a bookable item without a timezone field. Confirm its
   read-only scheduling timezone equals the institution timezone and no
   timezone editor is present.
8. Send a REST v2 create and patch containing `timezone`; confirm both return
   400 while GET still returns the stored timezone.

**Verify**:

```bash
pnpm run i18n:extract --sync-primary
pnpm run i18n:types
pnpm run i18n:lint
pnpm tsc
pnpm lint
mvn test -Dtest=BookingTimeConfigTest,BookingDisplaySettingsTest,BookingDisplayPreferencesManagerTest,BookingConfigurationDefaultsManagerTest,BookingConfigurationManagerTest,BookingConfigurationResourceOperationsTest,ApiV2BookingConfigurationResourceTest -Dfast=true
mvn test -Dtest=BookingSettingsControllerMVCIT,BookingDisplayPreferencesControllerMVCIT,ApiV2OpenApiContractMVCIT
VITEST_BROWSERS=chromium pnpm test-browser src/modules/booking/pages/all-bookable-items/BookingPages.spec.tsx src/modules/booking/pages/preferences/BookingPreferencesPage.spec.tsx
mvn package -DskipTests=true
```

Expected: every command exits 0, focused tests pass, no MSW unhandled requests
are reported, and all eight live scenarios match the behavior above.

## Test plan

### Backend

- Model tests prove display-window and timezone-mode validation independently
  of Spring or controllers.
- Manager tests prove singleton locking/version conflict, atomic preference
  serialization, inheritance, replace/reset, corrupt JSON fallback, and
  `UserManager` cache freshness.
- MVC tests prove authentication, Booking feature access, run-as ownership,
  externalized errors, and the exact GET/PUT/DELETE wire shapes.
- Resource tests prove timezone is read-only at the public API seam and remains
  writable through the internal manager seam.
- OpenAPI contract tests prove the preference route and read-only scheduling
  timezone are documented.

### Frontend

- Pure tests cover resolution, browser fallback, display bounds, cross-zone
  dates, DST gaps/overlaps, and availability clipping.
- React Testing Library tests cover accessible preference controls, mutations,
  reset, validation, and all consumers of the resolved hook.
- Chromium Browser Mode covers persistence across reload/routes and aligned
  availability bars with different item scheduling zones.
- Stories declare all preference and network state through MSW so visual/manual
  checks do not depend on a developer machine timezone.

## Done criteria

- [ ] The global default is 08:00 to 18:00 and mode BROWSER after migration.
- [ ] The Institution timezone and new public-API-created item scheduling
  timezone equal the JVM default.
- [ ] Sysadmins can update display defaults with the existing optimistic
  Booking settings version.
- [ ] Every Booking user has a preferences page with the three fixed timezone
  options, Save, and Reset to global defaults.
- [ ] A saved preference persists through REST, reload, route changes, and a
  second browser session.
- [ ] Calendar, All Bookable Items, My Bookings, item event views, and booking
  forms use one resolved display timezone and window.
- [ ] Items with different scheduling zones have aligned availability domains
  and “Now” indicators, while opening-hour calculation still uses each item's
  scheduling zone.
- [ ] The public UI and REST API cannot set an item's timezone; internal Java
  code still can, and GET responses still expose it.
- [ ] Existing item timezone values and calendar feed behavior are unchanged.
- [ ] No Booking route uses a page-local browser timezone or browser-local
  `localToday()` decision.
- [ ] No new use of `UI_JSON_SETTINGS`, legacy preference Ajax, local storage,
  Zustand, or a new preference table exists.
- [ ] All commands in Step 7 exit 0 and all live-stack scenarios pass.
- [ ] `plans/README.md` marks Plan 008 DONE.

Useful mechanical checks:

```bash
rg -n 'resolvedOptions\(\)\.timeZone|localToday\(' src/main/webapp/ui/src/modules/booking
rg -n 'name: "timezone"|requiredOnCreate = true' \
  src/main/webapp/ui/src/modules/booking/pages/bookable-items/bookingConfiguration.ts \
  src/main/java/com/researchspace/model/booking/ApiV2BookingConfigurationResource.java
rg -n 'UI_JSON_SETTINGS|/userform/ajax/preference|localStorage|createStore' \
  src/main/webapp/ui/src/modules/booking \
  src/main/java/com/researchspace/booking
git status --short
```

Expected: the first command has no page-level matches outside the one browser
adapter in `bookingDisplayPreferences.ts`; the second has no writable item
timezone declaration; the third has no matches for the new feature; status
contains only operator-approved work plus this plan's in-scope files.

## STOP conditions

Stop and report instead of widening or improvising if:

- Plan 007 is not DONE or its merged page/calendar-subscription interfaces are
  still changing.
- “Default timezone” is intended to affect all RSpace modules, JSPs, exports,
  notifications, or legacy session formatting rather than Booking only. That
  is a separate application-wide migration.
- Product requires existing `BookingConfiguration.timeZone` values to be
  rewritten. That changes opening-hour and feed behavior and needs an explicit
  data migration/cutover design.
- The global display timezone is expected to seed item scheduling timezones or
  change opening-hour meaning. Browser mode cannot be a deterministic backend
  scheduling rule, so that needs a separate product decision.
- Product requires overnight or multiple availability windows.
- API clients need a deprecation period instead of immediate 400 responses for
  timezone writes. Add a compatibility rollout plan before changing the
  contract.
- A valid preference must follow the user across run-as subject changes in a
  way that conflicts with `caller.subject()` ownership.
- Correct client validation would require replacing the backend's scheduling
  policy rather than translating display-zone inputs to instants.
- Any step requires deleting the item timezone column, altering calendar feed
  semantics, changing the legacy session timezone, adding a dependency, or
  creating a second preference table.
- A focused verification fails twice after a reasonable fix, or the live code
  materially differs from “Current state”.

## Maintenance notes

- Treat `BookingDisplaySettings` and the frontend preference resolver as the
  two deep interfaces. Future Booking pages should consume them rather than
  resolving browser time independently.
- Adding an overnight window later requires an explicit date-boundary model,
  not weakening `start < end` validation.
- If REST compatibility requires restoring per-item timezone writes, restore
  the transport access separately; do not couple display preferences back to
  scheduling policy.
- Reviewers should scrutinize DST behavior, two-local-date availability
  clipping, run-as ownership, enum append order, and accidental removal of
  scheduling timezone from internal models.
