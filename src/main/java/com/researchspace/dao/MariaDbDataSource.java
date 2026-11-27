package com.researchspace.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.dbcp2.BasicDataSource;
import org.mariadb.jdbc.Driver;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Connection pool for the MariaDB Connector/J driver that keeps the connection behaviour of the
 * MySQL Connector/J settings existing deployments carry in their <code>deployment.properties</code>
 * :
 *
 * <ul>
 *   <li><code>jdbc:mysql:</code> URLs and MySQL driver class names are rewritten, with a
 *       deprecation warning.
 *   <li>MySQL TLS options are translated. With no TLS option at all, the connection is encrypted
 *       whenever the server offers TLS, as MySQL Connector/J's default <code>PREFERRED</code> mode
 *       did; MariaDB Connector/J would otherwise connect unencrypted.
 *   <li>Loading local files (<code>LOAD DATA LOCAL INFILE</code>) stays off unless configured, as
 *       it was with MySQL Connector/J.
 * </ul>
 *
 * Options are read from both the URL and <code>jdbc.connectionProperties</code>; URL options win in
 * the driver, so legacy options in the URL are rewritten in the URL.
 */
@Slf4j
public class MariaDbDataSource extends BasicDataSource {

  static final String MARIADB_DRIVER = "org.mariadb.jdbc.Driver";
  private static final String MYSQL_SCHEME = "jdbc:mysql:";
  private static final String MARIADB_SCHEME = "jdbc:mariadb:";
  private static final String JDBC_PREFIX = "jdbc:";
  private static final List<String> TLS_OPTIONS =
      List.of("sslMode", "useSSL", "useSsl", "requireSSL");
  private static final String NO_SERVER_TLS = "ssl not enabled in the server";

  // BasicDataSource keeps its connection properties package-private, so track them here
  private final Properties configured = new Properties();
  private volatile boolean optionsResolved;

  /** Returns the URL with a deprecated <code>jdbc:mysql:</code> scheme replaced. */
  public static String toMariaDbScheme(String url) {
    if (url != null && url.startsWith(MYSQL_SCHEME)) {
      return MARIADB_SCHEME + url.substring(MYSQL_SCHEME.length());
    }
    return url;
  }

  @Override
  public synchronized void setUrl(String url) {
    if (url != null && url.startsWith(MYSQL_SCHEME)) {
      // don't log the URL itself, it may contain credentials
      log.warn(
          "jdbc.url uses the deprecated '{}' scheme; change it to '{}'."
              + " Support for '{}' will be removed in a future release.",
          MYSQL_SCHEME,
          MARIADB_SCHEME,
          MYSQL_SCHEME);
    }
    super.setUrl(toMariaDbScheme(url));
  }

  @Override
  public synchronized void setDriverClassName(String driverClassName) {
    if ("com.mysql.cj.jdbc.Driver".equals(driverClassName)
        || "com.mysql.jdbc.Driver".equals(driverClassName)) {
      log.warn(
          "jdbc.driverClassName '{}' is no longer bundled with RSpace, using '{}' instead."
              + " Remove the jdbc.driverClassName setting from deployment.properties.",
          driverClassName,
          MARIADB_DRIVER);
      driverClassName = MARIADB_DRIVER;
    }
    super.setDriverClassName(driverClassName);
  }

  @Override
  public void setConnectionProperties(String connectionProperties) {
    super.setConnectionProperties(connectionProperties);
    // same "key=value;key=value" format BasicDataSource parses
    configured.clear();
    for (String entry : connectionProperties.split(";")) {
      if (!entry.isEmpty()) {
        int eq = entry.indexOf('=');
        configured.setProperty(
            eq > 0 ? entry.substring(0, eq) : entry, eq > 0 ? entry.substring(eq + 1) : "");
      }
    }
  }

  @Override
  public void addConnectionProperty(String name, String value) {
    super.addConnectionProperty(name, value);
    configured.setProperty(name, value);
  }

  @Override
  public void removeConnectionProperty(String name) {
    super.removeConnectionProperty(name);
    configured.remove(name);
  }

  @Override
  protected DataSource createDataSource() throws SQLException {
    if (!optionsResolved) {
      synchronized (this) {
        if (!optionsResolved) {
          resolveOptions();
          optionsResolved = true;
        }
      }
    }
    return super.createDataSource();
  }

