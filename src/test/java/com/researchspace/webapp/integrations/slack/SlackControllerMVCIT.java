package com.researchspace.webapp.integrations.slack;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.researchspace.Constants;
import com.researchspace.model.User;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.webapp.controller.MVCTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.util.UriComponentsBuilder;

@WebAppConfiguration
public class SlackControllerMVCIT extends MVCTestBase {

  private static final String CALLBACK_URL = "/slack/redirect_uri";
  private static final String CONNECTED_VIEW = "connect/connected";
  private static final String STATE_MISMATCH = "state' parameter is missing or doesn't match";

  @Autowired private SlackController slackController;
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
}
