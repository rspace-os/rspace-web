package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.API_ModelTestUtils.createAnyUserPost;
import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.core.util.DateUtil.localDateToDateUTC;
import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.testutils.MockAndStubUtils.modifyUserCreationDate;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.jdbc.JdbcTestUtils.countRowsInTable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.controller.SysadminApiController.GroupApiPost;
import com.researchspace.api.v1.controller.SysadminApiController.UserApiPost;
import com.researchspace.api.v1.controller.SysadminApiController.UserGroupPost;
import com.researchspace.api.v1.model.ApiGroupInfo;
import com.researchspace.api.v1.model.ApiSysadminUserSearchResult;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.apiutils.ApiError;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.testutils.MockAndStubUtils;
import com.researchspace.testutils.TestGroup;
import java.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.BindException;

@WebAppConfiguration
// test that this property is used.
@TestPropertySource(properties = {"sysadmin.nodeletenewerthan.days=7", "api.beta.enabled=true"})
public class SysadminUsersAPIControllerMVCIT extends API_MVC_TestBase {

  private @Autowired JdbcTemplate jdbcTemplate;

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void deleteTempUserFailsIfUserTooNew() throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    String apiKey = getApiKeyForuser(sysadmin);
    User temp = createAndSaveUser(getRandomName(10));
    temp.setTempAccount(true);
    userMgr.save(temp);
    mockMvc
        .perform(
            createBuilderForDelete(
                apiKey, "/sysadmin/users/temp/{id}", sysadmin, temp.getId() + ""))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  public void deleteTempUserSuccess() throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    String apiKey = getApiKeyForuser(sysadmin);
    final int initialUserCount = countRowsInTable(jdbcTemplate, "User");
    User temp = createAndSaveUser(getRandomName(10));
    temp.setTempAccount(true);
    modifyCreationDate(temp, LocalDate.now().minusDays(10));
    assertEquals(initialUserCount + 1, countRowsInTable(jdbcTemplate, "User"));
    mockMvc
        .perform(
            createBuilderForDelete(
                apiKey, "/sysadmin/users/temp/{id}", sysadmin, temp.getId() + ""))
        .andExpect(status().isNoContent());
    assertEquals(initialUserCount, countRowsInTable(jdbcTemplate, "User"));
  }

  @Test
  public void deleteRegularUserInGroup() throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    String apiKey = getApiKeyForuser(sysadmin);
    final int initialUserCount = countRowsInTable(jdbcTemplate, "User");
    TestGroup tg = createTestGroup(2);
    final int groupSize = tg.getGroup().getMemberCount();
    modifyCreationDate(userMgr.get(tg.u2().getId()), LocalDate.now().minusYears(2));
    modifyCreationDate(userMgr.get(tg.u1().getId()), LocalDate.now().minusYears(4));
    modifyLastLoginDate(userMgr.get(tg.u2().getId()), LocalDate.now().minusYears(2));
    modifyLastLoginDate(userMgr.get(tg.u1().getId()), LocalDate.now().minusYears(4));

    // u2 has logged in more recently, so u1 can't be deleted from group
    assertEquals(initialUserCount + groupSize, countRowsInTable(jdbcTemplate, "User"));
    mockMvc
        .perform(
            createBuilderForDelete(apiKey, "/sysadmin/users/{id}", sysadmin, tg.u1().getId() + "")
                .param("maxLastLogin", LocalDate.now().minusYears(3).toString()))
        .andExpect(status().is4xxClientError());
    assertEquals(initialUserCount + groupSize, countRowsInTable(jdbcTemplate, "User"));

    // without 'maxLastLogin' param,u1 is now newest lastLogin in the group, user is deleted
    modifyLastLoginDate(userMgr.get(tg.u1().getId()), LocalDate.now().minusYears(1).minusDays(7));
    mockMvc
        .perform(
            createBuilderForDelete(apiKey, "/sysadmin/users/{id}", sysadmin, tg.u1().getId() + ""))
        .andExpect(status().isNoContent());
    assertEquals(initialUserCount + groupSize - 1, countRowsInTable(jdbcTemplate, "User"));
  }

  @Test
  public void enableUserSuccessfully() throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    String apiKey = getApiKeyForuser(sysadmin);
    User userToEnable = createAndSaveUser(getRandomName(10));
    userToEnable.setEnabled(false);
    userMgr.save(userToEnable);
    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey, "/sysadmin/users/" + userToEnable.getId() + "/enable", sysadmin, ""))
        .andExpect(status().isNoContent());
    assertTrue(userMgr.getUser(userToEnable.getId().toString()).isEnabled());
  }

  @Test
  public void disableUserSuccessfully() throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    String apiKey = getApiKeyForuser(sysadmin);
    User userToDisable = createAndSaveUser(getRandomName(10));
    userToDisable.setEnabled(true);
    userMgr.save(userToDisable);
    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey, "/sysadmin/users/" + userToDisable.getId() + "/disable", sysadmin, ""))
        .andExpect(status().isNoContent());
    assertFalse(userMgr.getUser(userToDisable.getId().toString()).isEnabled());
  }

  private void modifyCreationDate(User temp, LocalDate localDate) {
    modifyUserCreationDate(temp, localDateToDateUTC(localDate));
    userMgr.save(temp);
  }

  private void modifyLastLoginDate(User temp, LocalDate localDate) {
    MockAndStubUtils.modifyDateField(
        temp, localDate != null ? localDateToDateUTC(localDate) : null, User.class, "setLastLogin");
    userMgr.save(temp);
  }

  @Test
  public void filterAllByLastLogin() throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    String apiKey = getApiKeyForuser(sysadmin);
    final int initialUserCount = countRowsInTable(jdbcTemplate, "User");
    final int initialNeverLoggedInUserCount =
        doInTransaction(
            () ->
                sessionFactory
                    .getCurrentSession()
                    .createQuery("from User where lastLogin is null")
                    .list()
                    .size());
    final int initialLoggedInUserCount = initialUserCount - initialNeverLoggedInUserCount;

    User anyUser = createAndSaveUser(getRandomName(10));
    anyUser.setTempAccount(true);
    modifyCreationDate(anyUser, LocalDate.now().minusYears(10));
    modifyLastLoginDate(anyUser, LocalDate.now().minusYears(7));
    // this user has never logged in - null values for lastLogin
    // should be included. So this returns all users as lastLogin is in the future

    // query both > 10 days ago. 0 results - all accounts created after this
    ApiSysadminUserSearchResult result =
        listUsersWithLastLoginBeforeOrNull(
            apiKey, LocalDate.now().minusYears(20), LocalDate.now().minusYears(20));
    assertEquals(0, result.getTotalHits().intValue());

    // lastlogin and creation date are before search criteria, get hit
    ApiSysadminUserSearchResult result4 =
        listUsersWithLastLoginBeforeOrNull(
            apiKey, LocalDate.now().minusYears(5), LocalDate.now().minusYears(8));
    assertEquals(1, result4.getTotalHits().intValue());

    // lastlogin after
    ApiSysadminUserSearchResult result5 =
        listUsersWithLastLoginBeforeOrNull(
            apiKey, LocalDate.now().minusYears(8), LocalDate.now().minusYears(8));
    assertEquals(0, result5.getTotalHits().intValue());

    // creationDate after
    ApiSysadminUserSearchResult result6 =
        listUsersWithLastLoginBeforeOrNull(
            apiKey, LocalDate.now().minusYears(5), LocalDate.now().minusYears(12));
    assertEquals(0, result6.getTotalHits().intValue());

    // now set lastLogin to be null.
    modifyLastLoginDate(anyUser, null);
    ApiSysadminUserSearchResult result7 =
        listUsersWithLastLoginBeforeOrNull(
            apiKey, LocalDate.now().minusYears(8), LocalDate.now().minusYears(8));
    assertEquals(1, result7.getTotalHits().intValue());
  }

  private ApiSysadminUserSearchResult listUsersWithLastLoginBeforeOrNull(
      String apiKey, LocalDate lastLoginDate, LocalDate creationDate) throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                get(createUrl(API_VERSION.ONE, "/sysadmin/users"))
                    .param("tempAccountsOnly", "false")
                    .param("lastLoginBefore", lastLoginDate.toString())
                    .param("createdBefore", creationDate.toString())
                    .header("apiKey", apiKey))
            .andReturn();
    return getFromJsonResponseBody(res, ApiSysadminUserSearchResult.class);
  }

  @Test
  public void searchForTempUsersByDate() throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    String apiKey = getApiKeyForuser(sysadmin);

    MvcResult result =
        mockMvc
            .perform(
                get(createUrl(API_VERSION.ONE, "/sysadmin/users"))
                    .param("createdBefore", "2018-01-01")
                    .header("apiKey", apiKey))
            .andReturn();
    ApiSysadminUserSearchResult res =
        getFromJsonResponseBody(result, ApiSysadminUserSearchResult.class);
    assertEquals(0, res.getTotalHits().intValue());

    // invalid date syntax rejected
    result =
        mockMvc
            .perform(
                get(createUrl(API_VERSION.ONE, "/sysadmin/users"))
                    .param("createdBefore", "abcde")
                    .header("apiKey", apiKey))
            .andReturn();
    assertException(result, BindException.class);
  }

  @Test
  public void createUser() throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    String apiKey = getApiKeyForuser(sysadmin);
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

    // check that newly created user can use API
    mockMvc
        .perform(
            createBuilderForGet2(
                API_VERSION.ONE,
                userToPost.getApiKey(),
                "/status",
                () -> createdApiUser::getUsername,
                new Object[] {}))
        .andExpect(status().isOk());
  }

  // Lab groups can be created by setting the group type,
  // or as the default if no group type is supplied for backwards compatibility purposes.
  @Test
  public void createLabGroupExplicitly() throws Exception {
    GroupApiPost post = new GroupApiPost();
    post.setDisplayName("g1");
    post.setType(GroupType.LAB_GROUP);
    createAndAssertLabGroup(post);
  }

  @Test
  public void createGroupDefaultsToLabWhenNoGroupTypeSupplied() throws Exception {
    GroupApiPost post = new GroupApiPost();
    post.setDisplayName("g1");
    createAndAssertLabGroup(post);
  }

  @Test
  public void createProjectGroup() throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    String apiKey = getApiKeyForuser(sysadmin);

    UserApiPost userToPost = createAnyUserPost(getRandomAlphabeticString("u1"));
    ApiUser userApiUser = createUserViaAPI(userToPost, apiKey, sysadmin);

    GroupApiPost post = new GroupApiPost();
    post.setDisplayName("g1");
    post.setType(GroupType.PROJECT_GROUP);
    UserGroupPost userGroupPost =
        new UserGroupPost(userApiUser.getUsername(), RoleInGroup.GROUP_OWNER.name());
    post.setUsers(toList(userGroupPost));

    MvcResult res =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/sysadmin/groups", sysadmin, post))
            .andReturn();
    ApiGroupInfo createdApiGroup = getFromJsonResponseBody(res, ApiGroupInfo.class);
    assertNotNull(createdApiGroup);
    assertEquals("g1", createdApiGroup.getName());
    assertEquals(1, createdApiGroup.getMembers().size());
    Group grp = grpMgr.getGroup(createdApiGroup.getId());
    assertEquals(
        RoleInGroup.GROUP_OWNER,
        grp.getRoleForUser(userMgr.getUserByUsername(userApiUser.getUsername())));
    assertEquals(GroupType.PROJECT_GROUP, grp.getGroupType());
  }

  @Test
  public void projectGroupShouldHaveAtLeast1GroupOwner() throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    String apiKey = getApiKeyForuser(sysadmin);

    UserApiPost userToPost = createAnyUserPost(getRandomAlphabeticString("u1"));
    ApiUser userApiUser = createUserViaAPI(userToPost, apiKey, sysadmin);

    GroupApiPost post = new GroupApiPost();
    post.setDisplayName("g1");
    post.setType(GroupType.PROJECT_GROUP);
    UserGroupPost userGroupPost =
        new UserGroupPost(userApiUser.getUsername(), RoleInGroup.DEFAULT.name());
    post.setUsers(toList(userGroupPost));

    mockMvc
        .perform(createBuilderForPostWithJSONBody(apiKey, "/sysadmin/groups", sysadmin, post))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            result ->
                assertEquals(
                    "Project group should have at least 1 GROUP_OWNER.",
                    result.getResolvedException().getMessage()));
  }

  @Test
  public void createLabGroupSetsDefaultPermissions() throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    String apiKey = getApiKeyForuser(sysadmin);

    UserApiPost userToPost = createAnyUserPost(getRandomAlphabeticString("u1"));
    userToPost.setRole(Role.PI_ROLE.getName());
    ApiUser userApiUser = createUserViaAPI(userToPost, apiKey, sysadmin);

    GroupApiPost post = new GroupApiPost();
    post.setDisplayName("g1");
    UserGroupPost userGroupPost =
        new UserGroupPost(userApiUser.getUsername(), RoleInGroup.PI.name());
    post.setUsers(toList(userGroupPost));

    MvcResult result =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/sysadmin/groups", sysadmin, post))
            .andReturn();

    ApiGroupInfo createdApiGroup = getFromJsonResponseBody(result, ApiGroupInfo.class);
    Group group = grpMgr.getGroup(createdApiGroup.getId());
    assertTrue(group.getPermissions().toString().contains("GROUP:SHARE:"));
    assertTrue(
        group
            .getPermissions()
            .toString()
            .contains("FORM:READ,SHARE,WRITE:property_group=true&group=" + group.getUniqueName()));
    assertTrue(
        group
            .getPermissions()
            .toString()
            .contains(
                "RECORD:READ,SHARE,WRITE:property_owner=${self}&group=" + group.getUniqueName()));
  }

  private void createAndAssertLabGroup(GroupApiPost post) throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    String apiKey = getApiKeyForuser(sysadmin);

    String unameString = getRandomAlphabeticString("pi");
    UserApiPost piToPost = createAnyUserPost(unameString);
    piToPost.setRole(Role.PI_ROLE.getName());
    ApiUser piApiUser = createUserViaAPI(piToPost, apiKey, sysadmin);

    UserApiPost userToPost = createAnyUserPost(getRandomAlphabeticString("u1"));
    ApiUser userApiUser = createUserViaAPI(userToPost, apiKey, sysadmin);

    UserGroupPost piGroupPost = new UserGroupPost(piApiUser.getUsername(), RoleInGroup.PI.name());
    UserGroupPost userGroupPost =
        new UserGroupPost(userApiUser.getUsername(), RoleInGroup.RS_LAB_ADMIN.name());
    post.setUsers(toList(piGroupPost, userGroupPost));

    MvcResult res =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/sysadmin/groups", sysadmin, post))
            .andReturn();
    ApiGroupInfo createdApiGroup = getFromJsonResponseBody(res, ApiGroupInfo.class);
    assertNotNull(createdApiGroup);
    assertEquals("g1", createdApiGroup.getName());
    assertEquals(2, createdApiGroup.getMembers().size());
    Group grp = grpMgr.getGroup(createdApiGroup.getId());
    assertEquals(
        RoleInGroup.PI, grp.getRoleForUser(userMgr.getUserByUsername(piApiUser.getUsername())));
    assertEquals(
        RoleInGroup.RS_LAB_ADMIN,
        grp.getRoleForUser(userMgr.getUserByUsername(userApiUser.getUsername())));
    assertEquals(GroupType.LAB_GROUP, grp.getGroupType());
  }

  private ApiUser createUserViaAPI(UserApiPost userToPost, String apiKey, User sysadmin)
      throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/sysadmin/users", sysadmin, userToPost))
            .andReturn();
    ApiUser createdApiUser = getFromJsonResponseBody(res, ApiUser.class);
    return createdApiUser;
  }
}
