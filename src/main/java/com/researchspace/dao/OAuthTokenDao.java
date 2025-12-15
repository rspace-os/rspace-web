package com.researchspace.dao;

import com.researchspace.model.oauth.OAuthToken;
import com.researchspace.model.oauth.OAuthTokenType;
import java.util.List;
import java.util.Optional;

/** OAuthTokens for user's client Apps */
public interface OAuthTokenDao extends GenericDao<OAuthToken, Long> {

  /**
   * Gets OAuthToken by encrypted Access Token
   *
   * @param accessTokenHash
   * @return Optional<OAuthToken>
   */
  Optional<OAuthToken> findByAccessTokenHash(String accessTokenHash);

  /**
   * Gets OAuthToken by encrypted Refresh Token
   *
   * @param refreshTokenHash
   * @return Optional<OAuthToken>
   */
  Optional<OAuthToken> findByRefreshTokenHash(String refreshTokenHash);

  /**
   * Gets possibly empty but non-null list of tokens for a user
   *
   * @param userId
   * @return
   */
  List<OAuthToken> listTokensForUser(Long userId);

  /**
   * Gets a list of tokens registered to a client
   *
   * @param clientId
   * @return
   */
  List<OAuthToken> listTokensForClient(String clientId);

  /**
   * Gets a single token for a combination of clientId and userId
   *
   * @param clientId
   * @param userId
   * @return
   */
  Optional<OAuthToken> getToken(String clientId, Long userId, OAuthTokenType tokenType);

  /**
   * Remove all tokens for a particular clientId
   *
   * @return the number of tokens removed
   */
  int removeAllTokens(String clientId);
}
