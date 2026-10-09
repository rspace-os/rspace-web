package com.researchspace.service;

import static com.researchspace.testutils.LegacyVerificationPasswordFixture.BCRYPT_HASH;
import static com.researchspace.testutils.LegacyVerificationPasswordFixture.PLAIN;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.auth.password.RSpacePasswordEncoder;
import com.researchspace.dao.customliquibaseupdates.PrefixBcryptVerificationPasswords_RSDEV894;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * RSDEV-894: bare bcrypt verification passwords as stored before this change go through the prefix
 * migration in its own committed transaction, then verify and stay {@code {bcrypt}}; unusable rows
 * are cleared and replaced through the normal path; new ones are Argon2id.
 */
class VerificationPasswordLegacyBcryptIT extends RealTransactionSpringTestBase {

  private @Autowired IVerificationPasswordValidator validator;
  private JdbcTemplate jdbc;

  @BeforeEach
  void setUpJdbc() {
    jdbc = new JdbcTemplate(dataSource);
  }

  @Test
  void bareBcryptRowsVerifyAfterThePrefixMigrationAndStayBcrypt() throws Exception {
    User u = createAndSaveUser(randomAlphabetic(10));
    store(u, BCRYPT_HASH);

    runPrefixMigration();
    assertEquals("{bcrypt}" + BCRYPT_HASH, stored(u));

    assertFalse(validator.authenticateVerificationPassword(reload(u), "verify1234x"));
    assertTrue(validator.authenticateVerificationPassword(reload(u), PLAIN));
    assertEquals("{bcrypt}" + BCRYPT_HASH, stored(u));

    assertTrue(runPrefixMigration().getConfirmationMessage().startsWith("Prefixed 0 "));
  }

  @Test
  void unusableRowIsClearedAndTheUserCanSetANewVerificationPassword() throws Exception {
    User u = createAndSaveUser(randomAlphabetic(10));
    User google = reload(u);
    google.setSignupSource(SignupSource.GOOGLE);
    userMgr.save(google);
    store(u, "not-bcrypt");

    runPrefixMigration();
    assertNull(stored(u));
    assertFalse(validator.isVerificationPasswordSet(reload(u)));

    setThroughTheNormalPath(u, "newVerify1");
    assertArgon2AndVerifies(u, "newVerify1");
  }

  @Test
  void newVerificationPasswordIsArgon2AndStaysSo() {
    User u = createAndSaveUser(randomAlphabetic(10));
    setThroughTheNormalPath(u, "newVerify1");
    String stored = assertArgon2AndVerifies(u, "newVerify1");
    assertEquals(stored, stored(u));
  }

  // as VerificationPasswordResetHandler does it
  private void setThroughTheNormalPath(User u, String verificationPassword) {
    User user = reload(u);
    user.setVerificationPassword(validator.hashVerificationPassword(verificationPassword));
    userMgr.saveUser(user);
  }

  private String assertArgon2AndVerifies(User u, String verificationPassword) {
    String stored = stored(u);
    assertTrue(stored.startsWith("{" + RSpacePasswordEncoder.ARGON2_ID + "}"), stored);
    assertTrue(validator.authenticateVerificationPassword(reload(u), verificationPassword));
    return stored;
  }

  // execute() opens and commits its own transaction, as when Liquibase runs it
  private PrefixBcryptVerificationPasswords_RSDEV894 runPrefixMigration() throws Exception {
    PrefixBcryptVerificationPasswords_RSDEV894 change =
        new PrefixBcryptVerificationPasswords_RSDEV894();
    ReflectionTestUtils.setField(change, "context", applicationContext);
    ReflectionTestUtils.setField(change, "sessionFactory", sessionFactory);
    change.execute(null);
    return change;
  }

  private void store(User u, String verificationPassword) {
    jdbc.update(
        "update User set verificationPassword = ? where id = ?", verificationPassword, u.getId());
  }

  private String stored(User u) {
    return jdbc.queryForObject(
        "select verificationPassword from User where id = ?", String.class, u.getId());
  }

  private User reload(User u) {
    return userMgr.getUserByUsernameNoSession(u.getUsername());
  }
}
