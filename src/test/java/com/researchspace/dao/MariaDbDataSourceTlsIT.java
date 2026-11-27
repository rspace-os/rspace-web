package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * MariaDB Connector/J connects unencrypted unless told otherwise; MySQL Connector/J preferred TLS.
 * {@link MariaDbDataSource} must keep the MySQL behaviour for configurations without a TLS option.
 */
class MariaDbDataSourceTlsIT {

  private static GenericContainer<?> withTls;
  private static GenericContainer<?> withoutTls;

  @BeforeAll
  static void startServers() {
    withTls = mariadb();
    withoutTls = mariadb().withCommand("--skip-ssl");
    withTls.start();
    withoutTls.start();
  }

  @AfterAll
  static void stopServers() {
    withTls.stop();
    withoutTls.stop();
  }

  @Test
  void legacyUrlWithoutTlsOptionIsEncryptedWhenServerOffersTls() throws SQLException {
    try (MariaDbDataSource ds = dataSource(url(withTls, "jdbc:mysql:"), "")) {
      assertTrue(encrypted(ds));
    }
  }

  @Test
  void connectsUnencryptedWhenServerHasNoTls() throws SQLException {
    try (MariaDbDataSource ds = dataSource(url(withoutTls, "jdbc:mariadb:"), "")) {
      assertFalse(encrypted(ds));
    }
  }

  @Test
  void requireSslInPropertiesStillEncrypts() throws SQLException {
    try (MariaDbDataSource ds = dataSource(url(withTls, "jdbc:mysql:"), "requireSSL=true")) {
      assertTrue(encrypted(ds));
    }
  }

  @Test
  void explicitDisableIsHonoured() throws SQLException {
    try (MariaDbDataSource ds =
        dataSource(url(withTls, "jdbc:mariadb:") + "?sslMode=disable", "")) {
      assertFalse(encrypted(ds));
    }
  }

  @Test
  void localInfileIsRefusedByDefault() throws Exception {
    Path file = Files.createTempFile("local-infile", ".txt");
    Files.writeString(file, "host file\n");
    String load = "LOAD DATA LOCAL INFILE '" + file + "' INTO TABLE infile";
    try (MariaDbDataSource ds = dataSource(url(withTls, "jdbc:mariadb:"), "");
        Connection connection = ds.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE IF NOT EXISTS infile (line TEXT)");
      assertThrows(SQLException.class, () -> statement.execute(load));
    }
    // control: the server accepts it when the setting is opted into
    try (MariaDbDataSource ds = dataSource(url(withTls, "jdbc:mariadb:"), "allowLocalInfile=true");
        Connection connection = ds.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(load);
    } finally {
      Files.delete(file);
    }
  }

  private static GenericContainer<?> mariadb() {
    return new GenericContainer<>("mariadb:11.8")
        .withEnv("MARIADB_ROOT_PASSWORD", "root")
        .withEnv("MARIADB_DATABASE", "tlstest")
        .withEnv("MARIADB_USER", "tls")
        .withEnv("MARIADB_PASSWORD", "tls")
        .withExposedPorts(3306)
        .waitingFor(Wait.forLogMessage(".*ready for connections.*", 2));
  }

  private static String url(GenericContainer<?> db, String scheme) {
    return scheme + "//" + db.getHost() + ":" + db.getMappedPort(3306) + "/tlstest";
  }

  private static MariaDbDataSource dataSource(String url, String connectionProperties) {
    MariaDbDataSource ds = new MariaDbDataSource();
    ds.setDriverClassName(MariaDbDataSource.MARIADB_DRIVER);
    ds.setUrl(url);
    ds.setUsername("tls");
    ds.setPassword("tls");
    ds.setConnectionProperties(connectionProperties);
    return ds;
  }

  private static boolean encrypted(MariaDbDataSource ds) throws SQLException {
    try (Connection connection = ds.getConnection();
        Statement statement = connection.createStatement();
        ResultSet cipher = statement.executeQuery("SHOW SESSION STATUS LIKE 'Ssl_cipher'")) {
      assertTrue(cipher.next());
      String value = cipher.getString(2);
      return value != null && !value.isEmpty();
    }
  }
}
