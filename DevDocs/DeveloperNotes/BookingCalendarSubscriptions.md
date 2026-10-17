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

Item creation and rotation run in a fresh `READ_COMMITTED` transaction. The
transaction loads the current subscriber, locks the configuration, checks the
persisted Booking access list, and then reads and compares the subscription.
This prevents a caller's older repeatable-read snapshot from replacing a token
after another request has already rotated it.

Calendar feed responses use `Cache-Control: private, no-store`,
`Referrer-Policy: no-referrer`, and `X-Content-Type-Options: nosniff`, including
empty error responses.

## Access and feed contents

Item subscriptions use the target Inventory item's current permissions. Sharing,
ownership, user status, group and community changes revalidate subscriptions
inside the changing transaction. Revoked item links are deleted, so restoring
access requires a new subscription. Group/community changes revalidate existing
item subscriptions in batches per subscriber because they can also change
access through an item's owner. This work grows with the number of item
subscriptions; keep it outside ordinary feed reads.

Ordinary item creation and rotation use a `READ_COMMITTED` transaction and take
the shared Booking permission gate before target, configuration and subscription
locks. If a permission-source transaction also creates or rotates an item link,
the operation stays in that transaction under its exclusive gate and reads
current scalar account, role, membership, sharing and token rows. User-wide
link creation and rotation keep their separate transaction and are rejected
beneath an active or suspended permission gate. See
[Booking permissions](BookingPermissions.md) for the writer and lock-order
requirements.

A user's own bookings remain readable after losing item access. Personal feeds
use "Unknown item" for these events and omit item details and links. Item feeds
and one-time downloads still require current item read permission.

A personal feed contains the user's requested bookings and the blockouts they created.
