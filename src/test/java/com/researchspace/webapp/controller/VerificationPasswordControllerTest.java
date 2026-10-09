package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.auth.password.BoundedPasswordVerifier;
import com.researchspace.model.User;
import com.researchspace.model.dtos.UserValidator;
import com.researchspace.service.IVerificationPasswordValidator;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

@ExtendWith(MockitoExtension.class)
public class VerificationPasswordControllerTest {
  private @Mock UserManager userMgr;
  private @Mock UserValidator userValidator;
  private @Mock IVerificationPasswordValidator verificationPasswordValidator;

  @InjectMocks private VerificationPasswordController verificationPasswordController;
  User anyUser;
  final String OK_PWD = "abcdefg";

  @BeforeEach
  public void setUp() throws Exception {
    anyUser = TestFactory.createAnyUser("any");
    verificationPasswordController.messages = new MessageSourceUtils(new JsonMessageSource());
    useVerifierWaiting(Duration.ofSeconds(5));
  }

  private BoundedPasswordVerifier useVerifierWaiting(Duration wait) {
    BoundedPasswordVerifier verifier =
        new BoundedPasswordVerifier(NoOpPasswordEncoder.getInstance(), 8, 8, wait);
    verificationPasswordController.setVerifier(verifier);
    return verifier;
  }

  // this is the only method not very similar t oSignup/Profile tests
  @Test
  public void testSetVerificationPassword() {
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(anyUser);
    when(verificationPasswordValidator.isVerificationPasswordSet(anyUser)).thenReturn(true);

    verificationPasswordController.setVerificationPassword(
        OK_PWD, OK_PWD, new MockHttpServletRequest());
    assertUserPasswordNotSaved();

    // only saved if validator is OK

  }

  @Test
  public void testSetVerificationPassword2() {
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(anyUser);
    when(verificationPasswordValidator.isVerificationPasswordSet(anyUser)).thenReturn(false);
    when(userValidator.validatePasswords(
            OK_PWD, "not matching confirm password", anyUser.getUsername()))
        .thenReturn("some error");
    verificationPasswordController.setVerificationPassword(
        OK_PWD, "not matching confirm password", new MockHttpServletRequest());
    assertUserPasswordNotSaved();
  }

  @Test
  public void testSetVerificationPasswordHappyCase() {
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(anyUser);
    when(verificationPasswordValidator.isVerificationPasswordSet(anyUser)).thenReturn(false);
    when(userValidator.validatePasswords(OK_PWD, OK_PWD, anyUser.getUsername()))
        .thenReturn(UserValidator.FIELD_OK);
    when(verificationPasswordValidator.hashVerificationPassword(OK_PWD)).thenReturn("hashedPW");
    when(userMgr.getUserByUsername(anyUser.getUsername(), true)).thenReturn(anyUser);
    verificationPasswordController.setVerificationPassword(
        OK_PWD, OK_PWD, new MockHttpServletRequest());
    assertUserPwdSaved();
    assertEquals("hashedPW", anyUser.getVerificationPassword());
  }

