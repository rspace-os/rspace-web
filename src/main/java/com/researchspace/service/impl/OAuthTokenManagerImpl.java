package com.researchspace.service.impl;

import com.researchspace.api.v1.model.NewOAuthTokenResponse;
import com.researchspace.core.util.CryptoUtils;
import com.researchspace.dao.OAuthTokenDao;
import com.researchspace.model.User;
import com.researchspace.model.frontend.PublicOAuthAppInfo;
import com.researchspace.model.oauth.OAuthToken;
import com.researchspace.model.oauth.OAuthTokenType;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.OAuthAppManager;
import com.researchspace.service.OAuthTokenManager;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.stereotype.Service;

@Service
public class OAuthTokenManagerImpl implements OAuthTokenManager {
  private static final int ALLOWED_CLOCK_SKEW = 3 * 60;
  private static final int TOKEN_LENGTH = 32; // 192 bits in base64 (6bit)
  private static final Pattern jwtToken = Pattern.compile(".+\\..+\\..+");

  @Autowired OAuthTokenDao tokenDao;

  @Autowired OAuthAppManager appManager;

  @Autowired IPropertyHolder properties;

  @Override
  public ServiceOperationResult<Void> validateToken(String token) {
    if (StringUtils.isEmpty(token)) {
      return new ServiceOperationResult<>(null, false, "token empty or missing");
    }
    boolean isJwtToken = jwtToken.matcher(token).matches();
    if (isJwtToken) {
      for (String section : token.split("\\.")) {
        if (!Base64.isBase64(section)) {
          return new ServiceOperationResult<>(null, false, section + " not in base64 format");
        }
      }
    } else {
      if (token.length() != TOKEN_LENGTH) {
        return new ServiceOperationResult<>(null, false, "token length incorrect");
      }
      if (!Base64.isBase64(token)) {
        return new ServiceOperationResult<>(null, false, "token not in base64 format");
      }
    }
    return new ServiceOperationResult<>(null, true);
  }

  @Override
  public ServiceOperationResult<OAuthToken> authenticate(String accessToken) {
    boolean isJwtToken = jwtToken.matcher(accessToken).matches();
    if (isJwtToken) {
      Jws<Claims> jws;
      try {
        jws =
            Jwts.parserBuilder()
                .setAllowedClockSkewSeconds(
                    ALLOWED_CLOCK_SKEW) // Expiration date is validated automatically
                .requireIssuer(properties.getServerUrl())
                .setSigningKey(properties.getJwtKey())
                .build()
                .parseClaimsJws(accessToken);
        String refreshTokenHash = jws.getBody().get("refreshTokenHash", String.class);
        Optional<OAuthToken> token = tokenDao.findByRefreshTokenHash(refreshTokenHash);
        return new ServiceOperationResult<>(token.orElse(null), token.isPresent());
      } catch (ExpiredJwtException ex) {
        return new ServiceOperationResult<>(null, false, "JWT token expired!");
      } catch (JwtException | ObjectRetrievalFailureException ex) {
        // This could only happen if someone was "pentesting" or pasted the token badly
        return new ServiceOperationResult<>(null, false, "Bad JWT token!");
      }
    }

    String hash = CryptoUtils.hashToken(accessToken);
    Optional<OAuthToken> tokenFound = tokenDao.findByAccessTokenHash(hash);
    if (tokenFound.isPresent()) {
      OAuthToken token = tokenFound.get();
      if (token.getExpiryTime().isAfter(Instant.now())) {
        return new ServiceOperationResult<>(token, true);
      } else {
        return new ServiceOperationResult<>(null, false, "OAuth token expired!");
      }
    } else {
      return new ServiceOperationResult<>(null, false, "OAuth token not found!");
    }
  }

  @Override
  public ServiceOperationResult<NewOAuthTokenResponse> refreshAccessToken(
      String clientId, String clientSecret, String refreshToken) {

    if (!appManager.isClientSecretCorrect(clientId, clientSecret)) {
      return new ServiceOperationResult<>(null, false, "ClientId or ClientSecret incorrect");
    }
    OAuthToken token = getOAuthTokenForRefreshToken(clientId, refreshToken);
    if (token == null) {
      return new ServiceOperationResult<>(
          null, false, "OAuth token not found! Perhaps its refresh rights have been removed.");
    }

    String newAccessToken = CryptoUtils.generateUnhashedToken();
    String hashedAccessToken = CryptoUtils.hashToken(newAccessToken);
    String newRefreshToken = CryptoUtils.generateUnhashedToken();
    String hashedRefreshToken = CryptoUtils.hashToken(newRefreshToken);
    Instant expiryTime = generateExpiryTime();

    token.setHashedAccessToken(hashedAccessToken);
    token.setHashedRefreshToken(hashedRefreshToken);

    NewOAuthTokenResponse response = new NewOAuthTokenResponse();
    response.setAccessToken(newAccessToken);
    response.setRefreshToken(newRefreshToken);
    response.setExpiryTime(expiryTime);

    return new ServiceOperationResult<>(response, true);
  }

  @Override
  public ServiceOperationResult<NewOAuthTokenResponse> refreshJwtAccessToken(
      String clientId, String clientSecret, String refreshToken) {

    if (!appManager.isClientSecretCorrect(clientId, clientSecret)) {
      return new ServiceOperationResult<>(null, false, "ClientId or ClientSecret incorrect");
    }
    OAuthToken token = getOAuthTokenForRefreshToken(clientId, refreshToken);
    if (token == null) {
      return new ServiceOperationResult<>(
          null, false, "OAuth token not found! Perhaps its refresh rights have been removed.");
    }

    Instant expiryTime = generateExpiryTime();
    String newAccessToken =
        Jwts.builder()
            .setIssuer(properties.getServerUrl())
            .setIssuedAt(new Date())
            .setExpiration(Date.from(expiryTime))
            .claim(
                "refreshTokenHash",
                token.getHashedRefreshToken()) // Don't invalidate previous JWT tokens
            .signWith(properties.getJwtKey())
            .compact();

    NewOAuthTokenResponse response = new NewOAuthTokenResponse();
    response.setAccessToken(newAccessToken);
    response.setRefreshToken(null);
    response.setExpiryTime(expiryTime);

    return new ServiceOperationResult<>(response, true);
  }

