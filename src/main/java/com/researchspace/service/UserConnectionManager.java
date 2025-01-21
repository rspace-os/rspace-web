package com.researchspace.service;

import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import java.util.List;
import java.util.Optional;

/** Service to handle storage and retrieval of UserConnection OAuth tokens */
public interface UserConnectionManager extends GenericManager<UserConnection, UserConnectionId> {

  /**
   * Gets the maximum RANK for the specific user and provider
   *
   * @param rspaceUserName
   * @param providerName
   * @return an Optional<Integer> representing the Rank
   */
  Optional<Integer> findMaxRankByUserNameProviderName(String rspaceUserName, String providerName);

  /**
   * Gets a UserConnection if it exists. <br>
   *
   * @param rspaceUserName RSpace username
   * @param providerName e.g. 'egnyte', 'figshare'
   * @return An Optional<UserConnection>
   */
  Optional<UserConnection> findByUserNameProviderName(String rspaceUserName, String providerName);

  /**
   * Gets a list of UserConnections if it exists. <br>
   *
   * @param rspaceUserName RSpace username
   * @param providerName e.g. 'egnyte', 'figshare'
   * @return An Optional<UserConnection>
   */
  List<UserConnection> findListByUserNameProviderName(String rspaceUserName, String providerName);

  Optional<UserConnection> findByUserNameProviderName(
      String rspaceUserName, String providerName, String discriminant);

  /**
   * Deletes connection, returning number of deleted rows
   *
   * @param providername
   * @param rspaceUserName
   */
  int deleteByUserAndProvider(String providername, String rspaceUserName);

  int deleteByUserAndProvider(String providername, String rspaceUserName, String discriminant);
}
