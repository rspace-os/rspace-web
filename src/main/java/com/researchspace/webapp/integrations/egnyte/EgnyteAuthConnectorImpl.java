package com.researchspace.webapp.integrations.egnyte;

import static com.researchspace.service.IntegrationsHandler.EGNYTE_APP_NAME;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.files.service.ExternalFileStoreProvider;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.PostAnyLoginAction;
import com.researchspace.service.PostFirstLoginAction;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.impl.AbstractPostFirstLoginHelper;
import com.researchspace.session.SessionAttributeUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
public class EgnyteAuthConnectorImpl extends AbstractPostFirstLoginHelper
    implements PostFirstLoginAction, PostAnyLoginAction, EgnyteAuthConnector {

  @Value("${egnyte.internal.app.client.id}")
  private String egnyteInternalAppClientId;

  @Value("${rs.ext.filestore.baseURL}")
  private String egnyteFilestoreBaseUrl;

  private @Autowired UserConnectionManager userConnectionManager;
  private @Autowired IPropertyHolder properties;

  @Override
  public Map<String, Object> queryForEgnyteAccessToken(String egnyteUsername, String egnytePassword)
      throws IOException {
    HttpPost httppost = new HttpPost(egnyteFilestoreBaseUrl + "/puboauth/token");
    List<NameValuePair> params = new ArrayList<>();
    params.add(new BasicNameValuePair("username", egnyteUsername));
    params.add(new BasicNameValuePair("password", egnytePassword));
    params.add(new BasicNameValuePair("client_id", egnyteInternalAppClientId));
    params.add(new BasicNameValuePair("grant_type", "password"));
    httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
    try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
      HttpResponse response = httpclient.execute(httppost);
      StatusLine responseStatus = response.getStatusLine();
      Map<String, Object> responseMap = null;
      if (responseStatus.getStatusCode() == HttpStatus.SC_OK) {
        HttpEntity entity = response.getEntity();
        if (entity != null) {
          ObjectMapper mapper = new ObjectMapper();
          responseMap = mapper.readValue(entity.getContent(), Map.class);
        }
      }
      if (responseMap == null) {
        log.warn(
            "No access token in egnyte response: {} ({})",
            responseStatus.getReasonPhrase(),
            responseStatus.getStatusCode());
      }
      return responseMap;
    }
  }

  @Override
  public Map<String, Object> queryForEgnyteUserInfoWithAccessToken(String token)
      throws IOException {
    HttpGet httpget = new HttpGet(egnyteFilestoreBaseUrl + "/pubapi/v1/userinfo/");
    httpget.setHeader("Authorization", "Bearer " + token);
    try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
      HttpResponse response = httpclient.execute(httpget);
      StatusLine responseStatus = response.getStatusLine();
      Map<String, Object> responseMap = null;
      if (responseStatus.getStatusCode() == HttpStatus.SC_OK) {
        HttpEntity entity = response.getEntity();
        if (entity != null) {
          ObjectMapper mapper = new ObjectMapper();
          responseMap = mapper.readValue(entity.getContent(), Map.class);
        }
      }
      if (responseMap == null) {
        log.warn(
            "Couldn't retrieve user info with current access token {} ({})",
            responseStatus.getReasonPhrase(),
            responseStatus.getStatusCode());
      }
      return responseMap;
    }
  }

  @Override
  public boolean isEgnyteConnectionSetupAndWorking(String username) {

    boolean canConnectFine = false;
    Optional<UserConnection> savedUserConnection =
        userConnectionManager.findByUserNameProviderName(username, EGNYTE_APP_NAME);

    if (savedUserConnection.isPresent()) {
      try {
        Map<String, Object> testEgnyteResponse =
            queryForEgnyteUserInfoWithAccessToken(savedUserConnection.get().getAccessToken());
        canConnectFine = testEgnyteResponse != null && testEgnyteResponse.containsKey("username");
      } catch (IOException e) {
        log.warn("IOException when checking user's Egynte token", e);
      }
    }
    return canConnectFine;
  }

  @Override
  public String onAnyLogin(User user, HttpSession session) {
    return egnyteConnectionSetup(user, session);
  }

  @Override
  protected String doFirstLoginBeforeContentInitialisation(User user, HttpSession session) {
    String rc = egnyteConnectionSetup(user, session);
    setCompleted(user, session, getSessionAttributeName());
    return rc;
  }

  private String egnyteConnectionSetup(User user, HttpSession session) {
    String redirect = null;
    if (ExternalFileStoreProvider.EGNYTE.name().equals(properties.getFileStoreType())) {
      Boolean egnyteConnected =
          (Boolean) session.getAttribute(SessionAttributeUtils.EXT_FILESTORE_CONNECTION_OK);
      // attribute not yet set (i.e. returns null) on first call in the session
      if (egnyteConnected == null) {
        egnyteConnected = isEgnyteConnectionSetupAndWorking(user.getUsername());
        session.setAttribute(SessionAttributeUtils.EXT_FILESTORE_CONNECTION_OK, egnyteConnected);
        if (!egnyteConnected) {
          redirect = "/egnyte/egnyteConnectionSetup";
        }
      }
    }
    return redirect;
  }

  @Override
  protected String getSessionAttributeName() {
    return SessionAttributeUtils.EXT_FILESTORE_CONNECTION_OK;
  }
}
