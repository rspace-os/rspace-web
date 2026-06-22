# RSDEV-444 Spring 6 / Hibernate 6: DevOps handover for prod-clone migration test

**Goal:** test a Liquibase migration of existing data on a prod-style Tomcat 10 deployment.
**Plan:** re-seed the prod clone from latest `main`, then deploy the Spring 6 build and let its startup Liquibase upgrade the data in place.
**Status:** this exact flow was verified locally on 2026-06-09 (seed with `main`, boot the branch with `keepdbintact`). All changesets applied cleanly and a live collision test passed.

## The id-sequence clash you found: fixed and verified

- New changeset `hibernate6-reseed-sequences` (in `changeLog-rsdev-444.xml`) reseeds every `hibernate_sequences` counter to `MAX(id)+51` and seeds a `default` insurance row above the global max. Verified on a populated DB: no PK collisions on insert after upgrade.
- Note: it is implemented as a stored procedure (the first in the project). **The DB user running the migration needs `CREATE ROUTINE` / `DROP ROUTINE` privileges.** The standard `ALL` grant covers this; a locked-down user may not.
- Behaviour change after upgrade: most table-generated entities now draw ids from one shared `default` counter, so ids in those tables will be interleaved and sparse. Safe, but expected.

## Other database changes in the migration

- **Spring Batch tables are dropped and recreated** (Batch 3 to Batch 5 schema). All batch job-execution history is discarded. This only affects REST API exports (`/api/v1/export`); UI exports use a different mechanism and existing archives stay downloadable. Any API export not yet fetched at upgrade time must be re-run.
- **Liquibase context must be `run`.** The new changesets carry `context="run"`; run the migration with `-Dliquibase.context=run`. Deploying a prod artifact with the wrong `-Denvironment` risks executing dev-test fixtures against real data (this is the likely root cause of the earlier prod-deploy context failure).
- The Java-based startup migrations (`customliquibaseupdates`) were rewritten for Hibernate 6. They ran cleanly in our test, but that DB was small; watch them on the larger clone.

## Runtime / environment changes

- **Java 17 JVM required.**
- **JDBC driver:** MySQL Connector 5.1.47 replaced by mysql-connector-j 8.4.0. Driver class is now `com.mysql.cj.jdbc.Driver`. Update any external datasource config (context.xml, JNDI, properties) that pins the old `com.mysql.jdbc.Driver`. Verified working against MariaDB 10.11.
- **`useSSL=true` in `jdbc.url` now hard-fails against non-SSL databases.** Connector/J 8 treats `useSSL=true` as `sslMode=REQUIRED`, where 5.1 silently fell back to plaintext. Found 2026-06-11: the stock rspace-docker `deployment.properties` ships `jdbc.url=...?useSSL=true` and the upgraded WAR refused to boot against the (non-SSL) MariaDB container with `Cannot create PoolableConnectionFactory (Communications link failure)` from the liquibase bean; changing to `useSSL=false` fixed it. **Action: audit `jdbc.url` in every deployment's properties before upgrading.** If the DB connection is not actually using TLS, set `useSSL=false` (or configure real certs and `sslMode=VERIFY_CA`/`VERIFY_IDENTITY`); leaving `useSSL=true` against a non-SSL DB means the app will not start after the upgrade. The same applies to the rspace-docker repo's template `deployment.properties`, which should be patched as part of the rollout.
- **Upload limits moved out of web.xml.** Multipart limits now resolve from `-Dfiles.maxUploadSize`, then `deployment.properties`, then a hardcoded 50 MB default. **Question for you:** is 50 MB the right default for prod, and does it fit your Tomcat `maxSwallowSize`? Please confirm what the deployed environments expect.
- **Search indexes must be rebuilt.** Lucene 5 to 9 and Hibernate Search 5 to 7; the on-disk index format is incompatible. Plan a full reindex after migrating. Search is also the least-verified area, so exercise it in testing.
- **WebSocket:** native WebSocket is enabled on Tomcat (Jetty dev uses the SockJS fallback), so Tomcat 10.1 is the better-supported target.

## Expected boot noise (not deploy failures)

- `HHH015007` Envers static-metamodel ERROR at every boot. Known and non-fatal; login, CRUD, and the migration all work. Audit end-to-end is still being verified, so please exercise document version history / revision views in your testing and report anything broken.
- `FileIndexSearcher ... this should not be called` at boot. Benign in this context.

## Caveats before you start

- **Re-merge of latest `main`: was current at 2026-06-10; now drifted (RSDEV-444 parked 2026-06-22).** At the 2026-06-10 sweep all 32 projects were current with their `main` and rspace-web was current with `upstream/main` as of commit `1fa02048c`. Since then `upstream/main` has advanced ~37 commits (incl. RSDEV-416 Liquibase changelog squash, Java 17 source bump, pnpm/Biome), so a re-merge is owed before any fresh prod-clone test. Until that re-merge, seed the clone from a `main` no newer than 2026-06-10, or check with us first.
- **Build freshness:** the branch builds against unpinned JitPack branch-SNAPSHOT dependencies, so the WAR depends on the latest pushed commits of the sibling repos. All sibling pushes are current as of 2026-06-10; confirm with us before building if time has passed.
- Dependency upgrades in this migration still owe Drata records; follow the AI and Third Party code policy in Drata.

## What to test on the upgraded clone

1. Migration completes with no Liquibase errors (watch the `customliquibaseupdates` output).
2. Login and basic CRUD (documents, inventory).
3. Create new records across modules and confirm no duplicate-key errors (the original clash symptom).
4. Full reindex, then search: documents, attachments by filename, inventory.
5. Document version history / audit views (Envers).
6. File upload at and above 50 MB (confirms the multipart default question).
7. An API export via `/api/v1/export` and a UI export.

Questions / results to: Fraser.
