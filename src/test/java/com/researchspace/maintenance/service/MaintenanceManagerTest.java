package com.researchspace.maintenance.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.researchspace.Constants;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Calendar;
import java.util.Date;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Unit tests covering scheduled maintenance. */
public class MaintenanceManagerTest extends SpringTransactionalTest {

  @Autowired private MaintenanceManager maintenanceManager;

  private User sysUser;
  private User regularUser;

  private static final String TEST_MSG = "test maintenance message";

  private static Date dateNow;
  private static Date dateLastHour;
  private static Date dateNextHour;
  private static Date dateNextDay;

  @BeforeClass
  public static void initDates() {
    Calendar cal = Calendar.getInstance();
    dateNow = cal.getTime();

    cal.add(Calendar.HOUR_OF_DAY, -1);
    dateLastHour = cal.getTime();

    cal.add(Calendar.HOUR_OF_DAY, 2);
    dateNextHour = cal.getTime();

    cal.add(Calendar.HOUR_OF_DAY, -1);
    cal.add(Calendar.DAY_OF_MONTH, 1);
    dateNextDay = cal.getTime();
  }

  @Before
  public void setUp() {
    initSysadminUser();
  }

  private void initSysadminUser() {
    sysUser = createAndSaveUserIfNotExists("testMaintenanceSysadmin", Constants.SYSADMIN_ROLE);
    regularUser = createAndSaveUserIfNotExists("testMaintenanceUser");
  }

  @Test
  public void testSaveRetrieveScheduledMaintenance() {

    ScheduledMaintenance newMaintenance = new ScheduledMaintenance(dateNow, dateNextHour);
    newMaintenance.setMessage(TEST_MSG);

    ScheduledMaintenance saved =
        maintenanceManager.saveScheduledMaintenance(newMaintenance, sysUser);
    assertNotNull(saved);

    ScheduledMaintenance retrieved = maintenanceManager.getScheduledMaintenance(saved.getId());
    assertNotNull(retrieved);
    assertEquals(
        "saved and retrieved message should be the same", TEST_MSG, retrieved.getMessage());

    // cleanup
    maintenanceManager.removeScheduledMaintenance(saved.getId(), sysUser);
  }

  @Test(expected = AuthorizationException.class)
  public void onlySysadminCanSaveScheduledMaintenance() {
    maintenanceManager.saveScheduledMaintenance(new ScheduledMaintenance(null, null), regularUser);
  }

  @Test
  public void testRetrievingNextScheduledMaintenance() {

    ScheduledMaintenance nextMaintenance = maintenanceManager.getNextScheduledMaintenance();
    assertEquals(ScheduledMaintenance.NULL, nextMaintenance);

    ScheduledMaintenance firstMaintenance = new ScheduledMaintenance(dateNextHour, dateNextDay);
    ScheduledMaintenance firstSavedMaintenance =
        maintenanceManager.saveScheduledMaintenance(firstMaintenance, sysUser);

    nextMaintenance = maintenanceManager.getNextScheduledMaintenance();
    assertNotNull(nextMaintenance);
    assertEquals(
        "next maintenance should be the saved one", firstSavedMaintenance, nextMaintenance);

    ScheduledMaintenance secondMaintenance = new ScheduledMaintenance(dateLastHour, dateNextHour);
    ScheduledMaintenance secondSavedMaintenance =
        maintenanceManager.saveScheduledMaintenance(secondMaintenance, sysUser);

    nextMaintenance = maintenanceManager.getNextScheduledMaintenance();
    assertNotNull(nextMaintenance);
    assertEquals(
        "next maintenance should be closest future maintenance",
        secondSavedMaintenance,
        nextMaintenance);

    // cleanup
    maintenanceManager.removeScheduledMaintenance(firstMaintenance.getId(), sysUser);
    maintenanceManager.removeScheduledMaintenance(secondMaintenance.getId(), sysUser);
  }

  @Test
  public void testRemovingScheduledMaintenance() {

    ScheduledMaintenance newMaintenance = new ScheduledMaintenance(dateNow, dateNextDay);
    maintenanceManager.saveScheduledMaintenance(newMaintenance, sysUser);

    ScheduledMaintenance nextMaintenance = maintenanceManager.getNextScheduledMaintenance();
    assertNotNull(nextMaintenance);

    maintenanceManager.removeScheduledMaintenance(newMaintenance.getId(), sysUser);

    nextMaintenance = maintenanceManager.getNextScheduledMaintenance();
    assertEquals(ScheduledMaintenance.NULL, nextMaintenance);
  }

  @Test(expected = AuthorizationException.class)
  public void onlySysadminCanRemoveScheduledMaintenance() {
    maintenanceManager.removeScheduledMaintenance(1L, regularUser);
  }
}
