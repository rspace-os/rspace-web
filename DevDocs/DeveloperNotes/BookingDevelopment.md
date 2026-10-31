# Booking development

## Database migrations

Follow [Database change guidelines](../../src/main/resources/sqlUpdates/DatabaseChangeGuidelines.md)
for changesets, contexts and fresh/upgrade validation. Add new changesets rather
than editing an applied changeset.

The [master changelog](../../src/main/resources/sqlUpdates/liquibase-master.xml)
includes the Booking schema in dependency order. Booking tables precede the
related resource-access and state changesets. Keep new includes after their
dependencies. `recurring-changeLog.xml` runs after the application changesets and
`customUpdates-changeLog.xml` is always the final include.

The additive
[`changeLog-booking-independent-hours.xml`](../../src/main/resources/sqlUpdates/changeLog-booking-independent-hours.xml)
runs after both legacy Booking seed includes and before the recurring and custom
updates. It stores each previously open weekday's effective interval in
`openingHours`, using that row's weekday override or shared times. Weekdays absent
from the old `openDays` stay closed. The legacy columns remain. The migration
removes obsolete live NOT NULL constraints so unmapped inserts succeed.

Each audit revision uses its own old values. A revision with shared times but no
weekday data expands to seven days. A revision without times or a deletion
revision leaves `openingHours` null. Historical audit snapshot JSON is unchanged.

Deploy application nodes and API clients together. After cutover, the old
schedule columns remain for history but are no longer synchronized. Older
application nodes cannot safely write schedules to the upgraded database.

## Static development seed

[`changeLog-rsdev-booking-dev-seed.xml`](../../src/main/resources/sqlUpdates/changeLog-rsdev-booking-dev-seed.xml)
runs only with the `dev-test` Liquibase context. It marks itself ran when any
Booking configuration already exists, so it populates only a fresh development
database. Its
[`booking-dev-seed.sql`](../../src/main/resources/sqlUpdates/booking-dev-seed.sql)
creates nine instruments, eight Booking configurations and 50 events. One
instrument has no configuration and one configuration permits double booking.

Liquibase calculates event dates from `CURRENT_DATE` when it runs. The seed
creates events three days before that date, on that date and seven days after it
for `sysadmin1`, `user1a`, `user2b` and `user3c`. The instruments belong to `user1a`
and use Inventory's `OWNER_GROUPS` sharing mode. The trusted development-only SQL
also inserts `ResourceAccess` and `ResourceRoleAssignment` rows, including
`BOOKER` grants for the `ALL_USERS` audience, and attributes configuration
creation to sysadmin `-12`. IDs in the `910100001` to `910100009` range keep these
rows separate from generated inventory data.

[`changeLog-rsdev-booking-dev-seed-edge-cases.xml`](../../src/main/resources/sqlUpdates/changeLog-rsdev-booking-dev-seed-edge-cases.xml),
also `dev-test`, then turns seven configurations into scheduling edge cases and
adds four events on closed days, across two all-day weekdays and ending at
closing time. Its precondition requires configurations `910100002` to `910100008`
to still carry the seed's defaults. Otherwise it marks itself ran without
changes, preserving developers' edits. The original seed remains checksummed and
unchanged. Both SQL seeds bypass Envers and write no audit revisions.

See the [edge-case SQL](../../src/main/resources/sqlUpdates/booking-dev-seed-edge-cases.sql)
for the fixture values.
