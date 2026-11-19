package com.researchspace.dao.customliquibaseupdates;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

/**
 * Creates the native sequences that Communication and CommunicationTarget take their IDs from. Each
 * sequence starts above every existing ID and every ID the old hibernate_sequences counter may have
 * handed out, so no new ID can repeat an old one.
 */
public class CreateCommunicationSequences implements CustomTaskChange {

  /** Hibernate's pooled optimizer treats each sequence value as the top of a block of 50 IDs. */
  static final int BLOCK_SIZE = 50;

  private static final List<String> COMMUNICATION_TABLES =
      List.of(
          "Notification",
          "MessageOrRequest",
          "GroupMessageOrRequest",
          "CreateGroupMessageOrRequest",
          "ShareRecordMessageOrRequest");

  private long communicationStart;
  private long targetStart;

  @Override
  public void execute(Database database) throws CustomChangeException {
    Connection connection = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
    try (Statement statement = connection.createStatement()) {
      communicationStart =
          createSequence(statement, "communication_seq", "Communication", COMMUNICATION_TABLES);
      targetStart =
          createSequence(
              statement,
              "communication_target_seq",
              "CommunicationTarget",
              List.of("CommunicationTarget"));
    } catch (SQLException e) {
      throw new CustomChangeException("Could not create the communication ID sequences", e);
    }
  }

  private static long createSequence(
      Statement statement, String sequence, String oldCounter, List<String> tables)
      throws SQLException {
    long highestUsedId =
        queryLong(
            statement,
            "SELECT COALESCE(MAX(next_val), 0) FROM hibernate_sequences WHERE sequence_name = '"
                + oldCounter
                + "'");
    for (String table : tables) {
      highestUsedId =
          Math.max(
              highestUsedId, queryLong(statement, "SELECT COALESCE(MAX(id), 0) FROM " + table));
    }
    long start = firstSequenceValue(highestUsedId);
    statement.execute(
        "CREATE SEQUENCE "
            + sequence
            + " START WITH "
            + start
            + " INCREMENT BY "
            + BLOCK_SIZE
            + " NOCACHE");
    return start;
  }

  /** Returns the first sequence value whose whole block of IDs is above {@code highestUsedId}. */
  static long firstSequenceValue(long highestUsedId) {
    return (highestUsedId / BLOCK_SIZE + 2) * BLOCK_SIZE;
  }

  private static long queryLong(Statement statement, String sql) throws SQLException {
    try (ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  @Override
  public String getConfirmationMessage() {
    return "Created communication_seq starting at "
        + communicationStart
        + " and communication_target_seq starting at "
        + targetStart;
  }

  @Override
  public void setUp() {}

  @Override
  public void setFileOpener(ResourceAccessor resourceAccessor) {}

  @Override
  public ValidationErrors validate(Database database) {
    return new ValidationErrors();
  }
}
