package com.researchspace.auth.password;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SentinelPasswordCheckTest {

  private @Mock BoundedPasswordVerifier verifier;

  @Test
  void padChecksTheSuppliedPasswordAgainstAnArgon2SentinelHash() {
    SentinelPasswordCheck check = new SentinelPasswordCheck(new RSpacePasswordEncoder(), verifier);

    check.pad("guess");

    ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
    verify(verifier)
        .verify(eq(SentinelPasswordCheck.SENTINEL_USERNAME), eq("guess"), stored.capture());
    assertTrue(stored.getValue().startsWith("{argon2@rspace_v1}"));
  }

  @Test
  void busyPadIsSwallowed() {
    when(verifier.verify(anyString(), any(), anyString()))
        .thenThrow(new LoginVerificationBusyException("busy"));
    SentinelPasswordCheck check = new SentinelPasswordCheck(new RSpacePasswordEncoder(), verifier);

    check.pad("guess");
  }
}
