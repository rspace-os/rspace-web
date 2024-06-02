package com.researchspace.service.impl;

import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.dao.UserApiKeyDao;
import com.researchspace.model.User;
import com.researchspace.model.UserApiKey;
import com.researchspace.service.UserApiKeyManager;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service("userApiKeyManager")
public class UserApiKeyManagerImpl extends GenericManagerImpl<UserApiKey, Long>
    implements UserApiKeyManager {
  UserApiKeyDao apiDao;

  public UserApiKeyManagerImpl(UserApiKeyDao apikeyDao) {
    super(apikeyDao);
    this.apiDao = apikeyDao;
  }

  @Override
  public Optional<User> findUserByKey(String apiKey) {
    return apiDao.getBySimpleNaturalId(apiKey).map(UserApiKey::getUser);
  }

  @Override
  public UserApiKey createKeyForUser(User user) {
    String apiKey = SecureStringUtils.getSecureRandomAlphanumeric(32);
    UserApiKey keyToSave = null;
    Optional<UserApiKey> keyForUser = apiDao.getKeyForUser(user);
    if (keyForUser.isPresent()) {
      keyToSave = keyForUser.get();
      keyToSave.setApiKey(apiKey);
    } else {
      keyToSave = new UserApiKey(user, apiKey);
    }
    return apiDao.save(keyToSave);
  }

  @Override
  public int revokeKeyForUser(User user) {
    return apiDao.deleteForUser(user);
  }

  @Override
  public Optional<UserApiKey> getKeyForUser(User user) {
    return apiDao.getKeyForUser(user);
  }
}
