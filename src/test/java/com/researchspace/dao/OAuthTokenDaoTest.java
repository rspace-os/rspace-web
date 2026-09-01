package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.model.User;
import com.researchspace.model.oauth.OAuthToken;
import com.researchspace.model.oauth.OAuthTokenType;
import com.researchspace.testutils.SpringTransactionalTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class OAuthTokenDaoTest extends SpringTransactionalTest {

  private @Autowired OAuthTokenDao tokenDao;
  private final String anyClientId = "testapp1";
  private User anyUser;

  @BeforeEach
  public void before() {
    anyUser = createAndSaveRandomUser();
  }

  @Test
  public void findByAccessTokenHash() throws Exception {
    OAuthToken validToken = createValidOAuthToken(anyUser);
    validToken = tokenDao.save(validToken);
    assertNotNull(validToken.getId());
    assertEquals(
        validToken, tokenDao.findByAccessTokenHash(validToken.getHashedAccessToken()).get());
    assertEquals(Optional.empty(), tokenDao.findByAccessTokenHash("missing hash"));
    assertEquals(
        true,
        tokenDao.getToken(anyClientId, anyUser.getId(), validToken.getTokenType()).isPresent());
    assertEquals(
        false,
        tokenDao.getToken("unknownClient", anyUser.getId(), validToken.getTokenType()).isPresent());
  }

  @Test
  public void findByRefreshTokenHash() {
    OAuthToken validToken = createValidOAuthToken(anyUser);
    validToken = tokenDao.save(validToken);
    assertNotNull(validToken.getId());
    assertEquals(
        validToken, tokenDao.findByRefreshTokenHash(validToken.getHashedRefreshToken()).get());
    assertEquals(Optional.empty(), tokenDao.findByRefreshTokenHash("missing hash"));
  }

  @Test
  public void listTokensForUser() {
    OAuthToken validToken = createValidOAuthToken(anyUser);
    validToken = tokenDao.save(validToken);
    assertEquals(1, tokenDao.listTokensForUser(anyUser.getId()).size());
    final long UNKNOWN_USER_ID = -2000L;

    assertEquals(0, tokenDao.listTokensForUser(UNKNOWN_USER_ID).size());
  }

  @Test
  public void listTokensForClient() {
    OAuthToken validToken = createValidOAuthToken(anyUser);
    validToken = tokenDao.save(validToken);
    assertEquals(1, tokenDao.listTokensForClient(anyClientId).size());
    assertEquals(0, tokenDao.listTokensForClient("unknownclient").size());
  }

  private OAuthToken createValidOAuthToken(User user) {
    OAuthToken token = new OAuthToken(user, anyClientId, OAuthTokenType.UI_TOKEN);
    token.setHashedAccessToken(RandomStringUtils.randomAlphabetic(64));
    token.setExpiryTime(Instant.now().plus(1, ChronoUnit.DAYS));
    token.setHashedRefreshToken(RandomStringUtils.randomAlphabetic(64));
    return token;
  }
}
