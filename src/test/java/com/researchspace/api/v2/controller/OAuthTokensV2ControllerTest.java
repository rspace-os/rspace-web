package com.researchspace.api.v2.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.model.User;
import com.researchspace.service.OAuthTokenManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OAuthTokensV2ControllerTest {

  @Test
  void returnsOnlyTheUiAccessTokenWithoutCaching() throws Exception {
    OAuthTokenManager tokenManager = mock(OAuthTokenManager.class);
    User user = mock(User.class);
    when(tokenManager.createUiToken(user)).thenReturn("access-token");
    MockMvc mockMvc =
        MockMvcBuilders.standaloneSetup(new OAuthTokensV2Controller(tokenManager)).build();

    mockMvc
        .perform(post("/api/v2/oauth/tokens").requestAttr("user", user))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$.accessToken").value("access-token"));

    verify(tokenManager).createUiToken(user);
  }
}
