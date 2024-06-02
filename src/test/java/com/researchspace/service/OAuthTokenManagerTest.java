package com.researchspace.service;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.NewOAuthTokenResponse;
import com.researchspace.core.util.CryptoUtils;
import com.researchspace.dao.OAuthTokenDao;
import com.researchspace.model.User;
import com.researchspace.model.frontend.OAuthAppInfo;
import com.researchspace.model.oauth.OAuthToken;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.testutils.SpringTransactionalTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class OAuthTokenManagerTest extends SpringTransactionalTest {
  private @Autowired OAuthTokenManager tokenManager;
  private @Autowired OAuthAppManager appManager;
  private @Autowired OAuthTokenDao tokenDao;
  private @Autowired IPropertyHolder properties;

  @Test
  public void createTokenWithWrongParams() {
    User user = createAndSaveRandomUser();

    OAuthAppInfo app = appManager.addApp(user, RandomStringUtils.randomAlphabetic(10)).getEntity();

    ServiceOperationResult<NewOAuthTokenResponse> tokenResponse1 =
        tokenManager.createNewToken(
            app.getClientId(), "incorrectClientSecret", user, OAuthToken.DEFAULT_SCOPE);
    assertFalse(tokenResponse1.isSucceeded());

    ServiceOperationResult<NewOAuthTokenResponse> tokenResponse2 =
        tokenManager.createNewToken(
            "incorrectClientId", app.getUnhashedClientSecret(), user, OAuthToken.DEFAULT_SCOPE);
    assertFalse(tokenResponse2.isSucceeded());
  }

  @Test
  public void refreshTokenWithWrongParams() {
    User user = createAndSaveRandomUser();

    OAuthAppInfo app = appManager.addApp(user, RandomStringUtils.randomAlphabetic(10)).getEntity();
    NewOAuthTokenResponse tokenResponse =
        tokenManager
            .createNewToken(
                app.getClientId(), app.getUnhashedClientSecret(), user, OAuthToken.DEFAULT_SCOPE)
            .getEntity();

    ServiceOperationResult<NewOAuthTokenResponse> failedRefresh1 =
        tokenManager.refreshAccessToken(
            app.getClientId(), "incorrectClientSecret", tokenResponse.getRefreshToken());
    assertFalse(failedRefresh1.isSucceeded());

    ServiceOperationResult<NewOAuthTokenResponse> failedRefresh2 =
        tokenManager.refreshAccessToken(
            app.getClientId(), app.getUnhashedClientSecret(), "incorrectRefreshToken");
    assertFalse(failedRefresh2.isSucceeded());
  }

  @Test
  public void createAndOverwriteAccessToken() {
    User user = createAndSaveRandomUser();

    OAuthAppInfo app = appManager.addApp(user, RandomStringUtils.randomAlphabetic(10)).getEntity();
    String clientId = app.getClientId();
    String clientSecret = app.getUnhashedClientSecret();

    ServiceOperationResult<NewOAuthTokenResponse> response =
        tokenManager.createNewToken(clientId, clientSecret, user, OAuthToken.DEFAULT_SCOPE);
    assertTrue(response.isSucceeded());
    assertEquals(1, tokenManager.getTokensForUser(user).size());
    assertEquals(
        CryptoUtils.hashToken(response.getEntity().getAccessToken()),
        tokenManager.getTokensForUser(user).get(0).getHashedAccessToken());
    assertEquals(
        CryptoUtils.hashToken(response.getEntity().getRefreshToken()),
        tokenManager.getTokensForUser(user).get(0).getHashedRefreshToken());

    // create another one for the same user, tokens are updated
    response = tokenManager.createNewToken(clientId, clientSecret, user, OAuthToken.DEFAULT_SCOPE);
    assertTrue(response.isSucceeded());
    assertEquals(1, tokenManager.getTokensForUser(user).size());
    assertEquals(
        CryptoUtils.hashToken(response.getEntity().getAccessToken()),
        tokenManager.getTokensForUser(user).get(0).getHashedAccessToken());
    assertEquals(
        CryptoUtils.hashToken(response.getEntity().getRefreshToken()),
        tokenManager.getTokensForUser(user).get(0).getHashedRefreshToken());
  }

  @Test
  public void createTokenAndThenCreateJwtToken() {
    User user = createAndSaveRandomUser();

    OAuthAppInfo app = appManager.addApp(user, RandomStringUtils.randomAlphabetic(10)).getEntity();
    String clientId = app.getClientId();
    String clientSecret = app.getUnhashedClientSecret();

    // Create normal token
    ServiceOperationResult<NewOAuthTokenResponse> response1 =
        tokenManager.createNewToken(clientId, clientSecret, user, OAuthToken.DEFAULT_SCOPE);
    assertTrue(response1.isSucceeded());
    NewOAuthTokenResponse tokenResponse = response1.getEntity();

    // Create multiple JWT tokens
    ServiceOperationResult<NewOAuthTokenResponse> response2 =
        tokenManager.createNewJwtToken(clientId, clientSecret, user, OAuthToken.DEFAULT_SCOPE);
    assertTrue(response2.isSucceeded());
    NewOAuthTokenResponse jwtResponse1 = response2.getEntity();

    ServiceOperationResult<NewOAuthTokenResponse> response3 =
        tokenManager.createNewJwtToken(clientId, clientSecret, user, OAuthToken.DEFAULT_SCOPE);
    assertTrue(response3.isSucceeded());
    NewOAuthTokenResponse jwtResponse2 = response2.getEntity();

    // JWT tokens work, normal token still works
    assertTrue(tokenManager.authenticate(tokenResponse.getAccessToken()).isSucceeded());
    assertTrue(tokenManager.authenticate(jwtResponse1.getAccessToken()).isSucceeded());
    assertTrue(tokenManager.authenticate(jwtResponse2.getAccessToken()).isSucceeded());
  }

  @Test
  public void crateJwtTokenAndThenCreateToken() {
    User user = createAndSaveRandomUser();

    OAuthAppInfo app = appManager.addApp(user, RandomStringUtils.randomAlphabetic(10)).getEntity();
    String clientId = app.getClientId();
    String clientSecret = app.getUnhashedClientSecret();

    // Create multiple JWT tokens
    ServiceOperationResult<NewOAuthTokenResponse> response1 =
        tokenManager.createNewJwtToken(clientId, clientSecret, user, OAuthToken.DEFAULT_SCOPE);
    assertTrue(response1.isSucceeded());
    NewOAuthTokenResponse jwtResponse1 = response1.getEntity();
    assertNotNull(jwtResponse1.getRefreshToken());

    ServiceOperationResult<NewOAuthTokenResponse> response2 =
        tokenManager.createNewJwtToken(clientId, clientSecret, user, OAuthToken.DEFAULT_SCOPE);
    assertTrue(response2.isSucceeded());
    NewOAuthTokenResponse jwtResponse2 = response1.getEntity();
    assertNotNull(jwtResponse2.getRefreshToken());

    // Create normal token
    ServiceOperationResult<NewOAuthTokenResponse> response3 =
        tokenManager.createNewToken(clientId, clientSecret, user, OAuthToken.DEFAULT_SCOPE);
    assertTrue(response3.isSucceeded());
    NewOAuthTokenResponse tokenResponse = response3.getEntity();
    assertNotNull(tokenResponse.getRefreshToken());

    // normal token works, JWT tokens are now invalid
    assertTrue(tokenManager.authenticate(tokenResponse.getAccessToken()).isSucceeded());
    assertFalse(tokenManager.authenticate(jwtResponse1.getAccessToken()).isSucceeded());
    assertFalse(tokenManager.authenticate(jwtResponse2.getAccessToken()).isSucceeded());

    // To create a new JWT token, refreshToken from normal token must be used
    ServiceOperationResult<NewOAuthTokenResponse> succRefresh =
        tokenManager.refreshJwtAccessToken(clientId, clientSecret, tokenResponse.getRefreshToken());
    assertTrue(succRefresh.isSucceeded());
  }

  @Test
  public void refreshToken() {
    User user = createAndSaveRandomUser();

    OAuthAppInfo app = appManager.addApp(user, RandomStringUtils.randomAlphabetic(10)).getEntity();
    String clientId = app.getClientId();
    String clientSecret = app.getUnhashedClientSecret();

    NewOAuthTokenResponse createdToken =
        tokenManager
            .createNewToken(clientId, clientSecret, user, OAuthToken.DEFAULT_SCOPE)
            .getEntity();
    ServiceOperationResult<NewOAuthTokenResponse> response =
        tokenManager.refreshAccessToken(clientId, clientSecret, createdToken.getRefreshToken());
    assertTrue(response.isSucceeded());
    NewOAuthTokenResponse refreshedToken = response.getEntity();

    // create another for same user, tokens are updated
    assertEquals(1, tokenManager.getTokensForUser(user).size());
    assertEquals(
        CryptoUtils.hashToken(refreshedToken.getAccessToken()),
        tokenManager.getTokensForUser(user).get(0).getHashedAccessToken());
    assertEquals(
        CryptoUtils.hashToken(refreshedToken.getRefreshToken()),
        tokenManager.getTokensForUser(user).get(0).getHashedRefreshToken());

    // refresh with obsolete refresh token
    ServiceOperationResult<NewOAuthTokenResponse> failedRefresh =
        tokenManager.refreshAccessToken(clientId, clientSecret, createdToken.getRefreshToken());
    assertFalse(failedRefresh.isSucceeded());

    // refresh token not updated
    assertEquals(
        CryptoUtils.hashToken(refreshedToken.getRefreshToken()),
        tokenManager.getTokensForUser(user).get(0).getHashedRefreshToken());

    // create a JWT token by refreshing
    response =
        tokenManager.refreshJwtAccessToken(
            clientId, clientSecret, refreshedToken.getRefreshToken());
    assertTrue(response.isSucceeded());
    assertNull(response.getEntity().getRefreshToken());
    assertTrue(tokenManager.authenticate(response.getEntity().getAccessToken()).isSucceeded());

    // refresh token not updated
    assertEquals(
        CryptoUtils.hashToken(refreshedToken.getRefreshToken()),
        tokenManager.getTokensForUser(user).get(0).getHashedRefreshToken());

    // JWT refresh with obsolete refresh token
    failedRefresh =
        tokenManager.refreshAccessToken(clientId, clientSecret, createdToken.getRefreshToken());
    assertFalse(failedRefresh.isSucceeded());
  }

  @Test
  public void removeToken() {
    User user = createAndSaveRandomUser();

    OAuthAppInfo app = appManager.addApp(user, RandomStringUtils.randomAlphabetic(10)).getEntity();

    tokenManager.createNewToken(
        app.getClientId(), app.getUnhashedClientSecret(), user, OAuthToken.DEFAULT_SCOPE);
    assertEquals(1, tokenManager.getTokensForUser(user).size());

    ServiceOperationResult<OAuthToken> tokenRemovedResult =
        tokenManager.removeToken(user, app.getClientId());
    assertTrue(tokenRemovedResult.isSucceeded());
    assertEquals(0, tokenManager.getTokensForUser(user).size());
  }

  @Test
  public void removeAllTokens() {
    User appDeveloper = createAndSaveRandomUser();
    User normalUser1 = createAndSaveRandomUser();
    User normalUser2 = createAndSaveRandomUser();

    OAuthAppInfo app =
        appManager.addApp(appDeveloper, RandomStringUtils.randomAlphabetic(10)).getEntity();

    tokenManager.createNewToken(
        app.getClientId(), app.getUnhashedClientSecret(), normalUser1, OAuthToken.DEFAULT_SCOPE);
    tokenManager.createNewToken(
        app.getClientId(), app.getUnhashedClientSecret(), normalUser2, OAuthToken.DEFAULT_SCOPE);

    assertEquals(1, tokenManager.getTokensForUser(normalUser1).size());
    assertEquals(1, tokenManager.getTokensForUser(normalUser2).size());
    assertEquals(2, tokenDao.listTokensForClient(app.getClientId()).size());

    // removal of all tokens is restricted to the app developer
    tokenManager.removeAllTokens(normalUser1, app.getClientId());

    assertEquals(1, tokenManager.getTokensForUser(normalUser1).size());
    assertEquals(1, tokenManager.getTokensForUser(normalUser2).size());
    assertEquals(2, tokenDao.listTokensForClient(app.getClientId()).size());

    tokenManager.removeAllTokens(appDeveloper, app.getClientId());

    assertEquals(0, tokenManager.getTokensForUser(normalUser1).size());
    assertEquals(0, tokenManager.getTokensForUser(normalUser2).size());
    assertEquals(0, tokenDao.listTokensForClient(app.getClientId()).size());
  }

  @Test
  public void authenticate() {
    User user = createAndSaveRandomUser();

    OAuthAppInfo app = appManager.addApp(user, RandomStringUtils.randomAlphabetic(10)).getEntity();

    NewOAuthTokenResponse token =
        tokenManager
            .createNewToken(
                app.getClientId(), app.getUnhashedClientSecret(), user, OAuthToken.DEFAULT_SCOPE)
            .getEntity();
    NewOAuthTokenResponse jwtToken =
        tokenManager
            .createNewJwtToken(
                app.getClientId(), app.getUnhashedClientSecret(), user, OAuthToken.DEFAULT_SCOPE)
            .getEntity();
    // Creating a JWT token will not override the normal token above,
    // it is assumed that the refresh token is known from the normal token
    assertNull(jwtToken.getRefreshToken());

    // successful
    assertTrue(tokenManager.authenticate(token.getAccessToken()).isSucceeded());
    assertTrue(tokenManager.authenticate(jwtToken.getAccessToken()).isSucceeded());

    // incorrect token
    assertFalse(tokenManager.authenticate(token.getRefreshToken()).isSucceeded());
    assertFalse(tokenManager.authenticate("").isSucceeded());

    // set token in the past
    OAuthToken valid = tokenManager.authenticate(token.getAccessToken()).getEntity();
    valid.setExpiryTime(Instant.now().minus(5, ChronoUnit.DAYS));
    tokenDao.save(valid);

    ServiceOperationResult<OAuthToken> expired = tokenManager.authenticate(token.getAccessToken());
    assertFalse(expired.isSucceeded());
    assertThat(expired.getMessage(), containsString("expired"));

    // Forge jwt tokens with information that should be rejected

    Optional<OAuthToken> actualToken =
        tokenDao.findByRefreshTokenHash(CryptoUtils.hashToken(token.getRefreshToken()));
    assertTrue(actualToken.isPresent());
    String refreshTokenHash = actualToken.get().getHashedRefreshToken();

    // Expiry in the past
    String expiredJwtToken =
        Jwts.builder()
            .setIssuer(properties.getServerUrl())
            .setIssuedAt(new Date())
            .setExpiration(Date.from(Instant.now().minusSeconds(3600)))
            .claim("refreshTokenHash", refreshTokenHash)
            .signWith(properties.getJwtKey())
            .compact();
    expired = tokenManager.authenticate(expiredJwtToken);
    assertFalse(expired.isSucceeded());
    assertThat(expired.getMessage(), containsString("expired"));

    // Wrong signature
    String badSignatureJwtToken =
        Jwts.builder()
            .setIssuer(properties.getServerUrl())
            .setIssuedAt(new Date())
            .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
            .claim("refreshTokenHash", refreshTokenHash)
            .signWith(Keys.secretKeyFor(SignatureAlgorithm.HS256))
            .compact();
    ServiceOperationResult<OAuthToken> badRequest = tokenManager.authenticate(badSignatureJwtToken);
    assertFalse(badRequest.isSucceeded());

    // Wrong issuing server
    String wrongIssuerJwtToken =
        Jwts.builder()
            .setIssuer("http://wrong-server.com")
            .setIssuedAt(new Date())
            .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
            .claim("refreshTokenHash", refreshTokenHash)
            .signWith(properties.getJwtKey())
            .compact();
    badRequest = tokenManager.authenticate(wrongIssuerJwtToken);
    assertFalse(badRequest.isSucceeded());

    // Non-existing refresh token
    String badRefreshTokenIdJwtToken =
        Jwts.builder()
            .setIssuer(properties.getServerUrl())
            .setIssuedAt(new Date())
            .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
            .claim("refreshTokenHash", "bad")
            .signWith(properties.getJwtKey())
            .compact();
    badRequest = tokenManager.authenticate(badRefreshTokenIdJwtToken);
    assertFalse(badRequest.isSucceeded());
  }
}
