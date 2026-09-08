package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Characterises the SCOPE of {@link GenericDao#lockRowForUpdate}: exclusive on exactly one row in
 * exactly one table (code review; RSDEV-1231).
 *
 * <p>The regression this pins: locking a loaded entity makes Hibernate emit the entity's
 * eager-fetch SELECT (a ~20-table join for a subsample) with {@code FOR UPDATE} on the end, taking
 * {@code lock_mode X} on rows in every joined table (measured: "mysql tables in use 21, locked
 * 20"), including the subsample's parent {@code Container}. That deadlocked inventory operations
 * against unrelated features. Lock scope is only observable across connections, so this test holds
 * the lock in one transaction and probes from a raw second connection; the single-connection
 * behaviour of the method is pinned in {@code SampleDaoTest}.
 */
public class GenericDaoLockScopeIT extends RealTransactionSpringTestBase {

  private @Autowired SubSampleDao subSampleDao;

  @Test
  public void lockRowForUpdateLocksOnlyTheSubSampleRowNotItsFetchGraph() throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    Long subSampleId = sample.getSubSamples().get(0).getId();
    Long sampleId = sample.getId();
    Long userId = user.getId();
    // the subsample's workbench: a row the old graph lock held via the eager-fetch join
    Long workbenchId = getWorkbenchForUser(user).getId();

    openTransaction();
    try {
      subSampleDao.lockRowForUpdate(subSampleId);

      // Probed from a raw second connection rather than information_schema.innodb_trx (whose
      // trx_tables_locked count needs the PROCESS privilege the app's DB user does not have).
      // A row is free exactly when another transaction can take FOR UPDATE on it within the
      // 1s lock wait; the old graph lock held all four rows probed here.
      try (Connection secondConnection = dataSource.getConnection()) {
        secondConnection.setAutoCommit(false);
        try (Statement statement = secondConnection.createStatement()) {
          statement.execute("set session innodb_lock_wait_timeout = 1");
          // Narrowness: rows the old eager-fetch join locked alongside the subsample must be free.
          assertRowLockable(statement, "Container", workbenchId);
          assertRowLockable(statement, "Sample", sampleId);
          assertRowLockable(statement, "User", userId);
          // Effectiveness: the subsample's own row must be held, so the same probe times out.
          assertThrows(
              SQLException.class,
              () ->
                  statement.executeQuery(
                      "select id from SubSample where id = " + subSampleId + " for update"),
              "the locked subsample row should not be lockable from a second connection");
        } finally {
          secondConnection.rollback();
        }
      }
    } finally {
      commitTransaction();
    }
  }

  private static void assertRowLockable(Statement statement, String table, Long id)
      throws SQLException {
    try (ResultSet row =
        statement.executeQuery("select id from " + table + " where id = " + id + " for update")) {
      assertTrue(row.next(), table + " row should exist and be lockable by another transaction");
    }
  }
}
