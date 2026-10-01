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
