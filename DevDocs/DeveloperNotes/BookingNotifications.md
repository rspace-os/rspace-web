# Booking notifications

My Profile's creation and cancellation switches default to enabled and use
`NOTIFICATION_BOOKING_CREATED_PREF` and `NOTIFICATION_BOOKING_CANCELLED_PREF`.
Bookings and instruments are not `BaseRecord` objects; notifications identify them through
their Booking-specific message data.

Booking notifications use a personal subscription keyed by user and Inventory instrument.
Any active user with Booking enabled and read access to the instrument can manage their
own subscription. `canManageNotificationSubscription` follows item read access, including
viewers, bookers and readable administrators. It grants no booking or access-management
rights and never allows editing someone else's saved choice. A non-owner starts OFF until
they explicitly subscribe; reading the setting creates no row.

An owner's automatic-subscribe preference defaults to on. Creating a bookable item snapshots
the actual owner's default; transferring instrument ownership initializes the new owner's
subscription only if that user has no saved choice. Changing the default never rewrites
existing subscriptions. Subscriptions survive archive, restore and replacement of the
booking configuration because they belong to the instrument. Explicit off is persisted.

Subscriber delivery also requires the corresponding global My Profile event preference.
Creation and cancellation are independently filtered. When someone else cancels a booking,
the active requester also receives an in-app notification if Booking is enabled and they
still have item read access, even without a subscription or with cancellation notifications
turned off. A requester who also subscribes receives just one notification.

The My Profile email preference still gates every email, including requester cancellations.
The requester override bypasses only the event preference in notification creation; it never
bypasses `BROADCAST_NOTIFICATIONS_BY_EMAIL` in the email broadcaster.
Subscribers receive notifications about their own booking creations and cancellations by default.
The Booking-only `BOOKING_NOTIFY_OWN_ACTIONS` preference can disable these self-action notifications.
Enabled subscription, matching event preference, active account, Booking access and target read access
still apply; email follows `BROADCAST_NOTIFICATIONS_BY_EMAIL`. A requester cancelling their own
booking is selected only as a subscriber. Archiving your own instrument retains one cancellation
notification per cancelled booking. Maintenance and rescheduling do not notify. Notification persistence remains
inside the booking transaction and external delivery remains after commit.

## API

All routes below start with `/api/v2`. The authenticated caller's effective subject owns
the setting; request bodies never accept a user ID.

| Method | Route | Payload / result |
| --- | --- | --- |
| GET / PUT | `/users/me/booking-notification-preferences` | `{autoSubscribeOwnedItems: boolean, notifyOwnActions: boolean} (both required on PUT)` |
| GET | `/booking-configurations/{id}/notification-subscription` | Saved subscription, version and effective event delivery |
| PUT | Same | `{enabled: boolean, version: number}`; stale version returns 409 |
| POST | `/users/me/booking-notification-subscriptions/lookup` | `{configurationIds: number[]}`; `{docs: [...]}` current-user subscription documents |
| PUT | `/users/me/booking-notification-subscriptions` | `{configurationIds: number[], enabled: boolean}`; explicit bulk choice |
| DELETE | Same | Unsubscribe from all instruments; returns `{updatedCount: number}` |

Item responses contain `configurationId`, `enabled`, `version`, `createdEnabled`,
`cancelledEnabled` and `emailEnabled`. An absent saved row is off and has version `-1`;
reads do not initialize subscriptions from a changing default. Single-item writes use
optimistic concurrency. Batch requests accept at most 100 configuration IDs and are atomic:
a request containing an unauthorized item is rejected. The UI submits the selected
instruments together; it does not silently skip items. Bulk choices intentionally
apply the requested state to the selected instruments without per-row version preconditions.

Unsubscribe-all retains saved off choices and leaves the automatic-subscribe, global event
and email preferences unchanged. It can therefore be followed by automatic subscription to
a newly bookable instrument if the default is still on.

## UI

Booking preferences contains an On/Off radio choice for the automatic default and an
unsubscribe-all action. The item page contains its personal On/Off subscription with Save
and Cancel. Saving shows the same green Saved confirmation as Booking preferences; Cancel
restores the saved choice without a request. A failed save preserves the edited choices so
the user can retry. Help text links to My Profile and explains
that global booking event preferences also gate delivery, while its email preference
controls email only. No delivery-status summary is displayed.
Both radio controls use the shared `RenderFields` select
field with `form.widget: "radio"`. The existing `select` and `card` widgets remain available.
All bookable items uses the production table's row selection for bulk subscribe/unsubscribe;
it does not contain a notification switch on each row. The selection survives paging, so rows
from several pages act together; a date, filter or reset change clears it. The same selection
offers a direct sysadmin Administration's bulk Disable and Archive (not Enable, since the list
holds only enabled items), and each row offers the Administration lifecycle menu its role allows.

