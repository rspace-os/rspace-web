# Booking notification subscriptions

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

Delivery also requires the corresponding global My Profile event preference. Creation and
cancellation are independently filtered. The existing email preference controls email only.
Self-actions, maintenance and rescheduling do not notify. Notification persistence remains
inside the booking transaction and external delivery remains after commit.

## API

All routes below start with `/api/v2`. The authenticated caller's effective subject owns
the setting; request bodies never accept a user ID.

| Method | Route | Payload / result |
| --- | --- | --- |
| GET / PUT | `/users/me/booking-notification-preferences` | `{autoSubscribeOwnedItems: boolean}` |
| GET | `/booking-configurations/{id}/notification-subscription` | Saved subscription, version and effective event delivery |
| PUT | Same | `{enabled: boolean, version: number}`; stale version returns 409 |
| POST | `/users/me/booking-notification-subscriptions/lookup` | `{configurationIds: number[]}`; current-user subscription documents |
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
restores the saved choice without a request. Help text links to My Profile and explains
that global booking event preferences also gate delivery, while its email preference
controls email only. No delivery-status summary is displayed.
Both radio controls use the shared `RenderFields` select
field with `form.widget: "radio"`. The existing `select` and `card` widgets remain available.
All bookable items uses the production table's row selection for bulk subscribe/unsubscribe;
it does not contain a notification switch on each row.

All readable instruments offer the same personal controls. There is no access-level or
notification column, per-row toggle, or access-lost panel.

## Delivery and access changes

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

## Notification times

Booking notifications retain their original booking details in the existing notification
JSON field. The dashboard and notification dialog format these details using the recipient's
current Booking display preferences. Browser mode uses the timezone captured at login;
custom and institution modes use their configured zones. Each time includes its UTC offset
so repeated daylight-saving hours remain distinguishable. Older booking messages with the
original ISO interval are also formatted at display time.

The saved message used by email is human-readable in the recipient's configured timezone.
Browser mode falls back to the institution timezone because email has no browser session.
Booking message text is decoded once as text in the legacy notification UI, preserving
literal instrument names without interpreting them as HTML.

## Verification

Run the database integration tests against a separate test database, never the live
development database. `BookingNotificationSubscriptionLifecycleIT` inherits the shared
`DatabaseCleanerLifecycle`, whose cleanup deletes records across the database.
`environment=keepdbintact` controls schema initialization; it does not disable that cleanup.
