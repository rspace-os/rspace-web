# Upgrading to the Spring 6 / Jakarta stack

This release moves RSpace from Spring 5.3 / Hibernate ORM 5.6 / Hibernate Search 5 /
Shiro 1.13 / the `javax.*` APIs to Spring 6.2 / Hibernate ORM 6.4 / Hibernate Search 7 /
Shiro 2.1 / the `jakarta.*` (Jakarta EE 10) APIs. rspace-web and all of the rspace-os
libraries it depends on upgrade together, and each library carries a major version bump.
The rspace-web version number itself stays on its normal release train.

## Who this page is for

- **Operators** (you run an RSpace server and are upgrading it): read
  [Upgrading a server](#upgrading-a-server). This is not a routine upgrade: the
  deployment prerequisites change and the database migration is one-way.
- **Developers** (you build or change RSpace code): read
  [Developer notes](#developer-notes). Local Jetty development works as before, and the
  Tomcat items in the server section do not apply to you unless you also run a
  Tomcat deployment.

## Upgrading a server

### Requirements

| Component | Before | After |
|---|---|---|
| JVM | Java 17 | Java 17 (unchanged) |
| Servlet container | Tomcat 9 | **Tomcat 10.1** (Tomcat 9 cannot run this WAR) |
| Database | MariaDB 10.11+ | MariaDB 10.11+ (unchanged) |
| JDBC driver | mysql-connector-java 5.1 | **mysql-connector-j 8.4** (bundled in the WAR) |

### Before you upgrade

1. **Back up the database.** The migration rewrites id-sequence counters and replaces
   the Spring Batch tables in place. There is no downgrade path: rolling back means
   restoring the backup and redeploying the previous WAR.
2. **Install Tomcat 10.1.**
3. **Update the JDBC driver class name** in any configuration of your own that pins it
   (context.xml, JNDI definitions, properties files): `com.mysql.jdbc.Driver` becomes
   `com.mysql.cj.jdbc.Driver`.
4. **Check `jdbc.url` for `useSSL=true`.** The new driver treats `useSSL=true` as
   "TLS required" and refuses to connect to a database without TLS; the old driver
   silently fell back to plaintext. If the app fails to boot with
   `Cannot create PoolableConnectionFactory (Communications link failure)`, this is the
   most likely cause. Set `useSSL=false` if the connection does not actually use TLS;
   otherwise configure certificates and use `sslMode=VERIFY_CA` or `VERIFY_IDENTITY`.
5. **Check DB user privileges.** The migration creates a stored procedure, so the DB
   user needs `CREATE ROUTINE` / `DROP ROUTINE`. The standard `ALL` grant on the RSpace
   schema covers this; a locked-down user may not.
6. **Leave the Liquibase context at `run`** (the production default). Running the
   migration with a dev/test context risks executing test fixtures against real data.
7. **Only if you customised the upload limit:** it is no longer set in `web.xml`. Set
   the JVM property `-Dfiles.maxUploadSize=<bytes>` or the `files.maxUploadSize` key in
   `deployment.properties` (default 50 MB, capping the total multipart request size).
   On Tomcat, make sure the connector's `maxSwallowSize` is `-1` or larger than the
   limit, otherwise oversize uploads reset the connection instead of returning HTTP 413.
8. **Download any pending API exports.** The upgrade discards Spring Batch job metadata,
   so any export started via `POST /api/v1/export` and not yet fetched must be re-run
   afterwards. UI-initiated exports are unaffected and existing archives stay
   downloadable.

### What the migration changes in the database

Everything runs automatically via Liquibase at first boot of the new version. On a large
database, expect that first boot to take noticeably longer than usual.

- **Id-sequence reseed.** Hibernate 6 reads the `hibernate_sequences` counters
  differently from Hibernate 5, so every counter is reseeded above its table's current
  maximum id to prevent primary-key collisions. Id allocation is otherwise unchanged.
  New ids may skip up to one allocation block (50) at the upgrade boundary; this is
  safe and expected.
- **Spring Batch tables.** The `BATCH_*` tables are dropped and recreated on the Spring
  Batch 5 layout. Job history is discarded; these tables only back the monitoring
  metadata for `/api/v1/export`.

### After the upgrade: search reindex (mandatory)

The Lucene index format changes (Lucene 5 to 9), so the text index must be rebuilt. With
`rs.indexOnstartup=true` (the shipped default) this happens automatically at boot. If
your deployment overrides it to `false`, enable it for the first post-upgrade boot.
Until the reindex finishes, search results will be missing or incomplete.

### Expected log noise (not failures)

- An Envers static-metamodel `ERROR` (`HHH015007 ... DefaultRevisionEntity_`) at every
  boot. Known and harmless: auditing, login, and CRUD all work. Fixed upstream in
  Hibernate ORM 7 (HHH-19259) with no 6.x backport, so it stays until a future upgrade.

## Developer notes

Day-to-day development is unchanged: same Java 17, same Jetty workflow, same database.
WebSocket under Jetty uses the SockJS fallback; native WebSocket is supported on Tomcat,
which is why Tomcat 10.1 is the production target. The rules below are what changed in
the stack.

- **`javax.*` is gone.** All servlet, persistence, validation, mail, JAXB, EL, and
  ws.rs imports are now `jakarta.*`; JSP taglibs use the `jakarta.tags.*` URIs. Code
  importing the old namespaces will not compile.
- **Transaction wiring.** Service-layer transactions come from the XML
  `<tx:annotation-driven>` config plus the pattern pointcut advisors in
  `applicationContext-service.xml`. The pattern advisors are load-bearing: many
  `*Manager` classes carry no annotations. Beans outside those patterns use class-level
  `@Transactional`, and `TransactionAdviceStartupCheck` fails startup if an
  early-instantiated bean is missing transaction advice. Verify any change to this
  wiring in the running app, not tests: the test context uses different XML and never
  loads security.xml. See `Transactions.md`.
- **`Session.saveOrUpdate` is deprecated** (removed in Hibernate 7).
  `GenericDaoHibernate.save()` now branches between `persist` and `merge`; for entities
  with assigned (non-generated) ids, use the instance returned by `save()`, as its
  javadoc explains.
- **HQL is parsed strictly.** Hibernate 6 rejects paths Hibernate 5 tolerated:
  transient properties (use the persistent path, e.g. `editInfo.name`, not the
  `@Transient` delegate) and raw discriminator columns (use `type(alias) = EntityName`,
  not `DTYPE='...'`).
- **Search mappings** use Hibernate Search 6/7 annotations (`@FullTextField`,
  `@GenericField`, `@KeywordField`, named `@IndexedEmbedded`). Index field names such as
  `extraFields.fieldData` are a contract between rspace-core-model mappings and
  rspace-web query builders; changing one side silently breaks search.
- **Caching** uses EhCache 3 via JCache (`ehcache.xml`, v3 schema). Every
  `@Cache`-annotated entity needs an explicit region; do not declare
  `key-type`/`value-type` on entity regions (Hibernate 6 cache keys are composite
  objects). `EhcacheRegionConfigTest` enforces the region/entity correspondence.
- **Multipart config** is programmatic (`DispatcherServletInitializer`), not in
  `web.xml`.
- **SiteMesh 3 decorators** are referenced by bare file name (the prefix
  `/WEB-INF/decorators/` is added automatically); an absolute path double-prefixes the
  URI and breaks every decorated page. Fragments included into a page body must not
  carry their own `<head>`: SiteMesh keeps only the first, silently dropping scripts in
  a second one.
