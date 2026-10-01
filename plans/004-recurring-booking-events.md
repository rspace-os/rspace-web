# Plan: Recurring booking events

Revised 2026-09-30 against `90c0e11bb`. See "History" at the end.

## Objective

Let a user create a bounded daily or weekly series of bookings or maintenance events from the
normal booking flow, see every occurrence before saving, and edit or cancel one occurrence, this and
following, or the whole series. No series may contain overlapping or out-of-hours events.

## Current state

- A booking is one `TimeSlotBooking` row. It is Envers-audited (`TimeSlotBooking_AUD`), and each
  change writes one audit-trail entry (`TimeSlotBookingAuditEvent`, `bookings:{id}`).
- `TimeSlotBookingManagerImpl.createBooking` takes a `PESSIMISTIC_WRITE` lock on the configuration
  row and checks the create capability. It then runs three checks:
  - `validateWindow`, including a global 366-day cap.
  - `BookingSchedulingPolicyImpl`: slot alignment, maximum duration and per-weekday opening hours,
    all in the item's timezone. Maintenance skips the last two.
  - `requireNoConflict`: overlap, then buffers.
- Cancel takes an optional reason. `restore` returns a cancelled future booking to its slot, and My
  Bookings' undo restores one row.
- `BookingNotificationService` notifies the instrument's subscribers per notification type. Each
  message (`BookingNotificationData`) covers one booking. Only the `BOOKING` kind notifies, and
  edits are silent.
- Feeds emit one event per row, with the row id as the UID.
- `BookingForm` is used for creating (`AddBookingContent`, `ActiveBookingCreationDialog`) and
  editing (`BookingInlineEditForm`, `InlineBookingEditor`). It already has an `afterWindowFields`
  slot and `submissionBlocked`. `DeleteBookingDialog` cancels on My Bookings and the details page.
- Times are shown in the user's display timezone (`useBookingDisplayPreferences().timeZone`).
- No series model exists.

## Contract

Items marked **decided** were confirmed on 2026-09-30. The rest are v1 defaults for product to
confirm.

1. **Rule.**
   - Frequency: `DAILY` or `WEEKLY`, with `interval` 1 to 4 and `weekdays` for weekly.
   - End: after `count` (2 to 52), or on an inclusive `until` date. Either way, within one year of
     occurrence 1.
   - The entered window is occurrence 1, and its weekday is always included.
   - Weekly intervals count 7-day blocks from occurrence 1.
   - No monthly rules.
2. **Timezone (decided).** The display timezone is used throughout. The client sends it with the
   rule, and the server expands the rule in it and stores it with the rule. An edit that reuses the
   stored rule keeps its zone. Scheduling rules still apply per occurrence in the item's timezone,
   so zone mismatches show up as ordinary scheduling failures in the preview.
3. **DST.** Occurrences keep occurrence 1's wall-clock start and end. A nonexistent time shifts
   forward by the gap, and an ambiguous time takes the earlier instant. The preview flags shifted
   rows.
4. **Atomic.** If one occurrence fails, against stored events or another occurrence, the whole
   operation fails, and every failure is reported.
5. **Kinds.** Bookings and maintenance share one code path. Maintenance keeps its scheduling
   exemptions and still sends no notifications.
6. **Concurrency and access.** A scoped operation checks only the clicked row's version and
   permissions. Its siblings share that row's item and requester, and are changed under the
   configuration lock.
7. **Retries.** No idempotency key, as for a single create. A retry after an attempt that actually
   committed fails on overlap, and the client refreshes.
8. **Feeds.** One event per row, with no RRULE. Regenerated rows appear as new events.
9. **Restore and undo.** Restore stays single-row. Undo is offered only after a single-row cancel.
10. **Feature flag (decided).** Uses the existing `bookingEnabled` flag.

## Open question for product

A regenerating `FOLLOWING` or `SERIES` edit cancels rows and creates new ones. Subscribers read
"8 bookings cancelled" then "8 bookings created" for what the user sees as a reschedule, and the
requester's Cancelled list fills with rows they did not cancel. Feeds already accept this
(contract 8). The options are to leave it, or to add a `replaced` flag that changes the wording and
hides those rows from the Cancelled list.

## Out of scope

- Monthly or custom rules.
- RRULE import, export and feed metadata.
- An exception model.
- Idempotency keys.
- Series-level audit events.
- Scoped restore.
- A new feature flag.
- Any change to non-recurring behaviour.

The 52-occurrence cap is what makes benchmarks and recovery tooling unnecessary.

## Backend

