package com.researchspace.api.v2.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.model.User;
import com.researchspace.service.OAuthTokenManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OAuthTokensV2ControllerTest {

  @Test
  void mintsADelegatedTokenDuringRunAs() throws Exception {
    OAuthTokenManager tokenManager = mock(OAuthTokenManager.class);
    User subject = mock(User.class);
    User actor = mock(User.class);
    when(tokenManager.createUiToken(
            ArgumentMatchers.same(subject),
            ArgumentMatchers.same(actor),
            ArgumentMatchers.anyString()))
        .thenReturn("delegated-token");
    MockMvc mockMvc =
        MockMvcBuilders.standaloneSetup(new OAuthTokensV2Controller(tokenManager)).build();

    mockMvc
        .perform(
            post("/api/v2/oauth/tokens")
                .session(new MockHttpSession())
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, new ApiV2Caller(subject, actor)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("delegated-token"));

    verify(tokenManager)
        .createUiToken(
            ArgumentMatchers.same(subject),
            ArgumentMatchers.same(actor),
            ArgumentMatchers.anyString());
  }

  @Test
  void returnsOnlyTheUiAccessTokenWithoutCaching() throws Exception {
    OAuthTokenManager tokenManager = mock(OAuthTokenManager.class);
    User user = mock(User.class);
    when(tokenManager.createUiToken(
            ArgumentMatchers.same(user), ArgumentMatchers.same(user), ArgumentMatchers.anyString()))
        .thenReturn("access-token");
    MockMvc mockMvc =
        MockMvcBuilders.standaloneSetup(new OAuthTokensV2Controller(tokenManager)).build();

    mockMvc
        .perform(
            post("/api/v2/oauth/tokens")
                .session(new MockHttpSession())
                .requestAttr(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(user)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$.accessToken").value("access-token"));

    verify(tokenManager)
        .createUiToken(
            ArgumentMatchers.same(user), ArgumentMatchers.same(user), ArgumentMatchers.anyString());
  }
}
