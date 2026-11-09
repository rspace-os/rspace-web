# Booking notifications

My Profile notification settings contain two independent switches for booking creation or time changes
and cancellation. Both default to enabled and apply to all instruments the user owns.
They use `NOTIFICATION_BOOKING_CREATED_PREF` and
`NOTIFICATION_BOOKING_CANCELLED_PREF` in the existing user preference system.

`BookingNotificationService` resolves the instrument's current owner inside the booking
transaction. It notifies that owner after a confirmed booking is saved, rescheduled or cancelled,
including cancellations caused by archiving a booking configuration. Maintenance,
purpose-only edits, repeated cancellations, deleted instruments, and actions by the owner
do not generate owner notifications.

Bookings and instruments are not `BaseRecord` objects, so these communications have no
record link. Their localized message identifies the booking and instrument and includes
the booked interval in UTC. Dashboard and email templates display that message.
Booking email subjects name the event and instrument. The header uses the owner display time zone
captured when the notification is created. Browser mode uses the institution time zone because
email has no browser context. The RESTORED enum slot is reserved before CHANGED for persisted
ordinal compatibility; restore delivery is introduced separately.

Email delivery also respects `BROADCAST_NOTIFICATIONS_BY_EMAIL`.

Notification rows are saved in the triggering transaction. `CommunicationManagerImpl`
defers broadcast and live notification counts until commit, preventing delivery on
rollback. The callback uses `NotificationPostCommitExecutor` in a separate transaction
so a failed broadcaster can persist the existing `PROCESS_FAILED` notification.

Focused coverage lives in `BookingNotificationServiceTest`, the booking manager tests,
`CommunicationManagerImplTest`, `BookingOwnerNotificationIT`, and
`UserProfileControllerMVCIT`. The latter two need the configured MariaDB.
