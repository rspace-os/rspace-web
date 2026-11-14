# Booking notifications

My Profile's creation and cancellation switches default to enabled and use
`NOTIFICATION_BOOKING_CREATED_PREF` and `NOTIFICATION_BOOKING_CANCELLED_PREF`.
Bookings and instruments are not `BaseRecord` objects; notifications identify them through
their Booking-specific message data.

Booking notifications use a personal subscription keyed by user and Inventory instrument.
Any active user with Booking enabled and read access to the instrument can manage their
own subscription. `canManageNotificationSubscription` follows item read access, including
viewers, bookers and readable administrators. It grants no booking or access-management
rights or permission to edit someone else's saved choice. A non-owner starts OFF until
they explicitly subscribe; reading the setting creates no row.

An owner's automatic-subscribe preference defaults to on. Creating a bookable item snapshots
the actual owner's default; transferring instrument ownership initializes the new owner's
subscription only if that user has no saved choice. Changing the default never rewrites
existing subscriptions. Subscriptions survive archive, restore and replacement of the
booking configuration because they belong to the instrument. Explicit off is persisted.

Subscriber delivery also requires the corresponding global My Profile event preference.
Creation and time changes share one event filter; cancellation has a separate filter. When someone else cancels or reschedules a booking,
the active requester also receives an in-app notification if Booking is enabled and they
still have item read access, even without a subscription or with cancellation notifications
turned off. A subscribed requester receives only one notification.

The My Profile email preference still gates every email, including requester cancellations and time changes.
The requester override bypasses only the event preference in notification creation; it never
bypasses `BROADCAST_NOTIFICATIONS_BY_EMAIL` in the email broadcaster.
Subscribers receive notifications about their own booking creations, changes and cancellations by default.
Restoring your own cancelled booking follows the same rule and uses the restored message.
The Booking-only `BOOKING_NOTIFY_OWN_ACTIONS` preference can disable these self-action notifications.
Enabled subscription, matching event preference, active account, Booking access and target read access
still apply; email follows `BROADCAST_NOTIFICATIONS_BY_EMAIL`. A requester cancelling their own
booking is selected only as a subscriber. Archiving your own instrument retains one cancellation
notification per cancelled booking. Maintenance and purpose-only edits do not notify. Notification persistence remains
inside the booking transaction and external delivery remains after commit.

## API

All routes below start with `/api/v2`. The authenticated caller's effective subject owns
the setting; request bodies never accept a user ID.

| Method | Route | Payload / result |
| --- | --- | --- |
| GET | `/users/me/booking-notification-preferences` | `{autoSubscribeOwnedItems, notifyOwnActions, notifyOnCreated, notifyOnCancelled, emailDelivery}`, all booleans |
| PUT | Same | `{autoSubscribeOwnedItems, notifyOwnActions, notifyOnCreated, notifyOnCancelled}`; all required; returns the GET shape |
| GET | `/booking-configurations/{id}/notification-subscription` | Saved subscription and effective event delivery; ETag response header |
| PUT | Same | `{enabled: boolean}` with `If-Match`; missing validator returns 428, malformed 400, stale 412 |
| POST | `/users/me/booking-notification-subscriptions/lookup` | `{configurationIds: number[]}`; `{docs: [...]}` current-user subscription documents |
| DELETE | `/users/me/booking-notification-subscriptions` | Unsubscribe from all instruments; returns `{updatedCount: number}` |

`notifyOnCreated` and `notifyOnCancelled` are the same global event preferences that My Profile
edits, so either page can change them. `emailDelivery` is the effective My Profile email preference;
it is read-only here and a PUT containing it is rejected as an unknown field.

Item responses contain `configurationId`, `enabled`, `createdEnabled`,
`cancelledEnabled` and `emailEnabled`. An absent saved row is off;
reads do not initialize subscriptions from a changing default. Single-item writes use
optimistic concurrency through the complete ETag, including effective preference flags. GET and PUT
return `Cache-Control: no-transform`. PUT compares the row under the instrument lock and the flags
from a same-transaction preference snapshot. Preference writes do not take that lock and can race
with PUT, which never changes preferences. Unsubscribe-all conflicts retain 409. Batch lookups
accept at most 100 configuration IDs.
An unauthorized item rejects the whole lookup request.

Unsubscribe-all retains saved off choices and leaves the automatic-subscribe, global event
and email preferences unchanged. If the default remains on, a newly bookable instrument can still trigger
automatic subscription.

## UI

Booking preferences contains an On/Off radio choice for the automatic default and an
unsubscribe-all action. The item page contains its personal On/Off subscription with Save
and Cancel. Saving shows the same green Saved confirmation as Booking preferences; Cancel
restores the saved choice without a request. A failed save retains edits for retry.
Help text links to My Profile and explains
that global booking event preferences also gate delivery, while its email preference
controls email only. No delivery-status summary is displayed.
Both radio controls use the shared `RenderFields` select
field with `form.widget: "radio"`. The existing `select` and `card` widgets remain available.
All bookable items has no row selection for bookers. Only direct sysadmins can select
rows and use bulk **Disable** and **Archive**. Selection survives paging; a date, filter
or reset change clears it. Each row offers the Administration lifecycle menu its role allows.
Personal subscriptions are managed on the item page.

