package com.researchspace.webapp.integrations.dmponline;

import static com.researchspace.service.IntegrationsHandler.DMPONLINE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.PROVIDER_USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.controller.API_MVC_TestBase;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.service.UserConnectionManager;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@WebAppConfiguration
public class DMPOnlineControllerMVCIT extends API_MVC_TestBase {

  private @Autowired UserConnectionManager userConnectionManager;
  private @Autowired DMPOnlineController dmpOnlineController;

  @Value("${dmponline.base.url}")
  private String baseUrl;

  private User user;
  private MockRestServiceServer mockServer;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    user = createInitAndLoginAnyUser();

    RestTemplate restTemplate = new RestTemplate();
    dmpOnlineController.setRestTemplate(restTemplate);
    mockServer = MockRestServiceServer.createServer(restTemplate);
  }

  @Test
  public void testConnect() throws Exception {
    MvcResult result = startOAuthFlow();

    String actualRedirectedUrl = result.getResponse().getRedirectedUrl();

    assertThat(actualRedirectedUrl)
        .contains("https://dmponline.dmptest.dcc.ac.uk/oauth/authorize?");
    assertThat(actualRedirectedUrl).contains("client_id=");
    assertThat(actualRedirectedUrl).contains("redirect_uri=");
    assertThat(actualRedirectedUrl).contains("scope=");
    assertThat(actualRedirectedUrl).contains("response_type=code");

    Optional<UserConnection> userConnection =
        userConnectionManager.findByUserNameProviderName(user.getUsername(), DMPONLINE_APP_NAME);
    assertThat(userConnection).isEmpty();
  }

  @Test
  public void testCallback() throws Exception {
    MvcResult connectResult = startOAuthFlow();
    expectTokenRequestRejected();
    MvcResult result =
        mockMvc
            .perform(
                get("/apps/dmponline/callback")
                    .principal(user::getUsername)
                    .param("state", stateFrom(connectResult))
                    .param("code", "authorization-code"))
            .andExpect(status().is(200)) // end point exists
            .andReturn();

    // assert is forwarded to the shared connection-result page (error variant)
    assertThat(result.getResponse().getForwardedUrl()).contains("connect/connected");
    // the shared page serves both outcomes, so pin the error branch via its model attribute
    assertThat(((String) result.getModelAndView().getModel().get("connectionError")))
        .contains("Error during token creation");
    mockServer.verify();
  }

  @Test
  public void testRefreshToken() throws Exception {
    // A connection must exist, otherwise refresh_token throws NOT_FOUND before reaching the
    // shared connection-result page.
    seedUserConnection();
    expectTokenRequestRejected();

    MvcResult result =
        mockMvc
            .perform(post("/apps/dmponline/refresh_token").principal(user::getUsername))
            .andExpect(status().is(200)) // end point exists
            .andReturn();

    // assert is forwarded to the shared connection-result page (error variant)
    assertThat(result.getResponse().getForwardedUrl()).contains("connect/connected");
    // the shared page serves both outcomes, so pin the error branch via its model attribute
    assertThat(((String) result.getModelAndView().getModel().get("connectionError")))
        .contains("Error during token refresh");
    mockServer.verify();
  }

  private void expectTokenRequestRejected() {
    mockServer
        .expect(requestTo(baseUrl + "/oauth/token"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withBadRequest());
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

  private MvcResult startOAuthFlow() throws Exception {
    return mockMvc
        .perform(post("/apps/dmponline/connect").principal(user::getUsername))
        .andExpect(status().is(302))
        .andReturn();
  }

  private String stateFrom(MvcResult connectResult) {
    return UriComponentsBuilder.fromUriString(connectResult.getResponse().getRedirectedUrl())
        .build()
        .getQueryParams()
        .getFirst("state");
  }

  @Test
  public void testIsConnectionAlive() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/apps/dmponline/test_connection").principal(user::getUsername))
            .andExpect(status().is(200)) // end point exists
            .andReturn();

    // assert is redirected to the error page
    assertThat(result.getResponse().getForwardedUrl()).contains("error");
  }
}
