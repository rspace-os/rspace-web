package com.researchspace.maintenance.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.User;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RsqlFilterParser;
import com.researchspace.service.CollectionMutationException;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

public class MaintenanceManagerBulkIT extends RealTransactionSpringTestBase {

  private static final String TEST_PREFIX = "apiV2BulkIT ";

  private @Autowired MaintenanceManager maintenanceManager;
  private @Autowired JdbcTemplate jdbcTemplate;
  private User sysUser;

  @Before
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
  @After
  public void removeRowsThisTestCreated() throws Exception {
    jdbcTemplate.update("DELETE FROM ScheduledMaintenance WHERE message LIKE ?", TEST_PREFIX + "%");
    super.tearDown();
  }

  @Test
  public void rollsBackEveryRowWhenOneBulkPatchIsInvalid() {
    ScheduledMaintenance first = saveMaintenance(2, 4, "first original");
    ScheduledMaintenance second = saveMaintenance(6, 8, "second original");
    ResourceRequest request = filter("id=in=(" + first.getId() + "," + second.getId() + ")");

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
        "first row must not be left half-updated",
        prefixed("first original"),
        maintenanceManager.getScheduledMaintenance(first.getId()).getMessage());
    assertEquals(
        "second row must not be left half-updated",
        prefixed("second original"),
        maintenanceManager.getScheduledMaintenance(second.getId()).getMessage());
  }

  @Test
  public void rejectsOneThousandAndOnePersistedMatchesBeforeMutatingThem() {
    String message = prefixed("persisted bulk cap");
    insertMaintenances(message, 1001);
    ResourceRequest request = filter("message=='" + message + "'");

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
        "no row may be mutated once the cap is exceeded",
        Integer.valueOf(1001),
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ScheduledMaintenance WHERE message = ?", Integer.class, message));
  }

  private static ResourceRequest filter(String rsql) {
    return ResourceRequest.unpaged(
        new RsqlFilterParser(ApiV2MaintenanceResource.DESCRIPTION).parse(rsql));
  }

  private ScheduledMaintenance saveMaintenance(int startHours, int endHours, String message) {
    ScheduledMaintenance maintenance =
        new ScheduledMaintenance(hoursFromNow(startHours), hoursFromNow(endHours));
    maintenance.setMessage(prefixed(message));
    return maintenanceManager.saveScheduledMaintenance(maintenance, sysUser);
  }

  /** Batch SQL rather than 1001 manager calls: the cap is under test here, not persistence. */
  private void insertMaintenances(String message, int count) {
    Timestamp start = new Timestamp(hoursFromNow(2).getTime());
    Timestamp end = new Timestamp(hoursFromNow(3).getTime());
    jdbcTemplate.batchUpdate(
        """
        INSERT INTO ScheduledMaintenance (startDate, endDate, stopUserLoginDate, message)
        VALUES (?, ?, ?, ?)
        """,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement statement, int index) throws SQLException {
            statement.setTimestamp(1, start);
            statement.setTimestamp(2, end);
            statement.setTimestamp(3, start);
            statement.setString(4, message);
          }

          @Override
          public int getBatchSize() {
            return count;
          }
        });
  }

  private static String prefixed(String message) {
    return TEST_PREFIX + message;
  }

  private static Date hoursFromNow(int hours) {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.HOUR_OF_DAY, hours);
    return calendar.getTime();
  }
}
