package com.researchspace.webapp.integrations.clustermarket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserConnectionManager;
import java.security.Principal;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

public class ClustermarketOAuthServiceTest {
  private static final String REFRESH_TOKEN = "EXISTING_TOKEN";
  private static final String EXISTING_TOKEN = "EXISTING_TOKEN";
  private static final String NEW_TOKEN = "NEW_TOKEN";
  public static final String USER_NAME = "user_name";
  public static final String CLIENT_ID = "clientID";
  public static final String CLIENT_SECRET = "clientSecret";
  private static final String SERVER_URL = "https://rspace_server_url";
  public static final String AUTH_CODE = "AUTH_CODE";
  public static final long EXPIRE_TIME = 1000L;
  @InjectMocks private ClustermarketOAuthService clustermarketOAuthService;
  @Mock private UserConnectionManager userConnectionManagerMock;
  @Mock private UserConnection userConnectionMock;
  @Mock private IPropertyHolder propertyHolderMock;
  @Mock private RestTemplate restTemplateMock;
  @Mock private Principal principalMock;
  @Mock private ResponseEntity<ClustermarketOAuthService.AccessToken> newTokenResponseMock;
  @Mock private ClustermarketOAuthService.AccessToken accessTokenMock;
  @Mock private User mockUser;

  @Before
  public void setUp() {
    openMocks(this);
    ReflectionTestUtils.setField(clustermarketOAuthService, "restTemplate", restTemplateMock);
    ReflectionTestUtils.setField(
        clustermarketOAuthService, "clustermarketWebUrl", "https://staging.clustermarket.com/");
    ReflectionTestUtils.setField(clustermarketOAuthService, "clientId", CLIENT_ID);
    ReflectionTestUtils.setField(clustermarketOAuthService, "clientSecret", CLIENT_SECRET);
    when(propertyHolderMock.getServerUrl()).thenReturn(SERVER_URL);
    when(principalMock.getName()).thenReturn(USER_NAME);
    when(userConnectionManagerMock.findByUserNameProviderName(eq(USER_NAME), eq("CLUSTERMARKET")))
        .thenReturn(Optional.of(userConnectionMock));
    when(userConnectionMock.getExpireTime()).thenReturn(Instant.now().toEpochMilli() + 100000);
    when(userConnectionMock.getAccessToken()).thenReturn(EXISTING_TOKEN);
    when(userConnectionMock.getRefreshToken()).thenReturn(REFRESH_TOKEN);
  }

  @Test
  public void shouldThrowNoTokenExceptionWhenNoExistingTest() throws Exception {
    when(principalMock.getName()).thenReturn("this name will have no token");
    CoreTestUtils.assertExceptionThrown(
        () -> clustermarketOAuthService.getExistingAccessTokenAndRefreshIfExpired(principalMock),
        NoTokenException.class);
  }

  @Test
  public void shouldSaveNewAuthCodeTokenTest() {
    ArgumentCaptor<UserConnection> captor = ArgumentCaptor.forClass(UserConnection.class);
    HttpEntity<Map<String, String>> accessTokenRequestEntity =
        getHttpEntityForAuthCodeToken(AUTH_CODE);
    setupGetTokenExpectations(accessTokenRequestEntity);
    clustermarketOAuthService.generateAndSaveAuthCodeAccessToken(AUTH_CODE, mockUser);
    verify(userConnectionManagerMock).save(captor.capture());
    UserConnection saved = captor.getValue();
    assertEquals(NEW_TOKEN, saved.getAccessToken());
    assertEquals(REFRESH_TOKEN, saved.getRefreshToken());
    assertEquals("RSpace Clustermarket access token", saved.getDisplayName());
    assertEquals(1, saved.getRank());
    assertTrue(saved.getExpireTime() > EXPIRE_TIME);
  }

  @Test
  public void shouldGetExistingTokenTest() {
    String token =
        clustermarketOAuthService.getExistingAccessTokenAndRefreshIfExpired(principalMock);
    assertEquals(EXISTING_TOKEN, token);
  }

  @Test
  public void shouldRefreshTokenIfExistingTokenExpiredTest() {
    HttpEntity<Map<String, String>> accessTokenRequestEntity = getHttpEntityForRefreshToken();
    setupGetTokenExpectations(accessTokenRequestEntity);
    String token =
        clustermarketOAuthService.getExistingAccessTokenAndRefreshIfExpired(principalMock);
    assertEquals(NEW_TOKEN, token);
  }

  @Test
  public void shouldSaveRefreshTokenIfExistingTokenExpiredTest() {
    HttpEntity<Map<String, String>> accessTokenRequestEntity = getHttpEntityForRefreshToken();
    setupGetTokenExpectations(accessTokenRequestEntity);
    clustermarketOAuthService.getExistingAccessTokenAndRefreshIfExpired(principalMock);
    verify(userConnectionManagerMock).save(eq(userConnectionMock));
    verify(userConnectionMock).setAccessToken(eq(NEW_TOKEN));
    verify(userConnectionMock).setRefreshToken(eq(REFRESH_TOKEN));
    verify(userConnectionMock).setExpireTime(any(Long.class));
  }

  private void setupGetTokenExpectations(HttpEntity<Map<String, String>> accessTokenRequestEntity) {
    when(userConnectionMock.getExpireTime()).thenReturn(Instant.now().toEpochMilli() + 59000);
    when(restTemplateMock.exchange(
            eq("https://staging.clustermarket.com/oauth/token"),
            eq(HttpMethod.POST),
            eq(accessTokenRequestEntity),
            eq(ClustermarketOAuthService.AccessToken.class)))
        .thenReturn(newTokenResponseMock);
    when(newTokenResponseMock.getBody()).thenReturn(accessTokenMock);
    when(newTokenResponseMock.hasBody()).thenReturn(true);
    when(accessTokenMock.getAccessToken()).thenReturn(NEW_TOKEN);
    when(accessTokenMock.getRefreshToken()).thenReturn(REFRESH_TOKEN);
    when(accessTokenMock.getExpiresIn()).thenReturn(EXPIRE_TIME);
  }

  private HttpEntity<Map<String, String>> getHttpEntityForRefreshToken() {
    Map<String, String> kv = new HashMap<>();
    kv.put("refresh_token", REFRESH_TOKEN);
    kv.put("grant_type", "refresh_token");
    kv.put("client_id", CLIENT_ID);
    kv.put("client_secret", CLIENT_SECRET);
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
    HttpEntity<Map<String, String>> accessTokenRequestEntity = new HttpEntity<>(kv, headers);
    return accessTokenRequestEntity;
  }

  private HttpEntity<Map<String, String>> getHttpEntityForAuthCodeToken(String authorizationCode) {
    Map<String, String> kv = new HashMap<>();
    kv.put("code", authorizationCode);
    kv.put("grant_type", "authorization_code");
    kv.put("client_id", CLIENT_ID);
    kv.put("client_secret", CLIENT_SECRET);
    String redirectUri = SERVER_URL + "/apps/clustermarket/redirect_uri";
    kv.put("redirect_uri", redirectUri);
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
    HttpEntity<Map<String, String>> accessTokenRequestEntity = new HttpEntity<>(kv, headers);
    return accessTokenRequestEntity;
  }
}
