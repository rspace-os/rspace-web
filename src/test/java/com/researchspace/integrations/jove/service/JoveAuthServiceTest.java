package com.researchspace.integrations.jove.service;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.researchspace.integrations.jove.model.JoveToken;
import com.researchspace.model.User;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.properties.PropertyHolder;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.UserConnectionManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/** This test uses MockRestServiceServer so it mocks responses to jove auth url */
public class JoveAuthServiceTest {

  private static final String API_KEY = "API_KEY";
  private static final String ACCESS_TOKEN = "ACCESS_TOKEN";

  @InjectMocks private JoveAuthService joveAuthService;

  @Mock private UserConnectionManager userConnectionManagerMock;

  @Mock private UserConnection userConnectionMock;

  @Mock private PropertyHolder propertyHolder;

  @Mock private IntegrationsHandler integrationsHandler;

  @Mock private User user;

  private RestTemplate restTemplate;

  private MockRestServiceServer server;

  @Before
  public void setUp() throws Exception {
    openMocks(this);
    restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    joveAuthService.setRestTemplate(restTemplate);
    when(propertyHolder.getJoveApiUrl()).thenReturn("https://richard-dev2.jove.com/api/external");
    when(userConnectionManagerMock.findByUserNameProviderName(anyString(), anyString()))
        .thenReturn(Optional.of(userConnectionMock));
    when(userConnectionMock.getAccessToken()).thenReturn(API_KEY);
    when(user.getUsername()).thenReturn("userName");
  }

  @After
  public void tearDown() {
    joveAuthService.setTokens(new HashMap<>());
  }

  @Test
  public void retrieveNewTokenTest() throws Exception {
    server
        .expect(requestTo(containsString("auth.php")))
        .andExpect(content().formData(getFormData()))
        .andRespond(
            withSuccess(
                "{\"token\":\""
                    + ACCESS_TOKEN
                    + "\""
                    + ", \"expires\":"
                    + getNewTimeStampPlusEightHours()
                    + "}",
                MediaType.APPLICATION_JSON));
    JoveToken token = joveAuthService.getTokenAndRefreshIfExpired(user);
    assertNotNull(token);
  }

  @Test
  public void invalidApiKeyTest() {
    server
        .expect(requestTo(containsString("auth.php")))
        .andExpect(content().formData(getFormData()))
        .andRespond(
            withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(getForbiddenResponse()));
    assertThrows(JoveTokenException.class, () -> joveAuthService.getTokenAndRefreshIfExpired(user));
  }

  @Test
  public void expiredTokenTest() {
    server
        .expect(requestTo(containsString("auth.php")))
        .andExpect(content().formData(getFormData()))
        .andRespond(
            withSuccess(
                "{\"token\":\""
                    + ACCESS_TOKEN
                    + RandomStringUtils.randomAlphabetic(5)
                    + "\""
                    + ", \"expires\":"
                    + getNewTimeStampPlusEightHours()
                    + "}",
                MediaType.APPLICATION_JSON));
    JoveToken expiredToken =
        new JoveToken(ACCESS_TOKEN + RandomStringUtils.randomAlphabetic(5), getExpiredTimestamp());
    Map<UserConnection, JoveToken> testTokens = new HashMap<>();
    testTokens.put(userConnectionMock, expiredToken);
    joveAuthService.setTokens(testTokens); // Set token to once that has expired
    joveAuthService.getTokenAndRefreshIfExpired(user);
    assertNotEquals(expiredToken, joveAuthService.getTokens().get(userConnectionMock));
  }

  @Test
  public void validTokenNotReplacedTest() {
    server
        .expect(requestTo(containsString("auth.php")))
        .andExpect(content().formData(getFormData()))
        .andRespond(
            withSuccess(
                "{\"token\":\""
                    + ACCESS_TOKEN
                    + RandomStringUtils.randomAlphabetic(5)
                    + "\""
                    + ", \"expires\":"
                    + getNewTimeStampPlusEightHours()
                    + "}",
                MediaType.APPLICATION_JSON));
    JoveToken validToken =
        new JoveToken(
            ACCESS_TOKEN + RandomStringUtils.randomAlphabetic(5), getNewTimeStampPlusEightHours());
    Map<UserConnection, JoveToken> testTokens = new HashMap<>();
    testTokens.put(userConnectionMock, validToken);
    joveAuthService.setTokens(testTokens);
    joveAuthService.getTokenAndRefreshIfExpired(user);
    assertEquals(validToken, joveAuthService.getTokens().get(userConnectionMock));
  }

  @Test
  public void testJoveDefaultEnabled() {
    server
        .expect(requestTo(containsString("auth.php")))
        .andExpect(content().formData(getFormData()))
        .andRespond(
            withSuccess(
                "{\"token\":\""
                    + ACCESS_TOKEN
                    + "\""
                    + ", \"expires\":"
                    + getNewTimeStampPlusEightHours()
                    + "}",
                MediaType.APPLICATION_JSON));
    when(userConnectionManagerMock.findByUserNameProviderName(anyString(), anyString()))
        .thenReturn(Optional.empty(), Optional.of(userConnectionMock));
    when(integrationsHandler.getIntegration(eq(user), eq(IntegrationsHandler.JOVE_APP_NAME)))
        .thenReturn(new IntegrationInfo());
    when(integrationsHandler.updateIntegrationInfo(eq(user), any(IntegrationInfo.class)))
        .thenReturn(new IntegrationInfo());
    JoveToken token = joveAuthService.getTokenAndRefreshIfExpired(user);
    assertNotNull(token);
    assertFalse(joveAuthService.getTokens().isEmpty());
  }

  @Test
  public void testJoveDefaultEnabledErrorAccessingUserConnection() {
    server
        .expect(requestTo(containsString("auth.php")))
        .andExpect(content().formData(getFormData()))
        .andRespond(
            withSuccess(
                "{\"token\":\""
                    + ACCESS_TOKEN
                    + "\""
                    + ", \"expires\":"
                    + getNewTimeStampPlusEightHours()
                    + "}",
                MediaType.APPLICATION_JSON));
    when(userConnectionManagerMock.findByUserNameProviderName(anyString(), anyString()))
        .thenReturn(Optional.empty(), Optional.empty());
    when(integrationsHandler.getIntegration(eq(user), eq(IntegrationsHandler.JOVE_APP_NAME)))
        .thenReturn(new IntegrationInfo());
    when(integrationsHandler.updateIntegrationInfo(eq(user), any(IntegrationInfo.class)))
        .thenReturn(new IntegrationInfo());
    assertThrows(JoveTokenException.class, () -> joveAuthService.getTokenAndRefreshIfExpired(user));
  }

  private MultiValueMap<String, String> getFormData() {
    MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
    map.add("key", API_KEY);
    return map;
  }

  private long getNewTimeStampPlusEightHours() {
    return (System.currentTimeMillis() + TimeUnit.HOURS.toMillis(8)) / 1000L;
  }

  private long getExpiredTimestamp() {
    return (System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)) / 1000L;
  }

  private String getForbiddenResponse() {
    return "\"type\": \"danger\","
        + "\"title\": \"Auth Failed\","
        + "\"message\": \"Invalid API Key provided\","
        + "\"providedkey\": \"\"";
  }
}
