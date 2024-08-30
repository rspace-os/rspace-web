package com.researchspace.service.impl;

import com.researchspace.core.util.CryptoUtils;
import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.dao.UserApiKeyDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.UserApiKey;
import com.researchspace.service.UserApiKeyManager;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service("userApiKeyManager")
public class UserApiKeyManagerImpl extends GenericManagerImpl<UserApiKey, Long>
    implements UserApiKeyManager {

  private UserApiKeyDao apiDao;
  private UserDao userDao;

  public UserApiKeyManagerImpl(UserApiKeyDao apikeyDao) {
    super(apikeyDao);
    this.apiDao = apikeyDao;
  }

  @Override
  public Optional<User> findUserByKey(String apiKey) {
    return apiDao.getAll().stream()
        .filter(userApiKey -> CryptoUtils.matchBCrypt(apiKey, userApiKey.getApiKey()))
        .map(UserApiKey::getUser)
        .findFirst();
  }

  @Override
  public UserApiKey createKeyForUser(User user) {
    String apiKey = SecureStringUtils.getSecureRandomAlphanumeric(32);
    UserApiKey userApiKey = null;
    Optional<UserApiKey> keyForUser = apiDao.getKeyForUser(user);
    // if the user already had an ApiKey THEN regenerate it
    if (keyForUser.isPresent()) {
      userApiKey = keyForUser.get();
      userApiKey.setApiKey(CryptoUtils.encodeBCrypt(apiKey));
      userApiKey.setCreated(new Date());
    } else { // if the user has not got an ApiKey THEN generate it
      userApiKey = new UserApiKey(user, CryptoUtils.encodeBCrypt(apiKey));
    }

    userApiKey = apiDao.save(userApiKey);
    return new UserApiKey(userApiKey.getId(), user, apiKey);
  }

  @Override
  public int revokeKeyForUser(User user) {
    return apiDao.deleteForUser(user);
  }

  @Override
  public boolean isKeyExistingForUser(User user) {
    return apiDao.getKeyForUser(user).isPresent();
  }

  @Override
  public long calculateApiKeyAgeForUser(User user) {
    Optional<UserApiKey> optApiKey = apiDao.getKeyForUser(user);
    if (optApiKey.isEmpty()) {
      return 0L;
    } else {
      Date created = optApiKey.get().getCreated();
      LocalDate createDate = created.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
      return ChronoUnit.DAYS.between(createDate, LocalDate.now());
    }
  }

  @Override
  public UserApiKey hashAndSaveApiKeyForUser(User user, UserApiKey apiKey) {
    UserApiKey apiKeyToSave = new UserApiKey(user, CryptoUtils.encodeBCrypt(apiKey.getApiKey()));
    apiKeyToSave = apiDao.save(apiKeyToSave);
    return new UserApiKey(apiKeyToSave.getId(), user, apiKey.getApiKey());
  }
}
