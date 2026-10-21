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

The additive `changeLog-booking-independent-hours.xml` runs after both legacy
booking seed includes and before `recurring-changeLog.xml` and the final custom
updates. It materializes each previously open weekday's effective interval in
`openingHours`, using that row's weekday override or shared times. Weekdays absent
from the old `openDays` stay closed. The legacy columns remain in place, with
obsolete live NOT NULL constraints removed so unmapped inserts succeed.

Each audit revision uses its own old values. A revision with shared times but no
weekday data expands to seven days; a revision without times or a deletion
revision leaves `openingHours` null. Historical audit snapshot JSON is unchanged.

Deploy application nodes and API clients together. After this cutover, the old
schedule columns are retained for history and are no longer synchronized. Older
application nodes cannot safely write schedules to the upgraded database.

Permanent user deletion removes live booking rows through
`UserDeletionDaoHibernate.table2UserIdColumn`, keyed by `TimeSlotBooking.requester_id`.
Keep that cleanup mapping aligned if the requester column or table changes; booking audit history
is retained.
