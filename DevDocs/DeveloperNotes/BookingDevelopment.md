# Booking development

## Database migrations

Follow [Database change guidelines](../../src/main/resources/sqlUpdates/DatabaseChangeGuidelines.md)
for changesets, contexts and fresh/upgrade validation. Add new changesets rather
than editing an applied changeset.

The [master changelog](../../src/main/resources/sqlUpdates/liquibase-master.xml)
creates each Booking table and its Envers history once, in dependency order, with
its final settings, display, lifecycle and event columns. `openingHours` stores
independent weekday intervals directly. Increment units default to `MINUTES` and
the singleton starts with a fifteen-minute increment.

The resource-access tables precede Booking configuration creation. The separate
[`changeLog-booking-drop-resource-access.xml`](../../src/main/resources/sqlUpdates/changeLog-booking-drop-resource-access.xml)
removes the configuration ACL column after Booking adopts inherited Inventory
access. `recurring-changeLog.xml` follows the application changesets, and
`customUpdates-changeLog.xml` remains the final include.

Two changesets explicitly mark pre-stack schema changes. The custom-field index
changelog drops the redundant `InventoryEntityField.templateField_id` index after
its composite replacement exists. The notification changelog expands
`Notification.notificationMessage` to `TEXT` for complete cancellation messages.
The nullable ScheduledMaintenance cutoff remains beside the API mutation work.

## Static development seed

The original and edge-case SQL seeds run only in `dev-test`, skipping existing or edited fixtures.
They use reserved IDs and bypass Envers, so they create no audit revisions. See their
[original changelog](../../src/main/resources/sqlUpdates/changeLog-rsdev-booking-dev-seed.xml) and
[edge-case changelog](../../src/main/resources/sqlUpdates/changeLog-rsdev-booking-dev-seed-edge-cases.xml)
for execution guards, and their [original SQL](../../src/main/resources/sqlUpdates/booking-dev-seed.sql)
and [edge-case SQL](../../src/main/resources/sqlUpdates/booking-dev-seed-edge-cases.sql) for fixture values.
The seed creates no independent Booking ACLs. It includes one-day increments and
off-grid fifteen-minute starts, and skips occupied fixture IDs.

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

## Rebuilding development databases

The Booking stack has not been deployed. Its changelogs create the final schema
without historical checksums, increment-schema detection or shared-hours
backfills. Rebuild development databases from the baseline after updating this
stack; do not reuse a database with the earlier Booking changesets applied.

The original seed creates its fifteen-minute, one-day and off-grid examples in
one guarded operation. It skips databases with existing configurations or
occupied fixture IDs. The edge-case seed runs only while its seven items retain
their initial schedule, increment and configuration version.
