package com.researchspace.webapp.integrations.slack;

import static com.researchspace.service.IntegrationsHandler.SLACK_APP_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.researchspace.Constants;
import com.researchspace.model.User;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.UserAppConfigManager;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.testutils.StubHttpServer;
import com.researchspace.webapp.controller.MVCTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.util.UriComponentsBuilder;

@WebAppConfiguration
public class SlackControllerMVCIT extends MVCTestBase {

  private static final String CALLBACK_URL = "/slack/redirect_uri";
  private static final String CONNECTED_VIEW = "connect/connected";
  private static final String STATE_MISMATCH = "state' parameter is missing or doesn't match";

  @Autowired private SlackController slackController;
  @Autowired private UserAppConfigManager userAppConfigManager;
  @Autowired private IntegrationsHandler integrationsHandler;
  @Autowired private UserConnectionManager userConnectionManager;
  private User user;

  @BeforeEach
  public void setUp() throws Exception {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    user = createAndSaveUser(getRandomAlphabeticString("user"), Constants.USER_ROLE);
    initUsers(user);
    logoutAndLoginAs(user);
    SessionAttributeUtils.removeSessionAttribute(SessionAttributeUtils.RS_OAUTH_STATE);
  }

  @Test
  public void callbackAcceptsMatchingState() throws Exception {
    String oauthUrl = slackController.oauthUrl().getData();
    String state =
        UriComponentsBuilder.fromUriString(oauthUrl).build().getQueryParams().getFirst("state");
    assertNotNull(state);

    mockMvc
        .perform(
            get(CALLBACK_URL)
                .param("error", "access_denied")
                .param("state", state)
                .principal(user::getUsername))
        .andExpect(status().isOk())
        .andExpect(view().name(CONNECTED_VIEW))
        .andExpect(modelAttributeContains("connectionError", "access_denied"))
        .andExpect(modelAttributeDoesNotContain("connectionError", STATE_MISMATCH));
  }

  @Test
  public void callbackRejectsMismatchedState() throws Exception {
    SessionAttributeUtils.setSessionAttribute(
        SessionAttributeUtils.RS_OAUTH_STATE, "expected-state");

    mockMvc
        .perform(
            get(CALLBACK_URL)
                .param("error", "access_denied")
                .param("state", "forged-state")
                .principal(user::getUsername))
        .andExpect(status().isOk())
        .andExpect(view().name(CONNECTED_VIEW))
        .andExpect(modelAttributeContains("connectionError", STATE_MISMATCH));
  }

  @Test
  public void callbackRejectsMissingState() throws Exception {
    mockMvc
        .perform(get(CALLBACK_URL).param("error", "access_denied").principal(user::getUsername))
        .andExpect(status().isOk())
        .andExpect(view().name(CONNECTED_VIEW))
        .andExpect(modelAttributeContains("connectionError", STATE_MISMATCH));
  }

  @Test
  public void tokenExchangeFailureDoesNotShowCredentials() throws Exception {
    String secret = "slack-secret-" + getRandomAlphabeticString("s");
    String code = "slack-code-" + getRandomAlphabeticString("c");
    // no stubs, so the exchange gets a 404 whose JDK message is the full request URL
    StubHttpServer slack = new StubHttpServer();
    Object target = AopTestUtils.getUltimateTargetObject(slackController);
    Object originalBaseUrl = ReflectionTestUtils.getField(target, "slackApiBaseUrl");
    Object originalSecret = ReflectionTestUtils.getField(target, "clientSecret");
    try {
      ReflectionTestUtils.setField(target, "slackApiBaseUrl", slack.getBaseUrl());
      ReflectionTestUtils.setField(target, "clientSecret", secret);
      String state =
          UriComponentsBuilder.fromUriString(slackController.oauthUrl().getData())
              .build()
              .getQueryParams()
              .getFirst("state");

      mockMvc
          .perform(
              get(CALLBACK_URL)
                  .param("code", code)
                  .param("state", state)
                  .principal(user::getUsername))
          .andExpect(view().name(CONNECTED_VIEW))
          .andExpect(modelAttributeContains("connectionError", "Exception during token exchange"))
          .andExpect(modelAttributeDoesNotContain("connectionError", secret))
          .andExpect(modelAttributeDoesNotContain("connectionError", code));
    } finally {
      ReflectionTestUtils.setField(target, "slackApiBaseUrl", originalBaseUrl);
      ReflectionTestUtils.setField(target, "clientSecret", originalSecret);
      slack.stop();
    }
  }

