package com.researchspace.webapp.filter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.auth.MaintenanceLoginAuthorizer;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.model.User;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Date;
import java.util.List;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class BaseShiroFormAuthFilterExtTest extends SpringTransactionalTest {

  private BaseShiroFormAuthFilterExt filter;

  private MockHttpServletRequest req;
  private MockHttpServletResponse resp;

  private ScheduledMaintenance testMaintenance;

  private MaintenanceManager maintenanceManagerStub =
      new MaintenanceManager() {
        @Override
        public ScheduledMaintenance getNextScheduledMaintenance() {
          return testMaintenance;
        }

        public ScheduledMaintenance getScheduledMaintenance(Long id) {
          return null;
        }

        public List<ScheduledMaintenance> getAllFutureMaintenances() {
          return null;
        }

        public ScheduledMaintenance saveScheduledMaintenance(ScheduledMaintenance m, User user) {
          return null;
        }

        public void removeScheduledMaintenance(Long id, User user) {}
      };

  @Before
  public void setUp() {
    filter = new BaseShiroFormAuthFilterExt();
    filter.setUserMgr(userMgr);

    MaintenanceLoginAuthorizer maintAuthorizer = new MaintenanceLoginAuthorizer();
    maintAuthorizer.setMaintenanceMgr(maintenanceManagerStub);
    filter.setMaintenanceLoginAuthorizer(maintAuthorizer);

    // sets maintenance starting now and ending in 10 seconds
    Date now = new Date();
    testMaintenance = new ScheduledMaintenance(now, new Date(now.getTime() + 10000));

    initHttpReqAndResp();
  }

  private void initHttpReqAndResp() {
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void onMaintenanceUnauthenticatedRequestIsRedirectedToMaintenancePage() {

    assertFalse(filter.isAccessAllowed(req, resp, null));
    assertTrue(
        "should be redirected to maintenance, but no location header",
        resp.containsHeader("Location"));
    assertTrue(
        "should be redirected to maintenance, but location was " + resp.getHeaderValue("Location"),
        resp.getHeaderValue("Location")
            .toString()
            .contains(MaintenanceLoginAuthorizer.REDIRECT_FOR_MAINTENANCE));

    Subject subject = SecurityUtils.getSubject();
    assertFalse(subject.isAuthenticated());
  }

  @Test
  public void onMaintenanceLoggedUserIsNotRedirectedAnywhere() {

    User u = createAndSaveUserIfNotExists(CoreTestUtils.getRandomName(10));
    RSpaceTestUtils.login(u.getUsername(), TESTPASSWD);

    assertTrue(filter.isAccessAllowed(req, resp, null));
    assertFalse(
        "authenticated user should not be redirected, but location header is set to "
            + resp.getHeaderValue("Location"),
        resp.containsHeader("Location"));

    RSpaceTestUtils.logout();
  }

  @Test
  public void onMaintenanceUnauthenticatedRequestIsAllowedWithRequestParameter() {

    assertFalse(filter.isAccessAllowed(req, resp, null));
    assertTrue(
        "should be redirected to maintenance, but no location header",
        resp.containsHeader("Location"));
    assertTrue(
        "should be redirected to maintenance, but location was " + resp.getHeaderValue("Location"),
        resp.getHeaderValue("Location")
            .toString()
            .contains(MaintenanceLoginAuthorizer.REDIRECT_FOR_MAINTENANCE));

    initHttpReqAndResp();
    req.addParameter(MaintenanceLoginAuthorizer.MAINTENANCE_LOGIN_REQUEST_PARAM, "");

    assertFalse(filter.isAccessAllowed(req, resp, null));
    assertFalse(
        "when providing maintenanceLogin request param user should not be redirected, but location"
            + " is "
            + resp.getHeaderValue("Location"),
        resp.containsHeader("Location"));
  }
}
