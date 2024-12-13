package com.researchspace.webapp.integrations.dmponline;

import static com.researchspace.service.IntegrationsHandler.DMPONLINE_APP_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller.AccessToken;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.support.BindingAwareModelMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;

public class DMPOnlineControllerTest extends SpringTransactionalTest {

  @Autowired private DMPOnlineController dmpOnlineController;

  @Autowired private UserConnectionManager userConnectionManager;

  @Value("${dmponline.client.id}")
  private String clientId;

  @Value("${dmponline.client.secret}")
  private String clientSecret;

  @Value("${dmponline.callback.base.url}")
  private String callbackBaseUrl;

  private @Autowired IPropertyHolder properties;

  private User testUser;
  private MockPrincipal principal;
  private UserConnectionId userConnectionId;

  @Mock private RestTemplate restTemplate;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    dmpOnlineController.setRestTemplate(restTemplate);

    testUser = createAndSaveUserIfNotExists("testUser");
    initialiseContentWithEmptyContent(testUser);
    assertTrue(testUser.isContentInitialized());

    principal = new MockPrincipal(testUser.getUsername());

    userConnectionId =
        new UserConnectionId(testUser.getUsername(), DMPONLINE_APP_NAME, "ProviderUserIdNotNeeded");
  }

  @After
  public void tearDownConnection() {
    userConnectionManager.deleteByUserAndProvider(DMPONLINE_APP_NAME, testUser.getUsername());
  }

  @Test
  public void testConnect() throws MalformedURLException {
    RedirectView result = dmpOnlineController.connect();

    UserConnection actualConnection = getUserConnection(testUser);
    assertTrue(result.getUrl().contains("dmponline.dmptest.dcc.ac.uk/oauth/authorize"));
    assertNull(actualConnection);
  }

  @Test
  public void testCallback() throws IOException, URISyntaxException {
    dmpOnlineController.connect();
    UserConnection actualConnection = getUserConnection(testUser);
    String clientCode = "CODE_RETURNED_BY_CONNECT";

    Map<String, String> params = Map.of("code", clientCode);

    AccessToken tokenObject = new AccessToken();
    tokenObject.setAccessToken("NEW_ACCESS_TOKEN");
    tokenObject.setRefreshToken("NEW_REFRESH_TOKEN");
    tokenObject.setExpiresIn(299L);

    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/x-www-form-urlencoded");
    headers.add("Accept", "*/*");
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "authorization_code");
    formData.add("code", clientCode);
    formData.add("redirect_uri", getCallbackUrl());
    formData.add("client_id", clientId);
    formData.add("client_secret", clientSecret);
    HttpEntity<MultiValueMap<String, String>> excpectedRequest =
        new HttpEntity<>(formData, headers);

    when(restTemplate.exchange(
            eq("https://dmponline.dmptest.dcc.ac.uk/oauth/token"),
            eq(HttpMethod.POST),
            eq(excpectedRequest),
            ArgumentMatchers.<Class<String>>any()))
        .thenReturn(new ResponseEntity(tokenObject, HttpStatus.OK));

    String result = dmpOnlineController.callback(params, new BindingAwareModelMap(), principal);
    actualConnection = getUserConnection(testUser);

    assertTrue(result.contains("connected"));
    assertEquals("DMPonline access token", actualConnection.getDisplayName());
    assertNotNull(actualConnection.getExpireTime());
    assertEquals("NEW_ACCESS_TOKEN", actualConnection.getAccessToken());
    assertEquals("NEW_REFRESH_TOKEN", actualConnection.getRefreshToken());
  }

  @Test
  public void testRefreshToken() {
    createExistingUserConnection();

    AccessToken refreshedTokenObject = new AccessToken();
    refreshedTokenObject.setAccessToken("NEW_ACCESS_TOKEN");
    refreshedTokenObject.setRefreshToken("NEW_REFRESH_TOKEN");
    refreshedTokenObject.setExpiresIn(299L);

    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/x-www-form-urlencoded");
    headers.add("Accept", "*/*");
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "refresh_token");
    formData.add("refresh_token", "REFRESH_TOKEN");
    formData.add("redirect_uri", getCallbackUrl());
    formData.add("client_id", clientId);
    formData.add("client_secret", clientSecret);
    HttpEntity<MultiValueMap<String, String>> excpectedRequest =
        new HttpEntity<>(formData, headers);

    when(restTemplate.exchange(
            eq("https://dmponline.dmptest.dcc.ac.uk/oauth/token"),
            eq(HttpMethod.POST),
            eq(excpectedRequest),
            ArgumentMatchers.<Class<String>>any()))
        .thenReturn(new ResponseEntity(refreshedTokenObject, HttpStatus.OK));

    String result = dmpOnlineController.refreshToken(new BindingAwareModelMap(), principal);
    UserConnection actualConnection = getUserConnection(testUser);

    assertTrue(result.contains("connected"));
    assertEquals("DMPonline refreshed access token", actualConnection.getDisplayName());
    assertNotNull(actualConnection.getExpireTime());
    assertEquals("NEW_ACCESS_TOKEN", actualConnection.getAccessToken());
    assertEquals("NEW_REFRESH_TOKEN", actualConnection.getRefreshToken());
  }

  @Test
  public void testConnectionIsAlive() {
    createExistingUserConnection();

    AccessToken connectionInfo = new AccessToken();
    connectionInfo.setExpiresIn(299L);

    when(restTemplate.exchange(
            eq("https://dmponline.dmptest.dcc.ac.uk/oauth/token/info"),
            eq(HttpMethod.GET),
            any(),
            ArgumentMatchers.<Class<String>>any()))
        .thenReturn(new ResponseEntity(connectionInfo, HttpStatus.OK));

    Boolean result = dmpOnlineController.isConnectionAlive(principal);
    assertTrue(result);
  }

  @Test
  public void testConnectionIsNotAlive() {
    // given: no user connection exists
    Boolean isConnectionAlive = dmpOnlineController.isConnectionAlive(principal);
    assertFalse(isConnectionAlive);

    // given: exception occurred while calling the end point
    createExistingUserConnection();

    when(restTemplate.exchange(
            eq("https://dmponline.dmptest.dcc.ac.uk/oauth/token/info"),
            eq(HttpMethod.DELETE),
            any(),
            ArgumentMatchers.<Class<String>>any()))
        .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

    isConnectionAlive = dmpOnlineController.isConnectionAlive(principal);
    assertFalse(isConnectionAlive);

    // given: the token expires in less than 120 seconds
    AccessToken connectionInfo = new AccessToken();
    connectionInfo.setExpiresIn(100L);

    when(restTemplate.exchange(
            eq("https://dmponline.dmptest.dcc.ac.uk/oauth/token/info"),
            eq(HttpMethod.GET),
            any(),
            ArgumentMatchers.<Class<String>>any()))
        .thenReturn(new ResponseEntity(connectionInfo, HttpStatus.OK));

    isConnectionAlive = dmpOnlineController.isConnectionAlive(principal);
    assertFalse(isConnectionAlive);
  }

  @Test
  public void testDisconnect() {
    dmpOnlineController.disconnect(principal);

    UserConnection actualConnection = getUserConnection(testUser);
    assertNull(actualConnection);
  }

  @Test
  public void testGetUserConnectionSucceed() {
    createExistingUserConnection();

    UserConnection actualConnection =
        dmpOnlineController.getExistingUserConnection(testUser.getUsername()).orElseGet(null);

    assertNotNull(actualConnection);
    assertEquals("ACCESS_TOKEN", actualConnection.getAccessToken());
    assertEquals("REFRESH_TOKEN", actualConnection.getRefreshToken());
    assertEquals("299", actualConnection.getExpireTime().toString());
    assertEquals("DMPonline access token", actualConnection.getDisplayName());
  }

  @Test
  public void testGetUserConnectionFails() {
    UserConnection actualConnection =
        dmpOnlineController.getExistingUserConnection("wrong_username").orElse(null);
    assertNull(actualConnection);
  }

  private UserConnection createExistingUserConnection() {
    UserConnection actualConnection = new UserConnection();
    actualConnection.setId(userConnectionId);
    actualConnection.setAccessToken("ACCESS_TOKEN");
    actualConnection.setRefreshToken("REFRESH_TOKEN");
    actualConnection.setExpireTime(299L);
    actualConnection.setDisplayName("DMPonline access token");
    userConnectionManager.save(actualConnection);
    return actualConnection;
  }

  private UserConnection getUserConnection(User user) {
    return userConnectionManager
        .findByUserNameProviderName(user.getUsername(), DMPONLINE_APP_NAME)
        .orElse(null);
  }

  @NotNull
  private String getCallbackUrl() {
    String base = "";
    if (StringUtils.isBlank(this.callbackBaseUrl)) {
      base = this.properties.getServerUrl();
    } else {
      base = this.callbackBaseUrl;
    }
    return base + "/apps/dmponline/callback";
  }
}
