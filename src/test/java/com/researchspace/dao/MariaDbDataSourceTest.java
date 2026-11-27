package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MariaDbDataSourceTest {

  @Test
  void rewritesDeprecatedMysqlScheme() throws Exception {
    try (MariaDbDataSource ds = new MariaDbDataSource()) {
      ds.setUrl("jdbc:mysql://localhost:3306/rspace?sslMode=DISABLED");
      assertEquals("jdbc:mariadb://localhost:3306/rspace?sslMode=DISABLED", ds.getUrl());
    }
  }

  @Test
  void keepsMariadbUrl() throws Exception {
    try (MariaDbDataSource ds = new MariaDbDataSource()) {
      ds.setUrl("jdbc:mariadb://db:3306/rspace");
      assertEquals("jdbc:mariadb://db:3306/rspace", ds.getUrl());
    }
  }

  @Test
  void replacesMysqlDriverClasses() throws Exception {
    try (MariaDbDataSource ds = new MariaDbDataSource()) {
      ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
      assertEquals(MariaDbDataSource.MARIADB_DRIVER, ds.getDriverClassName());
      ds.setDriverClassName("com.mysql.jdbc.Driver");
      assertEquals(MariaDbDataSource.MARIADB_DRIVER, ds.getDriverClassName());
    }
  }

  @Test
  void legacyTlsOptionsKeepMysqlMeaning() {
    assertEquals(Map.of("sslMode", "trust"), translated(Map.of("useSSL", "true")));
    assertEquals(Map.of("sslMode", "trust"), translated(Map.of("requireSSL", "true")));
    assertEquals(
        Map.of("sslMode", "verify-ca"),
        translated(Map.of("useSSL", "true", "verifyServerCertificate", "true")));
    // PREFERRED has no MariaDB equivalent; dropping it leaves the TLS choice to the probe
    assertEquals(Map.of(), translated(Map.of("sslMode", "PREFERRED")));
  }

  @Test
  void explicitTlsChoicesAreKept() {
    assertEquals(
        Map.of("useSSL", "false", "requireSSL", "true"),
        translated(Map.of("useSSL", "false", "requireSSL", "true")));
    assertEquals(
        Map.of("sslMode", "verify-full"),
        translated(Map.of("useSSL", "true", "sslMode", "verify-full")));
  }

  @Test
  void legacyLocalInfileNameIsMapped() {
    assertEquals(
        Map.of("allowLocalInfile", "false"), translated(Map.of("allowLoadLocalInfile", "false")));
  }

  @Test
  void detectsTlsOptions() {
    assertTrue(MariaDbDataSource.hasTlsOption(Map.of("useSsl", "false")));
    assertFalse(MariaDbDataSource.hasTlsOption(Map.of("connectTimeout", "5000")));
  }

  private static Map<String, String> translated(Map<String, String> options) {
    Map<String, String> copy = new HashMap<>(options);
    MariaDbDataSource.translateLegacyOptions(copy);
    return copy;
  }
}
