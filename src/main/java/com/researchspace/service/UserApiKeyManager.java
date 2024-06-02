package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.UserApiKey;
import java.util.Optional;

public interface UserApiKeyManager extends GenericManager<UserApiKey, Long> {

  Optional<User> findUserByKey(String apiKey);

  /**
   * Creates or updates a UserApiKey for the given user
   *
   * @param username
   * @return
   */
  UserApiKey createKeyForUser(User username);

  /**
   * Deletes key for user, if it exists
   *
   * @return Either 1 (key was deleted) or 0 (key never existed)
   */
  int revokeKeyForUser(User user);

  /**
   * Looks up a key for a given user
   *
   * @param user
   * @return
   */
  Optional<UserApiKey> getKeyForUser(User user);
}
