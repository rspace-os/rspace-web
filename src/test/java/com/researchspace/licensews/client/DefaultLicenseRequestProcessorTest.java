package com.researchspace.licensews.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.licenseserver.model.License;
import com.researchspace.model.Role;
import com.researchspace.model.views.UserStatistics;
import com.researchspace.service.LicenseRequestResult;
import java.util.Calendar;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DefaultLicenseRequestProcessorTest {

  DefaultLicenseRequestProcessor handler;

  @Before
  public void setUp() throws Exception {
    handler = new DefaultLicenseRequestProcessor();
  }

  @After
  public void tearDown() throws Exception {}

  private void setUpDates(License license2) {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.YEAR, -1);
    license2.setActivationDate(cal.getTime());
    cal.add(Calendar.YEAR, 2);
    license2.setExpiryDate(cal.getTime());
  }

  @Test
  public void testProcessLicenseRequest() {
    License license = new License();
    final int TOTAL_LICENSE_SEATS = 100;
    final int TOTAL_RSPACEADMIN_SEATS = 5;
    license.setTotalFreeRSpaceadmin(TOTAL_RSPACEADMIN_SEATS);
    license.setTotalFreeSysadmin(3);
    license.setTotalUserSeats(TOTAL_LICENSE_SEATS);
    setUpDates(license);
    // we have 45 enabled users, of which 3  are admins. So we have 42  used  seats
    final int ENABLED_USERS = 45;
    UserStatistics userStats = new UserStatistics(50, ENABLED_USERS, 0, 10);
    final int RSPACE_ADMINCOUNT = 2;
    final int SYS_ADMINCOUNT = 1;
    userStats.setTotalEnabledRSpaceAdmins(RSPACE_ADMINCOUNT);
    userStats.setTotalEnabledSysAdmins(SYS_ADMINCOUNT);
    final int TOTAL_ADMIN_COUNT = RSPACE_ADMINCOUNT + SYS_ADMINCOUNT;
    // happy case
    LicenseRequestResult result =
        handler.processLicenseRequest(10, Role.USER_ROLE, userStats, license);
    assertTrue(result.isRequestOK());

    assertEquals(
        TOTAL_LICENSE_SEATS - ENABLED_USERS + TOTAL_ADMIN_COUNT, result.getAvailableSeats());

    // now let's request more seats than are available:
    result = handler.processLicenseRequest(60, Role.USER_ROLE, userStats, license);
    assertFalse(result.isRequestOK());
    assertEquals(
        TOTAL_LICENSE_SEATS - ENABLED_USERS + TOTAL_ADMIN_COUNT, result.getAvailableSeats());
    // now let's disable some users, so that we can create 40 more.
    userStats.setTotalEnabledUsers(ENABLED_USERS - 40);
    result = handler.processLicenseRequest(60, Role.USER_ROLE, userStats, license);
    assertTrue(result.isRequestOK());

    // now let's request some admin seats - 2 are left for sys admin
    result = handler.processLicenseRequest(2, Role.SYSTEM_ROLE, userStats, license);
    assertTrue(result.isRequestOK());
    assertEquals(2, result.getAvailableSeats());
    // this assumes we created 2 new sysadmins
    userStats.setTotalEnabledSysAdmins(SYS_ADMINCOUNT + 2);

    // now, no more are available:
    result = handler.processLicenseRequest(1, Role.SYSTEM_ROLE, userStats, license);
    assertFalse(result.isRequestOK());
    assertEquals(0, result.getAvailableSeats());

    // and for RSpace admins...
    final int REQUEST_ALL_AVAILABLE_ADMINS = TOTAL_RSPACEADMIN_SEATS - RSPACE_ADMINCOUNT;
    result =
        handler.processLicenseRequest(
            REQUEST_ALL_AVAILABLE_ADMINS, Role.ADMIN_ROLE, userStats, license);
    assertTrue(result.isRequestOK());
    assertEquals(REQUEST_ALL_AVAILABLE_ADMINS, result.getAvailableSeats());
    // this assumes we created 2 new sysadmins
    userStats.setTotalEnabledRSpaceAdmins(TOTAL_RSPACEADMIN_SEATS);

    // now, no more are available:
    result = handler.processLicenseRequest(1, Role.ADMIN_ROLE, userStats, license);
    assertFalse(result.isRequestOK());
    assertEquals(0, result.getAvailableSeats());
  }
}
