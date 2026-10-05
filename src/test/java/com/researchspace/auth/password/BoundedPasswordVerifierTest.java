package com.researchspace.auth.password;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class BoundedPasswordVerifierTest {

  private static final int PERMITS = 8;

  /** Matches "ok", and blocks inside matches until released. */
  private static class BlockingEncoder implements PasswordEncoder {
    final CountDownLatch release = new CountDownLatch(1);
    final AtomicInteger entered = new AtomicInteger();

    @Override
    public String encode(CharSequence rawPassword) {
      return "new";
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
      entered.incrementAndGet();
      try {
        release.await(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return "ok".contentEquals(rawPassword);
    }
  }

  private final ExecutorService pool = Executors.newCachedThreadPool();
  private final BlockingEncoder encoder = new BlockingEncoder();

  @AfterEach
  void tearDown() {
    encoder.release.countDown();
    pool.shutdownNow();
  }

  @Test
  void ninthConcurrentVerificationTimesOutAsBusy() throws Exception {
    BoundedPasswordVerifier verifier =
        new BoundedPasswordVerifier(encoder, PERMITS, Duration.ofMillis(200), Duration.ZERO);
    List<Future<Boolean>> running = new ArrayList<>();
    for (int i = 0; i < PERMITS; i++) {
      String user = "user" + i;
      running.add(pool.submit(() -> verifier.verify(user, "ok", "stored")));
    }
    awaitEntered(PERMITS);

    assertThrows(
        LoginVerificationBusyException.class, () -> verifier.verify("user8", "ok", "stored"));

    encoder.release.countDown();
    for (Future<Boolean> f : running) {
      assertTrue(f.get(5, TimeUnit.SECONDS));
    }
    assertEquals(PERMITS, verifier.availablePermits());
    assertEquals(0, verifier.trackedPrincipals());
  }

  @Test
  void sameUsernameVerifiesOneAtATime() throws Exception {
    BoundedPasswordVerifier verifier =
        new BoundedPasswordVerifier(encoder, PERMITS, Duration.ofSeconds(5), Duration.ZERO);
    Future<Boolean> first = pool.submit(() -> verifier.verify("alice", "ok", "stored"));
    awaitEntered(1);
    Future<Boolean> second = pool.submit(() -> verifier.verify("alice", "ok", "stored"));
    Future<Boolean> other = pool.submit(() -> verifier.verify("bob", "ok", "stored"));
    awaitEntered(2);
    Thread.sleep(100);
    assertEquals(2, encoder.entered.get(), "second alice check must wait for the first");

    encoder.release.countDown();
    assertTrue(first.get(5, TimeUnit.SECONDS));
    assertTrue(second.get(5, TimeUnit.SECONDS));
    assertTrue(other.get(5, TimeUnit.SECONDS));
    assertEquals(3, encoder.entered.get());
  }

  @Test
  void sameUsernameBusyWhenFirstCheckOutlastsTheWait() throws Exception {
    BoundedPasswordVerifier verifier =
        new BoundedPasswordVerifier(encoder, PERMITS, Duration.ofMillis(200), Duration.ZERO);
    Future<Boolean> first = pool.submit(() -> verifier.verify("alice", "ok", "stored"));
    awaitEntered(1);
    assertThrows(
        LoginVerificationBusyException.class, () -> verifier.verify("alice", "ok", "stored"));
    assertEquals(PERMITS - 1, verifier.availablePermits());

    encoder.release.countDown();
    first.get(5, TimeUnit.SECONDS);
    assertEquals(0, verifier.trackedPrincipals());
  }

  @Test
  void rejectsConfigurationThatWouldRefuseEveryCheck() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new BoundedPasswordVerifier(encoder, 0, Duration.ofSeconds(5), Duration.ZERO));
    assertThrows(
        IllegalArgumentException.class,
        () -> new BoundedPasswordVerifier(encoder, PERMITS, Duration.ofSeconds(-1), Duration.ZERO));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BoundedPasswordVerifier(
                encoder, PERMITS, Duration.ofSeconds(5), Duration.ofSeconds(-1)));
  }

  @Test
  void permitAndPrincipalReleasedWhenEncoderThrows() {
    PasswordEncoder failing =
        new BlockingEncoder() {
          @Override
          public boolean matches(CharSequence rawPassword, String encodedPassword) {
            throw new IllegalArgumentException("unknown id");
          }
        };
    BoundedPasswordVerifier verifier =
        new BoundedPasswordVerifier(failing, PERMITS, Duration.ofSeconds(1), Duration.ZERO);
    assertThrows(IllegalArgumentException.class, () -> verifier.verify("alice", "x", "stored"));
    assertEquals(PERMITS, verifier.availablePermits());
    assertEquals(0, verifier.trackedPrincipals());
  }

  @Test
  void wrongGuessHoldsTheUsernameForTheDelayButNotOtherUsernames() throws Exception {
    encoder.release.countDown();
    Duration delay = Duration.ofMillis(300);
    BoundedPasswordVerifier verifier =
        new BoundedPasswordVerifier(encoder, PERMITS, Duration.ofSeconds(5), delay);

    long start = System.nanoTime();
    Future<Boolean> wrong = pool.submit(() -> verifier.verify("alice", "bad", "stored"));
    awaitEntered(1);
    assertTrue(verifier.verify("bob", "ok", "stored"));
    assertTrue(elapsedSince(start) < delay.toMillis(), "another username must not wait");
    assertTrue(verifier.verify("alice", "ok", "stored"));
    long sameUserElapsed = elapsedSince(start);
    assertTrue(sameUserElapsed >= delay.toMillis(), "took " + sameUserElapsed + " ms");
    assertFalse(wrong.get(1, TimeUnit.SECONDS));

    start = System.nanoTime();
    assertTrue(verifier.verify("alice", "ok", "stored"));
    assertTrue(elapsedSince(start) < delay.toMillis(), "a match is not delayed");
  }

  @Test
  void delayCountsAgainstTheWaitSoAQueuedGuessTimesOutAsBusy() throws Exception {
    encoder.release.countDown();
    BoundedPasswordVerifier verifier =
        new BoundedPasswordVerifier(
            encoder, PERMITS, Duration.ofMillis(200), Duration.ofMillis(600));
    Future<Boolean> wrong = pool.submit(() -> verifier.verify("alice", "bad", "stored"));
    awaitEntered(1);

    assertThrows(
        LoginVerificationBusyException.class, () -> verifier.verify("alice", "ok", "stored"));
    assertFalse(wrong.get(2, TimeUnit.SECONDS));
    assertEquals(0, verifier.trackedPrincipals());
  }

  @Test
  void permitIsFreeDuringTheDelay() throws Exception {
    encoder.release.countDown();
    BoundedPasswordVerifier verifier =
        new BoundedPasswordVerifier(encoder, 1, Duration.ofMillis(200), Duration.ofSeconds(2));
    Future<Boolean> wrong = pool.submit(() -> verifier.verify("alice", "bad", "stored"));
    awaitEntered(1);

    for (int i = 0; i < PERMITS; i++) {
      assertTrue(verifier.verify("user" + i, "ok", "stored"));
    }
    assertFalse(wrong.isDone(), "alice must still be in her delay");
    assertFalse(wrong.get(3, TimeUnit.SECONDS));
  }

  private static long elapsedSince(long startNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
  }

  private void awaitEntered(int count) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (encoder.entered.get() < count && System.nanoTime() < deadline) {
      Thread.sleep(5);
    }
    assertEquals(count, encoder.entered.get());
  }
}
