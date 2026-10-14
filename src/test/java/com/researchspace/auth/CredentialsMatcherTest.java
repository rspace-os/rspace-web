package com.researchspace.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.model.User;
import com.researchspace.service.IReauthenticator;
import com.researchspace.service.ReauthenticationResult;
import com.researchspace.service.UserExistsException;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class CredentialsMatcherTest extends SpringTransactionalTest {

  private @Autowired IReauthenticator reauthenticator;

  @Test
  public void testCredentialsMatch() throws UserExistsException {
    User u = createAndSaveRandomUser();
    RSpaceTestUtils.logoutCurrUserAndLoginAs(u.getUsername(), TESTPASSWD);
    assertEquals(ReauthenticationResult.OK, reauthenticator.reauthenticate(u, TESTPASSWD));
  }

  // RSPAC-602
  @Test
  public void testSysadminCanReauthenticateAsUserWithSysadminPassword() throws UserExistsException {
    User anyUser = createAndSaveRandomUser();
    logoutAndLoginAsSysAdmin();
    runAsUser(anyUser);
    assertEquals(ReauthenticationResult.OK, reauthenticator.reauthenticate(anyUser, SYS_ADMIN_PWD));
    releaseRunAs();
    // sysadmin can't do this by default
    assertEquals(
        ReauthenticationResult.WRONG, reauthenticator.reauthenticate(anyUser, SYS_ADMIN_PWD));
    // user can still reauthenticate
    logoutAndLoginAs(anyUser);
    assertEquals(ReauthenticationResult.OK, reauthenticator.reauthenticate(anyUser, TESTPASSWD));
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
