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

Item create and rotate read current inherited Inventory permissions in
`BookingCalendarCreationTransaction` at `READ_COMMITTED`. For the permission gate,
writer registration and mixed source-transaction requirements, see
[Booking permissions](BookingPermissions.md).

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

A user's own bookings remain readable after losing item access. Personal feeds
use "Unknown item" for these events and omit item details and links. Item feeds
and one-time downloads still require current item read permission.

A personal feed contains the user's requested bookings and the blockouts they created.
