package com.researchspace.webapp.integrations.dmponline;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.springframework.web.client.ResourceAccessException;

class DMPOnlineProviderImplTest {

  @Test
  @Timeout(value = 10, threadMode = ThreadMode.SEPARATE_THREAD)
  void requestToUnresponsiveServerFailsAfterReadTimeout() throws Exception {
    // the OS accepts the connection into the backlog, but nothing ever replies
    try (ServerSocket silentServer = new ServerSocket(0)) {
      DMPOnlineProviderImpl provider =
          new DMPOnlineProviderImpl(Duration.ofSeconds(5), Duration.ofMillis(200));
      String planUrl = "http://localhost:" + silentServer.getLocalPort() + "/api/v2/plans/1";

      ResourceAccessException thrown =
          assertThrows(
              ResourceAccessException.class, () -> provider.getPlanByUrlId(planUrl, "token"));

      assertInstanceOf(SocketTimeoutException.class, thrown.getCause());
    }
  }
}
