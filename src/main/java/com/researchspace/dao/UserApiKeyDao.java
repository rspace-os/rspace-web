package com.researchspace.dao;

import com.researchspace.model.User;
import com.researchspace.model.UserApiKey;
import java.util.Optional;

/** DAO operations on API keys */
public interface UserApiKeyDao extends GenericDao<UserApiKey, Long> {

  /**
   * Deletes API key for a user
   *
   * @param user A {@link User}
   * @return Number of erows deleted - should be either 1 (user had a key) or 0 (user did not have a
   *     key)
   */
  int deleteForUser(User user);

  /** Gets UserApiKey if it exists. It may not exist if user has never generated it. */
  Optional<UserApiKey> getKeyForUser(User user);
}
