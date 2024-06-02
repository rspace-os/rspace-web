package com.researchspace.webapp.controller;

import static com.researchspace.webapp.controller.MvcTestUtils.parseOAuthTokenResponse;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.controller.API_MVC_TestBase;
import com.researchspace.api.v1.controller.API_VERSION;
import com.researchspace.api.v1.model.ApiRecordTreeItemListing;
import com.researchspace.api.v1.model.NewOAuthTokenResponse;
import com.researchspace.model.User;
import com.researchspace.testutils.RSpaceTestUtils;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

public class OAuthAPIAccessMVCIT extends API_MVC_TestBase {
  @Test
  public void createAccessTokenAndAccessApi() throws Exception {
    User user = createInitAndLoginAnyUser();
    RSpaceTestUtils.logout();

    // Get access token
    MvcResult result =
        mockMvc
            .perform(
                post("/oauth/token")
                    .param("client_id", testOAuthAppClientId)
                    .param("client_secret", testOAuthAppClientSecret)
                    .param("grant_type", "password")
                    .param("username", user.getUsername())
                    .param("password", "testpass"))
            .andExpect(status().isOk())
            .andReturn();
    String jsonResponse = result.getResponse().getContentAsString();
    NewOAuthTokenResponse response = parseOAuthTokenResponse(jsonResponse);
    String token = response.getAccessToken();

    // List root folder tree, this could be any call but testing we are accessing API
    result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(createUrl(API_VERSION.ONE, "/folders/tree"))
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    ApiRecordTreeItemListing folders =
        getFromJsonResponseBody(result, ApiRecordTreeItemListing.class);
    assertTrue(folders.getTotalHits() > 0);
  }
}
