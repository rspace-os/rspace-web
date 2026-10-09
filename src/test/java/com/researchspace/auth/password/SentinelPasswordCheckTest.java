package com.researchspace.auth.password;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class SentinelPasswordCheckTest {

  private @Mock BoundedPasswordVerifier verifier;

  @Test
  void padChecksTheSuppliedPasswordAgainstAnArgon2SentinelHash() {
    SentinelPasswordCheck check = new SentinelPasswordCheck(new RSpacePasswordEncoder(), verifier);

    check.pad("bob", "guess");

    ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
    verify(verifier)
        .verify(eq(SentinelPasswordCheck.sentinelKey("bob")), eq("guess"), stored.capture());
    assertTrue(stored.getValue().startsWith("{argon2@rspace_v1}"));
  }

  @Test
  void busyPadIsSwallowed() {
    when(verifier.verify(anyString(), any(), anyString()))
        .thenThrow(new LoginVerificationBusyException("busy"));
    SentinelPasswordCheck check = new SentinelPasswordCheck(new RSpacePasswordEncoder(), verifier);

    check.pad("bob", "guess");
  }

  @Test
  void unknownNamesDoNotQueueBehindEachOther() throws Exception {
    CountDownLatch inside = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    when(encoder.matches(any(), any()))
        .thenAnswer(
            inv -> {
              if ("block".contentEquals((CharSequence) inv.getArgument(0))) {
                inside.countDown();
                release.await(10, TimeUnit.SECONDS);
              }
              return false;
            });
    BoundedPasswordVerifier realVerifier =
        new BoundedPasswordVerifier(encoder, 2, 100, Duration.ofSeconds(5));
    SentinelPasswordCheck check =
        new SentinelPasswordCheck(new RSpacePasswordEncoder(), realVerifier);
    Thread blocker = new Thread(() -> check.pad("ghost1", "block"));
    blocker.start();
    try {
      assertTrue(inside.await(5, TimeUnit.SECONDS));

      long start = System.nanoTime();
      check.pad("ghost2", "guess");
      long unknownMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      start = System.nanoTime();
      assertFalse(realVerifier.verify("alice", "guess", "stored"));
      long realMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

      assertTrue(unknownMillis < 1000, "unknown name waited " + unknownMillis + " ms");
      assertTrue(realMillis < 1000, "real name waited " + realMillis + " ms");
    } finally {
      release.countDown();
      blocker.join(5000);
    }
  }
}
