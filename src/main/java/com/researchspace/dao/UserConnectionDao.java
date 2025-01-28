package com.researchspace.dao;

import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import java.util.List;
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
   * Retrieves a user connection for the specified username and provider name
   *
   * @param providerName the provider name (i.e.: dryad)
   * @param rspaceUserName the username
   * @param discriminant it is a third level of uniqueness to identify the connection
   * @return an Optional<UserConnection> which may be empty if no UserConnection exists, i.e if user
   *     has not authorised a connection to RSpace.
   */
  Optional<UserConnection> findByUserNameProviderName(
      String rspaceUserName, String providerName, String discriminant);

  /**
   * Deletes connection for a user-provider combination, returning number of items deleted
   *
   * @param providername
   * @param rspaceUserName
   */
  int deleteByUserAndProvider(String rspaceUserName, String providername);

  /**
   * Deletes connection for a user-provider combination, returning number of items deleted
   *
   * @param rspaceUserName
   * @param providername
   * @param discriminant
   */
  int deleteByUserAndProvider(String rspaceUserName, String providername, String discriminant);

  /**
   * ** Retrieves the maximum RANK for the specified username and provider name
   *
   * @param rspaceUserName
   * @param providerName
   * @return
   */
  Optional<Integer> findMaxRankByUserNameProviderName(String rspaceUserName, String providerName);

  /**
   * Retrieves a list of UserConnection (is exists) for the specified username and provider name
   *
   * @param providerName
   * @param rspaceUserName it is a third level of uniqueness to identify the connection: i.e.:
   *     serverUrl
   * @return an Optional<UserConnection> which may be empty if no UserConnection exists, i.e if user
   *     has not authorised a connection to RSpace.
   */
  List<UserConnection> findListByUserNameProviderName(String rspaceUserName, String providerName);
}
