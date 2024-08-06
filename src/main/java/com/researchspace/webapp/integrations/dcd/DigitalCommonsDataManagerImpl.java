package com.researchspace.webapp.integrations.dcd;

import static com.researchspace.service.IntegrationsHandler.DIGITAL_COMMONS_DATA_APP_NAME;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.dcd.client.DigitalCommonsDataClient;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.UserConnectionManager;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
/** API Client wrapper for making calls to DCD API */
public class DigitalCommonsDataManagerImpl implements DigitalCommonsDataManager {

  private @Autowired UserConnectionManager userConnectionManager;
  private @Autowired AnalyticsManager analyticsManager;

  private DigitalCommonsDataClient digitalCommonsDataClient;

  // public ServiceOperationResult<String> addDoiIdentifierToDigitalCommonsData(
  // String dmpId, String doiIdentifier, String accessToken) {
  // DcdRelatedIdentifier relatedIdentifier =
  // new DcdRelatedIdentifier("documents", "dataset", "doi", doiIdentifier);
  // try {
  // this.digitalCommonsDataClient.postRelatedIdentifiers(dmpId, relatedIdentifier, accessToken);
  // } catch (RestClientException
  // | MalformedURLException
  // | URISyntaxException
  // | JsonProcessingException e) {
  // return ServiceOperationResult.fromOptionalError(
  // Optional.of("Couldn't update DMP with DOI: " + e.getMessage()));
  // }

  // return new ServiceOperationResult<>("DMP updated", true);
  // }

  // @Override
  // public ServiceOperationResult<String> addDoiIdentifierToDigitalCommonsData(
  // String digitalCommonsDataId, String doiIdentifier, User user) {
  // Optional<UserConnection> optConn = getUserConnection(user.getUsername());
  // if (!optConn.isPresent()) {
  // return ServiceOperationResult.fromOptionalError(Optional.of(noAccessTokenMsg()));
  // }
  // String accessToken = optConn.get().getAccessToken();
  // var result =
  // addDoiIdentifierToDigitalCommonsData(digitalCommonsDataId, doiIdentifier, accessToken);
  // analyticsManager.dmpsViewed(user);
  // return result;
  // }

  public Optional<UserConnection> getUserConnection(String username) {
    Optional<UserConnection> optConn =
        userConnectionManager.findByUserNameProviderName(username, DIGITAL_COMMONS_DATA_APP_NAME);
    if (!optConn.isPresent()) {
      log.error("No Digital Commons Data connection found for user {}", username);
    }
    return optConn;
  }

  private String noAccessTokenMsg() {
    return "Access token isn't enabled - user must connect in Apps page";
  }
}
