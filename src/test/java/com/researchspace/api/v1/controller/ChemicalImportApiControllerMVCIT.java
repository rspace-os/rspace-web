package com.researchspace.api.v1.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.model.User;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class ChemicalImportApiControllerMVCIT extends API_MVC_TestBase {

  private User user;
  private String apiKey;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    user = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(user);
  }

  @Test
  public void testSearchByName() throws Exception {
    MvcResult result =
        mockMvc
            .perform(createBuilderForPost(API_VERSION.ONE, apiKey, "chemical/search", user))
            .andExpect(status().isOk())
            .andReturn();
  }
}
