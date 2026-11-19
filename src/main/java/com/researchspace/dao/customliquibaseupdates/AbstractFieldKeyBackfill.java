package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.model.field.rule.FieldKeys;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;

/**
 * Gives existing fields a field key, generated in Java by {@link FieldKeys} as a lowercase UUID
 * version 7. SQL's {@code UUID()} would give version 1, which embeds the database host's node ID,
 * and is unsafe for statement-based replication.
 *
 * <p>It runs on the connection Liquibase supplies, so the changeset's transaction holds every
 * update and Liquibase rolls them all back if one fails. Ids are read and updated a page at a time.
 * Only rows that still have no key are updated, so running it again changes nothing.
 */
public abstract class AbstractFieldKeyBackfill extends AbstractCustomLiquibaseUpdater {

  private static final int PAGE_SIZE = 1000;

  int pageSize = PAGE_SIZE;

  private int keyed;

  /** The table whose {@code fieldKey} column this fills. */
  protected abstract String table();

  /**
   * Native SQL selecting, in id order, the ids of the next page of rows of {@link #table()} to key.
   * Its first parameter is the last id of the previous page ({@link Long#MIN_VALUE} for the first
   * page) and its second the page size.
   */
  protected abstract String pageOfIdsToKeySql();

  @Override
  public String getConfirmationMessage() {
    return "Gave " + keyed + " " + table() + " rows a field key";
  }

  @Override
  public void execute(Database database) throws CustomChangeException {
    logger.info("Calling custom task - {}", getClass().getName());
    try {
      backfill(((JdbcConnection) database.getConnection()).getUnderlyingConnection());
    } catch (SQLException e) {
      throw new CustomChangeException("Could not give " + table() + " rows a field key", e);
    }
  }

  /** Not called: {@link #execute} runs on Liquibase's connection, not in a Spring transaction. */
  @Override
  protected final void doExecute(Database database) {
    throw new UnsupportedOperationException("Run through execute(Database)");
  }

  /** Keys every eligible row, without committing; the caller owns the transaction. */
  void backfill(Connection connection) throws SQLException {
    keyed = 0;
    String updateSql = "update " + table() + " set fieldKey = ? where id = ? and fieldKey is null";
    try (PreparedStatement select = connection.prepareStatement(pageOfIdsToKeySql());
        PreparedStatement update = connection.prepareStatement(updateSql)) {
      long lastId = Long.MIN_VALUE;
      List<Long> page;
      do {
        page = nextPage(select, lastId);
        if (page.isEmpty()) {
          break;
        }
        for (Long id : page) {
          update.setString(1, FieldKeys.newFieldKey());
          update.setLong(2, id);
          update.addBatch();
        }
        for (int count : update.executeBatch()) {
          // a driver may report a successful update without its row count
          keyed += count == Statement.SUCCESS_NO_INFO ? 1 : Math.max(count, 0);
        }
        lastId = page.get(page.size() - 1);
        logger.info("Gave {} {} rows a field key so far, up to id {}", keyed, table(), lastId);
      } while (page.size() == pageSize);
    }
    logger.info(getConfirmationMessage());
  }

  private List<Long> nextPage(PreparedStatement select, long lastId) throws SQLException {
    select.setLong(1, lastId);
    select.setInt(2, pageSize);
    List<Long> ids = new ArrayList<>(pageSize);
    try (ResultSet rows = select.executeQuery()) {
      while (rows.next()) {
        ids.add(rows.getLong(1));
      }
    }
    return ids;
  }
}
