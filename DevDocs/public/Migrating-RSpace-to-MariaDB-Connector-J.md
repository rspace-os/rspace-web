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

Check `jdbc.url` query parameters and `jdbc.connectionProperties`. RSpace translates the MySQL Connector/J options
below so the connection behaves as before, logs a warning, and you can update them at your own pace.

| MySQL Connector/J setting | With MariaDB Connector/J in RSpace |
|---|---|
| No TLS option at all | Same as before: the connection is encrypted when the database server offers TLS, without checking its certificate, and unencrypted otherwise. RSpace checks this once at startup and logs the result. Set `sslMode` to choose explicitly. |
| `sslMode=PREFERRED` | Same as no TLS option. Remove it, or set `sslMode` explicitly. |
| `useSSL=false` | Unencrypted, as before. `sslMode=disable` is the new name. |
| `useSSL=true` or `requireSSL=true` | Encrypted without checking the certificate, as before. `sslMode=trust` is the new name. |
| `useSSL=true` with `verifyServerCertificate=true` | Encrypted and the certificate is checked, as before. `sslMode=verify-ca` is the new name. |
| `sslMode=DISABLED` / `REQUIRED` / `VERIFY_CA` / `VERIFY_IDENTITY` | Accepted, same meaning (`disable` / `trust` / `verify-ca` / `verify-full`). |
| `allowLoadLocalInfile` | Translated to `allowLocalInfile`. Loading local files stays **off** unless you set it to `true`. |
| `serverTimezone=...` | **Ignored.** Use `connectionTimeZone=...` instead, or timestamps may be read in the wrong time zone. |
| `characterEncoding`, `useUnicode` | Ignored, and safe to remove. The connection always uses `utf8mb4`. |
| `connectionCollation=...` | Must be a `utf8mb4_*` collation, otherwise RSpace fails to start. |

For TLS options (trust stores, server certificates), see
[Using TLS/SSL with MariaDB Connector/J](https://mariadb.com/kb/en/using-tls-ssl-with-mariadb-java-connector/).

If you need help, contact [support@researchspace.com](mailto:support@researchspace.com).