All readable instruments offer the same personal controls. There is no access-level or
notification column, per-row toggle, or access-lost panel.

## Delivery and access changes

`BookingNotificationService` notifies eligible subscribers after creation, time changes and cancellation,
including archive-triggered cancellations. Maintenance, purpose-only edits, repeated cancellations,
deleted instruments do not generate notifications. Self-actions follow `notifyOwnActions`.
Restoring a cancelled booking emits `NOTIFICATION_BOOKING_RESTORED`, with its own label and restored message. It uses the created event preference and the same own-action rules. The reserved RESTORED enum value remains before CHANGED to preserve persisted ordinals.

Enabled subscribers are selected using plain reads on the booking transaction's connection.
Eligibility uses that transaction's snapshot, including feature flags, permissions and preferences.
The subscription-ID query alone carries a read-only hint and creates no user proxies. User and preference queries bypass
Hibernate's second-level cache while keeping those entities writable. Selection batches users,
preferences, permission checks and feature overrides, and reads display defaults once.
Notification rows commit or roll back atomically with the booking.

Access revocation, unsubscribe,
or global preference changes after the snapshot can still allow the in-flight notification;
later transactions see the change. Saved choices remain dormant while access is absent
and resume when access returns. An explicit OFF is never reset by ordinary sharing or restore.
Former owners with saved ON choices continue receiving updates if they retain read access.
This also applies to existing saved rows at rollout; no reader subscriptions are backfilled.

Subscription writes recheck committed access under instrument locks; unsubscribe-all can disable
dormant rows. Notifications and targets persist in the booking transaction; `CommunicationManagerImpl`
broadcasts and updates live counts only after commit. `NotificationPostCommitExecutor` runs in a
separate transaction so broadcaster failure can persist `PROCESS_FAILED`.

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

The dashboard formats structured booking data for the recipient. If structured data is absent
or fails to format, it logs formatting failures and displays the stored message unchanged.
Email delivery also respects `BROADCAST_NOTIFICATIONS_BY_EMAIL`.

Subjects identify the lifecycle event and instrument. Header and body use each recipient's saved zone and clock snapshot. Dashboard and notification dialog messages link to the booking and bookable item. Email links are absolute and use `server.urls.prefix`. The legacy UI preserves booking markup across repeated renders; the JSP undoes only the EL resolver's escaping, so escaped user values remain literal.

Cancellation notifications include the optional reason as HTML-escaped text. The complete formatted
message is stored so a maximum-length reason remains intact in email even when escaping expands it.

Messages are HTML. The booking ID links to `/booking/calendar/bookings/{id}`, and the instrument
name and global ID link to `/booking/bookable-items/{globalId}`. User-controlled values are
HTML-escaped and link identifiers are path-encoded. In-app lists re-format structured data with
root-relative links; the saved message, which is also the email body, uses absolute links built from
`server.urls.prefix`, like the rest of the notification email. The legacy notification UI leaves
booking messages as the browser decoded them, so the escaped instrument name stays literal text and
the links stay clickable.
## Verification

Run the database integration tests against a separate test database, never the live
development database. `BookingNotificationSubscriptionLifecycleIT` inherits the shared
`DatabaseCleanerLifecycle`, whose cleanup deletes records across the database.
`environment=keepdbintact` controls schema initialization; it does not disable that cleanup.

Focused tests are in `BookingNotificationServiceTest`, the booking manager tests,
`CommunicationManagerImplTest`, `BookingOwnerNotificationIT`, and
`UserProfileControllerMVCIT`. The latter two need the configured MariaDB.

RESTORED is reserved before CHANGED for persisted ordinal compatibility and is wired only
when restoration is introduced.

The Booking preferences switch “Also notify me about bookings I create or cancel myself”
uses the notification section’s Save and Cancel controls and Saved status. It applies only
to subscribed instruments and preserves the created and cancelled event preferences.

After commit, live delivery still borrows another connection while Spring retains the request
connection until cleanup. A saturated pool can delay or fail live delivery. The callback logs
failures at WARN and leaves the committed notifications available to polling; later callbacks
continue. An asynchronous executor or transactional outbox would remove this residual risk.

Legacy TABLE identifier allocation also borrows isolated connections while the booking fence is
held. `Communication` allocates one identifier per borrow (`comm_gen`, allocation size 1), so
removing the recipient reader's extra transaction does not remove this existing pool dependency.
The pool regression reserves capacity for identifier allocation and counts those borrows separately
from the booking stack's own reads and default insertion. Changing this identifier strategy needs
a separate migration against `main`.
