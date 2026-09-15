# Upgrading to the Spring 6 / Jakarta stack

This release moves RSpace from Spring 5.3 / Hibernate ORM 5.6 / Hibernate Search 5 /
Shiro 1.13 / the `javax.*` APIs to Spring 6.2 / Hibernate ORM 6.4 / Hibernate Search 7 /
Shiro 2.1 / the `jakarta.*` (Jakarta EE 10) APIs. rspace-web and all of the rspace-os
libraries it depends on upgrade together, and each library carries a major version bump.
The rspace-web version number itself stays on its normal release train.

Operators should read [Upgrading a server](#upgrading-a-server); developers should read
[Developer notes](#developer-notes). This is not a routine server upgrade: deployment
prerequisites change and the database migration is one-way.

## Upgrading a server

If you followed previous setup instructions, your RSpace is running on Ubuntu 22.04 LTS. 
We recommend upgrading OS to Ubuntu 24.04 LTS, which will update Tomcat to version 10.

More details in [Migrating RSpace to Tomcat 10](../public/Migrating-RSpace-to-Tomcat-10.md).

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
2. **Update the JDBC driver class name** in any configuration of your own that pins it
   (context.xml, JNDI definitions, properties files): `com.mysql.jdbc.Driver` becomes
   `com.mysql.cj.jdbc.Driver`.
3. **Make the JDBC TLS mode explicit.** Connector/J 8.4 deprecates `useSSL`; replace it
   with `sslMode=DISABLED` for an intentionally unencrypted connection, or configure
   certificates and use `sslMode=VERIFY_CA` or `VERIFY_IDENTITY` for TLS.
4. **Check DB user privileges.** The migration creates a stored procedure, so the DB
   user needs `CREATE ROUTINE` / `DROP ROUTINE`. The standard `ALL` grant on the RSpace
   schema covers this; a locked-down user may not.
5. **Only if you customised the upload limit:** it is no longer set in `web.xml`. Set
   the JVM property `-Dfiles.maxUploadSize=<bytes>` or the `files.maxUploadSize` key in
   `deployment.properties` (default 50 MB, capping the total multipart request size).
   On Tomcat, set the connector's `maxSwallowSize` to `-1` to ensure clients receive
   HTTP 413 for oversized uploads instead of a connection reset.
6. **Resolve pending API exports.** The upgrade discards Spring Batch job metadata, so
   wait for exports under `/api/v1/export/...` to finish and obtain their download URLs.
   Jobs that remain pending or have not been polled for their result must be re-run.
   UI-initiated exports are unaffected.

### What the migration changes in the database

Everything runs automatically via Liquibase at first boot of the new version.

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

- **Java EE APIs moved from `javax.*` to `jakarta.*`.** This includes servlet,
  persistence, validation, mail, JAXB, EL, and JAX-RS; JSP taglibs use
  `jakarta.tags.*`. Java SE packages and JCache still use `javax.*`.
- **Transaction wiring.** Service-layer transactions come from the XML
  `<tx:annotation-driven>` config plus the pattern pointcut advisors in
  `applicationContext-service.xml`; many `*Manager` classes rely on those advisors
  rather than annotations. Beans outside those patterns use class-level
  `@Transactional`.
- **`Session.saveOrUpdate` is deprecated** (removed in Hibernate 7).
  `GenericDaoHibernate.save()` now branches between `persist` and `merge`; for entities
  with assigned (non-generated) ids, use the instance returned by `save()`, as its
  javadoc explains.
- **HQL is parsed strictly.** Hibernate 6 rejects paths Hibernate 5 tolerated:
  transient properties (use the persistent path, e.g. `editInfo.name`, not the
  `@Transient` delegate) and raw discriminator columns (use `type(alias) = EntityName`,
  not `DTYPE='...'`).
- **Search mappings** use Hibernate Search 7 annotations (`@FullTextField`,
  `@GenericField`, `@KeywordField`, named `@IndexedEmbedded`). Index field names such as
  `extraFields.fieldData` are a contract between the entity mappings and the
  query builders; changing one side silently breaks search.
- **Caching** uses EhCache 3 via JCache (`ehcache.xml`, v3 schema). Every
  `@Cache`-annotated entity needs an explicit region; do not declare
  `key-type`/`value-type` on entity regions (Hibernate 6 cache keys are composite
  objects). `EhcacheRegionConfigTest` enforces the region/entity correspondence.
- **Multipart config** is programmatic (`DispatcherServletInitializer`), not in
  `web.xml`.
- **SiteMesh 3 decorators** use bare file names because `/WEB-INF/decorators/` is added
  automatically. Included fragments must not contain `<head>` because SiteMesh keeps
  only the first one.
