package com.researchspace.service;

import com.researchspace.api.v1.model.NewOAuthTokenResponse;
import com.researchspace.model.User;
import com.researchspace.model.oauth.OAuthToken;
import com.researchspace.model.oauth.OAuthTokenType;
import com.researchspace.model.views.ServiceOperationResult;
import java.util.List;

/**
 * Manager for OAuth tokens issued by RSpace for other apps. Issues and receives two kinds of tokens
 * - the "conventional" token and the JWT token. JWT tokens are still used for OAuth, they just
 * allow us to have many tokens for the same OAuthApp
 */
public interface OAuthTokenManager {

  /**
   * Validate access or refresh token to make sure it is valid.
   *
   * @return an error message if the token is not valid.
   */
  ServiceOperationResult<Void> validateToken(String token);

  /**
   * Attempt to get the OAuthToken associated with the request.
   *
   * @param accessToken base64 (unhashed token) or base64.base64.base64 (JWT) string
   * @return the associated token, which includes the authenticated user
   */
  ServiceOperationResult<OAuthToken> authenticate(String accessToken);

  /**
   * Generate a new access/refresh token pair
   *
   * @param refreshToken unhashed refresh token supplied by client
   * @return NewOAuthTokenResponse, which includes new unhashed access and refresh tokens
   */
  ServiceOperationResult<NewOAuthTokenResponse> refreshAccessToken(
      String clientId, String clientSecret, String refreshToken);

  /**
   * Generate new JWT access token WITHOUT changing the access/refresh tokens
   *
   * @param refreshToken unhashed refresh token supplied by client
   * @return NewOAuthTokenResponse, which includes new JWT access token
   */
  ServiceOperationResult<NewOAuthTokenResponse> refreshJwtAccessToken(
      String clientId, String clientSecret, String refreshToken);

  /**
   * Generate new access and refresh token for the client app and user pair.
   *
   * @param clientId client app identifier
   * @param user upon behalf the app will be acting as
   * @return generated access token, refresh token, expiry time for the access token, and scope
   */
  ServiceOperationResult<NewOAuthTokenResponse> createNewToken(
      String clientId, String clientSecret, User user, OAuthTokenType tokenType);

  /**
   * Generate new JWT access token. Does not override the access/refresh tokens if there is an
   * existing connection.
   */
  ServiceOperationResult<NewOAuthTokenResponse> createNewJwtToken(
      String clientId, String clientSecret, User user, OAuthTokenType tokenType);

  List<OAuthToken> getTokensForUser(User user);

  /**
   * Remove (invalidate access) the OAuth app specified by `clientId` for the `user`
   *
   * @return the removed OAuthToken, or an error message if the token between the user and clientId
   *     did not exist
   */
  ServiceOperationResult<OAuthToken> removeToken(User user, String clientId);

  /**
   * Remove (invalidate access) all tokens for the OAuth app identified by `clientId`. Removal of
   * all tokens is restricted to the developer of the app
   *
   * @return an error message if the token between the user and clientId did not exist
   */
  ServiceOperationResult<Void> removeAllTokens(User appDeveloper, String clientId);
}
