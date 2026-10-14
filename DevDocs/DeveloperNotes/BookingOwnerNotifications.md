# Booking notifications

My Profile notification settings contain two independent switches for booking creation
and cancellation. Both default to enabled and apply to all instruments the user subscribes to.
They use `NOTIFICATION_BOOKING_CREATED_PREF` and
`NOTIFICATION_BOOKING_CANCELLED_PREF` in the existing user preference system.

`BookingNotificationService` notifies enabled subscribers with current instrument read access
after a booking is saved or cancelled, including archive-triggered cancellations. Maintenance,
ordinary edits, repeated cancellations, deleted instruments, and the recipient's own actions
do not generate notifications. Owners can subscribe automatically; other readers opt in.
See [Booking notification subscriptions](BookingNotificationSubscriptions.md) for eligibility,
selection timing, and per-user settings.

Bookings and instruments are not `BaseRecord` objects, so these communications have no
record link. Their localized message identifies the booking and instrument and shows the booked
interval in a human-readable recipient timezone. The notification's existing JSON data column
stores the booking ID, instrument identity, and UTC instants so the dashboard can render the
current recipient's display preference each time the list is opened. Browser mode uses the
recipient's session timezone and falls back to the institution zone when it is missing. The stored
message used by email follows Custom or Institution mode; Browser mode falls back to Institution
because email delivery has no browser session. The time offset appears with the zone so repeated
local times at a DST transition remain distinct. Email delivery also respects
`BROADCAST_NOTIFICATIONS_BY_EMAIL`.

Older notification rows have no structured data. The dashboard reformats their final ISO interval
only when both trailing values match the booking message shape. Existing rows remain readable and
need no migration.

Notification rows are saved in the triggering transaction. `CommunicationManagerImpl`
defers broadcast and live notification counts until commit, preventing delivery on
rollback. The callback uses `NotificationPostCommitExecutor` in a separate transaction
so a failed broadcaster can persist the existing `PROCESS_FAILED` notification.

Focused coverage lives in `BookingNotificationServiceTest`, the booking manager tests,
`CommunicationManagerImplTest`, `BookingOwnerNotificationIT`, and
`UserProfileControllerMVCIT`. The latter two need the configured MariaDB.
