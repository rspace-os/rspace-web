package com.researchspace.service.impl;

import static com.researchspace.CacheNames.INTEGRATION_INFO;

import com.researchspace.dao.UserConnectionDao;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.UserConnectionManager;
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
  public Optional<UserConnection> findByUserNameProviderName(
      String rspaceUserName, String providerName) {
    return userConnectionDao.findByUserNameProviderName(rspaceUserName, providerName);
  }

  @Override
  @CacheEvict(value = INTEGRATION_INFO, key = "#rspaceUserName + #providername")
  public int deleteByUserAndProvider(String providername, String rspaceUserName) {
    return userConnectionDao.deleteByUserAndProvider(providername, rspaceUserName);
  }

  // overrides so we can add cache eviction annotation
  @Override
  @CacheEvict(value = INTEGRATION_INFO, key = SAVE_CONNECTION_SPEL)
  public UserConnection save(UserConnection connection) {
    return super.save(connection);
  }
}
