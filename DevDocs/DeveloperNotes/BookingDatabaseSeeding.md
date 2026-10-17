# Booking database seeding

## Findings

RSpace applies `classpath:sqlUpdates/liquibase-master.xml` at startup through
`SpringLiquibase`. Context selection, deployment defaults, and append-only checksum rules are in
[DatabaseChangeGuidelines.md](../../src/main/resources/sqlUpdates/DatabaseChangeGuidelines.md).

Sources: [`applicationContext-dao.xml`](../../src/main/resources/applicationContext-dao.xml#L8-L16),
[`defaultDeployment.properties`](../../src/main/resources/deployments/defaultDeployment.properties#L367),
[Liquibase guidelines](../../src/main/resources/sqlUpdates/DatabaseChangeGuidelines.md).

The master changelog includes the booking schema in dependency order. The
booking tables are created before resource-access and state changesets, and the
recurring and custom changelogs run last. A booking seed changeset that depends
on those tables must be a new include after the relevant booking schema files,
without reordering or editing existing includes.

Source: [`liquibase-master.xml`](../../src/main/resources/sqlUpdates/liquibase-master.xml#L67-L92).

The existing baseline seed is a special fresh-install migration. It is guarded
by preconditions and marks itself ran when the database is already populated.
Its `dev-test` and `cloud` seed branches only run while the database changelog
contains no later changesets. This pattern is suitable for one-time bootstrap
data, not for fixtures that must be repaired on every development restart.

Source: [`initial-seed-changeLog.xml`](../../src/main/resources/sqlUpdates/initial-seed-changeLog.xml#L5-L47).

Booking development data currently comes from
`BookingFixturesAppInitialiser`, registered in `TestAppConfig` and `ProductionConfig`.
It runs only when `deployment.test.fb.instance=true` and `GlobalInitManager`
identifies the first deployment. The property defaults to `false` and works with
`run`, `prod`, and `prod-test`, including AWS feature-branch deployments using
`prod`. Set it in the instance's external `deployment.properties` or as the JVM
argument `-Ddeployment.test.fb.instance=true`. The fixture users must already
exist with their initial fixture credentials. After checking all required users,
the fixture initializes missing user content and creates only the lab-group
relationship needed for `user1a` to be PI of `user2b`, if that relationship is
absent. It does not run the broader developer group setup in production. Existing databases are not reseeded
on restart, even with the flag enabled. It waits for the outer startup transaction
to commit, then creates inventory and booking data in two new transactions.

`BookingFixturesProductionPermissionsIT` exercises the real production-profile
startup and its viewer permissions. Run it only against a fresh, isolated database
with `liquibase.context=run,dev-test` and `-Drspace.booking.fixture.integration=true`.
The test retains that database rather than running the shared cleanup that removes
seed data.

Sources: [`TestAppConfig.java`](../../src/main/java/com/axiope/service/cfg/TestAppConfig.java#L55-L63),
[`TestAppConfig.java`](../../src/main/java/com/axiope/service/cfg/TestAppConfig.java#L125-L156),
[`BookingFixturesAppInitialiser.java`](../../src/main/java/com/researchspace/service/impl/BookingFixturesAppInitialiser.java#L49-L117).

The initializer is idempotent. It finds fixture users, instruments, and
containers by stable names plus the fixture description, creates missing
objects through inventory managers, creates or updates booking configurations
through `BookingConfigurationManager`, and creates bookings through
`TimeSlotBookingManager`. Booking dates are calculated relative to the current
date at first deployment. The bulk dataset contains 500 instruments and 1,003
events across the next full Monday-to-Sunday week. Three events have distinctive
names—Aurora, Beacon, and Comet—for testing Calendar event-name search.

The initial six smaller booking examples use the following calendar date, so
new bookings start in the future even when the stack starts late in the day.
Bookings that depend on an open weekday use the first open day on or after the
fixture date, because that date floats. The smaller examples include these
access and scheduling cases:

| # | Fixture | Access or schedule to verify |
|---|---|---|
| 1 | Restricted-location plate reader | `user2b` owns it with owner-only sharing. As PI of `user2b`'s group, `user1a` sees it in Calendar with the Booking viewer role but can neither book it nor edit its configuration. It is closed on Wednesdays and opens only 10:00–14:00 Europe/Berlin on Thursdays, making closure shading visible on a read-only row. |
| 2 | Electron microscope (America/New_York) | Closed at weekends and at noon on Fridays. |
| 3 | Mass spectrometer (UTC) | Closed on Sundays and open all day on Wednesdays despite partial shared hours. It allows double booking and has two overlapping bookings. |
| 4 | No-parent centrifuge | Open all day except Sundays, with a Saturday booking ending at midnight. |
| 5 | Deleted-location sequencer | Open all day on weekdays only, with a booking from Monday to Wednesday. |
| 6 | Auckland PCR cycler (Pacific/Auckland) | Shows day offsets and weekday differences to a European viewer. |
| 7 | Honolulu X-ray diffractometer (Pacific/Honolulu) | Shows day offsets and weekday differences to a European viewer. |
| 8 | Kolkata NMR spectrometer (UTC+05:30) | Uses 15-minute increments and shows day offsets and weekday differences to a European viewer. |
| 9 | Closed-day incubator | Has a booking and a maintenance event on two weekdays closed after the events were created. |

Sources: [`BookingFixturesAppInitialiser.java`](../../src/main/java/com/researchspace/service/impl/BookingFixturesAppInitialiser.java#L119-L220),
[`BookingFixturesAppInitialiser.java`](../../src/main/java/com/researchspace/service/impl/BookingFixturesAppInitialiser.java#L237-L440),
[`BookingFixturesAppInitialiser.java`](../../src/main/java/com/researchspace/service/impl/BookingFixturesAppInitialiser.java#L442-L628),
[`docker/dev/README.md`](../../docker/dev/README.md#quick-start).

## Recommended approach

Follow [DatabaseChangeGuidelines.md](../../src/main/resources/sqlUpdates/DatabaseChangeGuidelines.md)
for changeset registration, contexts, and fresh/upgrade validation. The booking-specific choice between
a permanent static seed and a repeatable development fixture is described below.

Do not convert the full current booking fixture set into raw SQL. It depends on
inventory creation, ownership, resource-access rows, audit behavior, generated
IDs, and booking validation. Direct inserts would need to maintain
`BookingConfiguration`, `TimeSlotBooking`, `ResourceAccess`,
`ResourceRoleAssignment`, inventory tables, and their audit tables in the right
order. The service-based initializer already supplies those side effects and
handles the date-relative, repeatable behavior.

For a small static booking seed, a Liquibase changeset can insert rows after the
booking schema includes. For a repeatable development fixture, keep the
initializer or extract its stable-key logic into a dedicated development
initializer. Add a focused integration test that starts from a clean database,
runs the seed twice, and checks that the second run does not duplicate rows.

## Development booking seed

`changeLog-rsdev-booking-dev-seed.xml` runs only with the `dev-test` Liquibase
context, independently of `deployment.test.fb.instance`. It marks itself ran when
any booking configuration already exists, so it populates only a brand-new
development database. The changeset loads
`booking-dev-seed.sql`, which creates nine instruments, eight booking
configurations, and 50 booking events. One instrument has no booking
configuration and one configuration permits double booking.

The event dates are calculated from `CURRENT_DATE` when Liquibase runs. The
seed creates events three days before that date, on that date, and seven days
after it for `sysadmin1`, `user1a`, `user2b`, and `user3c`. The seeded
instruments belong to `user1a`, and all users receive the booking role through
the `ALL_USERS` audience. IDs in the `910100001` to `910100009` range keep the
rows separate from generated inventory data.

Sources: [`changeLog-rsdev-booking-dev-seed.xml`](../../src/main/resources/sqlUpdates/changeLog-rsdev-booking-dev-seed.xml),
[`booking-dev-seed.sql`](../../src/main/resources/sqlUpdates/booking-dev-seed.sql).

The seeded instruments have no parent location: the SQL inserts no
`ContainerLocation` rows, whereas an instrument created through Inventory always
lands on its owner's workbench, so in a development database seeded this way
they are usually the only parentless instruments. Booking catalogue text search (`q` on
`/api/v2/booking-catalogue`, used by All bookable items and the item pickers)
does not use a search index. `InstrumentDaoHibernateImpl.searchBookingCatalogueTargetIds`
matches name, description or readable parent-container name with SQL `LIKE`,
so rows inserted by Liquibase are found without reindexing. The parent-name
match is an `exists` subquery. Hibernate renders the implicit joins behind
`location.storedInstrument` and `parent.owner` as inner joins, so outer joins
would drop every parentless instrument from the name match too; that is why
these fixtures were once found only by global ID.
`InstrumentBookingCatalogueSearchTest` covers both cases. Inventory's own search
uses the Hibernate Search index instead, which sees SQL-inserted rows only after
a reindex: `rs.indexOnstartup=true` (the default deployment value; the Docker
stack turns it off unless `RSPACE_INDEX_ON_STARTUP=true`).

Sources: [`InstrumentDaoHibernateImpl.java`](../../src/main/java/com/researchspace/dao/hibernate/InstrumentDaoHibernateImpl.java),
[`InstrumentBookingCatalogueSearchTest.java`](../../src/test/java/com/researchspace/dao/InstrumentBookingCatalogueSearchTest.java).

`changeLog-rsdev-booking-dev-seed-edge-cases.xml` (also `dev-test`) then turns
seven of those configurations into scheduling edge cases and adds four events
on closed days, across two all-day weekdays and ending at closing time. Its
precondition requires configurations `910100002` to `910100008` to still carry
the seed's defaults; otherwise it marks itself ran and changes nothing, so it
never overwrites a developer's edits. The original seed changeset is not edited,
because it is already applied and checksummed. Like the original seed, the raw
SQL writes no Envers audit rows.

Sources: [`changeLog-rsdev-booking-dev-seed-edge-cases.xml`](../../src/main/resources/sqlUpdates/changeLog-rsdev-booking-dev-seed-edge-cases.xml),
[`booking-dev-seed-edge-cases.sql`](../../src/main/resources/sqlUpdates/booking-dev-seed-edge-cases.sql).

## Upgrading an existing increment schema

Three historical booking changesets list their exact legacy checksums as accepted alternatives.
A new `run` guard requires all four booking tables and either no increment columns
or all eight compatible columns (`VARCHAR(8)` and `BIT(1)`, non-null on live tables
and nullable in audit tables). A partial or incompatible schema stops before the
additive migration. A complete existing schema marks that migration ran without
executing its DDL or backfill, preserving day units, unaligned starts, existing
settings versions, and historical audit nulls. Fresh databases still run the
unchanged additive body; fresh-only seed guards remain in place. This upgrade
uses normal forward Liquibase processing, without clearing checksums or resetting
the database.

## Verification path

For a Liquibase change, use a fresh database and an upgrade database. The
repository documents `mvn -Denvironment=drop-recreate-db` for rebuilding from
the baseline and applying all changelogs at application startup. The Docker
development stack provides `rspace-dev reset-db` / `up --fresh` for the same
workflow, but those commands destroy the local database volume and should only
be used when explicitly intended.

Source: [`DatabaseChangeGuidelines.md`](../../src/main/resources/sqlUpdates/DatabaseChangeGuidelines.md#L106-L112),
[`docker/dev/README.md`](../../docker/dev/README.md#destroying-a-worktrees-instance).
