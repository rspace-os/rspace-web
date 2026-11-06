package com.researchspace.dao.customliquibaseupdates;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import org.junit.jupiter.api.Test;

class CreateCommunicationSequencesTest {

  @Test
  void startsEachSequenceAboveItsHighestIdAndOldCounter() throws Exception {
    // The highest ID is in MessageOrRequest, not Notification, so every table must count.
    Map<String, Long> results =
        Map.of(
            "SELECT COALESCE(MAX(next_val), 0) FROM hibernate_sequences"
                + " WHERE sequence_name = 'Communication'",
            20_000L,
            "SELECT COALESCE(MAX(id), 0) FROM MessageOrRequest",
            40_000L,
            "SELECT COALESCE(MAX(next_val), 0) FROM hibernate_sequences"
                + " WHERE sequence_name = 'CommunicationTarget'",
            30_000L,
            "SELECT COALESCE(MAX(id), 0) FROM CommunicationTarget",
            100L);
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

    // The first block of 50 (40051 to 40100) is entirely above 40000.
    verify(statement)
        .execute("CREATE SEQUENCE communication_seq START WITH 40100 INCREMENT BY 50 NOCACHE");
    verify(statement)
        .execute(
            "CREATE SEQUENCE communication_target_seq START WITH 30100 INCREMENT BY 50 NOCACHE");
  }
}
