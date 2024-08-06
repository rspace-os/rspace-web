package com.researchspace.webapp.integrations.dcd;

import com.researchspace.model.oauth.UserConnection;
import java.util.Optional;

/** API Client wrapper for making calls to DMP API */
public interface DigitalCommonsDataManager {

  /**
   * Attaches a DOI (perhaps obtained from depositing to a repository) to the DMP
   *
   * @param digitalCommonsDataId
   * @param doiIdentifier
   * @param user Assumes this user has performed OAUth flow and can retrieve an accessToken from
   *     UserConnection table
   * @return
   */
  // ServiceOperationResult<String> addDoiIdentifierToDigitalCommonsData(
  // String digitalCommonsDataId, String doiIdentifier, User user);

  Optional<UserConnection> getUserConnection(String username);
}
