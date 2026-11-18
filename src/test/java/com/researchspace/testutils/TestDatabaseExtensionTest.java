package com.researchspace.testutils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.GenericContainer;

/**
 * {@link DatabaseCleaner} empties the database these tests use, so the context must be connected to
 * the one {@link TestDatabaseExtension} selected (a throwaway container, or {@code -Djdbc.url}),
 * not the {@code defaultDeployment.properties} fallback of the local {@code rspace} database.
 */
class TestDatabaseExtensionTest extends SpringTransactionalTest {

  @Autowired private DataSource dataSource;

  @Test
  void contextUsesTheSelectedDatabase() throws SQLException {
    String selected = System.getProperty("jdbc.url");
    assertNotNull(selected);
    try (Connection connection = dataSource.getConnection()) {
      assertEquals(selected, connection.getMetaData().getURL());
    }
  }

  // CI passes -Djdbc.url, so the extension never starts a container there; start one directly.
  @Test
  void containerStartsWithBaselineSchema() throws SQLException {
    try (GenericContainer<?> db = TestDatabaseExtension.startContainer();
        Connection connection =
            DriverManager.getConnection(
                TestDatabaseExtension.jdbcUrl(db),
                TestDatabaseExtension.DB_USER,
                TestDatabaseExtension.DB_PASSWORD);
        ResultSet tables =
            connection.getMetaData().getTables(TestDatabaseExtension.DB_NAME, null, "User", null)) {
      assertTrue(tables.next(), "baseline User table missing");
    }
  }
}
