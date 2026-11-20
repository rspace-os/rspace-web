package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.researchspace.model.User;
import com.researchspace.model.dtos.FormFieldSaveItem;
import com.researchspace.model.dtos.StringFieldDTO;
import com.researchspace.model.record.RSForm;
import com.researchspace.service.FormManager.FieldEditorForm;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.shiro.SecurityUtils;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Form field editor saves that overlap: of two saves made from one revision, only one applies. */
public class FormFieldsConcurrentSaveIT extends RealTransactionSpringTestBase {

  private User user;
  private ExecutorService pool;
  private Long formId;
  private Long fieldId;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    user = createInitAndLoginAnyUser();
    pool = Executors.newFixedThreadPool(2);
    RSForm form = formMgr.create(user);
    formId = form.getId();
    formMgr.saveFields(formId, List.of(rename(null, "Original")), user);
    fieldId = doInTransaction(() -> formMgr.get(formId, user).getFieldForms().get(0).getId());
  }

  @AfterEach
  public void tearDown() throws Exception {
    pool.shutdownNow();
    super.tearDown();
  }

  @Test
  public void ofTwoOverlappingSavesFromOneRevisionOnlyTheFirstApplies() throws Exception {
    long read = revision();
    CountDownLatch firstSaved = new CountDownLatch(1);
    CountDownLatch commitFirst = new CountDownLatch(1);
    // the first save has advanced the revision, and holds its transaction open
    Future<?> first =
        submit(
            () ->
                new TransactionTemplate(getTxMger())
                    .execute(
                        tx -> {
                          formMgr.saveFields(formId, List.of(rename(fieldId, "First")), read, user);
                          firstSaved.countDown();
                          awaitQuietly(commitFirst);
                          return null;
                        }));
    assertTrue(firstSaved.await(30, TimeUnit.SECONDS));

    Future<FieldEditorForm> second =
        submit(() -> formMgr.saveFields(formId, List.of(rename(fieldId, "Second")), read, user));
    awaitWaitingForTheFirst();
    assertFalse(second.isDone());
    commitFirst.countDown();
    first.get(30, TimeUnit.SECONDS);

    ExecutionException refused =
        assertThrows(ExecutionException.class, () -> second.get(30, TimeUnit.SECONDS));
    assertInstanceOf(FormFieldsConflictException.class, refused.getCause());
    assertEquals("First", fieldName());
    assertEquals(read + 1, revision());
  }

  /**
   * With MariaDB's innodb_snapshot_isolation, which this test turns on for its own transaction, a
   * locking write to a row that changed after the transaction first read anything fails; that is a
   * conflict, not an error. Skipped on a server without the variable (it came with MariaDB 11.x
   * point releases, so a 10.11 server may not have it).
   */
  @Test
  public void aSaveWhoseTransactionReadBeforeAnotherSaveCommittedIsAConflict() throws Exception {
    assumeTrue(
        !new JdbcTemplate(dataSource)
            .queryForList("show variables like 'innodb_snapshot_isolation'")
            .isEmpty(),
        "this MariaDB server has no innodb_snapshot_isolation");
    long read = revision();
    CountDownLatch snapshotTaken = new CountDownLatch(1);
    CountDownLatch saveLate = new CountDownLatch(1);
    Future<?> late =
        submit(
            () ->
                new TransactionTemplate(getTxMger())
                    .execute(
                        tx -> {
                          var session = sessionFactory.getCurrentSession();
                          setSnapshotIsolation(session, "ON");
                          // Put back on this connection before it returns to the pool, whether
                          // the transaction commits or rolls back.
                          TransactionSynchronizationManager.registerSynchronization(
                              new TransactionSynchronization() {
                                @Override
                                public void beforeCompletion() {
                                  setSnapshotIsolation(session, "DEFAULT");
                                }
                              });
                          // any read starts the transaction's snapshot
                          session.createNativeQuery("select count(*) from User").uniqueResult();
                          snapshotTaken.countDown();
                          awaitQuietly(saveLate);
                          return formMgr.saveFields(
                              formId, List.of(rename(fieldId, "Late")), read, user);
                        }));
    assertTrue(snapshotTaken.await(30, TimeUnit.SECONDS));

    formMgr.saveFields(formId, List.of(rename(fieldId, "Earlier")), read, user);
    saveLate.countDown();

    ExecutionException refused =
        assertThrows(ExecutionException.class, () -> late.get(30, TimeUnit.SECONDS));
    assertInstanceOf(FormFieldsConflictException.class, refused.getCause());
    assertEquals("Earlier", fieldName());
    assertEquals(read + 1, revision());
  }

  @Test
  public void aSaveThatChangesNothingKeepsTheRevision() throws Exception {
    long read = revision();

    FieldEditorForm saved =
        formMgr.saveFields(formId, List.of(rename(fieldId, "Original")), read, user);

    assertEquals(read, saved.fieldsRevision());
    assertEquals(read, revision());
    assertEquals(
        read + 1,
        formMgr
            .saveFields(formId, List.of(rename(fieldId, "Renamed")), read, user)
            .fieldsRevision());
  }

  /** Waits until the second save's conditional update is waiting for the first save's lock. */
  private void awaitWaitingForTheFirst() throws InterruptedException {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Integer waiting =
          jdbc.queryForObject(
              "select count(*) from information_schema.processlist"
                  + " where info like 'update RSForm set fieldsRevision%'",
              Integer.class);
      if (waiting != null && waiting > 0) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("the second save never waited for the first");
  }

  private <T> Future<T> submit(Callable<T> task) {
    return pool.submit(SecurityUtils.getSubject().associateWith(task));
  }

  private long revision() {
    return formMgr.getForFieldEditor(formId, user).fieldsRevision();
  }

  /** On the session's own connection, so it never reaches a connection another test uses. */
  private static void setSnapshotIsolation(Session session, String value) {
    session.doWork(
        connection -> {
          try (Statement statement = connection.createStatement()) {
            statement.execute("set session innodb_snapshot_isolation = " + value);
          }
        });
  }

  private String fieldName() throws Exception {
    return doInTransaction(() -> formMgr.getField(fieldId).getName());
  }

  private static FormFieldSaveItem rename(Long id, String name) {
    return new FormFieldSaveItem(
        id, "String", null, null, new StringFieldDTO<>(name, false, "no", ""));
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