1. **Expansion.** One domain class validates the rule and the zone and expands the rule into
   windows. It is the only recurrence arithmetic; the frontend never expands rules.

   Tests:
   - Each frequency, interval and weekday set.
   - Count and until bounds, the cap and the one-year horizon.
   - Leap years.
   - Both DST shifts, at the start and at the end.
   - A rule zone that differs from the item's zone.
   - Malformed input.

2. **Schema.** One `context="run"` changeset, included from `liquibase-master.xml`, modelled on
   `changeLog-rsdev-booking-cancellation-reason.xml`:
   - `BookingSeries`: identity `id`, `rule` as JSON (including the zone), `createdBy_id` and
     `createdAt`. It is insert-only and not audited.
   - `TimeSlotBooking` and `TimeSlotBooking_AUD`: nullable `series_id`, `seriesPosition` and
     `seriesCount`. They are set at creation and never changed. Index `(series_id, startTime)`.
   - Entity: a lazy `@ManyToOne series` with a `NOT_AUDITED` target, and
     `@AuditTrailProperty(name = "seriesId")`.

   Tests:
   - The migration profile on a clean database.
   - A committing `*IT` that checks `_AUD` rows and audit-trail entries carry `seriesId`.

3. **Validate, dry run, create.** Validation runs in this order:
   - The single-create preamble: subject, instrument, enabled configuration and create capability.
   - For each occurrence: `validateWindow`, the full scheduling policy and `requireNoConflict`.
   - Occurrences against each other: buffered intervals against requested ones, using the same
     `conflictingKinds`.

   There are no weekday or DST shortcuts.

   - **Dry run** reads the configuration without the lock and writes nothing. Each occurrence
     returns a `status` (`ok` or the single-create error code), the conflicting event or occurrence
     number, and `flags: [dstShifted]`.
   - **Create** takes the lock, re-validates, and writes the series and all its rows, or nothing.
     On failure it returns the occurrence list.

   Tests:
   - A dry run writes nothing, completes while the lock is held, and needs the create capability.
   - Each status.
   - A second weekday on which the item is closed.
   - A series that overlaps itself, and one that conflicts with itself only through buffers.
   - All-or-nothing creation.
   - Two concurrent series: disjoint ones both commit, overlapping ones do not.
   - The cap, an archived target, and a maintenance series.

4. **Scoped update and cancel.** Add an optional `scope`. The default, `THIS`, is today's
   behaviour.
   - **Cancel.** `FOLLOWING` cancels the confirmed future rows from the clicked row onward, and
     `SERIES` cancels all confirmed future rows. The one reason is applied to every row cancelled.
     Past and already cancelled rows are untouched.
   - **Update, window or rule changed.** `FOLLOWING` cancels the confirmed future rows from the
     clicked row onward, without a reason, and creates a new series starting at the edited window.
     The new series uses the request's rule, or else the stored rule and zone with
     `count = seriesCount - seriesPosition + 1` (or the same `until`). `SERIES` is `FOLLOWING` from
     the earliest confirmed future row. The response adds `replacementBookingId`.
   - **Update, purpose only.** Applied in place to the affected rows.
   - **Dry run.** A dry run of a scoped update validates the new series, excluding the rows it
     would cancel. `requireNoConflict`'s `excludedId` becomes a set.

   Tests:
   - Every scope, for update and for cancel.
   - A purpose-only edit keeps row ids.
   - A moved occurrence followed by a `FOLLOWING` edit.
   - The remaining count when the stored rule is reused.
   - A stale version on the clicked row returns 412.
   - A conflicting new series rolls back the cancellation too.
   - The reason reaches only the rows this operation cancels.
   - A `FOLLOWING` dry run does not report the rows it replaces as overlaps.
   - Restoring a replaced row returns an overlap.

5. **Notifications.** `BookingNotificationData` gains optional `occurrenceCount`, `lastStartTime`
   and `lastEndTime`, and `bookingId` is the first affected row. Add plural email and in-app
   messages to `BookingNotificationMessageFormatter` and the server catalogues. Each action sends at
   most one notification per type:
   - Create sends "created".
   - A scoped cancel sends "cancelled", with the reason.
   - A regenerating update sends "cancelled" and "created".
   - Single-row and purpose-only edits stay silent, and maintenance sends nothing.

   Tests:
   - One message per recipient per type in each case.
   - Single-booking messages are unchanged.

