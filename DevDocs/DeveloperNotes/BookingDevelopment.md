# Booking development

## Database migrations

Follow [Database change guidelines](../../src/main/resources/sqlUpdates/DatabaseChangeGuidelines.md)
for changesets, contexts and fresh/upgrade validation. Add new changesets rather
than editing an applied changeset.

The [master changelog](../../src/main/resources/sqlUpdates/liquibase-master.xml)
creates each Booking table and its Envers history once, in dependency order, with
its final columns. `openingHours` stores independent weekday intervals directly.
The resource-access tables precede Booking configuration creation. Keep new
includes after their dependencies. `recurring-changeLog.xml` follows the
application changesets, and `customUpdates-changeLog.xml` remains the final
include.

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

The same changeset then runs
[`booking-dev-seed-edge-cases.sql`](../../src/main/resources/sqlUpdates/booking-dev-seed-edge-cases.sql),
which turns seven configurations into scheduling edge cases and adds four events
on closed days, across two all-day weekdays and ending at closing time. See it
for the fixture values. Both SQL files bypass Envers and write no audit
revisions.
