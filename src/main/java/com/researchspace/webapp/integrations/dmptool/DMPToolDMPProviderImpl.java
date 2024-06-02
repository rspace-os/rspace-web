package com.researchspace.webapp.integrations.dmptool;

import static com.researchspace.service.IntegrationsHandler.DMPTOOL_APP_NAME;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.dmptool.client.DMPToolClient;
import com.researchspace.dmptool.client.DMPToolClientImpl;
import com.researchspace.dmptool.model.DMPList;
import com.researchspace.dmptool.model.DMPPlanScope;
import com.researchspace.dmptool.model.DMPToolDMP;
import com.researchspace.dmptool.model.RelatedIdentifier;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.UserConnectionManager;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Rest client for DMPTool API */
@Slf4j
public class DMPToolDMPProviderImpl extends AbstractDMPToolDMPProvider
    implements DMPToolDMPProvider {

  private @Autowired UserConnectionManager userConnectionManager;
  private @Autowired AnalyticsManager analyticsManager;

  private final RestTemplate restTemplate;

  private DMPToolClient dmpToolClient;

  public DMPToolDMPProviderImpl(URL baseUrl) {
    super(baseUrl);
    this.restTemplate = new RestTemplate();
    try {
      this.dmpToolClient = new DMPToolClientImpl(new URL(baseUrl, "/api/v2/"));
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Couldn't create DMP baseURL " + e.getMessage());
    }
  }

  @Override
  public ServiceOperationResult<String> addDoiIdentifierToDMP(
      String dmpId, String doiIdentifier, String accessToken) {
    var relatedIdentifier = new RelatedIdentifier("documents", "dataset", "doi", doiIdentifier);
    try {
      this.dmpToolClient.postRelatedIdentifiers(dmpId, relatedIdentifier, accessToken);
    } catch (RestClientException
        | MalformedURLException
        | URISyntaxException
        | JsonProcessingException e) {
      return ServiceOperationResult.fromOptionalError(
          Optional.of("Couldn't update DMP with DOI: " + e.getMessage()));
    }

    return new ServiceOperationResult<>("DMP updated", true);
  }

  @Override
  public ServiceOperationResult<String> addDoiIdentifierToDMP(
      String dmpId, String doiIdentifier, User user) {
    Optional<UserConnection> optConn = getUserConnection(user.getUsername());
    if (!optConn.isPresent()) {
      return ServiceOperationResult.fromOptionalError(Optional.of(noAccessTokenMsg()));
    }
    String accessToken = optConn.get().getAccessToken();
    var result = addDoiIdentifierToDMP(dmpId, doiIdentifier, accessToken);
    analyticsManager.dmpsViewed(user);
    return result;
  }

  byte[] getPdfBytes(DMPToolDMP dmp, String accessToken)
      throws URISyntaxException, MalformedURLException {
    return this.dmpToolClient.getPdfBytes(dmp, accessToken);
  }

  @Override
  public DMPList listPlans(DMPPlanScope scope, String accessToken)
      throws MalformedURLException, URISyntaxException {
    return this.dmpToolClient.listPlans(scope, accessToken);
  }

  @Override
  public ServiceOperationResult<DMPList> listPlans(DMPPlanScope scope, User user)
      throws MalformedURLException, URISyntaxException {
    Optional<UserConnection> optConn = getUserConnection(user.getUsername());
    if (!optConn.isPresent()) {
      return new ServiceOperationResult<>(null, false, noAccessTokenMsg());
    }
    var apiDMPlanList = listPlans(scope, optConn.get().getAccessToken());
    analyticsManager.dmpsViewed(user);
    return new ServiceOperationResult<>(apiDMPlanList, true);
  }

  @Override
  public ServiceOperationResult<DMPToolDMP> getPlanById(String dmpId, User user)
      throws MalformedURLException, URISyntaxException {
    Optional<UserConnection> optConn = getUserConnection(user.getUsername());
    if (!optConn.isPresent()) {
      return noAccessTokenFailure(DMPToolDMP.class);
    }
    var apiDMPlan = getPlanById(dmpId, optConn.get().getAccessToken());
    return new ServiceOperationResult<>(apiDMPlan, true);
  }

  @Override
  public DMPToolDMP getPlanById(String dmpId, String accessToken)
      throws MalformedURLException, URISyntaxException {
    return this.dmpToolClient.getPlanById(dmpId, accessToken);
  }

  private String noAccessTokenMsg() {
    return "Access token isn't enabled - user must connect in Apps page";
  }

  public <T> T doGet(String accessToken, String path, Class<T> response)
      throws URISyntaxException, MalformedURLException {
    HttpEntity<String> request = new HttpEntity<>(makeApiHeaders(accessToken));
    try {
      URI uri = new URL(apiBaseUrl, path).toURI();
      log.info("calling get URL:" + uri.toString());
      ResponseEntity<T> resp = restTemplate.exchange(uri, HttpMethod.GET, request, response);
      return resp.getBody();

    } catch (RestClientException e) {
      log.error(e.getMessage());
      throw e;
    }
  }

  @Override
  public DMPUser doPdfDownload(DMPToolDMP dmp, String title, String accessToken)
      throws URISyntaxException, IOException {
    User user = userManager.getAuthenticatedUserInSession();
    if (!assertIsNewDMP(dmp, user)) {
      return null;
    }
    byte[] pdfBytes = getPdfBytes(dmp, accessToken);
    return saveDMPPdf(dmp, title, user, pdfBytes);
  }

  @Override
  public ServiceOperationResult<DMPUser> doPdfDownload(DMPToolDMP dmp, String title, User user)
      throws URISyntaxException, IOException {
    Optional<UserConnection> optConn = getUserConnection(user.getUsername());
    if (!optConn.isPresent()) {
      return noAccessTokenFailure(DMPUser.class);
    } else {
      DMPUser created = doPdfDownload(dmp, title, optConn.get().getAccessToken());
      if (created != null) {
        return new ServiceOperationResult<>(created, true);
      } else {
        return new ServiceOperationResult<>(
            null,
            false,
            "A DMP with id " + dmp.getId() + ", title: " + dmp.getTitle() + " already exists");
      }
    }
  }

  private <T> ServiceOperationResult<T> noAccessTokenFailure(Class<T> clazz) {
    return new ServiceOperationResult<>(null, false, noAccessTokenMsg());
  }

  private HttpHeaders makeApiHeaders(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.add("Authorization", String.format("Bearer %s", accessToken));
    return headers;
  }

  private Optional<UserConnection> getUserConnection(String username) {
    Optional<UserConnection> optConn =
        userConnectionManager.findByUserNameProviderName(username, DMPTOOL_APP_NAME);
    if (!optConn.isPresent()) {
      log.error("No DMPtool OAuth connection found for user {}", username);
    }
    return optConn;
  }
}
