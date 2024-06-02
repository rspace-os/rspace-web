package com.researchspace.service;

import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import java.util.Optional;

/** Service to handle storage and retrieval of UserConnection OAuth tokens */
public interface UserConnectionManager extends GenericManager<UserConnection, UserConnectionId> {

  /**
   * Gets a UserConnection if it exists. <br>
   *
   * @param rspaceUserName RSpace username
   * @param providerName e.g. 'egnyte', 'figshare'
   * @return An Optional<UserConnection>
   */
  Optional<UserConnection> findByUserNameProviderName(String rspaceUserName, String providerName);

  /**
   * Deletes connection, returning number of deleted rows
   *
   * @param providername
   * @param rspaceUserName
   */
  int deleteByUserAndProvider(String providername, String rspaceUserName);
}
