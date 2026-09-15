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
import org.springframework.stereotype.Service;

@Service("userConnectionManager")
public class UserConnectionManagerImpl extends GenericManagerImpl<UserConnection, UserConnectionId>
    implements UserConnectionManager {

  static final String SAVE_CONNECTION_SPEL = "#connection.id.userId + #connection.id.providerId";
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
  public Optional<UserConnection> findByUserNameProviderName(
      String rspaceUserName, String providerName, String discriminant) {
    return userConnectionDao.findByUserNameProviderName(rspaceUserName, providerName, discriminant);
  }

  @Override
  @CacheEvict(value = INTEGRATION_INFO, key = "#rspaceUserName + #providername")
  public int deleteByUserAndProvider(String rspaceUserName, String providername) {
    return userConnectionDao.deleteByUserAndProvider(rspaceUserName, providername);
  }

  @Override
  @CacheEvict(value = INTEGRATION_INFO, key = "#rspaceUserName + #providername")
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

  @Override
  @CacheEvict(value = INTEGRATION_INFO, key = SAVE_CONNECTION_SPEL)
  public UserConnection replaceConnection(UserConnection connection) {
    // one transaction, so a failed save cannot leave the user with no connection at all. Goes
    // straight to the DAO because a self-invocation would bypass the transactional proxy anyway.
    userConnectionDao.deleteByUserAndProvider(
        connection.getId().getUserId(), connection.getId().getProviderId());
    return userConnectionDao.save(connection);
  }
}
