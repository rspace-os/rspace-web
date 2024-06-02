package com.researchspace.dao;

import com.researchspace.model.oauth.OAuthApp;
import java.util.List;
import java.util.Optional;

public interface OAuthAppDao extends GenericDao<OAuthApp, Long> {
  /** Get the OAuth app with clientId registered by the user */
  Optional<OAuthApp> getApp(Long userId, String clientId);

  Optional<OAuthApp> getApp(String clientId);

  /** Get a list of OAuth apps registered by a user */
  List<OAuthApp> getApps(Long userId);

  /**
   * @return <code>true</code> if 1 app has been removed, 0 otherwise.
   */
  boolean removeApp(Long userId, String clientId);
}
