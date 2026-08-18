package com.researchspace.webapp.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.Constants;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.model.frontend.OAuthAppInfo;
import com.researchspace.service.OAuthAppManager;
import com.researchspace.testutils.SSOTestContext;
import com.researchspace.webapp.filter.RemoteUserRetrievalPolicy;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

@SSOTestContext
public class OAuthClientSSOControllerMVCIT extends MVCTestBase {
  @Autowired private OAuthAppManager oAuthAppManager;
  private @Autowired VerificationPasswordResetHandler verificationPasswordhandler;

  /**
   * This test disables API and OAuth access mid-method, and the system property outlives the test,
   * so without this every later API test is unauthorised.
   */
  @After
  public void restoreApiAccess() {
    enableGlobalApiAccess();
    enableApiOAuthAuthentication();
  }

  @Test
  public void ssoSignupUserUsesVerificationPassword() throws Exception {

    // recreate SSO signup
    String username = RandomStringUtils.randomAlphabetic(10);
    String password = RemoteUserRetrievalPolicy.SSO_DUMMY_PASSWORD;
    User user = createAndSaveUser(username, Constants.USER_ROLE, password);
    user.setSignupSource(SignupSource.SSO);
    userMgr.save(user);

    // if API access is globally disabled, the SSO verification password shouldn't work either
    disableGlobalApiAccess();
    disableApiOAuthAuthentication();
    OAuthAppInfo app = oAuthAppManager.addApp(user, "newApp").getEntity();
    postOauthAccessTokenRequest(username, password, app)
        .andExpect(status().isUnauthorized())
        // The advice renders the exception's key into the response; assert on what callers see.
        .andExpect(
            jsonPath("$.message")
                .value("Access to API has been disabled by RSpace administrator."));

    // user1234 now fails
    enableGlobalApiAccess();
    enableApiOAuthAuthentication();
    app = oAuthAppManager.addApp(user, "newApp").getEntity();
    postOauthAccessTokenRequest(username, password, app)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Invalid user credentials."));

    // save verification password
    password = "abcdefghi";
    verificationPasswordhandler.encryptAndSavePassword(user, "abcdefghi");

    // verification password succeeds
    postOauthAccessTokenRequest(username, password, app).andExpect(status().isOk());

    // disabling just oauth authentication also stops generation of access tokens
    disableApiOAuthAuthentication();
    postOauthAccessTokenRequest(username, password, app).andExpect(status().isUnauthorized());
  }

  private ResultActions postOauthAccessTokenRequest(
      String username, String password, OAuthAppInfo app) throws Exception {
    return mockMvc.perform(
        post("/oauth/token")
            .param("client_id", app.getClientId())
            .param("client_secret", app.getUnhashedClientSecret())
            .param("grant_type", "password")
            .param("username", username)
            .param("password", password));
  }
}
