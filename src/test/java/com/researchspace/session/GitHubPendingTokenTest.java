package com.researchspace.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GitHubPendingTokenTest {
  @Test
  void tokenIsBoundToOwnerExpiresAndCanBeDisconnected() {
    Map<String, Object> session = new HashMap<>();
    try (var attributes = mockStatic(SessionAttributeUtils.class)) {
      attributes
          .when(() -> SessionAttributeUtils.setSessionAttribute(anyString(), any()))
          .thenAnswer(call -> session.put(call.getArgument(0), call.getArgument(1)));
      attributes
          .when(() -> SessionAttributeUtils.getSessionAttribute(anyString()))
          .thenAnswer(call -> session.get(call.getArgument(0)));
      attributes
          .when(() -> SessionAttributeUtils.removeSessionAttribute(anyString()))
          .thenAnswer(call -> session.remove(call.getArgument(0)));

      GitHubPendingToken.store("owner", "secret");
      assertEquals("secret", GitHubPendingToken.get("owner"));
      assertNull(GitHubPendingToken.get("other"));
      assertNull(GitHubPendingToken.get("owner"));

      GitHubPendingToken.store("owner", "secret");
      assertNull(
          GitHubPendingToken.get(
              "owner", System.currentTimeMillis() + Duration.ofMinutes(16).toMillis()));

      GitHubPendingToken.store("owner", "secret");
      GitHubPendingToken.clear();
      assertNull(GitHubPendingToken.get("owner"));
    }
  }
}