  private void resolveOptions() throws SQLException {
    Map<String, String> urlOptions = queryOptions(getUrl());
    Map<String, String> propertyOptions = new LinkedHashMap<>();
    configured
        .stringPropertyNames()
        .forEach(k -> propertyOptions.put(k, configured.getProperty(k)));

    if (translateLegacyOptions(urlOptions)) {
      super.setUrl(withQuery(getUrl(), urlOptions));
    }
    translateLegacyOptions(propertyOptions);
    if (!urlOptions.containsKey("allowLocalInfile")
        && !propertyOptions.containsKey("allowLocalInfile")) {
      propertyOptions.put("allowLocalInfile", "false");
    }
    configured.stringPropertyNames().stream()
        .filter(k -> !propertyOptions.containsKey(k))
        .forEach(this::removeConnectionProperty);
    propertyOptions.forEach(this::addConnectionProperty);

    if (!hasTlsOption(urlOptions) && !hasTlsOption(propertyOptions)) {
      String sslMode = preferTls();
      addConnectionProperty("sslMode", sslMode);
      log.info(
          "No database TLS option set; using sslMode={} because the server {}."
              + " Set sslMode explicitly to choose.",
          sslMode,
          "trust".equals(sslMode) ? "offers TLS" : "does not offer TLS");
    }
  }

  /**
   * Rewrites MySQL Connector/J options in one option source to their MariaDB Connector/J
   * equivalents, keeping the MySQL behaviour.
   *
   * @return whether any option changed
   */
  static boolean translateLegacyOptions(Map<String, String> options) {
    boolean changed = false;
    String loadLocal = options.remove("allowLoadLocalInfile");
    if (loadLocal != null) {
      options.putIfAbsent("allowLocalInfile", loadLocal);
      changed = true;
    }
    // MariaDB has no PREFERRED mode; dropping it means "encrypt when the server offers TLS"
    if ("PREFERRED".equalsIgnoreCase(options.get("sslMode"))) {
      options.remove("sslMode");
      changed = true;
    }
    String useSsl = options.containsKey("useSSL") ? options.get("useSSL") : options.get("useSsl");
    boolean requiredByLegacyOption =
        "true".equalsIgnoreCase(useSsl)
            || (useSsl == null && "true".equalsIgnoreCase(options.get("requireSSL")));
    if (requiredByLegacyOption) {
      // MySQL: encrypted, certificate checked only with verifyServerCertificate=true. MariaDB
      // reads useSSL=true as verify-full and ignores requireSSL.
      boolean verifyCa = "true".equalsIgnoreCase(options.get("verifyServerCertificate"));
      options.putIfAbsent("sslMode", verifyCa ? "verify-ca" : "trust");
      List.of("useSSL", "useSsl", "requireSSL", "verifyServerCertificate").forEach(options::remove);
      changed = true;
    }
    if (changed) {
      log.warn(
          "Database connection options use MySQL Connector/J names or values;"
              + " update them as described in Migrating-RSpace-to-MariaDB-Connector-J.md.");
    }
    return changed;
  }

  static boolean hasTlsOption(Map<String, String> options) {
    return TLS_OPTIONS.stream().anyMatch(options::containsKey);
  }

  /** Opens one connection with TLS; falls back to plaintext only if the server has no TLS. */
  private String preferTls() throws SQLException {
    Properties probe = new Properties();
    probe.putAll(configured);
    if (getUsername() != null) {
      probe.setProperty("user", getUsername());
    }
    if (getPassword() != null) {
      probe.setProperty("password", getPassword());
    }
    probe.setProperty("sslMode", "trust");
    try (Connection connection = new Driver().connect(getUrl(), probe)) {
      if (connection == null) {
        throw new SQLException("jdbc.url is not a MariaDB Connector/J URL");
      }
      return "trust";
    } catch (SQLException e) {
      if (e.getMessage() != null && e.getMessage().contains(NO_SERVER_TLS)) {
        return "disable";
      }
      throw e;
    }
  }

  private static Map<String, String> queryOptions(String url) {
    Map<String, String> options = new LinkedHashMap<>();
    if (url != null && url.startsWith(JDBC_PREFIX)) {
      UriComponentsBuilder.fromUriString(url.substring(JDBC_PREFIX.length()))
          .build()
          .getQueryParams()
          .forEach((k, v) -> options.put(k, v.isEmpty() ? null : v.get(0)));
    }
    return options;
  }

  private static String withQuery(String url, Map<String, String> options) {
    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    options.forEach(params::add);
    return JDBC_PREFIX
        + UriComponentsBuilder.fromUriString(url.substring(JDBC_PREFIX.length()))
            .replaceQueryParams(params)
            .build()
            .toUriString();
  }
}
