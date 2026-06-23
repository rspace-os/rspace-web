package com.researchspace.webapp.integrations.dmponline;

import static com.researchspace.service.IntegrationsHandler.DMPONLINE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.PROVIDER_USER_ID;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.controller.API_MVC_TestBase;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.UserConnectionManager;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class DMPOnlineControllerMVCIT extends API_MVC_TestBase {

  private @Autowired UserConnectionManager userConnectionManager;
  private User user;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    user = createInitAndLoginAnyUser();
  }

  @Test
  public void testConnect() throws Exception {
    MvcResult result =
        mockMvc
            .perform(post("/apps/dmponline/connect").principal(user::getUsername))
            .andExpect(status().is(302))
            .andReturn();

    String actualRedirectedUrl = result.getResponse().getRedirectedUrl();

    assertTrue(
        actualRedirectedUrl.contains("https://dmponline.dmptest.dcc.ac.uk/oauth/authorize?"));
    assertTrue(actualRedirectedUrl.contains("client_id="));
    assertTrue(actualRedirectedUrl.contains("redirect_uri="));
    assertTrue(actualRedirectedUrl.contains("scope="));
    assertTrue(actualRedirectedUrl.contains("response_type=code"));

    Optional<UserConnection> userConnection =
        userConnectionManager.findByUserNameProviderName(user.getUsername(), DMPONLINE_APP_NAME);
    assertTrue(userConnection.isEmpty());
  }

  @Test
  public void testCallback() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/apps/dmponline/callback").principal(user::getUsername))
            .andExpect(status().is(200)) // end point exists
            .andReturn();

    // assert is forwarded to the shared connection-result page (error variant)
    assertTrue(result.getResponse().getForwardedUrl().contains("connect/connected"));
  }

  @Test
  public void testRefreshToken() throws Exception {
    // A connection must exist, otherwise refresh_token throws NOT_FOUND before reaching the
    // shared connection-result page. With a connection present the upstream token refresh fails
    // (no reachable DMPonline server in the test environment), exercising the error variant of
    // that page. Like testCallback / testIsConnectionAlive above, this calls the real endpoint and
    // asserts the error-page forward rather than mocking the HTTP call.
    seedUserConnection();

    MvcResult result =
        mockMvc
            .perform(post("/apps/dmponline/refresh_token").principal(user::getUsername))
            .andExpect(status().is(200)) // end point exists
            .andReturn();

    // assert is forwarded to the shared connection-result page (error variant)
    assertTrue(result.getResponse().getForwardedUrl().contains("connect/connected"));
  }

  private void seedUserConnection() {
    UserConnection connection = new UserConnection();
    connection.setId(
        new UserConnectionId(user.getUsername(), DMPONLINE_APP_NAME, PROVIDER_USER_ID));
    connection.setAccessToken("ACCESS_TOKEN");
    connection.setRefreshToken("REFRESH_TOKEN");
    connection.setExpireTime(System.currentTimeMillis() + 60L * 60 * 1000);
    connection.setDisplayName("DMPonline access token");
    userConnectionManager.save(connection);
  }

  @Test
  public void testIsConnectionAlive() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/apps/dmponline/test_connection").principal(user::getUsername))
            .andExpect(status().is(200)) // end point exists
            .andReturn();

    // assert is redirected to the error page
    assertTrue(result.getResponse().getForwardedUrl().contains("error"));
  }
}
