package com.researchspace.service.raid.impl;

import static com.researchspace.service.IntegrationsHandler.RAID_APP_NAME;
import static java.net.URLEncoder.encode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.raid.client.RaIDClient;
import com.researchspace.raid.model.RaID;
import com.researchspace.raid.model.RaIDServicePoint;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.raid.RaIDServiceClientAdapter;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.integrations.helper.BaseOAuth2Controller.AccessToken;
import com.researchspace.webapp.integrations.raid.RaIDReferenceDTO;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpServerErrorException;

@TestPropertySource(
    properties = {
      "server.urls.prefix=http://localhost:8080",
      "raid.server.config={ \"DEMO\": { \"url\": \"https://demo.raid.au\", "
          + " \"authUrl\": \"https://demo.raid.org/realms/raid/protocol/openid-connect\", "
          + " \"servicePointId\": 12345678, \"clientId\": \"rspace\", "
          + " \"clientSecret\": \"secretfgubdfigu\" }}"
    })
public class RaIDServiceClientAdapterTest extends SpringTransactionalTest {

  private static final String CLIENT_SECRET = "secretfgubdfigu";
  private static final String AUTH_CODE = "authCodelohisgobg";
  private static final String CLIENT_ID = "rspace";
  private static final int SERVICE_POINT_ID = 12345678;
  private static final String AUTH_BASE_URL =
      "https://demo.raid.org/realms/raid/protocol/openid-connect";
  private static final String API_BASE_URL = "https://demo.raid.au";
  private static final String SERVER_ALIAS = "DEMO";
  private static final String OLD_ACCESS_TOKEN = "ACCESS_TOKEN";
  private static final String NEW_ACCESS_TOKEN = "NEW_ACCESS_TOKEN";
  private static final String RAID_PREFIX = "10.83334";
  private static final String RAID_SUFFIX = "c74980b1";
  private static final String DOI_PREFIX = "10.12345";
  private static final String DOI_SUFFIX = "5UFF1X";
  private static final String DOI_LINK = "https://doi.org/" + DOI_PREFIX + "/" + DOI_SUFFIX;

  @Autowired private IPropertyHolder properties;
  @Autowired private UserConnectionManager userConnectionManager;
  @Autowired private RaIDServiceClientAdapter raidServiceClientAdapter;
  @Mock private RaIDClient mockedRaidClient;

  private User user;
  private String jsonAccessToken;
  private AccessToken expectedAccessToken;
  private String jsonRefreshToken;
  private AccessToken expectedRefreshToken;
  private RaID expectedRaid;
  private RaID expectedRaidWithRelatedObject;
  private List<RaID> expectedRaidList;
  private RaIDServicePoint expectedServicePoint;
  private List<RaIDServicePoint> expectedServicePointList;

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

    expectedRaid =
        mapper.readValue(
            IOUtils.resourceToString(
                "/TestResources/raid/json/raid-test-1.json", Charset.defaultCharset()),
            RaID.class);

    expectedRaidWithRelatedObject =
        mapper.readValue(
            IOUtils.resourceToString(
                "/TestResources/raid/json/raid-test-1-with-related-object.json",
                Charset.defaultCharset()),
            RaID.class);

    expectedRaidList =
        Arrays.asList(
            mapper.readValue(
                IOUtils.resourceToString(
                    "/TestResources/raid/json/raid-list.json", Charset.defaultCharset()),
                RaID[].class));

    expectedServicePoint =
        mapper.readValue(
            IOUtils.resourceToString(
                "/TestResources/raid/json/service-point-rspace.json", Charset.defaultCharset()),
            RaIDServicePoint.class);

    expectedServicePointList =
        Arrays.asList(
            mapper.readValue(
                IOUtils.resourceToString(
                    "/TestResources/raid/json/service-point-list.json", Charset.defaultCharset()),
                RaIDServicePoint[].class));

