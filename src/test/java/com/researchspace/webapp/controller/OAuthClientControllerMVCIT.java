package com.researchspace.webapp.controller;

import static com.researchspace.webapp.controller.MvcTestUtils.parseOAuthTokenResponse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.Constants;
import com.researchspace.api.v1.model.NewOAuthTokenResponse;
import com.researchspace.model.User;
import com.researchspace.model.frontend.OAuthAppInfo;
import com.researchspace.service.OAuthAppManager;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

public class OAuthClientControllerMVCIT extends MVCTestBase {
  @Autowired private OAuthAppManager oAuthAppManager;

  @Test
  public void incorrectGrants() throws Exception {
    String username = RandomStringUtils.randomAlphabetic(10);
    String password = RandomStringUtils.randomAlphabetic(10);
    User user = createAndSaveUser(username, Constants.USER_ROLE, password);

    OAuthAppInfo app = oAuthAppManager.addApp(user, "newApp").getEntity();

    String clientId = app.getClientId();
    String clientSecret = app.getUnhashedClientSecret();

    // Missing parameter grant_type
    mockMvc
        .perform(
            post("/oauth/token").param("client_id", clientId).param("client_secret", clientSecret))
        .andExpect(status().is4xxClientError());

    // invalid value for parameter grant_type
    mockMvc
        .perform(
            post("/oauth/token")
                .param("client_id", clientId)
                .param("client_secret", clientSecret)
                .param("grant_type", "invalid"))
        .andExpect(status().is4xxClientError());

    // missing parameters for password grant
    mockMvc
        .perform(
            post("/oauth/token")
                .param("client_id", clientId)
                .param("client_secret", clientSecret)
                .param("grant_type", "password"))
        .andExpect(status().is4xxClientError());

    // missing user's password
    mockMvc
        .perform(
            post("/oauth/token")
                .param("client_id", clientId)
                .param("client_secret", clientSecret)
                .param("grant_type", "password")
                .param("username", username))
        .andExpect(status().is4xxClientError());

    // invalid password
    mockMvc
        .perform(
            post("/oauth/token")
                .param("client_id", clientId)
                .param("client_secret", clientSecret)
                .param("grant_type", "password")
                .param("username", username)
                .param("password", "invalid"))
        .andExpect(status().is4xxClientError());

    // invalid username
    mockMvc
        .perform(
            post("/oauth/token")
                .param("client_id", clientId)
                .param("client_secret", clientSecret)
                .param("grant_type", "password")
                .param("username", "unknown-username")
                .param("password", password))
        .andExpect(status().is4xxClientError());

    // missing refresh token
    mockMvc
        .perform(
            post("/oauth/token")
                .param("client_id", clientId)
                .param("client_secret", clientSecret)
                .param("grant_type", "refresh_token"))
        .andExpect(status().is4xxClientError());

    // invalid token syntax
    mockMvc
        .perform(
            post("/oauth/token")
                .param("client_id", clientId)
                .param("client_secret", clientSecret)
                .param("grant_type", "refresh_token")
                .param("refresh_token", "invalid token syntax"))
        .andExpect(status().is4xxClientError());

    // Non-existent token
    mockMvc
        .perform(
            post("/oauth/token")
                .param("client_id", clientId)
                .param("client_secret", clientSecret)
                .param("grant_type", "refresh_token")
                .param("refresh_token", "AAAAAAAAAAAAAAAAAAAAAA"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  public void passwordAndRefreshGrant() throws Exception {
    String username = RandomStringUtils.randomAlphabetic(10);
    String password = RandomStringUtils.randomAlphabetic(10);
    User user = createAndSaveUser(username, Constants.USER_ROLE, password);

    OAuthAppInfo app = oAuthAppManager.addApp(user, "newApp").getEntity();

    MvcResult result =
        mockMvc
            .perform(
                post("/oauth/token")
                    .param("client_id", app.getClientId())
                    .param("client_secret", app.getUnhashedClientSecret())
                    .param("grant_type", "password")
                    .param("username", username)
                    .param("password", password))
            .andExpect(status().isOk())
            .andReturn();
    String jsonResponse = result.getResponse().getContentAsString();
    NewOAuthTokenResponse response = parseOAuthTokenResponse(jsonResponse);

    assertEquals(response.getScope(), "all");
    assertNotNull(response.getAccessToken());
    assertNotNull(response.getRefreshToken());

    String oldAccessToken = response.getAccessToken();
    String oldRefreshToken = response.getRefreshToken();
    result =
        mockMvc
            .perform(
                post("/oauth/token")
                    .param("client_id", app.getClientId())
                    .param("client_secret", app.getUnhashedClientSecret())
                    .param("grant_type", "refresh_token")
                    .param("refresh_token", oldRefreshToken))
            .andExpect(status().isOk())
            .andReturn();
    jsonResponse = result.getResponse().getContentAsString();
    response = parseOAuthTokenResponse(jsonResponse);

    assertNotEquals(oldAccessToken, response.getAccessToken());
    assertNotEquals(oldRefreshToken, response.getRefreshToken());
  }
}
