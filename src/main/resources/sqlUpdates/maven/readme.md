### What is this folder?

These SQL scripts are not used by the current build, and they are not for runtime or production use.

`-Denvironment=drop-recreate-db` (the `mysql-clean-liquibase` profile in `pom.xml`) drops and recreates
the database named by `jdbc.db.maven` in the `initialize` phase, then loads
`../liquibaseConfig/rs-dbbaseline-utf8.sql`. Liquibase applies the remaining changesets when RSpace starts.

The flag is for resetting your local database before `mvn jetty:run`. Tests no longer need it:
DB-backed tests start their own throwaway MariaDB container (see `TestDatabaseExtension`).
