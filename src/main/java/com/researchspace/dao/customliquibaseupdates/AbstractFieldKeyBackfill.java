package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.model.field.rule.FieldKeys;
import java.sql.PreparedStatement;
import java.util.List;
import liquibase.database.Database;
import org.hibernate.Session;

/**
 * Gives existing fields a field key, generated in Java by {@link FieldKeys} as a lowercase UUID
 * version 7. SQL's {@code UUID()} would give version 1, which embeds the database host's node ID,
 * and is unsafe for statement-based replication.
 *
 * <p>Only rows that still have no key are updated, so running it again changes nothing.
 */
public abstract class AbstractFieldKeyBackfill extends AbstractCustomLiquibaseUpdater {

  private static final int BATCH_SIZE = 1000;

  private int keyed;

  /** The table whose {@code fieldKey} column this fills. */
  protected abstract String table();

  /**
   * Native SQL selecting the ids of the rows of {@link #table()} to key, in the order keys are
   * given.
   */
  protected abstract String idsToKeySql();

  @Override
  public String getConfirmationMessage() {
    return "Gave " + keyed + " " + table() + " rows a field key";
  }

  @Override
  protected void doExecute(Database database) {
    Session session = sessionFactory.getCurrentSession();
    List<Long> ids = session.createNativeQuery(idsToKeySql(), Long.class).list();
    String sql = "update " + table() + " set fieldKey = ? where id = ? and fieldKey is null";
    session.doWork(
        connection -> {
          try (PreparedStatement update = connection.prepareStatement(sql)) {
            int pending = 0;
            for (Long id : ids) {
              update.setString(1, FieldKeys.newFieldKey());
              update.setLong(2, id);
              update.addBatch();
              if (++pending == BATCH_SIZE) {
                update.executeBatch();
                pending = 0;
              }
            }
            update.executeBatch();
          }
        });
    keyed = ids.size();
    logger.info(getConfirmationMessage());
  }
}
