package com.researchspace.service.raid.impl;

import static com.researchspace.service.IntegrationsHandler.RAID_APP_NAME;
import static java.net.URLEncoder.encode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.raid.client.RaIDClient;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller.AccessToken;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties = {
      "raid.server.config={ \"DEMO\": { \"url\": \"https://demo.raid.au\", "
          + " \"authUrl\": \"https://demo.raid.org/realms/raid/protocol/openid-connect\", "
          + " \"servicePointId\": 12345678, \"clientId\": \"rspace\", "
          + " \"clientSecret\": \"secretfgubdfigu\" }}"
    })
public class RaIDServiceClientAdapterTest extends SpringTransactionalTest {

  private static final String CLIENT_SECRET = "secretfgubdfigu";
  private static final String AUTH_CODE = "authCodelohisgobg";
  private static final String CLIENT_ID = "rspace";
  private static final String AUTH_BASE_URL =
      "https://demo.raid.org/realms/raid/protocol/openid-connect";
  private static final String SERVER_ALIAS = "DEMO";

  @Autowired private IPropertyHolder properties;
  @Autowired private UserConnectionManager userConnectionManager;
  @Autowired private RaIDServiceClientAdapterImpl raidServiceClientAdapter;
  @Mock private RaIDClient mockedRaidClient;

  private User user;
  private String jsonAccessToken;
  private AccessToken expectedAccessToken;
  private String jsonRefreshToken;
  private AccessToken expectedRefreshToken;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    user = createAndSaveUserIfNotExists("testUser");
    raidServiceClientAdapter.setRaidClient(mockedRaidClient);
    ObjectMapper mapper = new ObjectMapper();
    jsonAccessToken =
        IOUtils.resourceToString(
            "/TestResources/raid/json/access-token-response.json", Charset.defaultCharset());
    expectedAccessToken = mapper.readValue(jsonAccessToken, AccessToken.class);
    jsonRefreshToken =
        IOUtils.resourceToString(
            "/TestResources/raid/json/refresh-token-response.json", Charset.defaultCharset());
    expectedRefreshToken = mapper.readValue(jsonRefreshToken, AccessToken.class);
  }

  @Test
  public void testPerformRedirectConnect() throws Exception {
    when(mockedRaidClient.getRedirectUriToConnect(
            eq(AUTH_BASE_URL), eq(CLIENT_ID), eq(getExpectedCallbackUrl()), eq(SERVER_ALIAS)))
        .thenReturn(
            "https://demo.raid.org/realms/raid/protocol/openid-connect/auth?client_id=rspace&"
                + "redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapps%2Fraid%2Fcallback&"
                + "response_type=code&scope=openid&state=DEMO");

    String actualRedirectedUrl = raidServiceClientAdapter.performRedirectConnect(SERVER_ALIAS);

    assertTrue(
        actualRedirectedUrl.contains(
            "https://demo.raid.org/realms/raid/protocol/openid-connect/auth?"));
    assertTrue(actualRedirectedUrl.contains("client_id=rspace"));
    assertTrue(
        actualRedirectedUrl.contains(
            "redirect_uri=" + encode(getExpectedCallbackUrl(), StandardCharsets.UTF_8)));
    assertTrue(actualRedirectedUrl.contains("response_type=code"));
    assertTrue(actualRedirectedUrl.contains("scope=openid"));
    assertTrue(actualRedirectedUrl.contains("state=" + SERVER_ALIAS));

    Optional<UserConnection> userConnection =
        userConnectionManager.findByUserNameProviderName(user.getUsername(), RAID_APP_NAME);
    assertTrue(userConnection.isEmpty());
  }

  @Test
  public void testPerformCreateAccessToken() throws Exception {
    // GIVEN
    when(mockedRaidClient.getAccessToken(
            eq(AUTH_BASE_URL),
            eq(CLIENT_ID),
            eq(CLIENT_SECRET),
            eq(AUTH_CODE),
            eq(getExpectedCallbackUrl())))
        .thenReturn(jsonAccessToken);

    // WHEN
    AccessToken actualAccessToken =
        raidServiceClientAdapter.performCreateAccessToken(
            user.getUsername(), SERVER_ALIAS, AUTH_CODE);

    // THEN
    assertEquals(expectedAccessToken, actualAccessToken);
    Optional<UserConnection> userConnection =
        userConnectionManager.findByUserNameProviderName(
            user.getUsername(), RAID_APP_NAME, SERVER_ALIAS);
    assertTrue(userConnection.isPresent());
    assertEquals(expectedAccessToken.getAccessToken(), userConnection.get().getAccessToken());
    assertEquals(expectedAccessToken.getRefreshToken(), userConnection.get().getRefreshToken());
  }

  @Test
  public void testPerformRefreshToken() throws Exception {
    // GIVEN
    when(mockedRaidClient.getAccessToken(
            eq(AUTH_BASE_URL),
            eq(CLIENT_ID),
            eq(CLIENT_SECRET),
            eq(AUTH_CODE),
            eq(getExpectedCallbackUrl())))
        .thenReturn(jsonAccessToken);
    when(mockedRaidClient.getRefreshToken(
            eq(AUTH_BASE_URL),
            eq(CLIENT_ID),
            eq(CLIENT_SECRET),
            eq(expectedAccessToken.getRefreshToken()),
            eq(getExpectedCallbackUrl())))
        .thenReturn(jsonRefreshToken);
    raidServiceClientAdapter.performCreateAccessToken(user.getUsername(), SERVER_ALIAS, AUTH_CODE);

    // WHEN
    AccessToken actualRefreshToken =
        raidServiceClientAdapter.performRefreshToken(user.getUsername(), SERVER_ALIAS);

    // THEN
    assertEquals(expectedRefreshToken, actualRefreshToken);
    Optional<UserConnection> userConnection =
        userConnectionManager.findByUserNameProviderName(
            user.getUsername(), RAID_APP_NAME, SERVER_ALIAS);
    assertTrue(userConnection.isPresent());
    assertEquals(expectedRefreshToken.getAccessToken(), userConnection.get().getAccessToken());
    assertEquals(expectedRefreshToken.getRefreshToken(), userConnection.get().getRefreshToken());
  }

  @Test
  public void testRaidConnectIsAlive() throws Exception {
    assertFalse(raidServiceClientAdapter.isRaidConnectionAlive(user.getUsername(), SERVER_ALIAS));

    // GIVEN
    when(mockedRaidClient.getAccessToken(
            eq(AUTH_BASE_URL),
            eq(CLIENT_ID),
            eq(CLIENT_SECRET),
            eq(AUTH_CODE),
            eq(getExpectedCallbackUrl())))
        .thenReturn(jsonAccessToken);
    when(mockedRaidClient.getRefreshToken(
            eq(AUTH_BASE_URL),
            eq(CLIENT_ID),
            eq(CLIENT_SECRET),
            eq(expectedAccessToken.getRefreshToken()),
            eq(getExpectedCallbackUrl())))
        .thenReturn(jsonRefreshToken);
    raidServiceClientAdapter.performCreateAccessToken(user.getUsername(), SERVER_ALIAS, AUTH_CODE);

    // WHEN / THEN
    assertTrue(raidServiceClientAdapter.isRaidConnectionAlive(user.getUsername(), SERVER_ALIAS));
  }

  private String getExpectedCallbackUrl() {
    return this.properties.getServerUrl() + "/apps/raid/callback";
  }
}
