package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
