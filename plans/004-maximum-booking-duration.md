# Plan 004: Limit the duration of one booking per bookable item

> **Executor instructions**: Read this plan fully before changing code. Follow the steps in order,
> run every verification command, and confirm the expected result before continuing. Preserve all
> unrelated and user-authored work. Never run a Maven `install` or deploy goal. Stop and report if
> a condition in "STOP conditions" occurs.

## Status

- **Status**: Complete
- **Priority**: P1
- **Effort**: M
- **Risk**: MED — the change crosses persistence, REST contracts, time policy, and strict frontend schemas
- **Category**: direction
- **Depends on**: The uncommitted Plan 009 booking-settings implementation in this worktree
- **Planned at**: commit `7e44f235e`, 2026-08-24, with the current dirty `feat/booking-settings` worktree present

## Why this matters

Sysadmins can control when an item may be booked, but they cannot stop one reservation from
occupying that item for an excessive period. Add a maximum duration to the global creation-time
defaults and to each bookable item's scheduling policy. Enforce the item value for booking creation
and time edits while preserving the existing 366-day system safety limit.

## Drift check

The relevant booking-settings files are uncommitted at the planned commit. Do not expect a clean
diff. Before implementation, run:

```bash
git rev-parse --short HEAD
git status --short
test -f src/main/java/com/researchspace/model/booking/BookingSchedulingSettings.java
test -f src/main/java/com/researchspace/booking/service/BookingSchedulingPolicyImpl.java
test -f src/main/webapp/ui/src/modules/booking/pages/bookable-items/schedulingSettings.tsx
test -f src/main/webapp/ui/src/modules/booking/pages/bookings/ZonedBookingWindowFields.tsx
git diff --stat 7e44f235e -- \
  src/main/java/com/researchspace/model/booking \
  src/main/java/com/researchspace/booking \
  src/main/java/com/researchspace/api/v2/controller/BookingSettingsController.java \
  src/main/webapp/ui/src/modules/booking \
  src/main/resources/sqlUpdates/changeLog-rsdev-1187-booking-settings.xml
```

Expected: HEAD is `7e44f235e` or contains it, all four files exist, and the diff contains the Plan
009 booking-settings work. Compare the live code with the excerpts below. If a material interface
has changed, update this plan before implementation.

## Goal and success measures

Add one non-null `maxBookingDurationMinutes` setting to global booking defaults and each
`BookingConfiguration`.

The feature is complete when:

1. A sysadmin can set the maximum in minutes in global defaults and on one bookable item.
2. New configurations copy the current default once. Later default changes do not affect existing
   configurations.
3. `0` disables the item-specific limit and preserves current behavior. The independent 366-day
   system safety limit still applies.
4. A positive maximum is inclusive. A booking of exactly the maximum elapsed duration succeeds;
   a longer booking fails on both create and time-changing update.
5. Duration is measured between UTC instants. A daylight-saving clock change does not alter the
   meaning of the configured number of minutes.
6. Lowering the limit does not rewrite existing bookings. A purpose-only edit of an existing
   over-limit booking remains possible, but any time edit must satisfy the current limit.
7. Focused backend and frontend tests, TypeScript, i18n, lint, package compilation, and Docker-stack
   browser verification pass.

## Fixed product decisions

### Public contract

| Property | Wire type | Default | Meaning and validation |
| --- | --- | --- | --- |
| `maxBookingDurationMinutes` | integer (`long` in Java) | `0` | `0` disables the item limit. A positive value must be at least the slot granularity, at most `527040` minutes (366 days), and divisible by the current slot granularity. |

- The maximum is one elapsed interval from booking start to booking end. Buffers are not part of
  the booking duration.
- The maximum is inclusive.
- Keep minutes as the only persisted and wire representation. Do not add hours/days fields or a
  duration value object.
- Keep `0` rather than a nullable column or a separate enable switch.
- Define `MAX_BOOKING_DURATION_MINUTES = 527_040` and
  `DEFAULT_MAX_BOOKING_DURATION_MINUTES = 0` with the other scheduling constants in
  `BookingSchedulingSettings`. Use that maximum in both settings validation and the manager's
  existing hard-cap check so the two limits cannot drift.
