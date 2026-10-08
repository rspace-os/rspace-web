package com.researchspace.auth.password;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
        new BoundedPasswordVerifier(encoder, PERMITS, Duration.ofMillis(200));
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
        new BoundedPasswordVerifier(encoder, PERMITS, Duration.ofSeconds(5));
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
        new BoundedPasswordVerifier(encoder, PERMITS, Duration.ofMillis(200));
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
  void interruptedWaitIsBusyAndKeepsTheInterruptFlag() throws Exception {
    BoundedPasswordVerifier verifier =
        new BoundedPasswordVerifier(encoder, PERMITS, Duration.ofSeconds(5));
    Future<Boolean> first = pool.submit(() -> verifier.verify("alice", "ok", "stored"));
    awaitEntered(1);
    AtomicReference<Throwable> thrown = new AtomicReference<>();
    AtomicBoolean stillInterrupted = new AtomicBoolean();
    Thread waiter =
        new Thread(
            () -> {
              try {
                verifier.verify("alice", "ok", "stored");
              } catch (Throwable t) {
                thrown.set(t);
              }
              stillInterrupted.set(Thread.currentThread().isInterrupted());
            });
    waiter.start();
    awaitState(waiter, Thread.State.TIMED_WAITING);
    waiter.interrupt();
    waiter.join(1000);

    assertInstanceOf(LoginVerificationBusyException.class, thrown.get());
    assertTrue(stillInterrupted.get());
    encoder.release.countDown();
    first.get(5, TimeUnit.SECONDS);
    assertEquals(0, verifier.trackedPrincipals());
  }

  @Test
  void rejectsConfigurationThatWouldRefuseEveryCheck() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new BoundedPasswordVerifier(encoder, 0, Duration.ofSeconds(5)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new BoundedPasswordVerifier(encoder, PERMITS, Duration.ofSeconds(-1)));
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
        new BoundedPasswordVerifier(failing, PERMITS, Duration.ofSeconds(1));
    assertThrows(IllegalArgumentException.class, () -> verifier.verify("alice", "x", "stored"));
    assertEquals(PERMITS, verifier.availablePermits());
    assertEquals(0, verifier.trackedPrincipals());
  }

  @Test
  void runExclusiveReturnsTheActionsValue() throws Exception {
    BoundedPasswordVerifier verifier =
        new BoundedPasswordVerifier(encoder, PERMITS, Duration.ofSeconds(1));
    assertEquals("done", verifier.runExclusive("alice", () -> "done"));
    assertEquals(0, verifier.trackedPrincipals());
  }

  @Test
  void runExclusiveBusyWhileAnotherThreadHoldsTheUsername() throws Exception {
    BoundedPasswordVerifier verifier =
        new BoundedPasswordVerifier(encoder, PERMITS, Duration.ofMillis(200));
    CountDownLatch held = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Future<Object> holder =
        pool.submit(() -> verifier.runExclusive("alice", () -> awaitWhileHeld(held, release)));
    held.await(5, TimeUnit.SECONDS);

    assertThrows(
        LoginVerificationBusyException.class, () -> verifier.runExclusive("alice", () -> "x"));

    release.countDown();
    holder.get(5, TimeUnit.SECONDS);
    assertEquals(0, verifier.trackedPrincipals());
  }

  @Test
  void verifyInsideRunExclusiveReentersTheUsernameLock() throws Exception {
    BoundedPasswordVerifier verifier =
        new BoundedPasswordVerifier(encoder, PERMITS, Duration.ofMillis(200));
    encoder.release.countDown();
    assertTrue(verifier.runExclusive("alice", () -> verifier.verify("alice", "ok", "stored")));
    assertEquals(0, verifier.trackedPrincipals());
  }

  @Test
  void verifyInsideRunExclusiveSharesItsWait() throws Exception {
    BoundedPasswordVerifier verifier =
        new BoundedPasswordVerifier(encoder, 1, Duration.ofSeconds(1));
    Future<Boolean> permitHolder = pool.submit(() -> verifier.verify("bob", "ok", "stored"));
    awaitEntered(1);
    CountDownLatch held = new CountDownLatch(1);
    Future<Object> aliceHolder =
        pool.submit(
            () ->
                verifier.runExclusive(
                    "alice",
                    () -> {
                      held.countDown();
                      Thread.sleep(700);
                      return null;
                    }));
    held.await(5, TimeUnit.SECONDS);

    long start = System.nanoTime();
    assertThrows(
        LoginVerificationBusyException.class,
        () -> verifier.runExclusive("alice", () -> verifier.verify("alice", "ok", "stored")));
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    assertTrue(elapsedMillis < 1400, "refused after " + elapsedMillis + " ms, expected about 1 s");

    aliceHolder.get(5, TimeUnit.SECONDS);
    encoder.release.countDown();
    permitHolder.get(5, TimeUnit.SECONDS);
  }

  private static void awaitState(Thread thread, Thread.State state) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
    while (thread.getState() != state && System.nanoTime() < deadline) {
      Thread.sleep(5);
    }
    assertEquals(state, thread.getState());
  }

  private static Object awaitWhileHeld(CountDownLatch held, CountDownLatch release)
      throws InterruptedException {
    held.countDown();
    release.await(10, TimeUnit.SECONDS);
    return null;
  }

  private void awaitEntered(int count) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (encoder.entered.get() < count && System.nanoTime() < deadline) {
      Thread.sleep(5);
    }
    assertEquals(count, encoder.entered.get());
  }
}
