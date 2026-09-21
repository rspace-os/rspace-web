# Booking calendar subscriptions

Item subscriptions (`/api/v2/booking-configurations/{id}/calendar-subscription`)
and user subscriptions (`/api/v2/users/me/booking-calendar-subscription`) return
an `ETag` on GET and POST. Clients must send the last received ETag as `If-Match`
when creating or rotating a subscription. Missing preconditions return 428;
stale preconditions return 409. On conflict, reload the status before retrying
or using its current URL. This prevents duplicate requests from invalidating a
URL that another request just returned successfully.

Item writes compare the credential fingerprint while holding the configuration
lock; user writes compare the version while holding the user lock. Token storage
and explicit revocation behavior are unchanged.

Calendar feed responses use `Cache-Control: private, no-store`,
`Referrer-Policy: no-referrer`, and `X-Content-Type-Options: nosniff`, including
empty error responses.

Item subscriptions use the target Inventory item's current permissions. Sharing,
ownership, user status, group and community changes revalidate subscriptions
inside the changing transaction. Revoked item links are deleted, so restoring
access requires a new subscription. Group/community changes revalidate existing
item subscriptions in batches per subscriber because they can also change
access through an item's owner. This work grows with the number of item
subscriptions; keep it outside ordinary feed reads.

Creation and rotation load a fresh subscriber in a `READ_COMMITTED` transaction,
then lock the target and configuration before acquiring the shared Booking
permission fence. Inventory permission-change listeners use the same
target/configuration-to-fence order before scanning subscriptions with locking
reads. User, group and community changes take their authoritative mutation locks
before the fence. Thus a link cannot be created from an old membership snapshot
after a revocation scan has completed. See [Booking permissions](BookingPermissions.md)
for the fence's connection, contention and transaction requirements.

A user's own bookings remain readable after losing item access. Personal feeds
use "Unknown item" for these events and omit item details and links. Item feeds
and one-time downloads still require current item read permission.
