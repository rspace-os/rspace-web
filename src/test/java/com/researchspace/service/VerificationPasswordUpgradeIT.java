package com.researchspace.service;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.auth.password.RSpacePasswordEncoder;
import com.researchspace.model.User;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** RSDEV-894: a prefixed BCrypt verification password verifies and is stored as Argon2 once. */
class VerificationPasswordUpgradeIT extends RealTransactionSpringTestBase {

  // BCrypt.hashpw("verify1234", BCrypt.gensalt()), as stored before RSDEV-894
  private static final String LEGACY_BCRYPT =
      "{bcrypt}$2a$10$fqWevKAPMKNsortKy6gS9eZbYfMnuItTnN4KUf2cy0w0dMTcIjzA6";

  private @Autowired IVerificationPasswordValidator verificationPasswordValidator;

  @Test
  void legacyBcryptVerifiesAndIsUpgradedToArgon2() {
    User u = createAndSaveUser(randomAlphabetic(10));
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.update("update User set verificationPassword = ? where id = ?", LEGACY_BCRYPT, u.getId());

    assertFalse(verificationPasswordValidator.authenticateVerificationPassword(reload(u), "x"));
    assertTrue(
        verificationPasswordValidator.authenticateVerificationPassword(reload(u), "verify1234"));

    String stored =
        jdbc.queryForObject(
            "select verificationPassword from User where id = ?", String.class, u.getId());
    assertTrue(stored.startsWith("{" + RSpacePasswordEncoder.ARGON2_ID + "}"), stored);
    assertTrue(
        verificationPasswordValidator.authenticateVerificationPassword(reload(u), "verify1234"));
  }

  private User reload(User u) {
    return userMgr.getUserByUsernameNoSession(u.getUsername());
  }
}
