package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.API_ModelTestUtils.createAnyUserPost;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.controller.SysadminApiController.UserApiPost;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.apiutils.ApiError;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.User;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
// test that this property is used.
@TestPropertySource(
    properties = {
      "sysadmin.apikey.generation=false",
      "sysadmin.nodeletenewerthan.days=7",
      "api.beta.enabled=true"
    })
public class SysadminUsersAPIController2MVCIT extends API_MVC_TestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void createUser() throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    String apiKey = createNewApiKeyForUser(sysadmin);
    String unameString = getRandomAlphabeticString("u1");
    UserApiPost userToPost = createAnyUserPost(unameString);
    System.err.println(JacksonUtil.toJson(userToPost));

    MvcResult res =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/sysadmin/users", sysadmin, userToPost))
            .andReturn();
    System.err.println(res.getResponse().getContentAsString());
    ApiUser createdApiUser = getFromJsonResponseBody(res, ApiUser.class);
    assertNotNull(createdApiUser);
    assertNotNull(createdApiUser.getHomeFolderId()); // account is initialised

    // post again, should get exception, user exists already
    res =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/sysadmin/users", sysadmin, userToPost))
            .andExpect(status().is4xxClientError())
            .andReturn();
    ApiError error = getErrorFromJsonResponseBody(res, ApiError.class);
    assertTrue(error.getMessage().contains("already exists"));

    // check that newly created user cannot use API
    mockMvc
        .perform(
            createBuilderForGet2(
                API_VERSION.ONE,
                userToPost.getApiKey(),
                "/status",
                () -> createdApiUser::getUsername,
                new Object[] {}))
        .andExpect(status().is4xxClientError());
  }
}
