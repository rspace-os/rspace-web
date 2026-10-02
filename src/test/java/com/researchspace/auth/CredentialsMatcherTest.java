package com.researchspace.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.auth.password.RSpacePasswordEncoder;
import com.researchspace.model.User;
import com.researchspace.service.IReauthenticator;
import com.researchspace.service.UserExistsException;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class CredentialsMatcherTest extends SpringTransactionalTest {

  private @Autowired IReauthenticator reauthenticator;
  private @Autowired RSpacePasswordEncoder passwordEncoder;

  // Shiro Sha256Hash fixtures, see RSpacePasswordEncoderTest
  private static final String LEGACY_SALT = "AxQlNkdYaXqLnK2+z+DxAg==";
  private static final String LEGACY_SALTED_HEX =
      "500ae17802c8636f5983eab68f86e151e3f0cb054f3250be363c46f2335844c8"; // legacyPass1
  private static final String LEGACY_UNSALTED_HEX =
      "CAA6EEC0FAA20EFBA3B7AF44AF7107B05334759954EF3581030CC8E6199A33BF"; // sysWisc23!

  @Test
  public void legacySaltedHashReauthenticatesAndIsUpgraded() throws UserExistsException {
    User u = createAndSaveRandomUser();
    storeWrappedLegacyHash(u, LEGACY_SALTED_HEX, LEGACY_SALT);

    assertFalse(reauthenticator.reauthenticate(u, "legacyPass1x"));
    assertTrue(reauthenticator.reauthenticate(u, "legacyPass1"));

    assertUpgraded(u.getUsername());
    assertTrue(reauthenticator.reauthenticate(u, "legacyPass1"));
  }

  @Test
  public void legacyUnsaltedHashLogsInAndIsUpgraded() throws UserExistsException {
    User u = createAndSaveRandomUser();
    storeWrappedLegacyHash(u, LEGACY_UNSALTED_HEX, null);

    RSpaceTestUtils.logoutCurrUserAndLoginAs(u.getUsername(), "sysWisc23!");

    assertUpgraded(u.getUsername());
    assertTrue(reauthenticator.reauthenticate(u, "sysWisc23!"));
  }

  private void storeWrappedLegacyHash(User u, String hex, String salt) {
    sessionFactory.getCurrentSession().flush();
    sessionFactory
        .getCurrentSession()
        .createNativeMutationQuery("update User set password = :pwd, salt = :salt where id = :id")
        .setParameter("pwd", passwordEncoder.wrapLegacySha256(hex, salt))
        .setParameter("salt", salt)
        .setParameter("id", u.getId())
        .executeUpdate();
    sessionFactory.getCurrentSession().clear();
  }

  private void assertUpgraded(String username) {
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();
    User stored = userDao.getUserByUsername(username);
    assertTrue(stored.getPassword().startsWith("{" + RSpacePasswordEncoder.ARGON2_ID + "}"));
    assertNull(stored.getSalt());
    assertEquals(stored.getPassword(), userDao.getUserPassword(username));
  }

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
