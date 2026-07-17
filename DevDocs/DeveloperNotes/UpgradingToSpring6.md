# Upgrading to the Spring 6 / Jakarta stack

This release upgrades the platform from Spring 5.3 / Hibernate ORM 5.6 / Hibernate Search 5 /
Shiro 1.13 / the `javax.*` (Java EE) APIs to Spring 6.2 / Hibernate ORM 6.4 / Hibernate
Search 7 / Shiro 2.1 / the `jakarta.*` (Jakarta EE 10) APIs. It is a coordinated upgrade of
rspace-web and all of the rspace-os libraries it depends on; each library carries a major
version bump. Although the rspace-web version number itself stays on its normal release
train, this is NOT a routine upgrade for operators: the deployment prerequisites change and
the data migration is one-way, as detailed below.

This page covers what operators need to do to upgrade an existing deployment, what the
upgrade changes in the database, and what developers should know about the new stack.

## Requirements

| Component | Before | After |
|---|---|---|
| JVM | Java 17 | Java 17 |
| Servlet container | Tomcat 9 | **Tomcat 10.1** (Jakarta servlet API; Tomcat 9 cannot run this WAR) |
| Database | MariaDB 10.11+ | MariaDB 10.11+ |
| JDBC driver | mysql-connector-java 5.1 | **mysql-connector-j 8.4** (bundled) |

## Pre-upgrade checklist

1. **Take a full database backup.** The upgrade rewrites id-sequence counters and
   drops/recreates the Spring Batch tables in place. There is no downgrade path: rolling back
   means restoring the backup and redeploying the previous WAR.
2. **Install/configure Tomcat 10.1.** Native WebSocket is supported on Tomcat (development
   under Jetty uses the SockJS fallback), so Tomcat 10.1 is the supported production target.
3. **Update the JDBC driver class** in any external datasource configuration (context.xml,
   JNDI definitions, properties files) that pins the old name: `com.mysql.jdbc.Driver`
   becomes `com.mysql.cj.jdbc.Driver`.
4. **Audit `jdbc.url` for `useSSL=true`.** Connector/J 8 treats `useSSL=true` as
   `sslMode=REQUIRED` and refuses to connect to a database that does not offer TLS, where the
   old 5.1 driver silently fell back to plaintext. If the app fails to boot after the upgrade
   with `Cannot create PoolableConnectionFactory (Communications link failure)`, this is the
   most likely cause. If the DB connection is not actually using TLS, set `useSSL=false`;
   otherwise configure certificates and use `sslMode=VERIFY_CA` or `VERIFY_IDENTITY`.
5. **Check DB user privileges.** The schema migration includes a stored procedure, so the
   user running the migration needs `CREATE ROUTINE` / `DROP ROUTINE`. The standard `ALL`
   grant on the RSpace schema covers this; a locked-down user may not.
6. **Liquibase context must be `run`** (the production default). Running the migration with a
   dev/test environment setting risks executing test fixtures against real data.
7. **Upload limit (if customised).** Multipart upload limits are no longer set in `web.xml`.
   They resolve, in order, from the JVM system property `-Dfiles.maxUploadSize=<bytes>`, then
   the `files.maxUploadSize` key in `deployment.properties`, then a 50 MB default. The total
   multipart request limit is automatically twice the file limit. If your deployment needs a
   different limit, set the property explicitly. Tomcat ops note: ensure the connector's
   `maxSwallowSize` is `-1` or larger than the configured upload limit, otherwise oversize
   uploads result in a connection reset instead of a clean HTTP 413 response.
8. **Download any pending API exports.** Spring Batch job metadata is discarded by the
   upgrade (see below). Any export started via `POST /api/v1/export` whose result has not yet
   been fetched must be re-run after the upgrade. UI-initiated exports are unaffected and
   previously created archives remain downloadable.

## What the upgrade does to the database

All changes run automatically via Liquibase at first boot of the new version.

- **Id-sequence reseed.** Hibernate 6 interprets the `hibernate_sequences` counter values
  differently from Hibernate 5's legacy hi/lo generator (it reads `next_val` literally rather
  than as a hi/lo block marker). The migration reseeds every counter above its table's current
  maximum id so that new inserts cannot collide with existing primary keys. Id allocation is
  otherwise unchanged: each table-generated entity keeps its own counter row, exactly as
  before the upgrade. New ids may skip up to one allocation block (50) at the upgrade
  boundary; this is safe and expected.
