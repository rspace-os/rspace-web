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
`BookingFixturesAppInitialiser`, which is registered only by the `run` Spring
profile in `TestAppConfig`. `GlobalInitManager` runs it after the development
group setup. It waits for the outer startup transaction to commit, then creates
inventory and booking data in two new transactions.

Sources: [`TestAppConfig.java`](../../src/main/java/com/axiope/service/cfg/TestAppConfig.java#L55-L63),
[`TestAppConfig.java`](../../src/main/java/com/axiope/service/cfg/TestAppConfig.java#L125-L156),
[`BookingFixturesAppInitialiser.java`](../../src/main/java/com/researchspace/service/impl/BookingFixturesAppInitialiser.java#L49-L117).

The initializer is idempotent. It finds fixture users, instruments, and
containers by stable names plus the fixture description, creates missing
objects through inventory managers, creates or updates booking configurations
through `BookingConfigurationManager`, and creates bookings through
`TimeSlotBookingManager`. Booking dates are calculated relative to the current
date so fixtures remain in the future. Docker documentation describes the same
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

## Verification path

For a Liquibase change, use a fresh database and an upgrade database. The
repository documents `mvn -Denvironment=drop-recreate-db` for rebuilding from
the baseline and applying all changelogs at application startup. The Docker
development stack provides `rspace-dev reset-db` / `up --fresh` for the same
workflow, but those commands destroy the local database volume and should only
be used when explicitly intended.

Source: [`DatabaseChangeGuidelines.md`](../../src/main/resources/sqlUpdates/DatabaseChangeGuidelines.md#L106-L112),
[`docker/dev/README.md`](../../docker/dev/README.md#L250-L280).
