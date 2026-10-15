package com.researchspace.maintenance.service;

import static com.researchspace.maintenance.service.MaintenanceTestFixtures.filter;
import static com.researchspace.maintenance.service.MaintenanceTestFixtures.hoursFromNow;
import static com.researchspace.maintenance.service.MaintenanceTestFixtures.maintenance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.User;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.service.CollectionMutationException;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public class MaintenanceManagerBulkIT extends RealTransactionSpringTestBase {

  private static final String TEST_PREFIX = "apiV2BulkIT ";

  private @Autowired MaintenanceManager maintenanceManager;
  private @Autowired JdbcTemplate jdbcTemplate;
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
