package com.researchspace.api.v1.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.model.User;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@WebAppConfiguration
public class UserDetailsApiControllerMVCIT extends API_MVC_TestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void getCurrentUserDetails() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(createUrl(API_VERSION.ONE, "/userDetails/whoami"))
                    .header("apiKey", apiKey))
            .andReturn();

    ApiUser retrievedUser = getFromJsonResponseBody(result, ApiUser.class);
    assertEquals(anyUser.getUsername(), retrievedUser.getUsername());
    assertEquals(anyUser.getFirstName(), retrievedUser.getFirstName());
    assertEquals(anyUser.getEmail(), retrievedUser.getEmail());
    assertNotNull(retrievedUser.getHomeFolderId());
    assertNotNull(retrievedUser.getWorkbenchId());
    assertFalse(retrievedUser.getHasPiRole());
    assertFalse(retrievedUser.getHasSysAdminRole());
  }

  /**
   * When calling the endpoint with apiKey in MVCIT test the response is always 'false', as api
   * session doesn't seem to know about web session subject begin "operated as".
   *
   * <p>Therefore, the only test that I can get working is one asserting the endpoint returns
   * 'false'. That's better than nothing - at least we confirm that endpoint doesn't error.
   */
  @Test
  public void getIsOperatedAs() throws Exception {
    User regularUser = createInitAndLoginAnyUser();
    String regularUserApiKey = createNewApiKeyForUser(regularUser);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(createUrl(API_VERSION.ONE, "/userDetails/isOperatedAs"))
                    .header("apiKey", regularUserApiKey))
            .andReturn();
    assertEquals("false", result.getResponse().getContentAsString());
  }

  @Test
  public void getGroupMembersForCurrentUser() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    User pi = createAndSaveUser(getRandomName(10), Constants.PI_ROLE);
    User secondUser = createAndSaveUser(getRandomName(10));
    initUsers(pi, secondUser);
    createGroupForUsersWithDefaultPi(pi, anyUser, secondUser);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(createUrl(API_VERSION.ONE, "/userDetails/groupMembers"))
                    .header("apiKey", apiKey))
            .andReturn();

    List<ApiUser> foundUsers =
        mvcUtils.getFromJsonResponseBodyByTypeRef(result, new TypeReference<List<ApiUser>>() {});
    assertEquals(3, foundUsers.size());
    ApiUser foundPiUser =
        foundUsers.stream().filter(u -> u.getUsername().equals(pi.getUsername())).findFirst().get();
    assertEquals(pi.getUsername(), foundPiUser.getUsername());
    assertNotNull(foundPiUser.getHomeFolderId());
    assertNotNull(foundPiUser.getWorkbenchId());
    assertTrue(foundPiUser.getHasPiRole());
    assertFalse(foundPiUser.getHasSysAdminRole());
    ApiUser foundSecondUser =
        foundUsers.stream()
            .filter(u -> u.getUsername().equals(secondUser.getUsername()))
            .findFirst()
            .get();
    assertEquals(secondUser.getUsername(), foundSecondUser.getUsername());
    assertFalse(foundSecondUser.getHasPiRole());
    assertFalse(foundSecondUser.getHasSysAdminRole());
  }

  @Test
  public void findUserDetails() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String userApiKey = createNewApiKeyForUser(anyUser);
    User sysAdminUser = getSysAdminUser();
    String sysAdminApiKey = createNewApiKeyForUser(sysAdminUser);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(createUrl(API_VERSION.ONE, "/userDetails/search"))
                    .param("query", "asdf")
                    .header("apiKey", userApiKey))
            .andReturn();

    List<ApiUser> foundUsers =
        mvcUtils.getFromJsonResponseBodyByTypeRef(result, new TypeReference<List<ApiUser>>() {});
    assertEquals(0, foundUsers.size());

    result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(createUrl(API_VERSION.ONE, "/userDetails/search"))
                    .param("query", sysAdminUser.getUsername().substring(0, 4))
                    .header("apiKey", userApiKey))
            .andReturn();
    foundUsers =
        mvcUtils.getFromJsonResponseBodyByTypeRef(result, new TypeReference<List<ApiUser>>() {});
    assertEquals(1, foundUsers.size());
    ApiUser foundUser = foundUsers.get(0);
    assertEquals(sysAdminUser.getUsername(), foundUser.getUsername());
    assertEquals(sysAdminUser.getFirstName(), foundUser.getFirstName());
    assertEquals(sysAdminUser.getLastName(), foundUser.getLastName());
    // only public data (i.e. username + name) for unreleated user
    assertNull(foundUser.getEmail());
    assertNull(foundUser.getHomeFolderId());
    assertNull(foundUser.getWorkbenchId());
    assertNull(foundUser.getHasPiRole());
    assertNull(foundUser.getHasSysAdminRole());

    // for current user return full data
    result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(createUrl(API_VERSION.ONE, "/userDetails/search"))
                    .param("query", anyUser.getUsername().substring(0, 4))
                    .header("apiKey", userApiKey))
            .andReturn();
    foundUsers =
        mvcUtils.getFromJsonResponseBodyByTypeRef(result, new TypeReference<List<ApiUser>>() {});
    assertEquals(1, foundUsers.size());
    foundUser = foundUsers.get(0);
    assertEquals(anyUser.getUsername(), foundUser.getUsername());
    assertEquals(anyUser.getEmail(), foundUser.getEmail());
    assertNotNull(foundUser.getHomeFolderId());
    assertNotNull(foundUser.getWorkbenchId());
    assertFalse(foundUser.getHasPiRole());
    assertFalse(foundUser.getHasSysAdminRole());

    // sysadmin can query any user's details
    result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(createUrl(API_VERSION.ONE, "/userDetails/search"))
                    .param("query", anyUser.getUsername().substring(0, 4))
                    .header("apiKey", sysAdminApiKey))
            .andReturn();
    foundUsers =
        mvcUtils.getFromJsonResponseBodyByTypeRef(result, new TypeReference<List<ApiUser>>() {});
    assertEquals(1, foundUsers.size());
    foundUser = foundUsers.get(0);
    assertEquals(anyUser.getUsername(), foundUser.getUsername());
    assertEquals(anyUser.getEmail(), foundUser.getEmail());
    assertNotNull(foundUser.getHomeFolderId());
    assertNotNull(foundUser.getWorkbenchId());
    assertFalse(foundUser.getHasPiRole());
    assertFalse(foundUser.getHasSysAdminRole());
  }
}
