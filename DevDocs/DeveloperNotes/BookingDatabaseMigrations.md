# Booking stack database migrations

`stack/booking/00-database-migrations` contains one commit for the non-booking
changes, including generic resource-access tables and their audit history. The
branches through `10-access-editor` inherit these changes without booking tables
or development seeds.

`stack/booking/10-booking-database-migrations` contains one commit for the booking
schema and development-only seeds. It follows `10-access-editor` and precedes
`11-booking-domain`. It adds the booking changesets to the shared resource-access
changelog and restores the complete master include order.

Changeset paths, IDs, authors, contexts and contents remain unchanged. An upgrade
from the non-booking prefix skips its already-applied generic changesets. A fresh
installation of the full stack uses the original complete migration order.

The migrations already present on `origin/main` remain unchanged. Follow
`src/main/resources/sqlUpdates/DatabaseChangeGuidelines.md` for subsequent schema
changes: add new changesets rather than editing an applied changeset.
