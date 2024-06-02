package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.frontend.OAuthAppInfo;
import com.researchspace.model.frontend.PublicOAuthAppInfo;
import com.researchspace.model.views.ServiceOperationResult;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.Optional;

/** Validates clientIDs for registered OAuth Apps */
public interface OAuthAppManager {
  /** Check if the client secret is correct for the given clientId and app developer */
  boolean isClientSecretCorrect(String clientId, String secret);

  ServiceOperationResult<OAuthAppInfo> addApp(User appDeveloper, String appName);

  ServiceOperationResult<Void> removeApp(User appDeveloper, String clientId);

  Optional<PublicOAuthAppInfo> getApp(User appDeveloper, String clientId);

  Optional<PublicOAuthAppInfo> getApp(String clientId);

  /** Get all public information on the OAuth apps created by a developer */
  List<PublicOAuthAppInfo> getApps(User appDeveloper);

  TemporalAmount getOAuthTokenExpiryTimeInSeconds();
}
