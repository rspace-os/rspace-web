package com.researchspace.api.v1.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiContainerSearchResult;
import com.researchspace.model.User;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

public class WorkbenchApiControllerMVCIT extends API_MVC_InventoryTestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void containerVisibilityWithinGroup() throws Exception {

    // create users and a group
    User pi = createAndSaveUser("pi" + getRandomName(10), Constants.PI_ROLE);
    User user = createAndSaveUser("user" + getRandomName(10));
    User secondUser = createAndSaveUser("secUser" + getRandomName(10));
    initUsers(pi, user, secondUser);
    createGroupForUsersWithDefaultPi(pi, user);

    String userApiKey = createNewApiKeyForUser(user);
    String secondUserApiKey = createNewApiKeyForUser(secondUser);

    // check visibility within a group - user can see pi's bench
    MvcResult result = retrieveVisibleWorkbenches(user, userApiKey);
    assertNull(result.getResolvedException());
    ApiContainerSearchResult workbenches =
        getFromJsonResponseBody(result, ApiContainerSearchResult.class);
    assertNotNull(workbenches);
    assertEquals(2, workbenches.getTotalHits().intValue());
    assertEquals(2, workbenches.getContainers().size());

    ApiContainerInfo piWorkbenchInfo = workbenches.getContainers().get(0);
    assertEquals("WB " + pi.getUsername(), piWorkbenchInfo.getName());
    ApiContainerInfo userWorkbenchInfo = workbenches.getContainers().get(1);
    assertEquals("WB " + user.getUsername(), userWorkbenchInfo.getName());

    // user in group should be able to see details of pi's workbench
    result =
        this.mockMvc
            .perform(getWorkbenchById(user, userApiKey, piWorkbenchInfo.getId(), true))
            .andReturn();
    assertNull(result.getResolvedException(), "unexpected: " + result.getResolvedException());
    ApiContainer retrievedPiContainer = getFromJsonResponseBody(result, ApiContainer.class);
    assertNotNull(retrievedPiContainer);
    assertNotNull(retrievedPiContainer.getCreatedBy()); // full view
    assertTrue(retrievedPiContainer.getStoredContent().isEmpty());

    // user outside the group see only their workbench
    result = retrieveVisibleWorkbenches(secondUser, secondUserApiKey);
    assertNull(result.getResolvedException());
    workbenches = getFromJsonResponseBody(result, ApiContainerSearchResult.class);
    assertNotNull(workbenches);
    assertEquals(1, workbenches.getTotalHits().intValue());
    assertEquals("WB " + secondUser.getUsername(), workbenches.getContainers().get(0).getName());
    assertFalse(workbenches.getContainers().get(0).isClearedForPublicView());
    // they can only see public view of other user's workbench
    result =
        this.mockMvc
            .perform(getWorkbenchById(secondUser, secondUserApiKey, piWorkbenchInfo.getId(), true))
            .andReturn();
    assertNull(result.getResolvedException());
    ApiContainer retrievedPiWorkbench = getFromJsonResponseBody(result, ApiContainer.class);
    assertNotNull(retrievedPiContainer);
    assertNull(retrievedPiWorkbench.getCreatedBy()); // public view
    assertNull(retrievedPiWorkbench.getStoredContent());
  }

  private MvcResult retrieveVisibleWorkbenches(User user, String apiKey) throws Exception {
    return this.mockMvc
        .perform(getVisibleWorkbenches(user, apiKey))
        .andExpect(status().isOk())
        .andReturn();
  }
}
