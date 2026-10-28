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

The original and edge-case SQL seeds run only in `dev-test`, skipping existing or edited fixtures.
They use reserved IDs and bypass Envers, so they create no audit revisions. See their
[original changelog](../../src/main/resources/sqlUpdates/changeLog-rsdev-booking-dev-seed.xml) and
[edge-case changelog](../../src/main/resources/sqlUpdates/changeLog-rsdev-booking-dev-seed-edge-cases.xml)
for execution guards, and their [original SQL](../../src/main/resources/sqlUpdates/booking-dev-seed.sql)
and [edge-case SQL](../../src/main/resources/sqlUpdates/booking-dev-seed-edge-cases.sql) for fixture values.
The legacy `ResourceAccess` and role rows in the static SQL seed do not grant current Booking access.

## Service-created development fixtures

Booking development data comes from
`BookingFixturesAppInitialiser`, registered in `TestAppConfig` and `ProductionConfig`.
It runs only when `deployment.test.fb.instance=true` and `GlobalInitManager`
identifies the first deployment. The property defaults to `false` and supports
`run`, `prod`, and `prod-test`, including AWS feature-branch deployments using
`prod`. Set it in the instance's external `deployment.properties` or as the JVM
argument `-Ddeployment.test.fb.instance=true`. The fixture users must already
exist with their initial fixture credentials. After checking all required users,
the fixture initializes missing user content and creates only the lab-group
relationship required for `user1a` to be PI of `user2b`, if that relationship is
absent. It does not run the broader developer group setup in production. Existing databases are not reseeded
on restart, even with the flag enabled. After the outer startup transaction
completes successfully and its booking state is cleared, it creates inventory
and booking data in two new transactions. Rollback does not schedule fixtures.
A fixture failure is logged but does not stop startup, and later restarts do not
retry it, so rebuild the database if seeding fails part-way.

`BookingFixturesProductionPermissionsIT` exercises the real production-profile
startup and its viewer permissions. Run it only against a fresh, isolated database
with `liquibase.context=run,dev-test` and `-Drspace.booking.fixture.integration=true`.
The test retains that database rather than running the shared cleanup that removes
seed data.

Sources: [`TestAppConfig`](../../src/main/java/com/axiope/service/cfg/TestAppConfig.java),
[`ProductionConfig`](../../src/main/java/com/axiope/service/cfg/ProductionConfig.java),
[`BookingFixturesAppInitialiser`](../../src/main/java/com/researchspace/service/impl/BookingFixturesAppInitialiser.java),
[`BookingFixturesProductionPermissionsIT`](../../src/test/java/com/researchspace/service/impl/BookingFixturesProductionPermissionsIT.java).

The initializer is idempotent. It finds fixture users, instruments, and
containers by stable names plus the fixture description, creates missing
objects through inventory managers, creates or updates booking configurations
through `BookingConfigurationManager`, and creates bookings through
`TimeSlotBookingManager`. Booking dates are calculated relative to the current
date at first deployment. The bulk dataset contains 500 instruments and 1,003
events across the next full Monday-to-Sunday week. Three events are named
Aurora, Beacon, and Comet to test Calendar event-name search.

See the annotated fixture catalogue in
[`BookingFixturesAppInitialiser`](../../src/main/java/com/researchspace/service/impl/BookingFixturesAppInitialiser.java)
for the smaller access and scheduling examples. They use the next calendar date, so new bookings
start in the future even if the stack starts late in the day. Bookings that depend on an open
weekday use the first open day on or after the fixture date, because that date floats.

See the [fixture initializer](../../src/main/java/com/researchspace/service/impl/BookingFixturesAppInitialiser.java)
and [Docker quick start](../../docker/dev/README.md#quick-start) for setup.

New service-created configurations inherit current Inventory access. See
[Booking permissions](BookingPermissions.md) for authorization behavior.

Keep service-created fixtures for data that depends on ownership, sharing,
validation or audit behavior. Raw SQL has to supply those effects itself.

## Catalogue search and SQL fixtures

The static SQL instruments have no parent location because the seed inserts no
`ContainerLocation` rows. Service-created Inventory instruments normally start
on their owner's workbench, although the Booking fixture also creates a
parentless example.

Booking catalogue text search uses database `LIKE` queries rather than a search
index. Its name, description and readable parent-name matching lives in
[`BookingInstrumentQueryDaoHibernate.searchBookingCatalogueTargetIds`](../../src/main/java/com/researchspace/dao/hibernate/BookingInstrumentQueryDaoHibernate.java).
SQL-inserted data therefore does not require reindexing for that search path.
When changing its joins, check parentless instruments as well as located ones.
Path expressions through a parent can introduce implicit inner joins that
exclude parentless rows. The existing
[`InstrumentBookingCatalogueSearchTest`](../../src/test/java/com/researchspace/dao/InstrumentBookingCatalogueSearchTest.java)
checks literal `LIKE` characters.

Inventory's own search uses Hibernate Search instead. It sees SQL-inserted rows
only after reindexing. `rs.indexOnstartup=true` is the deployment default. The
Docker stack disables it unless `RSPACE_INDEX_ON_STARTUP=true`.

## Permanent user deletion

Permanent user deletion removes live Booking rows through
[`UserDeletionDaoHibernate.table2UserIdColumn`](../../src/main/java/com/researchspace/dao/hibernate/UserDeletionDaoHibernate.java),
keyed by `TimeSlotBooking.requester_id`. Keep that cleanup mapping aligned if the
requester column or table changes. Booking audit history is retained.
