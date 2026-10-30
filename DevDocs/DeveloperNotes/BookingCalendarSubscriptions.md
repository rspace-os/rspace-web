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

Ordinary create and rotate use `BookingCalendarCreationTransaction` at `READ_COMMITTED`.
Item writes compare the credential fingerprint under the configuration lock; user writes
compare the version under the user lock. A create waiting on either lock sees the first
committed link and returns it. Item writes authorize current inherited Inventory access.
The permission gate, lock order and mixed source-transaction rules are in
[Booking permissions](BookingPermissions.md).

MariaDB supports these operations with `innodb_snapshot_isolation` either off or
on. With it on, an old repeatable-read snapshot can reject a current locking
read with error 1020 and roll back the source transaction. Tests specifically
covering that database error enable it for their transaction and restore the
connection's previous session setting after completion.

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
subscriptions, so keep it outside ordinary feed reads.

A user's own bookings remain readable after losing item access. Personal feeds
use "Unknown item" for these events and omit item details and links. Item feeds
and one-time downloads still require current item read permission.

A personal feed contains the user's requested bookings and the blockouts they created.