  @Test
  public void concurrentInitialSetHashesAndSavesOnce() throws Exception {
    User sessionCopy = TestFactory.createAnyUser("any");
    stubConcurrentSet(sessionCopy);
    CountDownLatch hashing = new CountDownLatch(1);
    CountDownLatch releaseHash = new CountDownLatch(1);
    when(verificationPasswordValidator.hashVerificationPassword(OK_PWD))
        .thenAnswer(
            invocation -> {
              hashing.countDown();
              releaseHash.await(10, TimeUnit.SECONDS);
              return "hashedPW";
            });
    when(verificationPasswordValidator.authenticateVerificationPassword(anyUser, OK_PWD))
        .thenReturn(true);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<AjaxReturnObject<String>> first = pool.submit(this::setOkPassword);
      assertTrue(hashing.await(10, TimeUnit.SECONDS));
      Thread[] second = new Thread[1];
      Future<AjaxReturnObject<String>> secondResult =
          pool.submit(
              () -> {
                second[0] = Thread.currentThread();
                return setOkPassword();
              });
      awaitTimedWaiting(second);
      releaseHash.countDown();

      String success = getText("verificationPassword.set.success");
      assertEquals(success, first.get(10, TimeUnit.SECONDS).getData());
      assertEquals(success, secondResult.get(10, TimeUnit.SECONDS).getData());
    } finally {
      releaseHash.countDown();
      pool.shutdownNow();
    }
    verify(verificationPasswordValidator, times(1)).hashVerificationPassword(OK_PWD);
    verify(userMgr, times(1)).saveUser(anyUser);
    assertEquals("hashedPW", anyUser.getVerificationPassword());
  }

  @Test
  public void setAfterTheFirstHasCommittedIsRefusedAsAlreadySet() {
    User sessionCopy = TestFactory.createAnyUser("any");
    stubConcurrentSet(sessionCopy);
    when(verificationPasswordValidator.hashVerificationPassword(OK_PWD)).thenReturn("hashedPW");
    setOkPassword();
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(anyUser);

    assertEquals(getText("verificationPassword.set.errors.alreadySet"), setOkPassword().getData());
    verify(verificationPasswordValidator, times(1)).hashVerificationPassword(OK_PWD);
  }

  @Test
  public void losingSetWithADifferentPasswordIsToldItIsAlreadySet() {
    User sessionCopy = TestFactory.createAnyUser("any");
    stubConcurrentSet(sessionCopy);
    anyUser.setVerificationPassword("setByAnotherSignIn");
    when(verificationPasswordValidator.authenticateVerificationPassword(anyUser, OK_PWD))
        .thenReturn(false);

    assertEquals(getText("verificationPassword.set.errors.alreadySet"), setOkPassword().getData());
    verify(verificationPasswordValidator, never()).hashVerificationPassword(any());
    assertUserPasswordNotSaved();
  }

  @Test
  public void setThatCannotGetTheUsersTurnIsRefusedAsBusyWithoutHashing() throws Exception {
    BoundedPasswordVerifier verifier = useVerifierWaiting(Duration.ofMillis(50));
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(anyUser);
    when(verificationPasswordValidator.isVerificationPasswordSet(anyUser)).thenReturn(false);
    when(userValidator.validatePasswords(OK_PWD, OK_PWD, anyUser.getUsername()))
        .thenReturn(UserValidator.FIELD_OK);
    CountDownLatch held = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<Object> holder =
          pool.submit(
              () ->
                  verifier.runExclusive(
                      anyUser.getUsername(),
                      () -> {
                        held.countDown();
                        return release.await(10, TimeUnit.SECONDS);
                      }));
      assertTrue(held.await(10, TimeUnit.SECONDS));

      assertEquals(getText("verificationPassword.set.errors.busy"), setOkPassword().getData());

      release.countDown();
      holder.get(10, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
    verify(verificationPasswordValidator, never()).hashVerificationPassword(any());
  }

  /**
   * The session holds a stale copy of the user; the reload inside the lock returns {@code anyUser},
   * which the first save marks as set.
   */
  private void stubConcurrentSet(User sessionCopy) {
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(sessionCopy);
    when(userMgr.getUserByUsername(anyUser.getUsername(), true)).thenReturn(anyUser);
    when(verificationPasswordValidator.isVerificationPasswordSet(any(User.class)))
        .thenAnswer(
            invocation -> invocation.<User>getArgument(0).getVerificationPassword() != null);
    when(userValidator.validatePasswords(OK_PWD, OK_PWD, anyUser.getUsername()))
        .thenReturn(UserValidator.FIELD_OK);
  }

  private AjaxReturnObject<String> setOkPassword() {
    return verificationPasswordController.setVerificationPassword(
        OK_PWD, OK_PWD, new MockHttpServletRequest());
  }

  private String getText(String key) {
    return verificationPasswordController.messages.getMessage(key);
  }

  private static void awaitTimedWaiting(Thread[] holder) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (holder[0] == null || holder[0].getState() != Thread.State.TIMED_WAITING) {
      assertTrue(System.nanoTime() < deadline, "second request never waited for the first");
      Thread.sleep(5);
    }
  }

  private void assertUserPwdSaved() {
    Mockito.verify(userMgr, times(1)).saveUser(anyUser);
  }

  private void assertUserPasswordNotSaved() {
    verify(userMgr, never()).saveUser(anyUser);
  }
}
