package com.researchspace.webapp.integrations.owncloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.MVCTestBase;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class OwnCloudControllerMVCIT extends MVCTestBase {

  private @Autowired OwnCloudController ownCloudController;

  private MockMvc mockMvc;
  private MockHttpSession mockSession;

  private UserConnectionManager mockConnectionManager;
  private UserConnection mockConnection;
  private UserConnectionId mockConnectionId;
  private UserManager mockUserManager;
  private User mockUser;
  private User myUser;

  private Map<UserConnectionId, UserConnection> connectionMap = new HashMap<>();

  @Before
  public void setUp() {

    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    mockSession = new MockHttpSession();

    mockConnectionManager = mock(UserConnectionManager.class);
    mockConnectionId = new UserConnectionId();
    mockConnectionId.setProviderUserId("my_user");

    mockConnection = new UserConnection();
    mockConnection.setId(mockConnectionId);
    mockConnection.setAccessToken("my_token");
    mockConnection.setExpireTime(100l);

    mockUserManager = mock(UserManager.class);

    myUser = new User("my_user");
    mockUser = null;

    ownCloudController.setUserManager(mockUserManager);
    ownCloudController.setUserConnectionManager(mockConnectionManager);
    ownCloudController.connector =
        new OwnCloudController.OwnCloudControllerConnector() {
          @Override
          String doRedirectCall(String ownCloudUrl, String clientId, String clientSecret)
              throws IOException {
            return "{\"access_token\":\"my_token\",\"user_id\":\"my_user_id\",\"refresh_token\":\"my_refresh_token\",\"expires_in\":\"1000\"}";
          }

          @Override
          String doRefreshCall(String ownCloudRefreshUrl, String clientId, String clientSecret)
              throws IOException {
            return "{\"access_token\":\"my_refreshed_access_token\",\"user_id\":\"my_user_id\",\"refresh_token\":\"my_refresh_token\",\"expires_in\":\"1000\"}";
          }
        };

    when(mockUserManager.getAuthenticatedUserInSession()).thenAnswer(invoc -> mockUser);

    when(mockConnectionManager.findByUserNameProviderName("my_user", "OWNCLOUD"))
        .thenAnswer(invoc -> Optional.ofNullable(connectionMap.get(mockConnectionId)));

    when(mockConnectionManager.get(mockConnectionId))
        .thenAnswer(invoc -> connectionMap.get(mockConnectionId));

    when(mockConnectionManager.deleteByUserAndProvider("OWNCLOUD", "my_user"))
        .then(
            invoc -> {
              connectionMap.remove(mockConnectionId);
              return null;
            });
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testRefreshToken() throws Exception {
    // Test with no user connection
    connectionMap.clear();

    MvcResult noConnectionResult =
        this.mockMvc
            .perform(
                get("/apps/owncloud/refreshToken")
                    .principal(new MockPrincipal("my_user"))
                    .session(mockSession))
            .andExpect(status().isOk())
            .andReturn();

    assertEquals(noConnectionResult.getResponse().getContentAsString(), "");

    // Test with user connection
    connectionMap.put(mockConnectionId, mockConnection);

    MvcResult connectionResult =
        this.mockMvc
            .perform(
                get("/apps/owncloud/refreshToken")
                    .principal(new MockPrincipal("my_user"))
                    .session(mockSession))
            .andExpect(status().isOk())
            .andReturn();

    assertNotEquals(connectionResult.getResponse().getContentAsString(), "");

    Map json = parseJSONObjectFromResponseStream(connectionResult);

    assertEquals(json.get("access_token"), "my_refreshed_access_token");
    assertEquals(json.get("username"), "my_user");
    assert (Long.parseLong(json.get("expire_time").toString())
        > System.currentTimeMillis() - 10000);

    connectionMap.clear();
  }

  @Test
  public void testAccessCredentials() throws Exception {
    // Test no credentials for ownCloud in session
    MvcResult noCredentialsResult =
        this.mockMvc
            .perform(
                get("/apps/owncloud/accessCredentials")
                    .principal(new MockPrincipal("my_user"))
                    .session(mockSession))
            .andExpect(status().isOk())
            .andReturn();

    assertEquals(noCredentialsResult.getResponse().getContentLength(), 0);

    // Test response with stored credentials
    connectionMap.clear();
    connectionMap.put(mockConnectionId, mockConnection);

    MvcResult credentialsResult =
        this.mockMvc
            .perform(
                get("/apps/owncloud/accessCredentials")
                    .principal(new MockPrincipal("my_user"))
                    .session(mockSession))
            .andExpect(status().isOk())
            .andReturn();

    Map json = parseJSONObjectFromResponseStream(credentialsResult);

    assertEquals(json.get("access_token"), "my_token");
    assertEquals(json.get("username"), "my_user");
    assertEquals(json.get("expire_time"), "100");

    connectionMap.clear();
  }

  @Test
  public void testRedirectLink() throws Exception {
    // Test successful retrieval when credentials have been stored
    this.mockMvc
        .perform(
            get("/apps/owncloud/redirectLink")
                .param("path", "/folder1/folder2/file")
                .session(mockSession))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("**/files/?dir=/folder1/folder2"))
        .andReturn();
  }

  @Test
  public void testSessionCredentials() throws Exception {
    // Test retrieval when no credentials stored
    MvcResult noStorageRetrieveResult =
        this.mockMvc
            .perform(get("/apps/owncloud/sessionInfo").session(mockSession))
            .andExpect(status().isOk())
            .andReturn();

    Map json = parseJSONObjectFromResponseStream(noStorageRetrieveResult);

    assertEquals(json.get("username"), null);
    assertEquals(json.get("password"), null);

    // Store some credentials
    this.mockMvc
        .perform(
            post("/apps/owncloud/sessionInfo")
                .param("username", "my_user")
                .param("password", "my_password")
                .session(mockSession))
        .andExpect(status().isOk())
        .andReturn();

    // Test successful retrieval when credentials have been stored
    MvcResult retrieveResult =
        this.mockMvc
            .perform(get("/apps/owncloud/sessionInfo").session(mockSession))
            .andExpect(status().isOk())
            .andReturn();

    json = parseJSONObjectFromResponseStream(retrieveResult);

    assertEquals(json.get("username"), "my_user");
    assertEquals(json.get("password"), "my_password");

    // Remove credentials from session, test that they are gone
    ownCloudController.removeOwnCloudCredentialsFromSession(mockSession);

    MvcResult removedRetrieveResult =
        this.mockMvc
            .perform(get("/apps/owncloud/sessionInfo").session(mockSession))
            .andExpect(status().isOk())
            .andReturn();

    json = parseJSONObjectFromResponseStream(removedRetrieveResult);

    assertEquals(json.get("username"), null);
    assertEquals(json.get("password"), null);
  }

  @Test
  public void testConnect() throws Exception {
    // Test authorization url redirect
    // Testing the url creation would just replicate the String.format code,
    // so here we will just make sure that the redirect happens.
    this.mockMvc
        .perform(post("/apps/owncloud/connect").session(mockSession))
        .andExpect(status().is3xxRedirection())
        .andReturn();

    // Test that disconnect is calling correct methods on connection manager
    connectionMap.clear();
    connectionMap.put(mockConnectionId, mockConnection);
    assertEquals(mockConnectionManager.get(mockConnectionId), mockConnection);

    this.mockMvc
        .perform(
            delete("/apps/owncloud/connect")
                .session(mockSession)
                .principal(new MockPrincipal("my_user")))
        .andExpect(status().is2xxSuccessful())
        .andReturn();

    assertEquals(mockConnectionManager.get(mockConnectionId), null);
  }

  @Test
  public void testAuthorizationFlow() throws Exception {

    /* OAuth authorization error flow */
    MvcResult authErrorResult =
        this.mockMvc
            .perform(
                get("/apps/owncloud/redirect_uri")
                    .param("error", "Error connecting to ownCloud")
                    .session(mockSession))
            .andExpect(status().isOk())
            .andExpect(view().name("connect/authorizationError"))
            .andReturn();

    assertEquals("Error connecting to ownCloud", getAuthError(authErrorResult).getErrorMsg());

    /* OAuth authorization without security token in session */
    MvcResult noTokenInSessionResult =
        this.mockMvc
            .perform(
                get("/apps/owncloud/redirect_uri").param("code", "code1234").session(mockSession))
            .andExpect(status().isOk())
            .andExpect(view().name("connect/authorizationError"))
            .andReturn();

    assertEquals(
        "Error connecting to ownCloud", getAuthError(noTokenInSessionResult).getErrorMsg());

    //		/* requesting security token for session without user */
    MvcResult unauthorizedTokenResult =
        this.mockMvc
            .perform(get("/apps/owncloud/accessCredentials").session(mockSession))
            .andExpect(status().isOk())
            .andReturn();

    // Shouldn't be any response content
    assertEquals(unauthorizedTokenResult.getResponse().getContentLength(), 0);

    //		/* requesting security token for session */
    connectionMap.clear();
    connectionMap.put(mockConnectionId, mockConnection);

    MvcResult tokenResult =
        this.mockMvc
            .perform(
                get("/apps/owncloud/accessCredentials")
                    .session(mockSession)
                    .principal(new MockPrincipal("my_user")))
            .andExpect(status().isOk())
            .andReturn();

    // Should be a token, username, and expire time
    Map json = parseJSONObjectFromResponseStream(tokenResult);
    assertNotNull(json.get("access_token"));
    assertNotNull(json.get("username"));
    assertNotNull(json.get("expire_time"));

    // OAuth success flow */
    mockUser = myUser;

    this.mockMvc
        .perform(
            get("/apps/owncloud/redirect_uri")
                .param("code", "code1234")
                .session(mockSession)
                .principal(new MockPrincipal("my_user")))
        .andExpect(status().isOk())
        .andExpect(view().name("connect/owncloud/connected"))
        .andExpect(model().attribute("ownCloudUsername", "my_user_id"))
        .andExpect(model().attribute("ownCloudAccessToken", "my_token"))
        .andReturn();
  }

  private OauthAuthorizationError getAuthError(MvcResult otherErrorResult) {
    return (OauthAuthorizationError) otherErrorResult.getModelAndView().getModel().get("error");
  }
}
