package com.researchspace.api.v1.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.service.GroupManager;
import com.researchspace.testutils.TestGroup;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
@TestPropertySource(properties = {"api.beta.enabled=true"})
public class GroupApiControllerMVCIT extends API_MVC_TestBase {

  public @Autowired GroupManager groupMgr;

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void validGroupSearches() throws Exception {

    TestGroup testGrp1 = createTestGroup(3, new TestGroupConfig(true));

    TestGroup testGrp2 = createTestGroup(1, new TestGroupConfig(false));
    Group group2 = testGrp2.getGroup();
    group2.setDisplayName("groupApiSearchTest - public profile");
    groupMgr.saveGroup(group2, testGrp2.getPi());

    TestGroup testGrp3 = createTestGroup(1, new TestGroupConfig(false));
    Group privateGroup = testGrp3.getGroup();
    privateGroup.setDisplayName("groupApiSearchTest - private profile");
    privateGroup.setPrivateProfile(true);
    groupMgr.saveGroup(privateGroup, testGrp3.getPi());

    // as a PI of 1st group, search for own groups
    String apiKey = createApiKeyForuser(testGrp1.getPi());
    MvcResult result =
        mockMvc
            .perform(get(createUrl(API_VERSION.ONE, "/groups")).header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].members.length()", is(5)))
            .andExpect(jsonPath("$[0].members[0].role", is("PI")))
            .andExpect(jsonPath("$[0].name", is(testGrp1.getGroup().getDisplayName())))
            .andExpect(jsonPath("$[0].type", is("LAB_GROUP")))
            .andExpect(
                jsonPath(
                    "$[0].sharedFolderId",
                    is(testGrp1.getGroup().getCommunalGroupFolderId().intValue())))
            .andReturn();

    // now search all public groups with a search term
    result =
        mockMvc
            .perform(
                get(createUrl(API_VERSION.ONE, "/groups/search"))
                    .header("apiKey", apiKey)
                    .param("query", "groupApiSearchTest"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].members.length()", is(2)))
            .andExpect(jsonPath("$[0].members[0].role", is("PI")))
            .andExpect(jsonPath("$[0].name", is(group2.getDisplayName())))
            .andReturn();
  }

  @Test
  public void noGroupsReturnsEmptyList() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);
    MvcResult result =
        mockMvc
            .perform(get(createUrl(API_VERSION.ONE, "/groups")).header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(0)))
            .andReturn();
  }
}
