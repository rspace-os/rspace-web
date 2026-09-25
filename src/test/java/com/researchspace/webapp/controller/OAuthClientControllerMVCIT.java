package com.researchspace.webapp.controller;

import static com.researchspace.webapp.controller.MvcTestUtils.parseOAuthTokenResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.Constants;
import com.researchspace.api.v1.model.NewOAuthTokenResponse;
import com.researchspace.model.User;
import com.researchspace.model.frontend.OAuthAppInfo;
import com.researchspace.service.OAuthAppManager;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

public class OAuthClientControllerMVCIT extends MVCTestBase {
  @Autowired private OAuthAppManager oAuthAppManager;

  /**
   * These tests disable API access mid-method, and the system property outlives the test, so a
   * failure before the re-enabling line left every later API test unauthorised.
   */
  @AfterEach
  public void restoreApiAccess() {
    enableGlobalApiAccess();
    enableApiOAuthAuthentication();
  }

  @Test
  public void incorrectGrants() throws Exception {
    String username = RandomStringUtils.randomAlphabetic(10);
    String password = RandomStringUtils.randomAlphabetic(10);
    User user = createAndSaveUser(username, Constants.USER_ROLE, password);

    OAuthAppInfo app = oAuthAppManager.addApp(user, "newApp").getEntity();

    String clientId = app.getClientId();
    String clientSecret = app.getUnhashedClientSecret();

    // let's disable API on the server
    disableGlobalApiAccess();
    disableApiOAuthAuthentication();

    // missing parameters (grant_type) are reported first
    mockMvc
        .perform(
            post("/oauth/token").param("client_id", clientId).param("client_secret", clientSecret))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                assertEquals(
                    "Required request parameter 'grant_type' "
                        + "for method parameter type String is not present",
                    result.getResolvedException().getMessage()));

    // disabled API access is reported next
    mockMvc
        .perform(
            post("/oauth/token")
                .param("client_id", clientId)
                .param("client_secret", clientSecret)
                .param("grant_type", "invalid"))
        .andExpect(status().isUnauthorized())
        // The advice renders the exception's key into the response; assert on what callers see.
        .andExpect(
            jsonPath("$.message")
                .value("Access to API has been disabled by RSpace administrator."));

    // re-enable global API access
    enableGlobalApiAccess();

    // disabled OAuth authentication is reported next
    mockMvc
        .perform(
            post("/oauth/token")
                .param("client_id", clientId)
                .param("client_secret", clientSecret)
                .param("grant_type", "invalid"))
        .andExpect(status().isUnauthorized())
        .andExpect(
            jsonPath("$.message")
                .value("OAuth authentication has been disabled by RSpace administrator."));

    // re-enable OAuth authentication
    enableApiOAuthAuthentication();

    // next, report invalid value for parameter grant_type
    mockMvc
        .perform(
            post("/oauth/token")
                .param("client_id", clientId)
                .param("client_secret", clientSecret)
                .param("grant_type", "invalid"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            result ->
                assertEquals(
                    "Only password grant and token refresh is "
                        + "supported for OAuth at this time.",
                    result.getResolvedException().getMessage()));

    // missing parameters for password grant
    mockMvc
        .perform(
            post("/oauth/token")
                .param("client_id", clientId)
                .param("client_secret", clientSecret)
                .param("grant_type", "password"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            result ->
                assertEquals(
                    "Password grant requires parameters `username` "
                        + "and `password` to be present.",
                    result.getResolvedException().getMessage()));

    // missing user's password
    mockMvc
        .perform(
            post("/oauth/token")
                .param("client_id", clientId)
                .param("client_secret", clientSecret)
                .param("grant_type", "password")
                .param("username", username))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            result ->
                assertEquals(
                    "Password grant requires parameters `username` "
                        + "and `password` to be present.",
                    result.getResolvedException().getMessage()));

    // invalid password
    mockMvc
        .perform(
            post("/oauth/token")
                .param("client_id", clientId)
                .param("client_secret", clientSecret)
                .param("grant_type", "password")
                .param("username", username)
                .param("password", "invalid"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Invalid user credentials."));

    // invalid username
    mockMvc
        .perform(
            post("/oauth/token")
                .param("client_id", clientId)
                .param("client_secret", clientSecret)
                .param("grant_type", "password")
                .param("username", "unknown-username")
                .param("password", password))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Invalid user credentials."));

    // missing refresh token
    mockMvc
        .perform(
            post("/oauth/token")
                .param("client_id", clientId)
                .param("client_secret", clientSecret)
                .param("grant_type", "refresh_token"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            result ->
                assertEquals(
                    "Refresh grant requires parameter `refresh_token` to be present.",
                    result.getResolvedException().getMessage()));

    // invalid token syntax
    mockMvc
        .perform(
            post("/oauth/token")
                .param("client_id", clientId)
                .param("client_secret", clientSecret)
                .param("grant_type", "refresh_token")
                .param("refresh_token", "invalid token syntax"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            result ->
                assertEquals("token length incorrect", result.getResolvedException().getMessage()));

    // Non-existent token
    mockMvc
        .perform(
            post("/oauth/token")
                .param("client_id", clientId)
                .param("client_secret", clientSecret)
                .param("grant_type", "refresh_token")
                .param("refresh_token", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
        .andExpect(status().isNotFound())
        .andExpect(
            result ->
                assertEquals(
                    "OAuth token not found! Perhaps its refresh rights have been removed.",
                    result.getResolvedException().getMessage()));
  }

  @Test
  public void passwordAndRefreshGrant() throws Exception {
    String username = RandomStringUtils.randomAlphabetic(10);
    String password = RandomStringUtils.randomAlphabetic(10);
    User user = createAndSaveUser(username, Constants.USER_ROLE, password);

    OAuthAppInfo app = oAuthAppManager.addApp(user, "newApp").getEntity();
    enableGlobalApiAccess();
    enableApiOAuthAuthentication();

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

    // confirm that refresh token request won't work after disabling oauth authentication
    disableApiOAuthAuthentication();
    mockMvc
        .perform(
            post("/oauth/token")
                .param("client_id", app.getClientId())
                .param("client_secret", app.getUnhashedClientSecret())
                .param("grant_type", "refresh_token")
                .param("refresh_token", response.getRefreshToken()))
        .andExpect(status().isUnauthorized())
        .andExpect(
            jsonPath("$.message")
                .value("OAuth authentication has been disabled by RSpace administrator."));
  }
}
