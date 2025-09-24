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
    String apiKey = createNewApiKeyForUser(testGrp1.getPi());
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
    String apiKey = createNewApiKeyForUser(anyUser);
    MvcResult result =
        mockMvc
            .perform(get(createUrl(API_VERSION.ONE, "/groups")).header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(0)))
            .andReturn();
  }

  @Test
  public void testGetUserGroupById() throws Exception {
    TestGroup group = createTestGroup(2);
    String apiKey = createNewApiKeyForUser(group.getPi());
    Long groupId = group.getGroup().getId();

    mockMvc
        .perform(get(createUrl(API_VERSION.ONE, "/groups/" + groupId)).header("apiKey", apiKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(groupId.intValue())))
        .andExpect(jsonPath("$.name", is(group.getGroup().getDisplayName())))
        .andExpect(jsonPath("$.type", is("LAB_GROUP")))
        .andReturn();
  }

  @Test
  public void testGetUserGroupById_NotMember_Returns404() throws Exception {
    TestGroup groupA = createTestGroup(2);
    TestGroup groupB = createTestGroup(2);

    String apiKeyA = createNewApiKeyForUser(groupA.getPi());
    Long groupBId = groupB.getGroup().getId();

    mockMvc
        .perform(get(createUrl(API_VERSION.ONE, "/groups/" + groupBId)).header("apiKey", apiKeyA))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath(
                "$.message",
                is("Group with id: " + groupBId + " not found, or the user isn't a member.")));
  }

  @Test
  public void testGetUserGroupById_NonExistentId_Returns404() throws Exception {
    TestGroup group = createTestGroup(2);
    String apiKey = createNewApiKeyForUser(group.getPi());

    long fakeGroupId = -999999999L;

    mockMvc
        .perform(get(createUrl(API_VERSION.ONE, "/groups/" + fakeGroupId)).header("apiKey", apiKey))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath(
                "$.message",
                is("Group with id: " + fakeGroupId + " not found, or the user isn't a member.")));
    ;
  }
}
