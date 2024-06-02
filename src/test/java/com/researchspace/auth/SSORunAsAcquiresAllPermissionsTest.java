package com.researchspace.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpSession;

public class SSORunAsAcquiresAllPermissionsTest extends SpringTransactionalTest {

  MockHttpSession session;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    session = new MockHttpSession();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testRSPAC_1498() {
    //		User admin = createAndSaveUser(getRandomAlphabeticString("admin"));
    //		User target = createAndSaveUser(getRandomAlphabeticString("target"));
    User admin = createAndSaveAdminUser();
    User target = createAndSaveRandomUser();
    RSpaceTestUtils.loginSSO(admin.getUsername());
    permissionUtils.doRunAs(session, admin, target);
    // assert runAs has worked
    assertEquals(target.getUsername(), getSubject().getPrincipal().toString());
    // and is using correct realm
    assertTrue(
        getSubject().getPrincipals().getRealmNames().contains(SSOPassThruRealm.SSO_REALM_NAME));

    // and that run as has picked permissions
    assertTrue(permissionUtils.isPermitted("FORM:CREATE"));
    assertTrue(permissionUtils.isPermitted(target, PermissionType.WRITE, target));
  }

  private Subject getSubject() {
    return SecurityUtils.getSubject();
  }
}
