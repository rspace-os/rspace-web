package com.researchspace.webapp.integrations.wopi;

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.webapp.integrations.wopi.WopiAccessTokenHandler.WopiAccessToken;
import java.time.Instant;
import org.junit.Test;

public class WopiAccessTokenHandlerTest {

  private WopiAccessTokenHandler tokenHandler = new WopiAccessTokenHandler();

  @Test
  public void checkTokenTTL() throws Exception {

    User testUser = TestFactory.createAnyUser("wopiTest");
    String fileId = TestFactory.createEcatImage(15L).getGlobalIdentifier();

    long millisBeforeCreation = Instant.now().toEpochMilli();
    WopiAccessToken wopiToken = tokenHandler.createAccessToken(testUser, fileId);
    long millisAfterCreation = Instant.now().toEpochMilli();

    // check dates in created token
    assertNotNull(wopiToken);
    assertTrue(
        millisBeforeCreation + WopiAccessTokenHandler.TOKEN_TTL_IN_MS <= wopiToken.getExpiryDate(),
        "token ttl suspiciously short");
    assertTrue(
        millisAfterCreation + WopiAccessTokenHandler.TOKEN_TTL_IN_MS >= wopiToken.getExpiryDate(),
        "token ttl suspiciously long");

    // let's check if token valid
    String usernameForValidToken =
        tokenHandler.getUsernameFromAccessToken(wopiToken.getAccessToken(), fileId);
    assertEquals(testUser.getUsername(), usernameForValidToken);

    // let's make token invalid by adjusting the expiration date
    wopiToken.setExpiryDate(millisAfterCreation - 1);
    String usernameForInvalidToken =
        tokenHandler.getUsernameFromAccessToken(wopiToken.getAccessToken(), fileId);
    assertNull(usernameForInvalidToken);
  }
}