- **Spring Batch schema replacement.** The `BATCH_*` tables move from the Spring Batch 3 to
  the Spring Batch 5 layout by drop-and-recreate, preserving job-id monotonicity. All batch
  job-execution history is discarded; these tables only back the monitoring metadata for
  `/api/v1/export`.
- The Java-based startup migrations run as part of the same boot. On a large database, expect
  the first boot to take noticeably longer than usual.

## Post-upgrade: search reindex (mandatory)

The upgrade moves from Lucene 5 to Lucene 9 and Hibernate Search 5 to 7. The on-disk search
index format is incompatible, so the index must be rebuilt. The rebuild mechanism is the
startup reindex: with `rs.indexOnstartup=true` (the shipped default) the text index is rebuilt
at boot. Before the first post-upgrade boot, confirm your deployment's properties do not
override this to `false`; if they do, enable it for that first boot. Until the reindex
completes, search results will be missing or incomplete.

## Expected log noise after upgrade (not failures)

- An Envers static-metamodel `ERROR` (`HHH015007 ... DefaultRevisionEntity_`) at every boot.
  Known and non-fatal: auditing, login, and CRUD all function. Fixed upstream in Hibernate
  ORM 7.0.0 (HHH-19259), with no backport to the 6.x line, so the error remains until a
  future ORM 7 upgrade.

## Notes for developers

- **`javax.*` is gone.** All servlet, persistence, validation, mail, JAXB, EL, and ws.rs
  imports are `jakarta.*`. JSP taglibs use the `jakarta.tags.*` URIs. New code importing a
  `javax.*` API from the old namespaces will not compile.
- **Transaction wiring.** Service-layer transactions are applied by the XML
  `<tx:annotation-driven>` configuration plus the pattern pointcut advisors in
  `applicationContext-service.xml`. The pattern advisors remain load-bearing because many
  `*Manager` classes carry no annotations at all. Beans outside those patterns use class-level
  `@Transactional`; that is reliable now that the Shiro beans resolve the securityManager
  lazily (the eager-init window that used to break annotation-driven advice is closed), and
  `TransactionAdviceStartupCheck` fails startup if an early-instantiated bean is missing
  transaction advice. Verify any change to this wiring in the running app, not tests: the test
  context uses different XML and never loads security.xml. See `Transactions.md` and the
  comment in `applicationContext-service.xml`.
- **`Session.saveOrUpdate` is deprecated** (removed in Hibernate 7). `GenericDaoHibernate.save()`
  now branches between `persist` and `merge`; for entities with assigned (non-generated) ids,
  callers must use the instance returned by `save()`, as documented in its javadoc.
- **HQL is parsed strictly.** Hibernate 6 rejects paths that Hibernate 5 tolerated: transient
  properties (use the persistent path, e.g. `editInfo.name`, not the `@Transient` delegate) and
  raw discriminator columns (use `type(alias) = EntityName`, not `DTYPE='...'`).
- **Search mappings** use Hibernate Search 6/7 annotations (`@FullTextField`, `@GenericField`,
  `@KeywordField`, named `@IndexedEmbedded`). Several index field names (for example
  `extraFields.fieldData`, `files.fieldData`, `notes.fieldData`) are a contract between the
  entity mappings in rspace-core-model and the query builders in rspace-web; changing one side
  silently breaks search for the affected content.
- **Caching** uses EhCache 3 via JCache (`ehcache.xml`, v3 schema). Every `@Cache`-annotated
  entity needs an explicit region; do not declare `key-type`/`value-type` on entity regions
  (Hibernate 6 cache keys are composite objects). `EhcacheRegionConfigTest` enforces the
  region/entity correspondence.
- **Multipart config** is programmatic (`DispatcherServletInitializer`), not in `web.xml`.
- **SiteMesh 3 decorators** are referenced by bare file name (the default decorator prefix
  `/WEB-INF/decorators/` is prepended automatically). Writing an absolute path double-prefixes
  the URI and produces container errors on every decorated page. Fragments included into a
  page body must not carry their own `<head>`: SiteMesh keeps only the first head, so scripts
  in a second one are silently dropped.