  @Test
  public void connectingSavesTheChannelAndKeepsItsCredentialsInUserConnection() throws Exception {
    // the Apps page refetches the integration, so the callback must evict its cached copy
    assertThat(integrationsHandler.getIntegration(user, SLACK_APP_NAME).getOptions()).isEmpty();
    MvcResult result =
        callbackWithSlackResponding(
            "{\"ok\":true,\"access_token\":\"xoxp-token\",\"user_id\":\"U1\","
                + "\"team_id\":\"T1\",\"team_name\":\"Team\",\"incoming_webhook\":"
                + "{\"channel\":\"#general\",\"channel_id\":\"C1\","
                + "\"url\":\"https://hooks.slack.com/services/x\"}}");

    assertNull(result.getModelAndView().getModel().get("connectionError"));
    AppConfigElementSet channel =
        userAppConfigManager.getByAppName("app.slack", user).getAppConfigElementSets().stream()
            .findFirst()
            .orElseThrow();
    assertEquals("#general", channel.findElementByPropertyName("SLACK_CHANNEL_LABEL").getValue());
    assertEquals(6, channel.getConfigElements().size());
    UserConnection connection =
        userConnectionManager
            .findByUserNameProviderName(
                user.getUsername(), SLACK_APP_NAME, channel.getId().toString())
            .orElseThrow();
    assertEquals("xoxp-token", connection.getAccessToken());
    assertEquals("https://hooks.slack.com/services/x", connection.getSecret());
    assertThat(integrationsHandler.getIntegration(user, SLACK_APP_NAME).getOptions())
        .containsKey(channel.getId().toString());
  }

  @Test
  public void eachConnectedChannelGetsItsOwnConnection() throws Exception {
    callbackWithSlackResponding(oauthAccessResponse("C1"));
    callbackWithSlackResponding(oauthAccessResponse("C2"));

    assertThat(
            userConnectionManager.findListByUserNameProviderName(
                user.getUsername(), SLACK_APP_NAME))
        .hasSize(2);
  }

  private static String oauthAccessResponse(String channelId) {
    return "{\"ok\":true,\"access_token\":\"xoxp-token\",\"user_id\":\"U1\",\"team_id\":\"T1\","
        + "\"team_name\":\"Team\",\"incoming_webhook\":{\"channel\":\"#general\",\"channel_id\":\""
        + channelId
        + "\",\"url\":\"https://hooks.slack.com/services/x\"}}";
  }

  @Test
  public void aFailedExchangeSavesNothing() throws Exception {
    MvcResult result = callbackWithSlackResponding("{\"ok\":false,\"error\":\"invalid_code\"}");

    assertThat((String) result.getModelAndView().getModel().get("connectionError"))
        .contains("invalid_code");
    assertThat(userAppConfigManager.getByAppName("app.slack", user).getAppConfigElementSets())
        .isEmpty();
  }

  @Test
  public void missingResponseFieldsUseTheLocalizedErrorAndSaveNothing() throws Exception {
    MvcResult result = callbackWithSlackResponding("{\"ok\":true}");

    assertThat((String) result.getModelAndView().getModel().get("connectionError"))
        .contains("The Slack response is missing required field team_name");
    assertThat(userAppConfigManager.getByAppName("app.slack", user).getAppConfigElementSets())
        .isEmpty();
    assertThat(
            userConnectionManager.findListByUserNameProviderName(
                user.getUsername(), SLACK_APP_NAME))
        .isEmpty();
  }

  private MvcResult callbackWithSlackResponding(String oauthAccessResponse) throws Exception {
    StubHttpServer slack = new StubHttpServer();
    slack.get("/oauth.access").respond(oauthAccessResponse);
    Object target = AopTestUtils.getUltimateTargetObject(slackController);
    Object originalBaseUrl = ReflectionTestUtils.getField(target, "slackApiBaseUrl");
    try {
      ReflectionTestUtils.setField(target, "slackApiBaseUrl", slack.getBaseUrl());
      String state =
          UriComponentsBuilder.fromUriString(slackController.oauthUrl().getData())
              .build()
              .getQueryParams()
              .getFirst("state");
      return mockMvc
          .perform(
              get(CALLBACK_URL)
                  .param("code", "code")
                  .param("state", state)
                  .principal(user::getUsername))
          .andExpect(view().name(CONNECTED_VIEW))
          .andReturn();
    } finally {
      ReflectionTestUtils.setField(target, "slackApiBaseUrl", originalBaseUrl);
      slack.stop();
    }
  }
}
