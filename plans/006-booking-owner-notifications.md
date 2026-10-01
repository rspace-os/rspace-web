# Plan 006: Notify instrument owners about bookings and cancellations

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat bb3d6dbb0..HEAD -- src/main/java/com/researchspace/booking src/main/java/com/researchspace/model/comms src/main/java/com/researchspace/model/preference src/main/java/com/researchspace/service src/main/java/com/researchspace/webapp/controller/UserProfileController.java src/main/webapp/WEB-INF/pages/admin/userprofile/preferences.jsp src/main/webapp/ui/src/modules/common/i18n/locales/en-US/server.dashboard.json src/test/java/com/researchspace/booking src/test/java/com/researchspace/service src/test/java/com/researchspace/webapp/controller/UserProfileController*`.
> The working tree already contains unrelated booking-permission and frontend changes. Preserve them. If the excerpts below no longer match, compare the live code and stop before changing source.

## Status

- **Priority**: P1
- **Effort**: L
- **Risk**: MED
- **Depends on**: none; written against the current booking baseline, commit `bb3d6dbb0`
- **Category**: direction
- **Planned at**: commit `bb3d6dbb0`, 2026-09-22

## Why this matters

Instrument owners currently receive no RSpace notification when another user books their instrument or when that booking is cancelled. Calendar subscriptions are a separate feature and do not satisfy this request. This plan adds two standard RSpace notification types, lets each user turn those notifications on or off from the existing profile notification settings, and dispatches notifications for both explicit cancellation and cancellation caused by archiving a booking configuration. The initial design is a global per-user preference: an owner controls these event types for all instruments they currently own.

## Current state

- `src/main/java/com/researchspace/booking/service/TimeSlotBookingManagerImpl.java` creates a confirmed booking, saves it, and publishes only `TimeSlotBookingAuditEvent` (`:314-330`). On update it changes state to `CANCELLED`, saves, and publishes the same audit event (`:404-419`).
- `src/main/java/com/researchspace/booking/service/BookingConfigurationManagerImpl.java` cancels each future confirmed booking while archiving a configuration and publishes only the audit event (`:494-507`).
- `src/main/java/com/researchspace/booking/service/BookingAuditTrail.java` consumes booking events at `AFTER_COMMIT` and writes audit history (`:59-71`); `AuditTrailImpl` is not the RSpace communication system.
- `src/main/java/com/researchspace/service/CommunicationManager.java` exposes `notify(User originator, BaseRecord record, NotificationConfig config, String sysMsg)` (`:186-196`). `CommunicationManagerImpl.notify` uses `notificationTargetsOverride` when supplied, removes the originator, filters each recipient with `User.wantsNotificationFor`, then persists/broadcasts the notification (`:438-449`).
- `src/main/java/com/researchspace/model/comms/NotificationType.java` has no booking constants. `NotificationTypeMessages.keyFor` is an exhaustive switch (`src/main/java/com/researchspace/service/NotificationTypeMessages.java:13-25`), and dashboard labels are in `src/main/webapp/ui/src/modules/common/i18n/locales/en-US/server.dashboard.json:135-143`.
- `src/main/java/com/researchspace/model/preference/Preference.java` derives notification preferences from enum names and warns that enum ordinals are persisted (`:5-10`). `BOOKING_DISPLAY_PREFERENCES` is currently the last enum entry (`:186-187`), so new messaging preferences must be appended after it. `User.wantsNotificationFor` constructs `<NotificationType>_PREF` and defaults to the preference default (`src/main/java/com/researchspace/model/User.java:1015-1024`).
- `src/main/java/com/researchspace/webapp/controller/UserProfileController.java:164-174` controls the display order of profile messaging preferences. `src/main/webapp/WEB-INF/pages/admin/userprofile/preferences.jsp:20-34` renders every `MESSAGING` preference as a checkbox; no new React booking control is required for the setting itself.
- Existing tests provide the patterns: `NotificationTypeMessagesTest` covers every enum value, `UserTest` covers preference lookup, `CommunicationManagerIT` covers persistence/filtering, `TimeSlotBookingManagerTest` and `BookingConfigurationManagerTest` capture booking events, and `UserProfileControllerMVCIT`/`UserProfileControllerTest` cover profile ordering and checkbox round trips.

### Product decision to preserve

Implement two global per-user switches, defaulting to `true`:

1. `NOTIFICATION_BOOKING_CREATED`: notify the current owner when another user creates a confirmed booking on the owner’s instrument.
2. `NOTIFICATION_BOOKING_CANCELLED`: notify the current owner when another user’s confirmed booking is cancelled, either directly or while archiving the booking configuration.

Resolve the owner at notification time from the instrument’s current owner, and use `notificationTargetsOverride = Set.of(owner)`. `CommunicationManager.notify` still removes the actor and applies the owner’s preference. Do not notify for ordinary edits/reschedules in this first version. Do not add calendar subscription controls. A per-instrument opt-out is materially larger (new persisted preference/model, API, and instrument UI); if that is required, stop and return a revised design instead of silently implementing global switches.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Backend unit tests | `mvn test -Dtest=NotificationTypeMessagesTest,UserTest,CommunicationManagerImplTest,TimeSlotBookingManagerTest,BookingConfigurationManagerTest,UserProfileControllerTest -Dfast=true` | Exit 0; all selected tests pass. |
| Backend integration tests | `mvn test -Dtest=CommunicationManagerIT,UserProfileControllerMVCIT` | Exit 0 with the configured MariaDB; if the DB is unavailable, report the environment failure separately. |
| Frontend typecheck | `pnpm tsc` | Exit 0. |
| Frontend focused tests | `pnpm test src/modules/common/i18n` | Exit 0, or explain if no matching test files exist. |
| Lint | `pnpm lint` | Exit 0; distinguish pre-existing failures from task-caused failures. |
| Diff hygiene | `git diff --check` | No whitespace errors. |

Do not run Maven `install`, deploy, or a Docker dev-stack command for this plan.

## Scope

**In scope**

- Two `NotificationType` values, matching appended `Preference` values, message-key mappings, English notification labels, and any required message-bundle entries for notification text.
- A focused booking notification service or equivalent small service that resolves the current instrument owner and calls `CommunicationManager.notify` inside the booking transaction, after the booking state is saved.
- Calls from confirmed booking creation, direct confirmed-booking cancellation, and archive-triggered cancellation.
- Profile notification checkbox ordering and persistence through `/userform/ajax/messageSettings`.
- Internal RSpace notification persistence, dashboard rendering, and the existing email broadcast path governed by `BROADCAST_NOTIFICATIONS_BY_EMAIL`.
- Unit and integration tests for recipient selection, actor exclusion, preference suppression, cancellation paths, and preference round trips.

**Out of scope**

- Calendar subscriptions, calendar feeds, or booking-page subscription UI.
- Per-instrument notification settings or a new booking-specific preference API.
- Notifications for rescheduling, purpose edits, maintenance blockouts, or configuration edits unless a product owner expands the event list.
- Changing booking authorization or instrument ownership semantics.
- Replacing the existing audit event/listener; audit history must continue to work independently.

## Steps

### Step 1: Confirm the notification contract and owner lookup

Inspect `TimeSlotBooking`, `BookingConfiguration`, `Instrument`, and the instrument DAO/access APIs. Confirm that a booking can be passed as the `BaseRecord` argument to `CommunicationManager.notify`; if it cannot, choose the existing supported record or `systemNotify` representation and document the consequence for dashboard/email links before coding. Confirm the authoritative current-owner field and load it in the same transaction, without trusting the booking requester or a stale denormalized owner. Define the system message text and whether booking notifications use `broadcast=true`; default to the existing standard notification behavior so the owner’s `BROADCAST_NOTIFICATIONS_BY_EMAIL` preference controls email delivery.

**Verify**: `rg -n "class TimeSlotBooking|extends BaseRecord|owner|InstrumentDao|CommunicationManager" src/main/java/com/researchspace/model/booking src/main/java/com/researchspace/model/inventory src/main/java/com/researchspace/dao src/main/java/com/researchspace/booking` identifies the real target and owner APIs. If no safe record/owner path exists, stop.

### Step 2: Add notification and preference vocabulary without changing persisted ordinals

Append `NOTIFICATION_BOOKING_CREATED` and `NOTIFICATION_BOOKING_CANCELLED` to `NotificationType` (unless the live persistence mapping proves enum ordinals are unsafe, in which case stop and redesign). Append `NOTIFICATION_BOOKING_CREATED_PREF` and `NOTIFICATION_BOOKING_CANCELLED_PREF` after the current final `Preference` constant, both Boolean, `MESSAGING`, default `true`. Use clear display labels such as “A booking is created on an instrument I own” and “A booking on an instrument I own is cancelled”. Add both cases to `NotificationTypeMessages.keyFor` and matching `notificationType.bookingCreated` / `notificationType.bookingCancelled` labels to `server.dashboard.json`. Add any backend/email catalog keys through the existing message source; do not hard-code user-facing text in Java.

**Verify**: `mvn test -Dtest=NotificationTypeMessagesTest,UserTest -Dfast=true` passes, and `rg -n "NOTIFICATION_BOOKING_(CREATED|CANCELLED)|booking(Created|Cancelled)" src/main/java src/main/webapp/ui/src/modules/common/i18n/locales/en-US` finds both enum-to-preference mappings and labels.

### Step 3: Implement transactional dispatch for creation and cancellation

Create a narrow `BookingNotificationService` (or the repository’s established equivalent) injected into both booking managers. It should accept the saved booking, actor, event kind, and current owner; build a `NotificationConfig` factory/helper with the new type, `notificationTargetsOverride = Set.of(owner)`, and the chosen broadcast/policy values; then call `CommunicationManager.notify`. Invoke it only after a successful save and while the caller’s transaction is still active. Do not call `CommunicationManager.notify` from the existing `AFTER_COMMIT` `BookingAuditTrail`: `createNotificationForUser` persists through Hibernate and the notify-once policy expects an active transactional session, while an after-commit listener would also risk a notification surviving a later transaction failure.

Call the service after `save` in `createBooking` for confirmed bookings. In `updateBooking`, call it only when the prior state is `CONFIRMED` and the patch changes it to `CANCELLED`; leave the idempotent already-cancelled branch silent. In `BookingConfigurationManagerImpl.archive`, call it once for each future confirmed booking that is changed to `CANCELLED`. The actor is excluded automatically, so an owner booking their own instrument does not receive a self-notification. Keep audit event publication unchanged.

**Verify**: `mvn test -Dtest=CommunicationManagerImplTest,TimeSlotBookingManagerTest,BookingConfigurationManagerTest -Dfast=true` passes, with mocks proving the notification service is called for create/direct-cancel/archive-cancel and not for ordinary edits or already-cancelled updates.

### Step 4: Wire the existing profile UI and notification presentation

Add the two new preferences to `UserProfileController.desiredMessageDisplayOrder` in a deliberate order near the other event preferences. The existing JSP loop should render them automatically because they are `MESSAGING` preferences; only change the JSP if a catalog/display-message limitation requires it. Verify that the POST handler persists checked values and clears unchecked messaging values as it does for existing notification preferences. Add or update the notification dashboard/email labels so an owner sees a readable event type, and ensure the standard notification email template receives the new `NotificationTypeMessages` key. Do not add a calendar or booking-page toggle.

**Verify**: `mvn test -Dtest=UserProfileControllerTest,UserProfileControllerMVCIT -Dfast=true` passes where supported; inspect the rendered model/order and assert both new preference names are present, can be checked, and can be turned off. Run `pnpm tsc` and `pnpm lint` after catalog/type generation if frontend catalog types changed.

### Step 5: Prove recipient, preference, persistence, and rollback behavior

Add focused tests following existing communication and booking fixtures. Cover: owner receives a persisted booking-created notification when another user books; owner receives a persisted cancellation notification for direct cancellation; archive cancellation sends one notification per cancelled booking; owner preference `false` suppresses the notification; actor/owner self-booking is excluded; a different current owner is used if ownership changed before the event; ordinary edits and already-cancelled updates do not notify; and broadcast/email behavior follows the selected `NotificationConfig` policy. Add a transaction-failure test or service-level assertion that a failed booking transaction does not leave a communication row. Add a profile controller test for both new checkboxes and default-true behavior. Include a manual/browser acceptance check if no JSP browser harness exists: log in as owner, turn each preference off/on in My Profile, create/cancel a booking as another user, and inspect My Notifications and email delivery settings.

**Verify**: run the complete command table above, then `git diff --check`; all task-caused tests pass and the notification rows/recipients match the cases.

## Test plan

- Extend `NotificationTypeMessagesTest` and `UserTest` for exhaustive mapping and default preference behavior.
- Add a unit test for the new booking notification service using mocked `CommunicationManager`, owner lookup, and `User.wantsNotificationFor` state.
- Extend `TimeSlotBookingManagerTest` for create, direct cancel, ordinary edit, and already-cancelled paths; extend `BookingConfigurationManagerTest` for archive cancellation.
- Add `CommunicationManagerIT` coverage for `notificationTargetsOverride`, preference filtering, persisted recipient, and broadcast/email configuration.
- Extend `UserProfileControllerMVCIT` or `UserProfileControllerTest` for display order and POST round trip of both preferences.
- Run frontend typecheck and catalog/lint checks; no new React booking settings component is expected unless the live product has replaced the JSP profile surface.

## Done criteria

- [ ] Two booking notification types and two appended Boolean messaging preferences exist; existing `Preference` ordinals are unchanged.
- [ ] Creation, direct cancellation, and archive cancellation notify the current instrument owner through RSpace notifications, while actor self-actions are excluded.
- [ ] Both preferences default ON and can be independently turned OFF from the existing profile notification settings; OFF suppresses internal notifications and the resulting email.
- [ ] Ordinary booking edits, reschedules, and already-cancelled idempotent updates do not notify.
- [ ] Dashboard and email notification labels resolve through catalogs; no user-facing Java string is left hard-coded.
- [ ] Focused unit/integration tests, `pnpm tsc`, applicable frontend tests/lint, and `git diff --check` pass.
- [ ] `git status --short` shows no modified files outside the agreed in-scope paths and `plans/README.md` has this plan’s status updated.

## STOP conditions

Stop and report back if:

- The live code cannot represent a booking notification with a safe `BaseRecord`/system notification target and readable dashboard/email semantics.
- Instrument ownership is not available transactionally or is ambiguous between multiple owner concepts.
- Notification or preference enum persistence uses ordinals in a way that makes appending unsafe, or a schema migration is required but not specified by the product owner.
- Product requirements change from global per-user switches to per-instrument opt-outs.
- A notification must be sent after commit to avoid a transaction/session issue and no safe transactional communication API exists; do not move the call into `AFTER_COMMIT` without resolving persistence semantics.
- A verification command fails twice after a reasonable fix attempt, or the failure is caused by unavailable MariaDB/environment rather than the change.
- Implementing the plan requires changing calendar subscriptions, booking permissions, or files listed as out of scope.

## Maintenance notes

- Any new booking lifecycle transition must explicitly decide whether it is a notification event; do not infer it from `AuditAction.WRITE` because that action also represents ordinary edits.
- Keep the owner lookup based on current instrument ownership and preserve actor exclusion and `User.wantsNotificationFor` filtering when refactoring communication dispatch.
- If notification types or preference enum values are renamed, update the exhaustive message switch, English catalog, profile ordering, email rendering tests, and any persisted preference compatibility logic together.
- A later per-instrument preference feature should introduce a separate model/API/UI and migration rather than overloading these global enum preferences.
