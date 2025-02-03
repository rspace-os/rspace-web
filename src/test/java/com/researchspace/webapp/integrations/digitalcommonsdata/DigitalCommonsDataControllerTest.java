package com.researchspace.webapp.integrations.digitalcommonsdata;

import static com.researchspace.service.IntegrationsHandler.DIGITAL_COMMONS_DATA_APP_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.researchspace.dcd.model.DcdAccessToken;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
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

public class DigitalCommonsDataControllerTest extends SpringTransactionalTest {

  @Autowired private DigitalCommonsDataController digitalCommonsDataController;

  @Autowired private UserConnectionManager userConnectionManager;

  @Value("${dcd.client.id}")
  private String clientId;

  @Value("${dcd.client.secret}")
  private String clientSecret;

  @Value("${dcd.callback.base.url}")
  private String callbackBaseUrl;

  private @Autowired IPropertyHolder properties;

  private User testUser;
  private MockPrincipal principal;
  private UserConnectionId userConnectionId;

  @Mock private RestTemplate restTemplate;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    digitalCommonsDataController.setRestTemplate(restTemplate);

    testUser = createAndSaveUserIfNotExists("testUser");
    initialiseContentWithEmptyContent(testUser);
    assertTrue(testUser.isContentInitialized());

    principal = new MockPrincipal(testUser.getUsername());