All readable instruments offer the same personal controls. There is no access-level or
notification column, per-row toggle, or access-lost panel.

## Delivery and access changes

`BookingNotificationService` notifies eligible subscribers after creation and cancellation,
including archive-triggered cancellations. Maintenance, ordinary edits, repeated cancellations,
deleted instruments do not generate notifications. Self-actions follow `notifyOwnActions`.

Enabled subscribers are selected in one read-only `REQUIRES_NEW`, `REPEATABLE_READ`
transaction. The first consistent SELECT establishes eligibility for that event. Preference
and permission reads bypass application preference caches and Hibernate's second-level cache.
The selected recipient's initialized event/email preferences and resolved timezone travel
with the notification into the outer booking transaction and after-commit delivery.

Changes committed before selection apply to that event. Access revocation, unsubscribe,
or global preference changes after the snapshot can still allow the in-flight notification;
subsequent selections see the change. Saved choices remain dormant while access is absent
and resume when access returns. An explicit OFF is never reset by ordinary sharing or restore.
Former owners with saved ON choices continue receiving updates if they retain read access.
This also applies to existing saved rows at rollout; no reader subscriptions are backfilled.

Subscription writes recheck committed access after acquiring the existing instrument locks.
Unsubscribe-all can still disable dormant rows. Notifications and targets persist in the
booking transaction; a rollback produces no emails or live count updates.

Notification rows are saved in the triggering transaction. `CommunicationManagerImpl`
defers broadcast and live notification counts until commit, preventing delivery on
rollback. The callback uses `NotificationPostCommitExecutor` in a separate transaction
so a failed broadcaster can persist the existing `PROCESS_FAILED` notification.

## Notification times

Booking notifications keep their booking ID, instrument identity, and UTC instants in the existing
JSON data field. The dashboard and notification dialog format these details using the recipient's
current display preferences. Browser mode uses the timezone captured at login, falling back to the
institution zone when the session timezone is missing or unusable. Custom and Institution modes use
their configured zones. Each time includes its UTC offset so repeated daylight-saving hours remain
distinguishable.

Dashboard messages keep the app language's words and date order. With Automatic, the clock follows
the first usable locale in `Accept-Language` preference order. Language-only locales such as `en` are
accepted; zero-weight and wildcard entries are ignored. When the header is missing, invalid, or has
no usable locale, the clock follows the app language. `LocaleFilter` replaces the request locale with
the app language, so the dashboard reads the clock locale from `Accept-Language`. ICU applies the
Unicode `hc` clock cycle. An explicit H12 or H24 Booking preference overrides that clock.
`BookingNotificationMessageFormatter` receives the choice as a `BookingTimeFormat` from
`DashboardController` or the `BookingNotificationRecipient` snapshot for email. It maps the
explicit choices to ICU `h12` or `h23`, using `h23` so midnight is 00:00.

Email has no browser region. Browser timezone mode falls back to the institution zone; Custom and
Institution modes use their configured zones. With Automatic, the email clock follows the app
language; an explicit H12 or H24 preference overrides that clock. The stored email message remains
human-readable in the recipient's configured timezone.

Older rows have no structured data. The dashboard reformats the final ISO interval only when both
trailing values match the booking message shape. Existing rows remain readable without migration.
The legacy notification UI decodes the booking message once as text, preserving literal instrument
names without interpreting them as HTML. Email delivery also respects
`BROADCAST_NOTIFICATIONS_BY_EMAIL`.

## Verification

Run the database integration tests against a separate test database, never the live
development database. `BookingNotificationSubscriptionLifecycleIT` inherits the shared
`DatabaseCleanerLifecycle`, whose cleanup deletes records across the database.
`environment=keepdbintact` controls schema initialization; it does not disable that cleanup.

Focused coverage lives in `BookingNotificationServiceTest`, the booking manager tests,
`CommunicationManagerImplTest`, `BookingOwnerNotificationIT`, and
`UserProfileControllerMVCIT`. The latter two need the configured MariaDB.

The Booking preferences switch “Also notify me about bookings I create or cancel myself”
uses the notification section’s Save and Cancel controls and Saved status. It applies only
to subscribed instruments and preserves the created and cancelled event preferences.
