package com.researchspace.webapp.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.Constants;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.model.frontend.OAuthAppInfo;
import com.researchspace.service.OAuthAppManager;
import com.researchspace.testutils.CommunityTestContext;
import com.researchspace.webapp.filter.RemoteUserRetrievalPolicy;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@CommunityTestContext
public class OAuthClientCommunityControllerMVCIT extends MVCTestBase {
  @Autowired private OAuthAppManager oAuthAppManager;
  private @Autowired VerificationPasswordResetHandler verificationPasswordhandler;

  @Test
  public void googleSignupUserUsesVerificationPassword() throws Exception {

    // recreate Google signup on community
    String username = RandomStringUtils.randomAlphabetic(10);
    String password = RemoteUserRetrievalPolicy.SSO_DUMMY_PASSWORD;
    User user = createAndSaveUser(username, Constants.USER_ROLE, password);
    user.setSignupSource(SignupSource.GOOGLE);
    userMgr.save(user);

    // user1234 now fails
    OAuthAppInfo app = oAuthAppManager.addApp(user, "newApp").getEntity();
    MvcResult result =
        postOauthAccessTokenRequest(username, password, app)
            .andExpect(status().isUnauthorized())
            .andReturn();

    password = "abcdefghi";

    // verification password succeeds
    verificationPasswordhandler.encryptAndSavePassword(user, "abcdefghi");
    result =
        postOauthAccessTokenRequest(username, password, app).andExpect(status().isOk()).andReturn();
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