    when(mockedRaidClient.getAccessToken(
            AUTH_BASE_URL, CLIENT_ID, CLIENT_SECRET, AUTH_CODE, getExpectedCallbackUrl()))
        .thenReturn(jsonAccessToken);
  }

  @Test
  public void testGetServicePointList() throws URISyntaxException, JsonProcessingException {
    // GIVEN
    when(mockedRaidClient.getServicePointList(API_BASE_URL, OLD_ACCESS_TOKEN))
        .thenReturn(expectedServicePointList);
    raidServiceClientAdapter.performCreateAccessToken(user.getUsername(), SERVER_ALIAS, AUTH_CODE);

    // WHEN
    List<RaIDServicePoint> actualServicePointList =
        raidServiceClientAdapter.getServicePointList(user.getUsername(), SERVER_ALIAS);

    // THEN
    assertEquals(expectedServicePointList, actualServicePointList);
  }

  @Test
  public void testGetServicePoint() throws URISyntaxException, JsonProcessingException {
    // GIVEN
    when(mockedRaidClient.getServicePoint(API_BASE_URL, OLD_ACCESS_TOKEN, SERVICE_POINT_ID))
        .thenReturn(expectedServicePoint);
    raidServiceClientAdapter.performCreateAccessToken(user.getUsername(), SERVER_ALIAS, AUTH_CODE);

    // WHEN
    RaIDServicePoint actualServicePoint =
        raidServiceClientAdapter.getServicePoint(
            user.getUsername(), SERVER_ALIAS, SERVICE_POINT_ID);

    // THEN
    assertEquals(expectedServicePoint, actualServicePoint);
  }

  @Test
  public void testGetRaidList() throws URISyntaxException, JsonProcessingException {
    // GIVEN
    when(mockedRaidClient.getRaIDList(API_BASE_URL, OLD_ACCESS_TOKEN)).thenReturn(expectedRaidList);
    Set<RaIDReferenceDTO> expectedRaidReferenceDTOList =
        expectedRaidList.stream()
            .map(
                el ->
                    new RaIDReferenceDTO(
                        SERVER_ALIAS,
                        el.getTitle().get(0).getText(),
                        el.getIdentifier().getId(),
                        el.getIdentifier().getRaidAgencyUrl()))
            .collect(Collectors.toSet());
    raidServiceClientAdapter.performCreateAccessToken(user.getUsername(), SERVER_ALIAS, AUTH_CODE);

    // WHEN
    Set<RaIDReferenceDTO> actualServicePointList =
        raidServiceClientAdapter.getRaIDList(user.getUsername(), SERVER_ALIAS);

    // THEN
    assertEquals(expectedRaidReferenceDTOList, actualServicePointList);
  }

  @Test
  public void testGetRaid() throws URISyntaxException, JsonProcessingException {
    // GIVEN
    when(mockedRaidClient.getRaID(API_BASE_URL, OLD_ACCESS_TOKEN, RAID_PREFIX, RAID_SUFFIX))
        .thenReturn(expectedRaid);
    RaIDReferenceDTO expectedRaidReferenceDTO =
        new RaIDReferenceDTO(
            SERVER_ALIAS,
            expectedRaid.getTitle().get(0).getText(),
            expectedRaid.getIdentifier().getId(),
            expectedRaid.getIdentifier().getRaidAgencyUrl());
    raidServiceClientAdapter.performCreateAccessToken(user.getUsername(), SERVER_ALIAS, AUTH_CODE);

    // WHEN
    RaIDReferenceDTO actualRaidDTO =
        raidServiceClientAdapter.getRaID(
            user.getUsername(), SERVER_ALIAS, RAID_PREFIX, RAID_SUFFIX);

    // THEN
    assertEquals(expectedRaidReferenceDTO, actualRaidDTO);
  }

  @Test
  public void testUpdateRaIDRelatedObject() throws Exception {
    // GIVEN
    when(mockedRaidClient.updateRaIDRelatedObject(
            eq(API_BASE_URL), eq(OLD_ACCESS_TOKEN), eq(RAID_PREFIX), eq(RAID_SUFFIX), any()))
        .thenReturn(expectedRaid);
    when(mockedRaidClient.updateRaIDRelatedObject(
            eq(API_BASE_URL), eq(OLD_ACCESS_TOKEN), eq(RAID_PREFIX), eq(RAID_SUFFIX), eq(DOI_LINK)))
        .thenReturn(expectedRaidWithRelatedObject);

    RaIDReferenceDTO raidReference =
        new RaIDReferenceDTO(
            SERVER_ALIAS,
            expectedRaidWithRelatedObject.getTitle().get(0).getText(),
            expectedRaidWithRelatedObject.getIdentifier().getId(),
            expectedRaidWithRelatedObject.getIdentifier().getRaidAgencyUrl());
    raidServiceClientAdapter.performCreateAccessToken(user.getUsername(), SERVER_ALIAS, AUTH_CODE);

    assertFalse(
        raidServiceClientAdapter.updateRaIDRelatedObject(
            user.getUsername(), raidReference, DOI_LINK + "ANOTHER_LINK"));

    assertTrue(
        raidServiceClientAdapter.updateRaIDRelatedObject(
            user.getUsername(), raidReference, DOI_LINK));
  }

  @Test
  public void testPerformRedirectConnect() throws Exception {
    when(mockedRaidClient.getRedirectUriToConnect(
            AUTH_BASE_URL, CLIENT_ID, getExpectedCallbackUrl(), SERVER_ALIAS))
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
    when(mockedRaidClient.getRefreshToken(
            AUTH_BASE_URL,
            CLIENT_ID,
            CLIENT_SECRET,
            expectedAccessToken.getRefreshToken(),
            getExpectedCallbackUrl()))
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
    // GIVEN connection is not existing so it is not alive
    // THEN
    assertFalse(raidServiceClientAdapter.isRaidConnectionAlive(user.getUsername(), SERVER_ALIAS));

    // GIVEN the connection exists but it is expired
    when(mockedRaidClient.getServicePoint(API_BASE_URL, OLD_ACCESS_TOKEN, SERVICE_POINT_ID))
        .thenThrow(new HttpServerErrorException(HttpStatus.UNAUTHORIZED, "Unauthorized access"));
    raidServiceClientAdapter.performCreateAccessToken(user.getUsername(), SERVER_ALIAS, AUTH_CODE);
    // THEN still is not alive
    assertFalse(raidServiceClientAdapter.isRaidConnectionAlive(user.getUsername(), SERVER_ALIAS));

    // GIVEN the refresh token
    when(mockedRaidClient.getRefreshToken(
            AUTH_BASE_URL,
            CLIENT_ID,
            CLIENT_SECRET,
            expectedAccessToken.getRefreshToken(),
            getExpectedCallbackUrl()))
        .thenReturn(jsonRefreshToken);
    when(mockedRaidClient.getServicePoint(API_BASE_URL, NEW_ACCESS_TOKEN, SERVICE_POINT_ID))
        .thenReturn(new RaIDServicePoint());
    raidServiceClientAdapter.performRefreshToken(user.getUsername(), SERVER_ALIAS);
    // THEN the connection is alive
    assertTrue(raidServiceClientAdapter.isRaidConnectionAlive(user.getUsername(), SERVER_ALIAS));
  }

  private String getExpectedCallbackUrl() {
    return this.properties.getServerUrl() + "/apps/raid/callback";
  }
}
