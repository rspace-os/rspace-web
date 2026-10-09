package com.researchspace.webapp.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.auth.password.BoundedPasswordVerifier;
import com.researchspace.auth.password.LoginVerificationBusyException;
import com.researchspace.auth.password.SentinelPasswordCheck;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.shiro.authc.AuthenticationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

class StandaloneShiroFormAuthFilterExtLockTest {

  private final ExecutorService pool = Executors.newCachedThreadPool();
  private final UserManager userMgr = mock(UserManager.class);
  private final IUserAccountLockoutPolicy lockoutPolicy = mock(IUserAccountLockoutPolicy.class);
  private final SentinelPasswordCheck sentinelCheck = mock(SentinelPasswordCheck.class);
  private final User user = new User("user1a");
  private final CountDownLatch held = new CountDownLatch(1);
  private final CountDownLatch release = new CountDownLatch(1);
  private BoundedPasswordVerifier verifier;
  private StandaloneShiroFormAuthFilterExt filter;

  @BeforeEach
  void setUp() {
    when(userMgr.loginLockKey(any())).thenAnswer(inv -> fold(inv.getArgument(0)));
    when(userMgr.getUserByUsernameOrAlias(any())).thenReturn(user);
    filter = new StandaloneShiroFormAuthFilterExt();
    filter.setUserMgr(userMgr);
    filter.setLockoutPolicy(lockoutPolicy);
    filter.setSentinelCheck(sentinelCheck);
  }

  @AfterEach
  void tearDown() {
    release.countDown();
    pool.shutdownNow();
  }

  @Test
  void loginWithAnotherCaseOfAHeldUsernameIsRefusedAsBusyWithoutCounting() throws Exception {
    useVerifierWaiting(Duration.ofMillis(100));
    Future<Object> inFlight = holdLoginFor("user1a");

    MockHttpServletRequest request = loginSubmission("USER1A");
    assertTrue(filter.onAccessDenied(request, new MockHttpServletResponse()));

    assertEquals(
        LoginVerificationBusyException.class.getName(), request.getAttribute("shiroLoginFailure"));
    verify(lockoutPolicy, never()).handleLockoutOnFailure(any());
    release.countDown();
    inFlight.get(5, TimeUnit.SECONDS);
  }

  @Test
  void loginWithAnAccentedSpellingOfAHeldUsernameIsRefusedAsBusyWithoutCounting() throws Exception {
    assertRefusedAsBusyWhileHeld("user1a", "úser1a");
  }

  @Test
  void loginWithAnAccentedSpellingOfAHeldAbsentNameIsRefusedAsBusyWithoutCounting()
      throws Exception {
    assertRefusedAsBusyWhileHeld("ghost", "ghóst");
  }

  private void assertRefusedAsBusyWhileHeld(String heldKey, String submitted) throws Exception {
    useVerifierWaiting(Duration.ofMillis(100));
    Future<Object> inFlight = holdLoginFor(heldKey);

    MockHttpServletRequest request = loginSubmission(submitted);
    assertTrue(filter.onAccessDenied(request, new MockHttpServletResponse()));

    assertEquals(
        LoginVerificationBusyException.class.getName(), request.getAttribute("shiroLoginFailure"));
    verify(lockoutPolicy, never()).handleLockoutOnFailure(any());
    release.countDown();
    inFlight.get(5, TimeUnit.SECONDS);
  }

  @Test
  void wholeLoginKeyNeverSharesAnEntryWithARawUsername() throws Exception {
    useVerifierWaiting(Duration.ofMillis(100));
    when(userMgr.loginLockKey("ghost")).thenReturn("abcdef012345");
    lockUser();
    Future<Object> inFlight = holdLock("abcdef012345");

    MockHttpServletRequest request = loginSubmission("ghost");
    assertTrue(filter.onAccessDenied(request, new MockHttpServletResponse()));

    assertEquals(
        AuthenticationException.class.getName(), request.getAttribute("shiroLoginFailure"));
    release.countDown();
    inFlight.get(5, TimeUnit.SECONDS);
  }

  @Test
  void busyAdminLoginIsRedirectedBackToTheAdminPageWithoutCounting() throws Exception {
    useVerifierWaiting(Duration.ofMillis(100));
    Future<Object> inFlight = holdLoginFor("user1a");

    MockHttpServletRequest request = loginSubmission("user1a");
    request.setParameter("adminLogin", "");
    MockHttpServletResponse response = new MockHttpServletResponse();
    assertFalse(filter.onAccessDenied(request, response));

    assertThat(response.getRedirectedUrl())
        .startsWith("/adminLogin")
        .contains("loginException=LoginVerificationBusyException");
    verify(lockoutPolicy, never()).handleLockoutOnFailure(any());
    release.countDown();
    inFlight.get(5, TimeUnit.SECONDS);
  }

  @Test
  void lockedAccountIsPaddedLikeAnUnknownName() throws Exception {
    useVerifierWaiting(Duration.ofSeconds(1));
    lockUser();

    MockHttpServletRequest request = loginSubmission("user1a");
    assertTrue(filter.onAccessDenied(request, new MockHttpServletResponse()));

    verify(sentinelCheck).pad("user1a", "wrong");
    assertEquals(
        AuthenticationException.class.getName(), request.getAttribute("shiroLoginFailure"));
    verify(lockoutPolicy, never()).handleLockoutOnFailure(any());
  }

  @Test
  void queuedLoginSeesTheLockoutSetByTheAttemptAheadOfIt() throws Exception {
    useVerifierWaiting(Duration.ofSeconds(5));
    when(lockoutPolicy.isAfterLockoutTime(user)).thenReturn(false);
    Future<Object> inFlight = holdLoginFor("user1a");

    MockHttpServletRequest request = loginSubmission("user1a");
    Future<Boolean> queued =
        pool.submit(() -> filter.onAccessDenied(request, new MockHttpServletResponse()));
    Thread.sleep(100);
    assertFalse(queued.isDone(), "second attempt must wait for the first");

    lockUser();
    release.countDown();
    inFlight.get(5, TimeUnit.SECONDS);

    assertTrue(queued.get(5, TimeUnit.SECONDS));
    assertNotNull(request.getAttribute("shiroLoginFailure"));
  }

  private void lockUser() {
    when(lockoutPolicy.isAfterLockoutTime(user)).thenReturn(false);
    user.setAccountLocked(true);
    user.setLoginFailure(new Date());
  }

  private void useVerifierWaiting(Duration wait) {
    verifier = new BoundedPasswordVerifier(mock(PasswordEncoder.class), 8, wait);
    filter.setVerifier(verifier);
  }

  private Future<Object> holdLoginFor(String usernameWeight) throws InterruptedException {
    return holdLock(StandaloneShiroFormAuthFilterExt.wholeLoginKey(usernameWeight));
  }

  private Future<Object> holdLock(String key) throws InterruptedException {
    Future<Object> inFlight =
        pool.submit(
            () ->
                verifier.runExclusive(
                    key,
                    () -> {
                      held.countDown();
                      release.await(10, TimeUnit.SECONDS);
                      return null;
                    }));
    assertTrue(held.await(5, TimeUnit.SECONDS));
    return inFlight;
  }

  /** Stands in for the database collation weight. */
  private static String fold(String name) {
    return Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }

  private static MockHttpServletRequest loginSubmission(String username) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login.jsp");
    request.setServletPath("/login.jsp");
    request.setParameter("username", username);
    request.setParameter("password", "wrong");
    return request;
  }
}
