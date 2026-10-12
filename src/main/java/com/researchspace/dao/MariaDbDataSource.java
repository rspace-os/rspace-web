package com.researchspace.dao;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.dbcp2.BasicDataSource;

/**
 * Connection pool for the MariaDB Connector/J driver that still accepts the MySQL Connector/J
 * settings existing deployments carry in their <code>deployment.properties</code>. The MariaDB
 * driver rejects <code>jdbc:mysql:</code> URLs and the MySQL driver classes are no longer bundled,
 * so both are rewritten here with a deprecation warning.
 */
@Slf4j
public class MariaDbDataSource extends BasicDataSource {

  static final String MARIADB_DRIVER = "org.mariadb.jdbc.Driver";
  private static final String MYSQL_SCHEME = "jdbc:mysql:";
  private static final String MARIADB_SCHEME = "jdbc:mariadb:";

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
      url = MARIADB_SCHEME + url.substring(MYSQL_SCHEME.length());
    }
    super.setUrl(url);
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
}
