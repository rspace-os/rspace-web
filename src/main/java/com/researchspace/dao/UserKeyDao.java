package com.researchspace.dao;

import com.researchspace.model.User;
import com.researchspace.model.UserKeyPair;

public interface UserKeyDao extends GenericDao<UserKeyPair, Long> {

  UserKeyPair getUserKeyPair(User user);
}
