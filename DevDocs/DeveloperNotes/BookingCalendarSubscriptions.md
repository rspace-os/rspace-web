# Booking calendar subscriptions

A calendar link is created once and kept until its owner rotates or disconnects it.
The caller’s item calendar links are returned as `{docs: [...]}`.
The owner can always read it again because every subscription row stores its raw
token (`rawToken` is `NOT NULL`), and GET returns the current URL.

Item subscriptions (`/api/v2/booking-configurations/{id}/calendar-subscription`)
and user subscriptions (`/api/v2/users/me/booking-calendar-subscription`) share
one contract:

- `GET` returns the status, the current URL and an `ETag`.
- `POST` only creates. It needs no precondition and is safe to repeat: it returns
  `201` with a new URL when none exists, or `200` with the existing URL
  unchanged. Duplicate or concurrent creates cannot fail or invalidate
  a URL another request just returned.
- `POST …/rotate` is the only way to replace a URL. It requires the last
  received ETag as `If-Match`: missing preconditions return 428, and a stale
  ETag or a missing subscription returns 412. On conflict, reload the status and
  show its current URL.
- `DELETE` revokes the link.

For proxy-modified ETags and client preconditions, see
[Conditional requests](RestApiV2QueriesAndAccess.md#conditional-requests).

`GET /api/v2/users/me/bookable-item-calendar-subscriptions` lists the caller's
item links, with URLs, for active items they can still read. The Booking
preferences page shows only the user-wide link; item links are managed from
each bookable item's page.

Item writes compare the credential fingerprint under the configuration lock; user writes
compare the version under the user lock. A create waiting on either lock sees the first
committed link and returns it.

Item creation and rotation run in a fresh `READ_COMMITTED` transaction. The
transaction loads the current subscriber, locks the configuration, checks the
persisted Booking access list, and then reads and compares the subscription.
This prevents a caller's older repeatable-read snapshot from replacing a token
after another request has already rotated it.

Calendar feed responses use `Cache-Control: private, no-store`,
`Referrer-Policy: no-referrer`, and `X-Content-Type-Options: nosniff`, including
empty error responses.

## Access and feed contents

A bookable-item feed is available only while its owner has effective permission to view the
configuration. Losing that final permission ends the subscription; restoring access does not revive
it. A personal feed contains the user's requested bookings and the blockouts they created.
