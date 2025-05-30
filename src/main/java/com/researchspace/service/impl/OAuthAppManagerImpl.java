package com.researchspace.service.impl;

import com.researchspace.core.util.CryptoUtils;
import com.researchspace.dao.OAuthAppDao;
import com.researchspace.model.User;
import com.researchspace.model.frontend.OAuthAppInfo;
import com.researchspace.model.frontend.PublicOAuthAppInfo;
import com.researchspace.model.oauth.OAuthApp;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.OAuthAppManager;
import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OAuthAppManagerImpl implements OAuthAppManager {
  private static TemporalAmount DEFAULT_EXPIRY_TIME = Duration.ofHours(1L);
  private final OAuthAppDao appDao;

  @Autowired
  public OAuthAppManagerImpl(OAuthAppDao appDao) {
    this.appDao = appDao;
  }

  @Override
  public boolean isClientSecretCorrect(String clientId, String secret) {
    Optional<OAuthApp> app = appDao.getApp(clientId);

    return app.filter(
            app2 -> app2.getHashedClientSecret().equals(CryptoUtils.hashClientSecret(secret)))
        .isPresent();
  }

  @Override
  public ServiceOperationResult<OAuthAppInfo> addApp(User appDeveloper, String appName) {
    String clientId = CryptoUtils.generateClientId();
    String clientSecret = CryptoUtils.generateUnhashedClientSecret();

    appDao.save(
        new OAuthApp(appDeveloper, appName, clientId, CryptoUtils.hashClientSecret(clientSecret)));

    return new ServiceOperationResult<>(new OAuthAppInfo(appName, clientId, clientSecret), true);
  }

  @Override
  public ServiceOperationResult<Void> removeApp(User appDeveloper, String clientId) {
    boolean isAppRemoved = appDao.removeApp(appDeveloper.getId(), clientId);

    return new ServiceOperationResult<>(null, isAppRemoved);
  }

  @Override
  public Optional<PublicOAuthAppInfo> getApp(User appDeveloper, String clientId) {
    Optional<OAuthApp> app = appDao.getApp(appDeveloper.getId(), clientId);

    return app.map(PublicOAuthAppInfo::new);
  }

  @Override
  public Optional<PublicOAuthAppInfo> getApp(String clientId) {
    Optional<OAuthApp> app = appDao.getApp(clientId);
    return app.map(PublicOAuthAppInfo::new);
  }

  @Override
  public List<PublicOAuthAppInfo> getApps(User appDeveloper) {
    return appDao.getApps(appDeveloper.getId()).stream()
        .map(PublicOAuthAppInfo::new)
        .collect(Collectors.toList());
  }

  @Override
  public TemporalAmount getOAuthTokenExpiryTimeInSeconds() {
    return DEFAULT_EXPIRY_TIME;
  }
}
