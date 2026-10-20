# Booking notifications

My Profile notification settings contain two independent switches for booking creation
and cancellation. Both default to enabled and apply to all instruments the user subscribes to.
They use `NOTIFICATION_BOOKING_CREATED_PREF` and
`NOTIFICATION_BOOKING_CANCELLED_PREF` in the existing user preference system.

`BookingNotificationService` notifies enabled subscribers with current instrument read access
after a booking is saved or cancelled, including archive-triggered cancellations. Maintenance,
ordinary edits, repeated cancellations, deleted instruments, and the recipient's own actions
do not generate notifications. Restoring a cancelled booking uses the creation type, and so the
creation switch, with a "was restored" message (`BookingNotificationData.restored`). Owners can subscribe automatically; other readers opt in.
See [Booking notification subscriptions](BookingNotificationSubscriptions.md) for eligibility,
selection timing, and per-user settings.

Bookings and instruments are not `BaseRecord` objects, so these communications have no
record link. Their localized message identifies the booking and instrument. See
[Notification times](BookingNotificationSubscriptions.md#notification-times) for stored time data,
timezone, locale, and legacy-message behavior.

Notification rows are saved in the triggering transaction. `CommunicationManagerImpl`
defers broadcast and live notification counts until commit, preventing delivery on
rollback. The callback uses `NotificationPostCommitExecutor` in a separate transaction
so a failed broadcaster can persist the existing `PROCESS_FAILED` notification.

Focused coverage lives in `BookingNotificationServiceTest`, the booking manager tests,
`CommunicationManagerImplTest`, `BookingOwnerNotificationIT`, and
`UserProfileControllerMVCIT`. The latter two need the configured MariaDB.
