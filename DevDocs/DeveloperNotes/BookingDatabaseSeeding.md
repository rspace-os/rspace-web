# Booking database seeding

## Findings

RSpace applies `classpath:sqlUpdates/liquibase-master.xml` at startup through
`SpringLiquibase`. The deployment property `liquibase.context` selects which
changesets run. Production uses `run`; local development and tests use
`run,dev-test`; cloud deployments use `run,cloud`. The repository requires every
new changeset to declare one of those contexts and forbids editing a changeset
after it has been applied because Liquibase checksums it.

Sources: [`applicationContext-dao.xml`](../../src/main/resources/applicationContext-dao.xml#L8-L16),
[`defaultDeployment.properties`](../../src/main/resources/deployments/defaultDeployment.properties#L351-L355),
[`DatabaseChangeGuidelines.md`](../../src/main/resources/sqlUpdates/DatabaseChangeGuidelines.md#L48-L65),
[`DatabaseChangeGuidelines.md`](../../src/main/resources/sqlUpdates/DatabaseChangeGuidelines.md#L106-L138).

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
exist with their initial fixture credentials. Existing databases are not reseeded
on restart, even with the flag enabled. It waits for the outer startup transaction
to commit, then creates inventory and booking data in two new transactions.

Sources: [`TestAppConfig.java`](../../src/main/java/com/axiope/service/cfg/TestAppConfig.java#L55-L63),
[`TestAppConfig.java`](../../src/main/java/com/axiope/service/cfg/TestAppConfig.java#L125-L156),
[`BookingFixturesAppInitialiser.java`](../../src/main/java/com/researchspace/service/impl/BookingFixturesAppInitialiser.java#L49-L117).

The initializer is idempotent. It finds fixture users, instruments, and
containers by stable names plus the fixture description, creates missing
objects through inventory managers, creates or updates booking configurations
through `BookingConfigurationManager`, and creates bookings through
`TimeSlotBookingManager`. Booking dates are calculated relative to the current
date at first deployment. Docker documentation describes the
behavior and the 500-instrument / 1,003-event development dataset.

Sources: [`BookingFixturesAppInitialiser.java`](../../src/main/java/com/researchspace/service/impl/BookingFixturesAppInitialiser.java#L119-L220),
[`BookingFixturesAppInitialiser.java`](../../src/main/java/com/researchspace/service/impl/BookingFixturesAppInitialiser.java#L237-L440),
[`BookingFixturesAppInitialiser.java`](../../src/main/java/com/researchspace/service/impl/BookingFixturesAppInitialiser.java#L442-L628),
[`docker/dev/README.md`](../../docker/dev/README.md#L110-L123).

## Recommended approach

Use a new Liquibase changeset for permanent reference data that must exist in
every deployment. Give it `context="run"`, add a comment, register it in
`liquibase-master.xml`, use stable natural-key lookups or guarded inserts when
the database may already contain the row, and test both a fresh baseline and an
upgrade database. Keep the seed data in a separate SQL file only when the
volume makes XML inserts unreadable; load it with `<sqlFile>` from the
changeset.

Use `context="dev-test"` for data that belongs only in local development or
automated test databases. Do not put development fixtures in the `run` context,
because production and enterprise deployments also use `run`.

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

## Verification path

For a Liquibase change, use a fresh database and an upgrade database. The
repository documents `mvn -Denvironment=drop-recreate-db` for rebuilding from
the baseline and applying all changelogs at application startup. The Docker
development stack provides `rspace-dev reset-db` / `up --fresh` for the same
workflow, but those commands destroy the local database volume and should only
be used when explicitly intended.

Source: [`DatabaseChangeGuidelines.md`](../../src/main/resources/sqlUpdates/DatabaseChangeGuidelines.md#L106-L112),
[`docker/dev/README.md`](../../docker/dev/README.md#L250-L280).
