# Booking calendar subscriptions

A calendar link is created once and kept until its owner rotates or disconnects
it. The owner can always read it again: every subscription row stores its raw
token (`rawToken` is `NOT NULL`), and GET returns the current URL.

Item subscriptions (`/api/v2/booking-configurations/{id}/calendar-subscription`)
and user subscriptions (`/api/v2/users/me/booking-calendar-subscription`) share
one contract:

- `GET` returns the status, the current URL and an `ETag`.
- `POST` only creates. It needs no precondition and is safe to repeat: it returns
  `201` with a new URL when none exists, or `200` with the existing URL
  unchanged. Duplicate or concurrent creates therefore cannot fail or invalidate
  a URL another request just returned.
- `POST …/rotate` is the only way to replace a URL. It requires the last
  received ETag as `If-Match`: missing preconditions return 428, and a stale
  ETag or a missing subscription returns 409. On conflict, reload the status and
  show its current URL.
- `DELETE` revokes the link.

Compressing proxies change ETags in transit. Apache `mod_deflate` appends
`-gzip` (or `-br`, `-deflate`) inside the quotes by default
(`DeflateAlterETag AddSuffix`), so the UI receives `"subscription-42-1-gzip"` and
echoes it in `If-Match`; other proxies weaken tags to `W/"…"`.
`ApiV2ConditionalRequest` removes one such suffix and the `W/` prefix before it
compares a subscription ETag (`parseStrongEtag`) or parses a version ETag such
as `"0-gzip"` (`parseVersion`, used by configuration `PATCH`/`DELETE` and
resource access), and returns the tag the server issued. Tolerating a weak tag
in `If-Match` is deliberate: RFC 9110 says weak tags never match there, but the
weakening is the proxy's, not a different representation. The server's own
tags never end in those suffixes (item tags are SHA-256 hex, user tags are
`"subscription-<id>-<version>"`). A deployment can also stop the rewrite with
`DeflateAlterETag NoChange`.

`GET /api/v2/users/me/bookable-item-calendar-subscriptions` lists the caller's
item links, with URLs, for active items they can still read. The Booking
preferences page shows only the user-wide link; item links are managed from
each bookable item's page.

Create and rotate run in `BookingCalendarCreationTransaction` (`READ_COMMITTED`).
Item writes compare the credential fingerprint while holding the configuration
lock; user writes compare the version while holding the user lock. Because the
isolation level is `READ_COMMITTED`, a create that waited on either lock sees the
link the first request committed and returns it instead of inserting a second
row.

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
