package com.researchspace.auth;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.service.IReauthenticator;
import com.researchspace.service.UserExistsException;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class CredentialsMatcherTest extends SpringTransactionalTest {

  private @Autowired IReauthenticator reauthenticator;

  @Test
  public void testCredentialsMatch() throws UserExistsException {
    User u = createAndSaveRandomUser();
    RSpaceTestUtils.logoutCurrUserAndLoginAs(u.getUsername(), TESTPASSWD);
    assertTrue(reauthenticator.reauthenticate(u, TESTPASSWD));
  }

  // RSPAC-602
  @Test
  public void testSysadminCanReauthenticateAsUserWithSysadminPassword() throws UserExistsException {
    User anyUser = createAndSaveRandomUser();
    logoutAndLoginAsSysAdmin();
    runAsUser(anyUser);
    assertTrue(reauthenticator.reauthenticate(anyUser, SYS_ADMIN_PWD));
    releaseRunAs();
    // sysadmin can't do this by default
    assertFalse(reauthenticator.reauthenticate(anyUser, SYS_ADMIN_PWD));
    // user can still reauthenticate
    logoutAndLoginAs(anyUser);
    assertTrue(reauthenticator.reauthenticate(anyUser, TESTPASSWD));
  }

  private void releaseRunAs() {
    SecurityUtils.getSubject().releaseRunAs();
  }

  private void runAsUser(User u) {
    SimplePrincipalCollection pc = new SimplePrincipalCollection();
    pc.add(u.getUsername(), ShiroRealm.DEFAULT_USER_PASSWD_REALM);
    SecurityUtils.getSubject().runAs(pc);
  }
}
