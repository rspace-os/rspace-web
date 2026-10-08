package com.researchspace.webapp.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.auth.password.BoundedPasswordVerifier;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
  private final User user = new User("user1a");
  private final CountDownLatch held = new CountDownLatch(1);
  private final CountDownLatch release = new CountDownLatch(1);
  private BoundedPasswordVerifier verifier;
  private StandaloneShiroFormAuthFilterExt filter;

  @BeforeEach
  void setUp() {
    when(userMgr.findUsernameByUsernameOrAlias(any())).thenAnswer(inv -> inv.getArgument(0));
    when(userMgr.getUserByUsernameOrAlias(any())).thenReturn(user);
    filter = new StandaloneShiroFormAuthFilterExt();
    filter.setUserMgr(userMgr);
    filter.setLockoutPolicy(lockoutPolicy);
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

    assertNotNull(request.getAttribute("shiroLoginFailure"));
    verify(lockoutPolicy, never()).handleLockoutOnFailure(any());
    release.countDown();
    inFlight.get(5, TimeUnit.SECONDS);
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

    user.setAccountLocked(true);
    user.setLoginFailure(new Date());
    release.countDown();
    inFlight.get(5, TimeUnit.SECONDS);

    assertTrue(queued.get(5, TimeUnit.SECONDS));
    assertNotNull(request.getAttribute("shiroLoginFailure"));
  }

  private void useVerifierWaiting(Duration wait) {
    verifier = new BoundedPasswordVerifier(mock(PasswordEncoder.class), 8, wait);
    filter.setVerifier(verifier);
  }

  private Future<Object> holdLoginFor(String username) throws InterruptedException {
    Future<Object> inFlight =
        pool.submit(
            () ->
                verifier.runExclusive(
                    username,
                    () -> {
                      held.countDown();
                      release.await(10, TimeUnit.SECONDS);
                      return null;
                    }));
    assertTrue(held.await(5, TimeUnit.SECONDS));
    return inFlight;
  }

  private static MockHttpServletRequest loginSubmission(String username) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login.jsp");
    request.setServletPath("/login.jsp");
    request.setParameter("username", username);
    request.setParameter("password", "wrong");
    return request;
  }
}