- Remove `MAX_BOOKING_DURATION_DAYS` from `TimeSlotBookingManager` after its only use moves to the
  shared minutes constant. Do not retain two units for the same system cap.

### Policy behavior

```text
Request interval
    |
    +-- invalid or over 366 days --> existing window/duration error, before lock
    |
    v
lock BookingConfiguration
    |
    +-- endpoint granularity
    +-- item maximum elapsed duration
    +-- opening-hour coverage
    +-- buffered overlap, unless double-booking is allowed
```

- Enforce the item maximum inside `BookingSchedulingPolicyImpl`, after both endpoints pass
  granularity validation and before opening-hour and overlap work.
- Compare `Duration.between(startInstant, endInstant)` with `Duration.ofMinutes(maximum)`. Do not
  subtract local times; that is incorrect across daylight-saving transitions.
- `allowDoubleBooking` bypasses only overlap detection. It does not bypass the duration limit.
- Keep the existing lock order. Do not add a second query or read the global defaults at booking
  time.
- `TimeSlotBookingManagerImpl` already applies scheduling policy only when an update changes the
  interval. Keep this behavior so a purpose-only edit remains valid after the item limit is lowered.
- Do not alter, cancel, or mark existing bookings invalid when settings change.

### Errors and UI

- Add `MAXIMUM_DURATION` to `InvalidBookingSchedulingSettingsException.Reason`, mapped to
  `errors.api.v2.bookingConfiguration.maximumDuration.invalid`.
- Add `MAXIMUM_DURATION` to `BookingPolicyException.Reason`, mapped to
  `errors.api.v2.booking.maximumDuration`.
- Reuse existing exception advice. Do not add another exception class or response shape.
- Label the shared settings control **Maximum booking duration (minutes)**.
- Use a numeric input with `min=0`, `max=527040`, and a dynamic `step` equal to the selected slot
  granularity. Explain that `0` permits bookings up to the 366-day system limit.
- On booking forms, show the configured maximum when it is positive. Resolve both wall-clock
  endpoints first, then run the same elapsed-instant comparison as the backend.
- Include a maximum-duration mismatch in the existing `allowPolicyMismatch` behavior. An unchanged
  legacy interval may be submitted for a purpose-only edit; a changed over-limit interval may not.
- Map the new server problem code to the same localized booking-form error on both Add and Edit.

## In scope

### Persistence and domain

- `src/main/resources/sqlUpdates/changeLog-rsdev-1187-booking-settings.xml`
- `src/main/java/com/researchspace/model/booking/BookingSchedulingSettings.java`
- `src/main/java/com/researchspace/model/booking/BookingConfiguration.java`
- `src/main/java/com/researchspace/model/booking/BookingConfigurationDefaults.java`
- `src/main/java/com/researchspace/booking/service/BookingSettingsValidation.java`
- `src/main/java/com/researchspace/booking/service/BookingSchedulingPolicy.java`
- `src/main/java/com/researchspace/booking/service/BookingSchedulingPolicyImpl.java`
- `src/main/java/com/researchspace/booking/service/BookingPolicyException.java`
- `src/main/java/com/researchspace/booking/service/InvalidBookingSchedulingSettingsException.java`
- `src/main/java/com/researchspace/booking/service/TimeSlotBookingManager.java`
- `src/main/java/com/researchspace/booking/service/TimeSlotBookingManagerImpl.java`
- `src/test/java/com/researchspace/testutils/DatabaseCleaner.java`

### REST and user-facing text

- `src/main/java/com/researchspace/model/booking/ApiV2BookingConfigurationResource.java`
- `src/main/java/com/researchspace/booking/api/v2/BookingConfigurationResourceOperations.java`
- `src/main/java/com/researchspace/booking/api/v2/TimeSlotBookingResourceOperations.java`
- `src/main/java/com/researchspace/api/v2/controller/BookingSettingsController.java`
- `src/main/webapp/ui/src/modules/common/i18n/locales/en-US/booking.json`
- `src/main/webapp/ui/src/modules/common/i18n/locales/en-US/server.core.json`
- `src/main/webapp/ui/src/modules/common/i18n/resources.d.ts`
- `CONTEXT.md`

### Frontend

- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/schedulingSettings.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/bookingConfiguration.ts`
- `src/main/webapp/ui/src/modules/booking/components/BookableItemPicker.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookableItemPage.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/all-bookable-items/availabilityQuickFilters.ts`
- `src/main/webapp/ui/src/modules/booking/pages/calendar/CalendarPage.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookings/BookingForm.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookings/ZonedBookingWindowFields.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookings/AddBookingPage.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookings/EditBookingPage.tsx`

### Focused tests and fixtures

- Backend tests under `src/test/java/com/researchspace/booking/`,
  `src/test/java/com/researchspace/api/v2/controller/BookingSettingsControllerMVCIT.java`, and
  `src/test/java/com/researchspace/model/booking/ApiV2BookingConfigurationResourceTest.java` that
  construct, publish, validate, or audit booking scheduling settings.
- Frontend tests and fixtures returned by:

  ```bash
  rg -l "slotGranularityMinutes" src/main/webapp/ui/src/modules/booking
  ```

  This includes the bookable-item picker, add/edit/settings pages, booking form, quick filters, and
  Calendar harness. Required strict-schema fixture updates are in scope even when a test does not
  exercise the new behavior directly.

## Out of scope

- Recurring bookings, quotas, user- or group-specific limits, and capacity.
- A minimum booking duration.
- Changing Calendar availability calculations. The rule limits one request; it does not change
  whether a resource is occupied.
- Rewriting or cancelling existing bookings when a limit changes.
- Bulk-applying global defaults to existing configurations.
- New npm dependencies, backend services, endpoints, tables, or exception types.
- A separate hours/days UI or automatic unit conversion.
- Browser-mode component tests beyond fixture maintenance. The Docker browser flow supplies the
  end-to-end UI proof for this slice.

## Current-state anchors

These anchors explain where the extension belongs. Re-locate by symbol if line numbers move.

### Shared settings aggregate

`BookingSchedulingSettings.java:9-22` currently owns the six settings and their defaults:

```java
public record BookingSchedulingSettings(
    long slotGranularityMinutes,
    String openingStart,
    String openingEnd,
    long bufferBeforeMinutes,
    long bufferAfterMinutes,
    boolean allowDoubleBooking) {
```

Extend this record, its nested `Patch`, `empty`, `merge`, `isEmpty`, both `from` methods, and both
`applyTo` methods. This keeps create, update, and defaults-copy behavior on the existing path.

### Locked scheduling policy

`BookingSchedulingPolicyImpl.java:18-28` resolves instants and validates policy under the existing
configuration lock:

```java
Instant startInstant = start.toInstant();
Instant endInstant = end.toInstant();
requireAligned(startInstant.atZone(zone), configuration.getSlotGranularityMinutes());
requireAligned(endInstant.atZone(zone), configuration.getSlotGranularityMinutes());
requireOpeningCoverage(configuration, startInstant, endInstant, zone);
```

Add a small `requireMaximumDuration` helper here. Do not put item-policy logic in the controller or
DAO.

### Update exception for unchanged intervals

`TimeSlotBookingManagerImpl.java:134-155` computes `intervalChanged` and invokes the policy only when
it is true. Preserve that conditional. The existing `validateWindow` at lines 229-237 owns the hard
system limit before locking; change only its unit/constant source.

### Frontend policy seam

`ZonedBookingWindowFields.tsx:29-43` resolves ambiguous wall-clock values to concrete instants.
Lines 75-84 combine the current client policy errors and control `onResolved`. Add duration to that
same calculation so DST behavior follows the chosen occurrences.

`BookingForm.tsx:175-190` passes one selected target's scheduling settings into those fields. Add
the maximum to `BookableItemOption`, its REST projection, and this prop chain.

## Implementation steps

### 1. Add an append-only schema change and extend the domain aggregate

Append a new `context="run"` changeset named `rsdev-1187-booking-maximum-duration` to
`changeLog-rsdev-1187-booking-settings.xml`. Do not edit either existing changeset body.

The new changeset must:

1. Add `maxBookingDurationMinutes BIGINT` with default `0` to `BookingConfiguration`.
2. Backfill all existing rows to `0`, then add a not-null constraint.
3. Add the nullable audited column to `BookingConfiguration_AUD`.
4. Add `maxBookingDurationMinutes BIGINT` with default `0` and a not-null constraint to
   `BookingConfigurationDefaults`, and update the singleton row to `0`.
5. Add the nullable audited column to `BookingConfigurationDefaults_AUD`.

Add the audited field, bean getters/setters, defaults, and validation annotations to both entity
classes. Extend `BookingSchedulingSettings` and central validation with the public-contract rules.
Add `MAXIMUM_DURATION` as the final settings-validation reason. Update `DatabaseCleaner` so reset
state restores the singleton maximum to `0`.

Verification:

```bash
mvn test -Dtest=BookingConfigurationManagerTest,BookingConfigurationDefaultsManagerTest -Dfast=true
```

Expected: defaults are copied once, explicit item overrides win, valid boundary values pass, and
negative, over-cap, less-than-granularity, and non-divisible values fail before persistence.

### 2. Publish the setting through both REST surfaces

Add the field to `ApiV2BookingConfigurationResource` and to the examples and patch construction in
`BookingConfigurationResourceOperations`. Add it to `BookingSettingsController.SettingsDocument`
and `SettingsPatch`, with range annotations for `0..527040`; rely on service validation for the
cross-field divisibility rule. Keep partial-patch merge semantics.

Update the time-slot collection description to say that 366 days is the system maximum and one
bookable item may configure a smaller maximum. Add the two localized server messages and reuse the
existing controller advice mapping.

Verification:

```bash
mvn test -Dtest=BookingConfigurationResourceOperationsTest,ApiV2BookingConfigurationResourceTest -Dfast=true
mvn test -Dtest=BookingSettingsControllerMVCIT
```

Expected: fields, examples, rendering, partial patches, validation problems, and the singleton
document include `maxBookingDurationMinutes`.

### 3. Enforce the elapsed duration under the configuration lock

Add `MAXIMUM_DURATION` to `BookingPolicyException.Reason`. In
`BookingSchedulingPolicyImpl.validate`, reject only when the configured maximum is positive and
the elapsed instant duration is greater than it. Keep equality valid. Keep the existing 366-day
check in `validateWindow`, but source it from the shared maximum-minutes constant.

Add focused tests for:

- `0` allowing normal bookings up to the hard cap;
- exact positive maximum accepted and one aligned increment over rejected;
- create and time-changing update rejection;
- purpose-only edit accepted after lowering the current configuration maximum below the existing
  booking duration;
- double-booking enabled but an over-limit request still rejected;
- a Berlin fall-back interval from `2026-10-25T00:30:00Z` to `2026-10-25T01:30:00Z` accepted at a
  60-minute maximum with one-minute granularity, proving elapsed-instant semantics;
- the existing over-366-day request still rejected before `lockByTarget`.

Verification:

```bash
mvn test -Dtest=TimeSlotBookingManagerTest -Dfast=true
mvn test -Dtest=TimeSlotBookingManagerIT
```

Expected: all policy boundary, edit, DST, lock-order, and integration cases pass.

### 4. Extend shared settings schemas and forms

In `schedulingSettings.tsx`, add the field to the name list, Valibot entries, default object, and
shared form. Export one frontend maximum constant and one validation helper. Apply the cross-field
check to both `SchedulingSettingsSchema` and `BookingSettingsSchema`; do not duplicate the rule in
page components.

Use the selected granularity as the number input's step. Show a field error when a positive value
is smaller than or not divisible by that granularity. Include localized label, helper, and error
text. Verify that add-item, edit-item, and global-default forms serialize the value.

Verification:

```bash
pnpm test src/modules/booking/pages/bookable-items/__tests__/BookingSettingsPage.test.tsx \
  src/modules/booking/pages/bookable-items/__tests__/AddBookableItemPage.test.tsx \
  src/modules/booking/pages/bookable-items/__tests__/BookableItemPage.test.tsx \
  src/modules/booking/pages/bookable-items/__tests__/BookableItemsPage.test.tsx
```

Expected: `0` and valid positive values submit, invalid cross-field values do not submit, and the
same control behaves on defaults, add, and edit pages.

### 5. Carry the field through strict projections and enforce it in booking forms

Add the required number field to `bookingConfiguration.ts`, `BookableItemPicker`, item projections,
quick filters, and Calendar projections. Calendar should only carry/parse the value; do not change
availability calculations.

Pass the value from `BookingForm` to `ZonedBookingWindowFields`. After wall-clock resolution,
compare the resulting instant epoch values. Add duration to `policyInvalid`, the visible localized
errors, and `allowPolicyMismatch`. Show positive-limit guidance near opening hours. Add the new
problem-code mapping to Add and Edit pages.

Update every strict booking-configuration fixture returned by the scoped `rg` command. Prefer
`0` in tests unrelated to the new behavior.

Verification:

```bash
pnpm test src/modules/booking/components/__tests__/BookableItemPicker.test.tsx \
  src/modules/booking/pages/bookings/__tests__/BookingForm.test.tsx \
  src/modules/booking/pages/bookings/__tests__/ZonedBookingWindowFields.test.tsx \
  src/modules/booking/pages/bookings/__tests__/AddBookingPage.test.tsx \
  src/modules/booking/pages/bookings/__tests__/EditBookingPage.test.tsx \
  src/modules/booking/pages/all-bookable-items/availabilityQuickFilters.test.ts \
  src/modules/booking/pages/calendar/__tests__/CalendarPage.test.tsx \
  src/modules/booking/pages/calendar/__tests__/CalendarResourceList.test.tsx
VITEST_BROWSERS=chromium pnpm test-browser src/modules/booking/pages/calendar/CalendarPage.spec.tsx
```

Expected: exact-limit windows resolve, over-limit windows do not submit, server errors are
localized, unchanged over-limit edits remain resolvable, and all strict projections still parse.

### 6. Update terminology, extract i18n, and run the full feature checks

Add **Maximum booking duration** to the Booking section of `CONTEXT.md`: the maximum elapsed time
allowed for one booking by one configuration, distinct from buffers and the 366-day system cap.
Update the scheduling-policy and time-slot-booking entries to reference it.

Author new English strings through `defaultValue`, then use the repository i18n workflow and remove
the temporary defaults after extraction.

Verification:

```bash
pnpm run i18n:extract --sync-primary
pnpm run i18n:types
pnpm run i18n:lint
pnpm tsc
pnpm lint
mvn package -DskipTests=true
```

Expected: catalogs and generated types contain semantic keys, TypeScript and lint pass, and Java
packages without writing to the local Maven repository.

### 7. Verify the feature with Browser Automation on the Docker Dev Stack

Use the `rspace-dev-stack` playbook and the T3 in-app preview when it is available. The user has
already authorized starting the stack for this verification.

```bash
./docker/dev/rspace-dev ps
./docker/dev/rspace-dev up
./docker/dev/rspace-dev logs app
```

Wait for the app-ready message and take the URL from `ps`. Do not use `--fresh`, `reset-db`, or
`nuke` without separate confirmation because those operations discard worktree-local data.

Drive the UI with this loop: navigate, snapshot, act on snapshot locators, and snapshot again to
assert. Prefer `preview_status`, `preview_open`, `preview_navigate`, `preview_snapshot`, and focused
preview interaction tools. Verify:

1. Sign in as `sysadmin1` / `sysWisc23!` and open `/booking/config/settings`.
2. Confirm **Maximum booking duration (minutes)** is present, defaults to `0`, has the helper text,
   and reports an invalid value that is not divisible by the selected granularity.
3. Save `60`, reload the page, and confirm it persists.
4. Create a bookable item and confirm its edit form copied `60`. Change the global default to `30`
   and confirm the existing item's value remains `60`.
5. For that item, create a future booking whose elapsed duration is exactly 60 minutes and confirm
   the success navigation/state.
6. Attempt a 65-minute booking at five-minute granularity. Confirm the maximum-duration error is
   visible and no create request is sent.
7. Lower the item's maximum to `30`. Edit the existing 60-minute booking's purpose without changing
   its times and confirm save succeeds. Then change a time while keeping the interval over 30
   minutes and confirm submission is blocked with the duration error.
8. Inspect the final preview snapshot for console errors and failed API requests. Capture a
   screenshot of the configured field and one of the blocked over-limit booking.

If the seed database lacks a second suitable instrument for checking new-default copying, do not
mutate unrelated inventory solely for this check. The manager and form tests cover copy semantics;
record that browser limitation in the handoff.

After verification, report whether the stack was already running or was started by this work. Ask
the user whether to run `./docker/dev/rspace-dev down`; do not stop it silently.

## Test plan summary

| Layer | Required proof |
| --- | --- |
| Model/service | Default and override copying; `0`; lower/upper/divisibility validation; unchanged existing configurations |
| Policy | Inclusive boundary; over-limit create/update; purpose-only edit exception; double-booking independence; DST elapsed time; hard-cap lock order |
| REST | Collection and singleton fields; examples; partial patch; localized 400 responses |
| Frontend | Shared control; dynamic step and cross-field error; strict projections; exact/over-limit booking windows; Add/Edit problem mapping |
| Build quality | Focused Vitest/JUnit, browser-mode regression, TypeScript, Biome, i18n, Maven package |
| End to end | Persisted sysadmin setting, copy-on-create, accepted boundary booking, blocked over-limit booking, purpose-only legacy edit |

## Toolkit and constraints

- Use `rg`/`rg --files` for discovery.
- Use `apply_patch` for edits.
- Before editing TanStack Router files, run the matching TanStack Intent command. This plan does not
  require router changes; stop and reassess if one appears necessary.
- Use React Testing Library conventions for `*.test.tsx`. Use the `rspace-browser-tests` playbook
  only if a `*.spec.tsx` behavior test must change.
- Do not add dependencies.
- Do not edit generated build output, `target`, `dist`, or `node_modules`.
- Do not run Maven `install`, `install:install-file`, or deploy goals.
- Preserve unrelated dirty work and never reset the worktree to make this change easier.

## Git workflow

The prerequisite implementation is currently uncommitted on `feat/booking-settings`. Do not switch
branches while it remains dirty. Implement on the user's current branch unless they first commit or
move the prerequisite work. If starting later from a clean integrated baseline, use
`feat/booking-max-duration`.

Suggested commits, if the user asks for commits:

1. `Add maximum booking duration policy`
2. `Add maximum booking duration controls`

Before each commit, inspect `git diff --check` and stage only the files in this plan.

## Done criteria

- [x] Schema columns exist on configuration/default and both audit tables, with `0` backfill and
  non-null live columns.
- [x] Global and item REST contracts publish and accept the field.
- [x] Shared validation accepts only `0` or a positive aligned maximum within 366 days.
- [x] Backend policy rejects only elapsed durations greater than a positive configured maximum.
- [x] Create and time-changing update enforce the current item value under the existing lock.
- [x] Purpose-only edits and already-persisted bookings survive a later limit reduction.
- [x] Shared settings and booking forms expose localized, accessible controls and errors.
- [x] Strict frontend projections and fixtures include the required field.
- [x] Focused backend/frontend tests, browser-mode check, i18n, TypeScript, lint, and Maven package pass.
- [x] Docker-stack browser verification completes with no unexplained console or network failures.
- [x] `CONTEXT.md` documents the new canonical term and its distinction from the hard cap.

## STOP conditions

Stop and ask for direction if any of these is true:

1. The Plan 009 files are absent, or their current interfaces differ materially from the anchors.
2. Either existing changeset in `changeLog-rsdev-1187-booking-settings.xml` has been released or
   applied outside this worktree in a way that makes appending the ticketed changeset unsafe.
3. Product requirements need `null` or another sentinel instead of `0`, accept values not aligned
   to granularity, or define duration by local wall-clock time rather than elapsed instants.
4. The feature must change or invalidate existing bookings when a limit is lowered.
5. Completion would require a new table, endpoint, dependency, recurrence model, or capacity model.
6. The Docker stack needs destructive reset or `nuke` to proceed and the user has not approved it.
7. An in-scope file contains overlapping user changes that cannot be preserved safely.

## Maintenance notes

- Keep the maximum and its validation in the shared scheduling aggregate. If another booking entry
  point is added later, it should call `BookingSchedulingPolicy`, not reproduce the check.
- A future multi-unit UI may convert hours or days at its boundary, but the persisted and API value
  should remain minutes.
- If recurrence is introduced, decide separately whether the maximum applies to each occurrence or
  the whole series. This plan deliberately defines only one booking interval.
- Calendar availability should continue to derive from persisted occupied intervals. Do not make it
  dependent on a request-duration policy.
