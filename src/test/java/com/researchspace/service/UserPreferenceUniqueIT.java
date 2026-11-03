package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.preference.Preference;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import liquibase.change.core.AddUniqueConstraintChange;
import liquibase.change.core.RawSQLChange;
import liquibase.changelog.ChangeLogParameters;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class UserPreferenceUniqueIT extends RealTransactionSpringTestBase {
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void migrationKeepsHighestIdAndCreatesUniqueKey() throws Exception {
    // Run the production changes against an isolated fixture table in the same throwaway schema.
    try (Connection connection = dataSource.getConnection();
        var resources = new ClassLoaderResourceAccessor()) {
      try {
        try (var statement = connection.createStatement()) {
          statement.execute(
              "CREATE TABLE UserPreferenceMigrationFixture (id BIGINT PRIMARY KEY, user_id BIGINT,"
                  + " preference INT, value VARCHAR(255))");
          statement.execute(
              "INSERT INTO UserPreferenceMigrationFixture VALUES (9, 1, 1, 'newest'), (2, 1, 1,"
                  + " 'old'), (6, 1, 1, 'middle'), (5, 2, 1, 'other user'), (3, 1, 2, 'other"
                  + " preference')");
        }
        String file = "sqlUpdates/changeLog-user-preference-unique.xml";
        var changelog =
            ChangeLogParserFactory.getInstance()
                .getParser(file, resources)
                .parse(file, new ChangeLogParameters(), resources);
        var database =
            DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        var changeset = changelog.getChangeSets().get(0);
        for (var change : changeset.getChanges()) {
          if (change instanceof RawSQLChange sql) {
            sql.setSql(sql.getSql().replace("UserPreference", "UserPreferenceMigrationFixture"));
          } else if (change instanceof AddUniqueConstraintChange unique) {
            unique.setTableName("UserPreferenceMigrationFixture");
          }
        }
        changeset.execute(changelog, database);
        try (var statement = connection.createStatement()) {
          try (var rows =
              statement.executeQuery(
                  "SELECT id, value FROM UserPreferenceMigrationFixture WHERE user_id=1 AND"
                      + " preference=1")) {
            assertTrue(rows.next());
            assertEquals(9L, rows.getLong(1));
            assertEquals("newest", rows.getString(2));
            assertFalse(rows.next());
          }
          try (var rows =
              statement.executeQuery("SELECT COUNT(*) FROM UserPreferenceMigrationFixture")) {
            assertTrue(rows.next());
            assertEquals(3L, rows.getLong(1));
          }
          SQLException duplicate =
              assertThrows(
                  SQLException.class,
                  () ->
                      statement.executeUpdate(
                          "INSERT INTO UserPreferenceMigrationFixture VALUES (10, 1, 1,"
                              + " 'duplicate')"));
          assertEquals(1062, duplicate.getErrorCode());
        }
      } finally {
        try (var statement = connection.createStatement()) {
          statement.execute("DROP TABLE IF EXISTS UserPreferenceMigrationFixture");
        }
      }
    }
  }

  @Test
  void committedUpdateInvalidatesPreviouslyCachedValue() {
    User user = createAndSaveUser(getRandomAlphabeticString("prefupdate"));
    UserPreference original = userMgr.setPreference(Preference.BOX, "true", user.getUsername());
    assertTrue(userMgr.getPreferenceForUser(user, Preference.BOX).getValueAsBoolean());
    UserPreference updated = userMgr.setPreference(Preference.BOX, "false", user.getUsername());
    assertEquals(original.getId(), updated.getId());
    assertFalse(userMgr.getPreferenceForUser(user, Preference.BOX).getValueAsBoolean());
  }

  @Test
  void concurrentFirstWritesLeaveOneRowAndCacheMatchesDatabase() throws Exception {
    User user = createAndSaveUser(getRandomAlphabeticString("pref"));
    Preference preference = Preference.NOTIFICATION_DOCUMENT_SHARED_PREF;
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch write = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var first =
          executor.submit(
              () ->
                  new TransactionTemplate(transactionManager)
                      .execute(
                          status -> {
                            assertNull(
                                userMgr
                                    .get(user.getId())
                                    .getValueForPreference(preference)
                                    .getId());
                            ready.countDown();
                            assertDoesNotThrow(() -> assertTrue(write.await(10, TimeUnit.SECONDS)));
                            return userMgr.setPreference(preference, "true", user.getUsername());
                          }));
      var second =
          executor.submit(
              () ->
                  new TransactionTemplate(transactionManager)
                      .execute(
                          status -> {
                            assertNull(
                                userMgr
                                    .get(user.getId())
                                    .getValueForPreference(preference)
                                    .getId());
                            ready.countDown();
                            assertDoesNotThrow(() -> assertTrue(write.await(10, TimeUnit.SECONDS)));
                            return userMgr.setPreference(preference, "false", user.getUsername());
                          }));
      assertTrue(ready.await(10, TimeUnit.SECONDS));
      write.countDown();
      assertNotNull(first.get(20, TimeUnit.SECONDS).getId());
      assertNotNull(second.get(20, TimeUnit.SECONDS).getId());
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM UserPreference WHERE user_id=? AND preference=?",
              Integer.class,
              user.getId(),
              preference.ordinal()));
      String value =
          jdbc.queryForObject(
              "SELECT value FROM UserPreference WHERE user_id=? AND preference=?",
              String.class,
              user.getId(),
              preference.ordinal());
      UserPreference cached = userMgr.getPreferenceForUser(user, preference);
      assertEquals(value, cached.getValue());
      assertEquals(value, userMgr.getPreferenceForUser(user, preference).getValue());
    } finally {
      write.countDown();
      executor.shutdownNow();
    }
  }
}
