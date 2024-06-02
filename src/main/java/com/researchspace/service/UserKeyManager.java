package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.UserKeyPair;

/** Interface for managing user ssh keys for Edinburgh instance */
public interface UserKeyManager {

  /** Methods for managing user ssh keys for 3rd part (i.e. UoE DataStore) */
  UserKeyPair getUserKeyPair(User user);

  /**
   * Creates new or overrides existing key pair for the user
   *
   * @param user
   * @return
   */
  UserKeyPair createNewUserKeyPair(User user);

  /**
   * Removes key pair registered for given user. This can't be reverted!
   *
   * @param user
   * @return
   */
  void removeUserKeyPair(User user);
}
