package com.researchspace.maintenance.service;

import static com.researchspace.maintenance.service.MaintenanceTestFixtures.filter;
import static com.researchspace.maintenance.service.MaintenanceTestFixtures.hoursFromNow;
import static com.researchspace.maintenance.service.MaintenanceTestFixtures.maintenance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.User;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.service.CollectionMutationException;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import jakarta.persistence.OptimisticLockException;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

public class MaintenanceManagerBulkIT extends RealTransactionSpringTestBase {

  private static final String TEST_PREFIX = "apiV2BulkIT ";

  private final MaintenanceManager maintenanceManager;
  private final JdbcTemplate jdbcTemplate;

  @Autowired
  MaintenanceManagerBulkIT(MaintenanceManager maintenanceManager, JdbcTemplate jdbcTemplate) {
    this.maintenanceManager = maintenanceManager;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Autowired private PlatformTransactionManager transactions;

  private User sysUser;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    sysUser = logoutAndLoginAsSysAdmin();
  }

  /**
   * Removes exactly the rows this test created. These tests commit for real, so without it they
   * leak into whatever runs next. The class-level {@code DatabaseCleaner} would also clear them,
   * but only after the whole class and by way of an ordered delete list covering the entire schema;
   * deleting by prefix per test keeps the test self-contained and independent of that list.
   */
  @AfterEach
  public void removeRowsThisTestCreated() throws Exception {
    jdbcTemplate.update("DELETE FROM ScheduledMaintenance WHERE message LIKE ?", TEST_PREFIX + "%");
    super.tearDown();
  }

  @Test
  public void rollsBackEveryRowWhenOneBulkPatchIsInvalid() {
    ScheduledMaintenance first = saveMaintenance(2, 4, "first original");
    ScheduledMaintenance second = saveMaintenance(6, 8, "second original");
    ResourceRequest request =
        ResourceRequest.unpaged(filter("id=in=(%s,%s)".formatted(first.getId(), second.getId())));

    // An endDate of now+5h is after the first window's start but before the second's, so the patch
    // is valid for one matched row and invalid for the other.
    assertThrows(
        MaintenanceOperationException.class,
        () ->
            maintenanceManager.updateResources(
                request,
                ParsedDocument.update(
                    Map.of("endDate", hoursFromNow(5), "message", prefixed("partially updated"))),
                sysUser));

    assertEquals(
        prefixed("first original"),
        maintenanceManager.getScheduledMaintenance(first.getId()).getMessage(),
        "first row must not be left half-updated");
    assertEquals(
        prefixed("second original"),
        maintenanceManager.getScheduledMaintenance(second.getId()).getMessage(),
        "second row must not be left half-updated");
  }

  @Test
  public void rejectsOneThousandAndOnePersistedMatchesBeforeMutatingThem() {
    String message = prefixed("persisted bulk cap");
    insertMaintenances(message, 1001);
    ResourceRequest request = ResourceRequest.unpaged(filter("message=='%s'".formatted(message)));

    CollectionMutationException exception =
        assertThrows(
            CollectionMutationException.class,
            () ->
                maintenanceManager.updateResources(
                    request,
                    ParsedDocument.update(Map.of("message", prefixed("changed"))),
                    sysUser));

    assertEquals(CollectionMutationException.Reason.BULK_LIMIT, exception.getReason());
    assertEquals(
        Integer.valueOf(1001),
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ScheduledMaintenance WHERE message = ?", Integer.class, message),
        "no row may be mutated once the cap is exceeded");
  }

