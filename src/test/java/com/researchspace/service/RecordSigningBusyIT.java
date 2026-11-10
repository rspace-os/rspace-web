package com.researchspace.service;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.auth.password.BoundedPasswordVerifier;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * RSDEV-894 F2: a verification-password user signing while the verifier is busy must get a busy
 * answer, not an UnexpectedRollbackException from the signing manager's commit.
 */
class RecordSigningBusyIT extends RealTransactionSpringTestBase {

  private @Autowired RecordSigningManager signingManager;
  private @Autowired IVerificationPasswordValidator validator;
  private @Autowired BoundedPasswordVerifier verifier;

  @Test
  void busyVerificationPasswordCheckIsReportedAsBusyAndCommits() throws Exception {
    User user = createAndSaveUser(randomAlphabetic(10));
    User google = reload(user);
    google.setSignupSource(SignupSource.GOOGLE);
    google.setVerificationPassword(validator.hashVerificationPassword("verify1234"));
    userMgr.saveUser(google);

    CountDownLatch held = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<Object> holder =
          pool.submit(
              () ->
                  verifier.runExclusive(
                      google.getUsername(),
                      () -> {
                        held.countDown();
                        release.await(30, TimeUnit.SECONDS);
                        return null;
                      }));
      assertTrue(held.await(10, TimeUnit.SECONDS));

      assertEquals(
          ReauthenticationResult.BUSY,
          signingManager.isReauthenticated(reload(google), "verify1234"));

      release.countDown();
      holder.get(10, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
    assertEquals(
        ReauthenticationResult.OK, signingManager.isReauthenticated(reload(google), "verify1234"));
  }

  private User reload(User u) {
    return userMgr.getUserByUsernameNoSession(u.getUsername());
  }
}
