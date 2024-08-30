package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.UserApiKey;
import java.util.Optional;

public interface UserApiKeyManager extends GenericManager<UserApiKey, Long> {

  Optional<User> findUserByKey(String apiKey);

  /**
   * Creates or updates a UserApiKey for the given user
   *
   * @param user
   * @return
   */
  UserApiKey createKeyForUser(User user);

  /**
   * Deletes key for user, if it exists
   *
   * @return Either 1 (key was deleted) or 0 (key never existed)
   */
  int revokeKeyForUser(User user);

  /**
   * Check if a key is already existing for a given user
   *
   * @param user
   * @return
   */
  boolean isKeyExistingForUser(User user);

  /**
   * Calculate the age of an UserApiKey
   *
   * @param user
   * @return if existing, returns a number of days since key creation, otherwise 0
   */
  long calculateApiKeyAgeForUser(User user);

  /**
   * Perform the bCrypt hashing of the apiKey for a given user before saving the object on the
   * database
   *
   * @param user
   * @param apiKey
   * @return the apiKey object with the clear apiKey secret
   */
  UserApiKey hashAndSaveApiKeyForUser(User user, UserApiKey apiKey);
}