  @Test
  public void commitConflictRollsBackTheEntireBulkBatch() {
    ScheduledMaintenance first = saveMaintenance(2, 4, "commit first");
    ScheduledMaintenance second = saveMaintenance(6, 8, "commit second");
    var request =
        ResourceRequest.unpaged(filter("id=in=(%s,%s)".formatted(first.getId(), second.getId())));
    assertThrows(
        OptimisticLockException.class,
        () ->
            new TransactionTemplate(transactions)
                .executeWithoutResult(
                    status -> {
                      maintenanceManager.updateResources(
                          request,
                          ParsedDocument.update(Map.of("message", prefixed("uncommitted"))),
                          sysUser);
                      TransactionSynchronizationManager.registerSynchronization(
                          new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void beforeCommit(boolean readOnly) {
                              throw new OptimisticLockException("Conflict at commit");
                            }
                          });
                    }));
    assertEquals(
        prefixed("commit first"),
        maintenanceManager.getScheduledMaintenance(first.getId()).getMessage());
    assertEquals(
        prefixed("commit second"),
        maintenanceManager.getScheduledMaintenance(second.getId()).getMessage());
  }

  @Test
  public void serializesConditionalPatchesAndRejectsTheStaleWriter() throws Exception {
    ScheduledMaintenance row = saveMaintenance(2, 4, "version race");
    interleave(
        () ->
            maintenanceManager.updateResource(
                row.getId(),
                ParsedDocument.update(Map.of("message", prefixed("winner"))),
                row.getVersion(),
                sysUser),
        () ->
            assertThrows(
                OptimisticLockException.class,
                () ->
                    maintenanceManager.updateResource(
                        row.getId(),
                        ParsedDocument.update(Map.of("message", prefixed("loser"))),
                        row.getVersion(),
                        sysUser)));
    assertEquals(
        prefixed("winner"), maintenanceManager.getScheduledMaintenance(row.getId()).getMessage());
  }

  @Test
  public void bulkAndSinglePatchPreserveBothFields() throws Exception {
    ScheduledMaintenance row = saveMaintenance(2, 4, "bulk race");
    var end = Date.from(hoursFromNow(5).toInstant().truncatedTo(ChronoUnit.SECONDS));
    interleave(
        () ->
            maintenanceManager.updateResources(
                ResourceRequest.unpaged(filter("id==" + row.getId())),
                ParsedDocument.update(Map.of("message", prefixed("bulk changed"))),
                sysUser),
        () ->
            maintenanceManager.updateResource(
                row.getId(), ParsedDocument.update(Map.of("endDate", end)), null, sysUser));
    var current = maintenanceManager.getScheduledMaintenance(row.getId());
    assertEquals(prefixed("bulk changed"), current.getMessage());
    assertEquals(end, current.getEndDate());
  }

  @Test
  public void concurrentEntityDeletesReturnOneMissingRow() throws Exception {
    ScheduledMaintenance row = saveMaintenance(2, 4, "delete race");
    interleave(
        () -> assertTrue(maintenanceManager.removeResource(row.getId(), null, sysUser).isPresent()),
        () -> assertTrue(maintenanceManager.removeResource(row.getId(), null, sysUser).isEmpty()));
  }

  @Test
  void legacyEditAfterVersionedPatchLoadsTheCurrentVersion() {
    ScheduledMaintenance row = saveMaintenance(2, 4, "legacy original");
    maintenanceManager.updateResource(
        row.getId(),
        ParsedDocument.update(Map.of("message", prefixed("v2 updated"))),
        row.getVersion(),
        sysUser);
    ScheduledMaintenance legacy = maintenance(3, 5, prefixed("legacy updated"));
    legacy.setId(row.getId());
    maintenanceManager.saveScheduledMaintenance(legacy, sysUser);
    ScheduledMaintenance saved = maintenanceManager.getScheduledMaintenance(row.getId());
    assertEquals(legacy.getMessage(), saved.getMessage());
    assertEquals(
        legacy.getStartDate().toInstant().truncatedTo(ChronoUnit.SECONDS),
        saved.getStartDate().toInstant());
    assertTrue(saved.getVersion() > row.getVersion());
  }

  private void interleave(Runnable first, Runnable second) throws Exception {
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var entering = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var winner =
          executor.submit(
              () ->
                  new TransactionTemplate(transactions)
                      .executeWithoutResult(
                          status -> {
                            first.run();
                            held.countDown();
                            await(release);
                          }));
      assertTrue(held.await(10, TimeUnit.SECONDS));
      var loser =
          executor.submit(
              () -> {
                entering.countDown();
                second.run();
              });
      assertTrue(entering.await(10, TimeUnit.SECONDS));
      release.countDown();
      winner.get(20, TimeUnit.SECONDS);
      loser.get(20, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Latch timed out");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
  }

  private ScheduledMaintenance saveMaintenance(int startHours, int endHours, String message) {
    return maintenanceManager.saveScheduledMaintenance(
        maintenance(startHours, endHours, prefixed(message)), sysUser);
  }

  /** Batch SQL rather than 1001 manager calls: the cap is under test here, not persistence. */
  private void insertMaintenances(String message, int count) {
    Timestamp start = Timestamp.from(hoursFromNow(2).toInstant());
    Timestamp end = Timestamp.from(hoursFromNow(3).toInstant());
    jdbcTemplate.batchUpdate(
        """
        INSERT INTO ScheduledMaintenance (startDate, endDate, stopUserLoginDate, message)
        VALUES (?, ?, ?, ?)
        """,
        Collections.nCopies(count, new Object[] {start, end, start, message}));
  }

  private static String prefixed(String message) {
    return TEST_PREFIX + message;
  }
}
