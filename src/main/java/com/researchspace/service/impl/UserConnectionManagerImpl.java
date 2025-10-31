package com.researchspace.service.impl;

import static com.researchspace.CacheNames.INTEGRATION_INFO;

import com.researchspace.dao.UserConnectionDao;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.UserConnectionManager;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service("userConnectionManager")
public class UserConnectionManagerImpl extends GenericManagerImpl<UserConnection, UserConnectionId>
    implements UserConnectionManager {

  static final String SAVE_CONNECTION_SPEL =
      "#connection.id.userId + #connection.id.providerId + "
          + "(#connection.id.providerUserId != null ? #connection.id.providerUserId : "
          + "T(com.researchspace.service.IntegrationsHandler).PROVIDER_USER_ID)";
  private UserConnectionDao userConnectionDao;

  public UserConnectionManagerImpl(@Autowired UserConnectionDao userDao) {
    this.dao = userDao;
    this.userConnectionDao = userDao;
  }

  @Override
  public Optional<Integer> findMaxRankByUserNameProviderName(
      String rspaceUserName, String providerName) {
    return userConnectionDao.findMaxRankByUserNameProviderName(rspaceUserName, providerName);
  }

  @Override
  public Optional<UserConnection> findByUserNameProviderName(
      String rspaceUserName, String providerName) {
    return userConnectionDao.findByUserNameProviderName(rspaceUserName, providerName);
  }

  @Override
  public List<UserConnection> findListByUserNameProviderName(
      String rspaceUserName, String providerName) {
    return userConnectionDao.findListByUserNameProviderName(rspaceUserName, providerName);
  }

  @Override
  @Cacheable(value = INTEGRATION_INFO, key = "#rspaceUserName + #providerName + #discriminant")
  public Optional<UserConnection> findByUserNameProviderName(
      String rspaceUserName, String providerName, String discriminant) {
    return userConnectionDao.findByUserNameProviderName(rspaceUserName, providerName, discriminant);
  }

  @Override
  @CacheEvict(
      value = INTEGRATION_INFO,
      key =
          "#rspaceUserName + #providername +"
              + " T(com.researchspace.service.IntegrationsHandler).PROVIDER_USER_ID")
  public int deleteByUserAndProvider(String rspaceUserName, String providername) {
    return userConnectionDao.deleteByUserAndProvider(rspaceUserName, providername);
  }

  @Override
  @CacheEvict(value = INTEGRATION_INFO, key = "#rspaceUserName + #providername + #discriminant")
  public int deleteByUserAndProvider(
      String rspaceUserName, String providername, String discriminant) {
    return userConnectionDao.deleteByUserAndProvider(rspaceUserName, providername, discriminant);
  }

  // overrides so we can add cache eviction annotation
  @Override
  @CacheEvict(value = INTEGRATION_INFO, key = SAVE_CONNECTION_SPEL)
  public UserConnection save(UserConnection connection) {
    return super.save(connection);
  }
}