6. **API v2.** Every new field is optional, so old clients see today's behaviour.

   | Where | Adds |
   |---|---|
   | Create input | `recurrence` (`frequency`, `interval`, `weekdays`, `count` or `until`, `timeZone`), `dryRun` |
   | Update input (cancel included) | `scope`; for `FOLLOWING` and `SERIES`, `recurrence` and `dryRun` |
   | Update response | `replacementBookingId` |
   | Read documents | `series` (`id`, `position`, `count`), absent on busy events; details also get the structured `rule` |

   Add problem codes for an invalid rule, an invalid zone, the cap, and occurrence conflicts (with
   the list). Feeds need no change.

   Tests:
   - Resource-operation, MVC and OpenAPI tests.
   - Single-booking payloads are unchanged.
   - One feed test: each row has its own UID, and no series metadata appears.

## Frontend

The UI will be built from one of two untracked prototypes under `modules/booking/prototypes/`:
`RecurringBookingFablePrototype.stories.tsx` or `RecurringBookingCodexPrototype.stories.tsx`.
Which one is decided later. Times use the display timezone and time format, with
`BookingInstrumentTimeTooltip` for the instrument's time.

1. **Repeat control.** Lives in `BookingForm`'s `afterWindowFields` slot and defaults to "Does not
   repeat". It offers frequency, interval, weekdays, and an end after N occurrences or on a date,
   and states the timezone and the 52 limit. It appears on create, and on edit for series rows,
   seeded from the stored rule.
2. **Preview.**
   - A debounced dry run runs on each valid change.
   - It shows a table of occurrences with each status (via `bookingProblemMessage`), the shifted
     flag, and the total.
   - While repeating, it replaces the `useBookingDraftAvailability` alerts, and the day timeline
     shows occurrence 1.
   - Failures block submit through `submissionBlocked`.
   - On edit, once a series row's window or rule changes, it previews the `FOLLOWING` result.
3. **Save.** Announce the count and the first date. A failed save keeps the entered values and
   shows the returned list. `outcomeUncertain` handling is unchanged.
4. **Scope choice.** For series rows, in `DeleteBookingDialog` (which keeps its reason field) and at
   save in both editors. It shows the affected count and date range.
   - `THIS` is disabled when the rule has changed.
   - Choices that regenerate say that bookings will be re-created and earlier moves replaced.
   - Afterwards, the details page goes to `replacementBookingId`, and the calendar editor closes and
     refetches.
   - Undo is offered only after a `THIS` cancel.
5. **Lists and details.** Show a "3 of 12" badge and a "Repeats" fact built from `series.rule`. The
   fact names the rule's zone when it differs from the viewer's. Busy events show neither.

Tests:
- RTL: the Repeat control, preview states, hidden availability alerts, submit blocking, and the
  scope dialog (reason field and undo gating).
- Browser Mode: creating a series, cancelling with each scope, and a `FOLLOWING` edit from the
  calendar.

## Verification gates

```bash
mvn test -Dtest=TimeSlotBookingManagerTest,TimeSlotBookingResourceOperationsTest,BookingCalendarManagerTest,BookingNotificationServiceTest -Dfast=true
mvn test -Dtest=TimeSlotBookingManagerIT,BookingCalendarFeedControllerMVCIT,BookingCalendarDownloadControllerMVCIT
pnpm test src/modules/booking/creation src/modules/booking/pages/bookings src/modules/booking/pages/calendar src/modules/booking/pages/my-bookings
pnpm test-browser src/modules/booking/pages/bookings src/modules/booking/pages/calendar src/modules/booking/pages/my-bookings
pnpm tsc
pnpm lint
```

Also run the migration profile for the new changeset.

## Done criteria

- A series of up to 52 occurrences can be previewed, created atomically, and edited or cancelled
  with each scope, for both bookings and maintenance.
- Every occurrence passes the full scheduling policy and conflicts with nothing, its siblings
  included.
- The frontend does no recurrence arithmetic and shows series times in the display timezone.
- Each action sends at most one notification per type, and a series' audit rows share a
  `seriesId`.
- Single-booking behaviour, payloads, feeds and tests are unchanged.

## History

- **2026-09-24:** two adversarial reviews, folded in. They led to the cap instead of benchmarks, no
  idempotency key, no exception model, and expansion on the server only.
- **2026-09-30:** reviewed against `90c0e11bb`, after cancellation reasons, restore, subscription
  notifications and weekday opening hours had landed. That review added:
  - the `_AUD` columns;
  - full validation of every occurrence, and the check of occurrences against each other;
  - a dry run that doesn't take the lock, and dry runs for scoped edits;
  - the reason and undo rules;
  - the notification payload;
  - three product decisions: display timezone, the existing flag, and deferring the prototype
    choice.
