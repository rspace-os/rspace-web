package com.researchspace.dao.customliquibaseupdates;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CreateCommunicationSequencesTest {

  private static final String COMMUNICATION_NEXT_VALUE =
      "SELECT next_not_cached_value FROM communication_seq";
  private static final String TARGET_NEXT_VALUE =
      "SELECT next_not_cached_value FROM communication_target_seq";

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Notification",
        "MessageOrRequest",
        "GroupMessageOrRequest",
        "CreateGroupMessageOrRequest",
        "ShareRecordMessageOrRequest"
      })
  void startsEachSequenceAboveItsHighestIdAndOldCounter(String tableWithHighestId)
      throws Exception {
    Statement statement = runWith(tableWithHighestId, Map.of(COMMUNICATION_NEXT_VALUE, 40_100L));

    // The first block of 50 (40051 to 40100) is entirely above 40000.
    verify(statement)
        .execute(
            "CREATE SEQUENCE IF NOT EXISTS communication_seq START WITH 40100 INCREMENT BY 50"
                + " NOCACHE");
    verify(statement)
        .execute(
            "CREATE SEQUENCE IF NOT EXISTS communication_target_seq START WITH 30100 INCREMENT BY"
                + " 50 NOCACHE");
    verify(statement, never()).execute(startsWith("ALTER SEQUENCE"));
  }

  @Test
  void movesUpASequenceLeftBehindBelowTheNewStart() throws Exception {
    Statement statement = runWith("Notification", Map.of(COMMUNICATION_NEXT_VALUE, 20_050L));

    verify(statement).execute("ALTER SEQUENCE communication_seq RESTART WITH 40100");
    verify(statement, never()).execute(startsWith("ALTER SEQUENCE communication_target_seq"));
  }

  /**
   * Runs the change with the highest communication ID (40000) in {@code tableWithHighestId}, old
   * counters of 20000 and 30000, and the given sequence next values.
   */
  private static Statement runWith(String tableWithHighestId, Map<String, Long> nextValues)
      throws Exception {
    Map<String, Long> results = new HashMap<>(nextValues);
    results.put(
        "SELECT COALESCE(MAX(next_val), 0) FROM hibernate_sequences"
            + " WHERE sequence_name = 'Communication'",
        20_000L);
    results.put("SELECT COALESCE(MAX(id), 0) FROM " + tableWithHighestId, 40_000L);
    results.put(
        "SELECT COALESCE(MAX(next_val), 0) FROM hibernate_sequences"
            + " WHERE sequence_name = 'CommunicationTarget'",
        30_000L);
    results.put("SELECT COALESCE(MAX(id), 0) FROM CommunicationTarget", 100L);
    results.put(TARGET_NEXT_VALUE, 30_100L);
    Statement statement = mock(Statement.class);
    when(statement.executeQuery(anyString()))
        .thenAnswer(
            invocation -> {
              ResultSet result = mock(ResultSet.class);
              when(result.next()).thenReturn(true);
              when(result.getLong(1))
                  .thenReturn(results.getOrDefault(invocation.getArgument(0, String.class), 0L));
              return result;
            });
    Connection connection = mock(Connection.class);
    when(connection.createStatement()).thenReturn(statement);
    Database database = mock(Database.class);
    when(database.getConnection()).thenReturn(new JdbcConnection(connection));

    new CreateCommunicationSequences().execute(database);
    return statement;
  }
}
