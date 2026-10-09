package com.researchspace.webapp.filter;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.auth.LoginAuthorizer;
import com.researchspace.auth.LoginHelper;
import com.researchspace.auth.password.RSpacePasswordEncoder;
import com.researchspace.dao.customliquibaseupdates.WrapLegacyPasswordHashes_RSDEV894;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.IReauthenticator;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.List;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * RSDEV-894: a hash wrapped at rest logs in and reauthenticates, through the realm and the filter's
 * post-login save, each in its own transaction as in production, and stays wrapped: a password
 * check never writes.
 */
class LegacyPasswordLoginIT extends RealTransactionSpringTestBase {

  // Shiro Sha256Hash fixtures, see RSpacePasswordEncoderTest
  private static final String SALT = "AxQlNkdYaXqLnK2+z+DxAg==";
  private static final String SALTED_HEX =
      "500ae17802c8636f5983eab68f86e151e3f0cb054f3250be363c46f2335844c8"; // legacyPass1
  private static final String UNSALTED_UPPER_HEX =
      "CAA6EEC0FAA20EFBA3B7AF44AF7107B05334759954EF3581030CC8E6199A33BF"; // sysWisc23!

  private @Autowired RSpacePasswordEncoder passwordEncoder;
  private @Autowired IPropertyHolder propertyHolder;
  private @Autowired List<LoginAuthorizer> loginAuthorizers;
  private @Autowired IReauthenticator reauthenticator;

  @Autowired
  @Qualifier("manualLoginHelper")
  private LoginHelper loginHelper;

  private StandaloneShiroFormAuthFilterExt filter;
  private JdbcTemplate jdbc;

  @BeforeEach
  void setUpFilter() {
    filter = new StandaloneShiroFormAuthFilterExt();
    filter.setUserMgr(userMgr);
    filter.setLoginHelper(loginHelper);
    filter.setLockoutPolicy(new DefaultLockoutPolicy());
    filter.setMessages(messages);
    filter.setLoginAuthorizers(loginAuthorizers);
    filter.setProperties(propertyHolder);
    jdbc = new JdbcTemplate(dataSource);
  }

  @Test
  void legacySaltedUserLogsInAndStaysWrapped() throws Exception {
    User u = createAndSaveUser(randomAlphabetic(10));
    String wrapped = storeWrapped(u, SALTED_HEX, SALT);

    login(u, "legacyPass1");
    assertStillWrapped(u, wrapped, "legacyPass1");
    login(u, "legacyPass1");
    assertStillWrapped(u, wrapped, "legacyPass1");
  }

  @Test
  void legacyUnsaltedUppercaseUserLogsInAndStaysWrapped() throws Exception {
    User u = createAndSaveUser(randomAlphabetic(10));
    String wrapped = storeWrapped(u, UNSALTED_UPPER_HEX, null);

    login(u, "sysWisc23!");
    assertStillWrapped(u, wrapped, "sysWisc23!");
  }

  @Test
  void rawLegacyRowsLogInAfterTheWrapMigration() throws Exception {
    User salted = createAndSaveUser(randomAlphabetic(10));
    User unsalted = createAndSaveUser(randomAlphabetic(10));
    storeRaw(salted, SALTED_HEX, SALT);
    storeRaw(unsalted, UNSALTED_UPPER_HEX, null);

    runWrapMigration();
    String saltedWrapped = assertWrappedAtRest(salted);
    String unsaltedWrapped = assertWrappedAtRest(unsalted);

    login(salted, "legacyPass1");
    login(unsalted, "sysWisc23!");
    assertStillWrapped(salted, saltedWrapped, "legacyPass1");
    assertStillWrapped(unsalted, unsaltedWrapped, "sysWisc23!");
  }

  @Test
  void newUserLogsInWithArgon2() throws Exception {
    User u = createAndSaveUser(randomAlphabetic(10));
    assertTrue(storedPassword(u).startsWith("{" + RSpacePasswordEncoder.ARGON2_ID + "}"));
    login(u, TESTPASSWD);
  }

  @Test
  void legacySaltedUserReauthenticatesAndStaysWrapped() throws Exception {
    User u = createAndSaveUser(randomAlphabetic(10));
    String wrapped = storeWrapped(u, SALTED_HEX, SALT);
    login(createAndSaveUser(randomAlphabetic(10)), TESTPASSWD);

    assertFalse(reauthenticator.reauthenticate(reload(u), "legacyPass1x"));
    assertTrue(reauthenticator.reauthenticate(reload(u), "legacyPass1"));
    assertStillWrapped(u, wrapped, "legacyPass1");
  }

  private void login(User u, String password) throws Exception {
    RSpaceTestUtils.logout();
    UsernamePasswordToken token = new UsernamePasswordToken(u.getUsername(), password);
    SecurityUtils.getSubject().login(token);
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setParameter("username", u.getUsername());
    filter.onLoginSuccess(token, SecurityUtils.getSubject(), req, new MockHttpServletResponse());
    assertTrue(SecurityUtils.getSubject().isAuthenticated());
  }

  private void assertStillWrapped(User u, String wrapped, String password) {
    assertEquals(wrapped, storedPassword(u));
    assertTrue(passwordEncoder.matches(password, wrapped));
    assertNull(storedSalt(u));
  }

  private String storeWrapped(User u, String hex, String salt) {
    String wrapped = passwordEncoder.wrapLegacySha256(hex, salt);
    jdbc.update("update User set password = ?, salt = null where id = ?", wrapped, u.getId());
    return wrapped;
  }

  private void storeRaw(User u, String hex, String salt) {
    jdbc.update("update User set password = ?, salt = ? where id = ?", hex, salt, u.getId());
  }

  // execute() opens and commits its own transaction, as when Liquibase runs it
  private void runWrapMigration() throws Exception {
    WrapLegacyPasswordHashes_RSDEV894 change = new WrapLegacyPasswordHashes_RSDEV894();
    ReflectionTestUtils.setField(change, "context", applicationContext);
    ReflectionTestUtils.setField(change, "sessionFactory", sessionFactory);
    ReflectionTestUtils.invokeMethod(change, "addBeans");
    change.execute(null);
  }

  private String assertWrappedAtRest(User u) {
    String stored = storedPassword(u);
    assertTrue(stored.startsWith("{" + RSpacePasswordEncoder.LEGACY_SHA256_ID + "}"), stored);
    assertNull(storedSalt(u));
    return stored;
  }

  private User reload(User u) {
    return userMgr.getUserByUsername(u.getUsername(), true);
  }

  private String storedPassword(User u) {
    return jdbc.queryForObject("select password from User where id = ?", String.class, u.getId());
  }

  private String storedSalt(User u) {
    return jdbc.queryForObject("select salt from User where id = ?", String.class, u.getId());
  }
}