    userConnectionId =
        new UserConnectionId(
            testUser.getUsername(), DIGITAL_COMMONS_DATA_APP_NAME, "ProviderUserIdNotNeeded");
  }

  @After
  public void tearDownConnection() {
    userConnectionManager.deleteByUserAndProvider(
        DIGITAL_COMMONS_DATA_APP_NAME, testUser.getUsername());
  }

  @Test
  public void testConnect() throws MalformedURLException {
    RedirectView result =
        digitalCommonsDataController.connect(new BindingAwareModelMap(), principal);

    UserConnection actualConnection = getUserConnection(testUser);
    assertTrue(result.getUrl().contains("data.mendeley.com"));
    assertEquals("DigitalCommonsData Client Access Secret", actualConnection.getDisplayName());
    assertNotNull(actualConnection.getExpireTime());
    assertFalse(actualConnection.getAccessToken().contains("ACCESS_TOKEN"));
    assertTrue(actualConnection.getAccessToken().contains("TEMP"));
    assertNotNull(actualConnection.getSecret());
    assertFalse(actualConnection.getSecret().isBlank());
  }

  @Test
  public void testCallback() throws IOException, URISyntaxException {
    digitalCommonsDataController.connect(new BindingAwareModelMap(), principal);
    UserConnection actualConnection = getUserConnection(testUser);
    String clientCode = "CODE_RETURNED_BY_CONNECT";

    Map<String, String> params = Map.of("state", actualConnection.getSecret(), "code", clientCode);

    DcdAccessToken tokenObject = new DcdAccessToken();
    tokenObject.setAccessToken("NEW_ACCESS_TOKEN");
    tokenObject.setRefreshToken("NEW_REFRESH_TOKEN");
    tokenObject.setExpiresIn(299L);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    headers.setBasicAuth(clientId, clientSecret);
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "authorization_code");
    formData.add("code", clientCode);
    formData.add("redirect_uri", getCallbackUrl());
    HttpEntity<MultiValueMap<String, String>> excpectedRequest =
        new HttpEntity<>(formData, headers);

    when(restTemplate.exchange(
            eq("https://auth.data.mendeley.com/oauth2/token"),
            eq(HttpMethod.POST),
            eq(excpectedRequest),
            ArgumentMatchers.<Class<String>>any()))
        .thenReturn(new ResponseEntity(tokenObject, HttpStatus.OK));

    String result =
        digitalCommonsDataController.callback(params, new BindingAwareModelMap(), principal);
    actualConnection = getUserConnection(testUser);

    assertTrue(result.contains("connected"));
    assertEquals("DigitalCommonsData access token", actualConnection.getDisplayName());
    assertNotNull(actualConnection.getExpireTime());
    assertEquals("NEW_ACCESS_TOKEN", actualConnection.getAccessToken());
    assertEquals("NEW_REFRESH_TOKEN", actualConnection.getRefreshToken());
  }

  @Test
  public void testRefreshToken() {
    createExistingUserConnection();

    DcdAccessToken refreshedTokenObject = new DcdAccessToken();
    refreshedTokenObject.setAccessToken("NEW_ACCESS_TOKEN");
    refreshedTokenObject.setRefreshToken("NEW_REFRESH_TOKEN");
    refreshedTokenObject.setExpiresIn(299L);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    headers.setBasicAuth(clientId, clientSecret);
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "refresh_token");
    formData.add("refresh_token", "REFRESH_TOKEN");
    formData.add("redirect_uri", getCallbackUrl());
    HttpEntity<MultiValueMap<String, String>> excpectedRequest =
        new HttpEntity<>(formData, headers);

    when(restTemplate.exchange(
            eq("https://auth.data.mendeley.com/oauth2/token"),
            eq(HttpMethod.POST),
            eq(excpectedRequest),
            ArgumentMatchers.<Class<String>>any()))
        .thenReturn(new ResponseEntity(refreshedTokenObject, HttpStatus.OK));

    String result =
        digitalCommonsDataController.refreshToken(new BindingAwareModelMap(), principal);
    UserConnection actualConnection = getUserConnection(testUser);

    assertTrue(result.contains("connected"));
    assertEquals("DigitalCommonsData refreshed access token", actualConnection.getDisplayName());
    assertNotNull(actualConnection.getExpireTime());
    assertEquals("NEW_ACCESS_TOKEN", actualConnection.getAccessToken());
    assertEquals("NEW_REFRESH_TOKEN", actualConnection.getRefreshToken());
  }

  @Test
  public void testConnectionIsAlive() {
    createExistingUserConnection();

    when(restTemplate.exchange(
            eq("https://api.data.mendeley.com/active-data-entities/datasets/drafts/FAKE_ID"),
            eq(HttpMethod.DELETE),
            any(),
            ArgumentMatchers.<Class<String>>any()))
        .thenReturn(new ResponseEntity(HttpStatus.OK));

    Boolean result = digitalCommonsDataController.isConnectionAlive(principal);
    assertTrue(result);

    when(restTemplate.exchange(
            eq("https://api.data.mendeley.com/active-data-entities/datasets/drafts/FAKE_ID"),
            eq(HttpMethod.DELETE),
            any(),
            ArgumentMatchers.<Class<String>>any()))
        .thenThrow(
            new HttpClientErrorException(
                HttpStatus.NOT_FOUND,
                "Not Found: \"{\"message\":\"Draft dataset 'FAKE_ID' not found\"}\""));

    result = digitalCommonsDataController.isConnectionAlive(principal);
    assertTrue(result);
  }

  @Test
  public void testConnectionIsNotAlive() {
    // given: no user connection exists

    when(restTemplate.exchange(
            eq("https://api.data.mendeley.com/active-data-entities/datasets/drafts/FAKE_ID"),
            eq(HttpMethod.DELETE),
            any(),
            ArgumentMatchers.<Class<String>>any()))
        .thenReturn(new ResponseEntity(HttpStatus.OK));

    Boolean result = digitalCommonsDataController.isConnectionAlive(principal);
    assertFalse(result);

    // given: user connection exists
    createExistingUserConnection();

    when(restTemplate.exchange(
            eq("https://api.data.mendeley.com/active-data-entities/datasets/drafts/FAKE_ID"),
            eq(HttpMethod.DELETE),
            any(),
            ArgumentMatchers.<Class<String>>any()))
        .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

    result = digitalCommonsDataController.isConnectionAlive(principal);
    assertFalse(result);
  }

  @Test
  public void testDisconnect() {
    digitalCommonsDataController.disconnect(principal);

    UserConnection actualConnection = getUserConnection(testUser);
    assertNull(actualConnection);
  }

  @Test
  public void testGetUserConnectionSucceed() {
    createExistingUserConnection();

    UserConnection actualConnection =
        digitalCommonsDataController.getUserConnection(testUser.getUsername()).orElseGet(null);

    assertNotNull(actualConnection);
    assertEquals("ACCESS_TOKEN", actualConnection.getAccessToken());
    assertEquals("REFRESH_TOKEN", actualConnection.getRefreshToken());
    assertEquals("299", actualConnection.getExpireTime().toString());
    assertEquals("DigitalCommonsData access token", actualConnection.getDisplayName());
  }

  @Test
  public void testGetUserConnectionFails() {
    UserConnection actualConnection =
        digitalCommonsDataController.getUserConnection("wrong_username").orElse(null);
    assertNull(actualConnection);
  }

  private UserConnection createExistingUserConnection() {
    UserConnection actualConnection = new UserConnection();
    actualConnection.setId(userConnectionId);
    actualConnection.setAccessToken("ACCESS_TOKEN");
    actualConnection.setRefreshToken("REFRESH_TOKEN");
    actualConnection.setExpireTime(299L);
    actualConnection.setDisplayName("DigitalCommonsData access token");
    userConnectionManager.save(actualConnection);
    return actualConnection;
  }

  private UserConnection getUserConnection(User user) {
    return userConnectionManager
        .findByUserNameProviderName(user.getUsername(), DIGITAL_COMMONS_DATA_APP_NAME)
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
    return base + "/apps/digitalcommonsdata/callback";
  }
}
