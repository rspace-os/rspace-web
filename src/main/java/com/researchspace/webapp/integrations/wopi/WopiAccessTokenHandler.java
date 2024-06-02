package com.researchspace.webapp.integrations.wopi;

import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.model.User;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WopiAccessTokenHandler {

  // token lifespan set to 2 hours (common-sense value, change if needed)
  protected static final long TOKEN_TTL_IN_MS = 2 * 60 * 60 * 1000;

  @Data
  @AllArgsConstructor
  protected static class WopiAccessToken {
    private String accessToken;
    private String username;
    private String fileId;
    private long expiryDate;
  }

  private Map<String, WopiAccessToken> tokens = new HashMap<>();

  public WopiAccessToken createAccessToken(User testUser, String fileId) {
    long expiryDate = Instant.now().toEpochMilli() + TOKEN_TTL_IN_MS;
    return createNewTokenWithExpiryDate(testUser, fileId, expiryDate);
  }

  public WopiAccessToken createAccessTokenWithOldTokenExpiryDate(
      User testUser, String fileId, String oldAccessToken) {
    long expiryDate = tokens.get(oldAccessToken).getExpiryDate();
    return createNewTokenWithExpiryDate(testUser, fileId, expiryDate);
  }

  private WopiAccessToken createNewTokenWithExpiryDate(
      User testUser, String fileId, long expiryDate) {
    String accessToken = SecureStringUtils.getURLSafeSecureRandomString(16);
    WopiAccessToken wopiToken =
        new WopiAccessToken(accessToken, testUser.getUsername(), fileId, expiryDate);
    tokens.put(accessToken, wopiToken);
    return wopiToken;
  }

  public String getUsernameFromAccessToken(String accessToken, String fileId) {
    WopiAccessToken token = tokens.get(accessToken);
    if (token != null) {
      if (token.getExpiryDate() < Instant.now().toEpochMilli()) {
        log.warn("received expired access token");
        return null;
      }
      if (token.getFileId().equals(fileId)) {
        return token.getUsername();
      }
    }
    log.warn("access token not recognized");
    return null;
  }

  /*
   * ===============
   *   for tests
   * ===============
   */
  Map<String, WopiAccessToken> getTokens() {
    return tokens;
  }
}
