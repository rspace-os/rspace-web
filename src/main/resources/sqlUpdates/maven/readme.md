### What is this folder?

This folder is for sql scripts to be executed during a clean maven build where the database is dropped and recreated. It is *not* for runtime/production use.

The mavenClean.sql will drop, then recreate an empty rspace database directly in a very early phase of maven build (the 'initialize' phase) if the property environment=drop-recreate-db is set.
This will then be populated at test-compile phase by hibernate-ddl maven plugin.
The 'maven-drop.sql' is probably now obsolete.

Old notes (pre October)

These scripts are called by the maven-sql plugin to clean out any tables that are not wiped out by Hibernate-ddl script.
 These tables are usually tables used by 3rd party libraries, not RSpace entities. E.g. Liquibase, Spring Social and Chem libraries all create tables in the DB that are not under direct Hibernate control.

Therefore, they need to be deleted manually before running the tests.