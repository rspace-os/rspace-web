# Migrating RSpace to MariaDB Connector/J

RSpace 2.29.0 and later bundle the [MariaDB Connector/J](https://mariadb.com/docs/connectors/mariadb-connector-j/about-mariadb-connector-j)
3.5 JDBC driver instead of MySQL Connector/J 8.4. The database schema is unchanged, and no data migration is needed.

Most installations will start without any changes. Existing MySQL-style settings in
`deployment.properties` are still accepted but are deprecated and will be removed in a future release.
Check the startup log for `WARN` lines from `MariaDbDataSource` and update the settings below.

## 1. Change the `jdbc.url` scheme

Replace `jdbc:mysql:` with `jdbc:mariadb:`:

```properties
# before (deprecated, logs a warning at startup)
jdbc.url=jdbc:mysql://localhost:3306/rspace
# after
jdbc.url=jdbc:mariadb://localhost:3306/rspace
```

RSpace rewrites `jdbc:mysql:` URLs to `jdbc:mariadb:` at startup. You don't need to add `permitMysqlScheme`.

**Rollback:** older RSpace versions don't recognise `jdbc:mariadb:` URLs. If you need to redeploy an older WAR,
change the scheme back to `jdbc:mysql:` first.

## 2. Remove any `jdbc.driverClassName` override

The driver class is set internally. If your `deployment.properties` still sets
`jdbc.driverClassName=com.mysql.cj.jdbc.Driver` (or `com.mysql.jdbc.Driver`), RSpace uses
`org.mariadb.jdbc.Driver` instead and logs a warning. Delete the line.

## 3. Review connection properties

Check `jdbc.url` query parameters and `jdbc.connectionProperties`.

| MySQL Connector/J setting | With MariaDB Connector/J |
|---|---|
| `useSSL=false` | Ignored. Connections are unencrypted by default, as before. Use `sslMode=disable` to be explicit. |
| `useSSL=true` | Now means `sslMode=verify-full`: the server certificate **and** hostname are verified. For the old unverified behaviour use `sslMode=trust`. |
| `sslMode=DISABLED` / `REQUIRED` / `VERIFY_CA` / `VERIFY_IDENTITY` | Accepted, same meaning (`disable` / `trust` / `verify-ca` / `verify-full`). |
| `sslMode=PREFERRED` | **Not supported, RSpace fails to start.** Use `sslMode=disable` or `sslMode=trust`. |
| `serverTimezone=...` | Ignored. Use `connectionTimeZone=...` instead. |
| `characterEncoding`, `useUnicode` | Ignored, and safe to remove. The connection always uses `utf8mb4`. |
| `connectionCollation=...` | Must be a `utf8mb4_*` collation, otherwise RSpace fails to start. |

For TLS options (trust stores, server certificates), see
[Using TLS/SSL with MariaDB Connector/J](https://mariadb.com/kb/en/using-tls-ssl-with-mariadb-java-connector/).

If you need help, contact [support@researchspace.com](mailto:support@researchspace.com).
