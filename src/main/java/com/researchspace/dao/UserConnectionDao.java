package com.researchspace.dao;

import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import java.util.Optional;

public interface UserConnectionDao extends GenericDao<UserConnection, UserConnectionId> {

  /**
   * Retrieves a user connection for the specified username and provider name
   *
   * @param rspaceUserName
   * @param providerName
   * @return an Optional<UserConnection> which may be empty if no UserConnection exists, i.e if user
   *     has not authorised a connection to RSpace.
   */
  Optional<UserConnection> findByUserNameProviderName(String rspaceUserName, String providerName);

  /**
   * Deletes connection for a user-provider combination, returning number of items deleted
   *
   * @param providername
   * @param rspaceUserName
   */
  int deleteByUserAndProvider(String providername, String rspaceUserName);
}