  private OAuthToken getOAuthTokenForRefreshToken(String clientId, String refreshToken) {
    String hash = CryptoUtils.hashToken(refreshToken);
    Optional<OAuthToken> optToken = tokenDao.findByRefreshTokenHash(hash);

    OAuthToken result = null;
    if (optToken.isPresent() && optToken.get().getClientId().equals(clientId)) {
      result = tokenDao.load(optToken.get().getId());
    }
    return result;
  }

  @Override
  public ServiceOperationResult<NewOAuthTokenResponse> createNewToken(
      String clientId, String clientSecret, User user, OAuthTokenType tokenType) {

    if (!appManager.isClientSecretCorrect(clientId, clientSecret)) {
      return new ServiceOperationResult<>(null, false, "ClientId or ClientSecret incorrect");
    }

    String newAccessToken = CryptoUtils.generateUnhashedToken();
    String hashedAccessToken = CryptoUtils.hashToken(newAccessToken);
    String newRefreshToken = CryptoUtils.generateUnhashedToken();
    String hashedRefreshToken = CryptoUtils.hashToken(newRefreshToken);
    Instant expiryTime = generateExpiryTime();

    NewOAuthTokenResponse response = new NewOAuthTokenResponse();
    response.setAccessToken(newAccessToken);
    response.setRefreshToken(newRefreshToken);
    response.setExpiryTime(expiryTime);

    Optional<OAuthToken> existingToken = tokenDao.getToken(clientId, user.getId(), tokenType);
    if (existingToken.isPresent()) {
      OAuthToken token = tokenDao.load(existingToken.get().getId());
      token.setHashedAccessToken(hashedAccessToken);
      token.setHashedRefreshToken(hashedRefreshToken);
      token.setExpiryTime(expiryTime);
    } else {
      OAuthToken token = new OAuthToken(user, clientId, tokenType);
      token.setHashedAccessToken(hashedAccessToken);
      token.setExpiryTime(expiryTime);
      token.setHashedRefreshToken(hashedRefreshToken);
      tokenDao.save(token);
    }
    return new ServiceOperationResult<>(response, true);
  }

  @Override
  public ServiceOperationResult<NewOAuthTokenResponse> createNewJwtToken(
      String clientId, String clientSecret, User user, OAuthTokenType tokenType) {

    if (!appManager.isClientSecretCorrect(clientId, clientSecret)) {
      return new ServiceOperationResult<>(null, false, "ClientId or ClientSecret incorrect");
    }
    OAuthToken originalToken = tokenDao.getToken(clientId, user.getId(), tokenType).orElse(null);
    NewOAuthTokenResponse response = new NewOAuthTokenResponse();

    // Assume that if a JWT is requested for an already existing OAuthToken, the unhashed refresh
    // token is known
    if (originalToken == null) {
      String refreshToken = CryptoUtils.generateUnhashedToken();
      originalToken = new OAuthToken(user, clientId, tokenType);
      originalToken.setHashedRefreshToken(CryptoUtils.hashToken(refreshToken));
      response.setRefreshToken(refreshToken);
      tokenDao.save(originalToken);
    }
    Instant expiryTime = generateExpiryTime();
    String newAccessToken =
        Jwts.builder()
            .setIssuer(properties.getServerUrl())
            .setIssuedAt(new Date())
            .setExpiration(Date.from(expiryTime))
            .claim("refreshTokenHash", originalToken.getHashedRefreshToken())
            .signWith(properties.getJwtKey())
            .compact();

    response.setAccessToken(newAccessToken);
    response.setExpiryTime(expiryTime);

    return new ServiceOperationResult<>(response, true);
  }

  private Instant generateExpiryTime() {
    TemporalAmount lifeTime = appManager.getOAuthTokenExpiryTimeInSeconds();
    return Instant.now().plus(lifeTime);
  }

  @Override
  public List<OAuthToken> getTokensForUser(User user) {
    return tokenDao.listTokensForUser(user.getId());
  }

  @Override
  public ServiceOperationResult<OAuthToken> removeToken(User user, String clientId) {
    Optional<OAuthToken> token =
        tokenDao.getToken(clientId, user.getId(), OAuthTokenType.API_GENERATED_TOKEN);
    if (!token.isPresent()) {
      return new ServiceOperationResult<>(
          null, false, "Could not find OAuth connection for the given client app and user.");
    }
    tokenDao.remove(token.get().getId());
    return new ServiceOperationResult<>(token.get(), true);
  }

  @Override
  public ServiceOperationResult<Void> removeAllTokens(User appDeveloper, String clientId) {
    Optional<PublicOAuthAppInfo> app = appManager.getApp(appDeveloper, clientId);

    if (!app.isPresent()) {
      return new ServiceOperationResult<>(
          null,
          false,
          "OAuth app with clientId "
              + clientId
              + "does not exist "
              + "for the user "
              + appDeveloper.getId());
    }
    int tokensRemoved = tokenDao.removeAllTokens(clientId);

    return new ServiceOperationResult<>(null, true, "Tokens removed: " + tokensRemoved);
  }
}
