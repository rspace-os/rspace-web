package com.researchspace.webapp.controller;

import static com.researchspace.Constants.ADMIN_ROLE;
import static com.researchspace.Constants.SYSADMIN_ROLE;
import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.webapp.filter.RemoteUserRetrievalPolicy.SSO_DUMMY_PASSWORD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.fileUpload;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axiope.userimport.UserImportResult;
import com.researchspace.Constants;
import com.researchspace.ldap.UserLdapRepo;
import com.researchspace.license.InactiveLicenseTestService;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.dto.UserRegistrationInfo;
import com.researchspace.model.field.ErrorList;
import com.researchspace.service.impl.ConfigurableLogger;
import com.researchspace.service.impl.license.NoCheckLicenseService;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

public class SysAdminUserRegistrationControllerMVCIT extends MVCTestBase {

  private static final String SYSTEM_USER_REGISTRATION_BATCH_CREATE =
      "/system/userRegistration/batchCreate";
  private static final String BATCH_REGISTRATION_COMPLETE_MSG = "Import complete";

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  private Principal sysAdminPrincipal;
  private User sysAdmin;

  @Autowired private SysAdminUserRegistrationController controller;

  @After
  public void teardown() throws Exception {
    getBeanOfClass(ConfigurableLogger.class).setLoggerDefault();
    super.tearDown();
  }

  @Before
  public void setup() throws Exception {
    super.setUp();
    sysAdmin = createAndSaveUser(getRandomName(10), Constants.SYSADMIN_ROLE);
    initUser(sysAdmin);
    logoutAndLoginAs(sysAdmin);
    sysAdminPrincipal = new MockPrincipal(sysAdmin.getUsername());
  }

  @Test
  public void testBatchUpload() throws Exception {

    // 3 people = 3 groups
    MockMultipartFile mf =
        new MockMultipartFile(
            "xfile", "usersShort.csv", "csv", getTestResourceFileStream("usersShort.csv"));
    MvcResult result =
        mockMvc
            .perform(
                fileUpload("/system/userRegistration/csvUpload")
                    .file(mf)
                    .principal(sysAdminPrincipal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    UserImportResult importResults = getFromJsonResponseBody(result, UserImportResult.class);
    assertEquals(3, importResults.getParsedUsers().size());
    assertEquals(3, importResults.getParsedGroups().size());
    assertFalse(importResults.getErrors().hasErrorMessages());
  }

  @Test
  public void testBatchRegistrationStringInputParsing() throws Exception {

    // 3 people = 3 groups
    String csvInput =
        "Tiguel,Adams,test1@researchspace.com,ROLE_PI,tadams,\n"
            + "Jichard,Adams,test2@researchspace.com,ROLE_PI,jadams,\n"
            + "Kimon,Adams,test3@researchspace.com,ROLE_PI,kadams,\n"
            + "#Groups\n"
            + "Group1,tadams,jadams\nGroup2,jadams\nGroup3,kadams\n";

    MvcResult result =
        mockMvc
            .perform(
                post("/system/userRegistration/parseInputString")
                    .principal(sysAdminPrincipal)
                    .param("usersAndGroupsCsvFormat", csvInput))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    UserImportResult importResults = getFromJsonResponseBody(result, UserImportResult.class);
    assertEquals(3, importResults.getParsedUsers().size());
    assertEquals(3, importResults.getParsedGroups().size());
    assertFalse(importResults.getErrors().hasErrorMessages());
  }

  @Test
  public void testBatchUploadUsersWithLdap() throws Exception {

    User ldapUserData = new User();
    ldapUserData.setFirstName("ldapFirstName");
    ldapUserData.setLastName("ldapLastName");
    ldapUserData.setEmail("email@ldap");

    UserLdapRepo ldapRepoMock = mock(UserLdapRepo.class);
    when(ldapRepoMock.findUserByUsername("unexistingLdapUser")).thenReturn(null);
    when(ldapRepoMock.findUserByUsername("exceptionalLdapUser"))
        .thenThrow(new IllegalArgumentException());
    when(ldapRepoMock.findUserByUsername("ldapUser")).thenReturn(ldapUserData);

    String csvInput =
        ",,,ROLE_USER,unexistingLdapUser,testpass\n"
            + ",,,ROLE_USER,exceptionalLdapUser,testpass\n"
            + ",,,ROLE_USER,ldapUser,testpass\n";

    controller.setUserLdapRepo(ldapRepoMock);
    propertyHolder.setLdapEnabled("true");

    MvcResult result =
        mockMvc
            .perform(
                post("/system/userRegistration/parseInputString")
                    .principal(sysAdminPrincipal)
                    .param("usersAndGroupsCsvFormat", csvInput))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    propertyHolder.setLdapEnabled("false");

    UserImportResult importResults = getFromJsonResponseBody(result, UserImportResult.class);
    assertEquals(3, importResults.getParsedUsers().size());
    assertFalse(importResults.getErrors().hasErrorMessages());

    UserRegistrationInfo unexistingUserInfo = importResults.getParsedUsers().get(0);
    assertEquals("unexistingLdapUser", unexistingUserInfo.getUsername());
    assertEquals("", unexistingUserInfo.getFirstName());
    assertEquals("", unexistingUserInfo.getLastName());
    assertEquals("", unexistingUserInfo.getEmail());

    UserRegistrationInfo exceptionalUserInfo = importResults.getParsedUsers().get(1);
    assertEquals("exceptionalLdapUser", exceptionalUserInfo.getUsername());
    assertEquals("", exceptionalUserInfo.getFirstName());
    assertEquals("", exceptionalUserInfo.getLastName());
    assertEquals("", exceptionalUserInfo.getEmail());

    UserRegistrationInfo ldapUserInfo = importResults.getParsedUsers().get(2);
    assertEquals("ldapUser", ldapUserInfo.getUsername());
    assertEquals(ldapUserData.getFirstName(), ldapUserInfo.getFirstName());
    assertEquals(ldapUserData.getLastName(), ldapUserInfo.getLastName());
    assertEquals(ldapUserData.getEmail(), ldapUserInfo.getEmail());
  }

  @Test
  public void testBatchUploadInvalidContent() throws Exception {

    MockMultipartFile mf =
        new MockMultipartFile(
            "xfile", "testemail.csv", "csv", getTestResourceFileStream("testemail.txt"));
    MvcResult result =
        mockMvc
            .perform(
                fileUpload("/system/userRegistration/csvUpload")
                    .file(mf)
                    .principal(sysAdminPrincipal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    UserImportResult importResults = getFromJsonResponseBody(result, UserImportResult.class);
    assertEquals(0, importResults.getParsedUsers().size());
    assertEquals(0, importResults.getParsedGroups().size());
    assertTrue(importResults.getErrors().hasErrorMessages());
  }

  @Test
  public void testBatchCreateUsersNoUsers() throws Exception {
    final String emptyImportJson = "{}";

    MvcResult result =
        mockMvc
            .perform(
                post("/system//userRegistration/batchCreate")
                    .principal(sysAdminPrincipal)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(emptyImportJson))
            .andExpect(status().is2xxSuccessful())
            .andExpect(noErrorMessages())
            .andReturn();

    // check the messages
    Map resultMap = parseJSONObjectFromResponseStream(result);
    List<String> resultMsgs = (List<String>) resultMap.get("data");
    assertEquals(1, resultMsgs.size()); // 1x complete
    assertEquals(BATCH_REGISTRATION_COMPLETE_MSG, resultMsgs.get(0));
  }

  @Test
  public void testBatchCreateSSOUser() throws Exception {

    assertEquals("true", controller.properties.getStandalone());

    // creating user with empty password
    String ssoUsername = "batchSSOUser";
    String ssoUserJson = getUserRegistrationInfoJson(ssoUsername, null, Constants.SYSADMIN_ROLE);
    String json = "{\"parsedUsers\":[" + ssoUserJson + "]}";

    propertyHolder.setStandalone("false");
    MvcResult result =
        mockMvc
            .perform(
                post("/system//userRegistration/batchCreate")
                    .principal(sysAdminPrincipal)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(json))
            .andExpect(status().is2xxSuccessful())
            .andExpect(noErrorMessages())
            .andReturn();

    propertyHolder.setStandalone("true");

    // check the messages
    Map resultMap = parseJSONObjectFromResponseStream(result);
    List<String> resultMsgs = (List<String>) resultMap.get("data");
    assertEquals(3, resultMsgs.size());
    assertEquals(BATCH_REGISTRATION_COMPLETE_MSG, resultMsgs.get(2));
    //    	asssertErrorListEmpty(resultMap);

    // check that default sso password has been set
    User ssoUser = userMgr.getUserByUsername(ssoUsername);
    assertNotNull(ssoUser);
    logoutAndLoginAs(ssoUser, SSO_DUMMY_PASSWORD);
  }

  @Test
  public void testBatchCreateTwoUsersAndGroup() throws Exception {

    final String testUsername1 = getRandomAlphabeticString("batchUser1");
    final String testUsername2 = getRandomAlphabeticString("batchUser2");

    String userInfo1 = getUserRegistrationInfoJson(testUsername1, Constants.PI_ROLE);
    String userInfo2 = getUserRegistrationInfoJson(testUsername2, Constants.USER_ROLE);

    String groupInfo =
        getGroupPublicInfoJson("testBG1un", "testBatchGroup1", testUsername1, testUsername2);

    String json =
        "{"
            + "\"parsedUsers\":["
            + userInfo1
            + ","
            + userInfo2
            + "],"
            + "\"parsedGroups\":["
            + groupInfo
            + "]"
            + "}";

    // check call with no users or groups
    MvcResult result =
        mockMvc
            .perform(
                post(SYSTEM_USER_REGISTRATION_BATCH_CREATE)
                    .principal(sysAdminPrincipal)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(json))
            .andExpect(status().is2xxSuccessful())
            .andExpect(noErrorMessages())
            .andReturn();

    // check the messages
    Map resultMap = parseJSONObjectFromResponseStream(result);
    List<String> resultMsgs = (List<String>) resultMap.get("data");
    assertEquals(
        6, resultMsgs.size()); // 2x userCreated, 1x groupCreated, 2x userNotified, 1x complete
    assertEquals(BATCH_REGISTRATION_COMPLETE_MSG, resultMsgs.get(5));
    // asssertErrorListEmpty(resultMap);
  }

  private ResultMatcher noErrorMessages() {
    return MockMvcResultMatchers.jsonPath("$.error.errorMessages.length()").value(0);
  }

  @Test
  public void testBatchCreateSingleCommunity() throws Exception {

    String testPiUsername = "commTestPi1";
    String piInfo = getUserRegistrationInfoJson(testPiUsername, Constants.PI_ROLE);

    String testAdminUsername = "commTestTestAdmin11";
    String adminInfo = getUserRegistrationInfoJson(testAdminUsername, Constants.ADMIN_ROLE);

    String groupDisplayName = "testBatchCommGroup1";
    String groupInfo = getGroupPublicInfoJson("testBCG1un", groupDisplayName, testPiUsername);

    String communityName = "testCommunity1";
    String communityInfo =
        getCommunityPublicInfoJson(
            communityName, new String[] {testAdminUsername}, new String[] {groupDisplayName});

    String json =
        "{ "
            + "\"parsedUsers\":["
            + piInfo
            + ","
            + adminInfo
            + "], "
            + "\"parsedGroups\":["
            + groupInfo
            + "], "
            + "\"parsedCommunities\":["
            + communityInfo
            + "]}";

    // check call with no users or groups
    MvcResult result =
        mockMvc
            .perform(
                post("/system/userRegistration/batchCreate")
                    .principal(sysAdminPrincipal)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(json))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    // check the messages
    Map resultMap = parseJSONObjectFromResponseStream(result);
    List<String> resultMsgs = (List<String>) resultMap.get("data");
    assertEquals(
        7, resultMsgs.size()); // 2x userCreated & notified, group & comm created, import success

    Community retrievedCommunity = getCommunityByDisplayName(communityName);
    assertNotNull(retrievedCommunity);
    assertEquals(1, retrievedCommunity.getAdmins().size());
    assertEquals(1, retrievedCommunity.getLabGroups().size());
  }

  private Community getCommunityByDisplayName(String string) {
    openTransaction();
    Community c =
        (Community)
            sessionFactory
                .getCurrentSession()
                .createQuery("from Community where displayName=:displayName")
                .setString(Group.DEFAULT_ORDERBY_FIELD, string)
                .uniqueResult();
    commitTransaction();
    return communityMgr.getCommunityWithAdminsAndGroups(c.getId());
  }

  @Test
  public void testValidationMessagesForBatchUserCreation() throws Exception {

    final String incompleteUserJson =
        "{ \"username\":\"user1\",\"firstName\":\"\",\"lastName\":\"\","
            + "\"email\":\"\",\"password\":\"testPass\"}";

    final String existingUsername = sysAdmin.getUsername();
    final String existingEmail = sysAdmin.getEmail();
    final String existingUserJson =
        "{ \"username\":\""
            + existingUsername
            + "\",\"firstName\":\"A\", \"lastName\":\"B\","
            + "\"email\":\""
            + existingEmail
            + "\",\"password\":\"testPass\", \"confirmPassword\":\"testPass\" }";

    final String repeatedUsername = "batchUser11";
    final String repeatedUserJson =
        getUserRegistrationInfoJson(repeatedUsername, Constants.USER_ROLE);

    final String repeatedEmailUsername = "batchUser12";
    final String repeatedEmailJson =
        getUserRegistrationInfoJson(repeatedEmailUsername, Constants.USER_ROLE)
            .replace("batchUser12@test", "batchUser11@test");

    final String problematicUsersJson =
        "{\"parsedUsers\":["
            + incompleteUserJson
            + ","
            + existingUserJson
            + ","
            + repeatedUserJson
            + "," // importing same user two times
            + repeatedUserJson
            + ","
            + repeatedEmailJson
            + "]}";

    MvcResult result =
        mockMvc
            .perform(
                post(SYSTEM_USER_REGISTRATION_BATCH_CREATE)
                    .principal(sysAdminPrincipal)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(problematicUsersJson))
            .andExpect(status().is2xxSuccessful())
            .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(0))
            .andReturn();

    // all problems should be reported
    ErrorList errorList = mvcUtils.getErrorListFromAjaxReturnObject(result);
    List<String> errorMsgs = errorList.getErrorMessages();
    assertEquals(12, errorMsgs.size());
    assertEquals(
        "U.user1.username.Username must be at least 6 alphanumeric characters.", errorMsgs.get(0));
    assertEquals("U.user1.email.Email is a required field.", errorMsgs.get(1));
    assertEquals(
        "U.user1.password.Password and Confirm Password fields are not identical.",
        errorMsgs.get(2));
    assertEquals("U.user1.confirmPassword.Confirm password is a required field.", errorMsgs.get(3));
    assertEquals("U.user1.firstName.First Name is a required field.", errorMsgs.get(4));
    assertEquals("U.user1.lastName.Last Name is a required field.", errorMsgs.get(5));

    // same user imported two times, messages not repeated as username is the same
    assertEquals("U." + repeatedUsername + ".username.Repeated username.", errorMsgs.get(6));

    // separate messages for repeated emails
    assertEquals("U." + repeatedUsername + ".email.Repeated email.", errorMsgs.get(7));
    assertEquals("U." + repeatedUsername + ".email.Repeated email.", errorMsgs.get(8));
    assertEquals("U." + repeatedEmailUsername + ".email.Repeated email.", errorMsgs.get(9));

    assertEquals(
        "U." + existingUsername + ".username.Username is already registered in RSpace.",
        errorMsgs.get(10));
    assertEquals(
        "U." + existingUsername + ".email.Email is already registered in RSpace.",
        errorMsgs.get(11));
  }

  @Test
  public void testOnlySysadminCanCreateSysadmin() throws Exception {

    User rspaceAdmin = createAndSaveUser(getRandomName(10), ADMIN_ROLE);
    initUser(rspaceAdmin);
    logoutAndLoginAs(rspaceAdmin);
    MockPrincipal rspaceAdminPrincipal = new MockPrincipal(rspaceAdmin.getUsername());

    String sysadminJson = getUserRegistrationInfoJson("batchSysadmin", SYSADMIN_ROLE);
    String json = "{\"parsedUsers\":[" + sysadminJson + "]}";
    log.info("sysadmin json: " + json);

    // try as RSpace Admin
    MvcResult result =
        mockMvc
            .perform(
                post(SYSTEM_USER_REGISTRATION_BATCH_CREATE)
                    .principal(rspaceAdminPrincipal)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(json))
            .andExpect(status().is2xxSuccessful())
            .andExpect(exceptionThrown())
            .andReturn();
    assertAuthorizationException(result);

    logoutAndLoginAs(sysAdmin);

    // now try as System Admin
    MvcResult result2 =
        mockMvc
            .perform(
                post("/system//userRegistration/batchCreate")
                    .principal(sysAdminPrincipal)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(json))
            .andExpect(status().is2xxSuccessful())
            .andExpect(noErrorMessages())
            .andReturn();

    // check the messages
    Map resultMap2 = parseJSONObjectFromResponseStream(result2);
    List<String> resultMsgs2 = (List<String>) resultMap2.get("data");
    assertEquals(3, resultMsgs2.size());
    assertEquals(BATCH_REGISTRATION_COMPLETE_MSG, resultMsgs2.get(2));
  }

  @Test
  public void testValidationMessagesForBatchGroupCreation() throws Exception {

    String testPiName = "batchPi";
    String piJson = getUserRegistrationInfoJson(testPiName, Constants.PI_ROLE);
    String testUserName = "batchUser";
    String userJson = getUserRegistrationInfoJson(testUserName, Constants.USER_ROLE);

    String testUnknownName = "batchInvalid";

    String groupWithoutPiJSON = getGroupPublicInfoJson("group1un", "Group1", "", "");
    String groupWithUnknownPiJSON =
        getGroupPublicInfoJson("group2un", "Group2", testUnknownName, "");
    String groupWithPIWithoutRoleJSON =
        getGroupPublicInfoJson("group3un", "Group3", testUserName, testUnknownName);
    String groupWithUnknownMemberJSON =
        getGroupPublicInfoJson("group4un", "Group4", testPiName, testUserName, testUnknownName);
    String groupWithCommaInDisplayNameJSON =
        getGroupPublicInfoJson("group5un", "Group,5", testPiName, testUserName);

    final String problematicGroupsJson =
        "{\"parsedUsers\":["
            + piJson
            + ","
            + userJson
            + "], \"parsedGroups\":["
            + groupWithoutPiJSON
            + ","
            + groupWithUnknownPiJSON
            + ","
            + groupWithPIWithoutRoleJSON
            + ","
            + groupWithUnknownMemberJSON
            + ","
            + groupWithCommaInDisplayNameJSON
            + "]}";

    // System.out.println("sending problematicGroupsJson: " + problematicGroupsJson);

    MvcResult result =
        mockMvc
            .perform(
                post("/system//userRegistration/batchCreate")
                    .principal(sysAdminPrincipal)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(problematicGroupsJson))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.data.length()").value(0))
            .andReturn();

    // all problems should be reported
    ErrorList errorList = mvcUtils.getErrorListFromAjaxReturnObject(result);
    // JSONObject errorList = (JSONObject) resultMap.get("errorMsg");
    List<String> errorMsgs = errorList.getErrorMessages();
    assertEquals(7, errorMsgs.size());
    assertEquals(
        "G.group1un.pi.No PI was chosen. Every group must have an associated PI.",
        errorMsgs.get(1));
    assertEquals("G.group2un.pi.Unknown user [" + testUnknownName + "].", errorMsgs.get(2));
    assertEquals("G.group3un.pi.User needs PI role.", errorMsgs.get(3));
    assertEquals(
        "G.group3un.otherMembers.Unknown user [" + testUnknownName + "].", errorMsgs.get(4));
    assertEquals(
        "G.group4un.otherMembers.Unknown user [" + testUnknownName + "].", errorMsgs.get(5));
    assertEquals(
        "G.group5un.displayName.Comma mark not allowed in display name.", errorMsgs.get(6));
  }

  @Test
  public void testValidationMessagesForBatchCommunityCreation() throws Exception {

    String piUsername = "testPIuser";
    String piInfo = getUserRegistrationInfoJson(piUsername, Constants.PI_ROLE);

    String unknownUsername = "unknownUsername";
    String unknownGroupName = "unknown group";

    String communityName = "testCommunity2";
    String communityInfo =
        getCommunityPublicInfoJson(
            communityName,
            new String[] {unknownUsername, piUsername},
            new String[] {unknownGroupName});

    String json =
        "{ \"parsedUsers\":[" + piInfo + "], " + "\"parsedCommunities\":[" + communityInfo + "]}";

    System.out.println("problematic community json: " + json);
    MvcResult result =
        mockMvc
            .perform(
                post("/system//userRegistration/batchCreate")
                    .principal(sysAdminPrincipal)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(json))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.data.length()").value(0))
            .andReturn();

    ErrorList errorList = mvcUtils.getErrorListFromAjaxReturnObject(result);
    List<String> errorMsgs = errorList.getErrorMessages();
    assertEquals(3, errorMsgs.size());
    assertEquals("C.testCommunity2.admins.Unknown user [unknownUsername].", errorMsgs.get(0));
    assertEquals(
        "C.testCommunity2.admins.User [testPIuser] needs Community Admin role.", errorMsgs.get(1));
    assertEquals("C.testCommunity2.labGroups.Unknown group [unknown group].", errorMsgs.get(2));
  }

  private String getUserRegistrationInfoJson(String uname, String role) {
    return getUserRegistrationInfoJson(uname, TESTPASSWD, role);
  }

  private String getUserRegistrationInfoJson(String uname, String password, String role) {

    String json =
        "{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\""
            + uname
            + "@test\","
            + "\"role\":\""
            + role
            + "\",\"username\":\""
            + uname
            + "\"";
    if (password != null) {
      json += ",\"password\":\"" + password + "\",\"confirmPassword\":\"" + password + "\"";
    }
    return json + "}";
  }

  private String getGroupPublicInfoJson(
      String uniqueName, String name, String pi, String... otherMembers) {
    String json =
        "{\"uniqueName\":\""
            + uniqueName
            + "\",\"displayName\":\""
            + name
            + "\","
            + "\"pi\":\""
            + pi
            + "\"";
    if (otherMembers != null && otherMembers.length > 0) {
      String membersString = "\"" + StringUtils.join(otherMembers, "\",\"") + "\"";
      json += ",\"otherMembers\":[" + membersString + "]";
    }
    return json + "}";
  }

  private String getCommunityPublicInfoJson(String name, String[] admins, String[] groupNames) {
    String communityString =
        "{ \"displayName\": \"" + name + "\"," + " \"uniqueName\": \"" + name + "\"";
    if (admins != null) {
      String adminsString = "\"" + StringUtils.join(admins, "\",\"") + "\"";
      communityString += ", \"admins\": [" + adminsString + "]";
    }
    if (groupNames != null) {
      String groupsString = "\"" + StringUtils.join(groupNames, "\",\"") + "\"";
      communityString += ", \"labGroups\": [" + groupsString + "]";
    }
    return communityString + "}";
  }

  // need some test for part where we actually create users
  // @Test
  public void testBatchUploadWhenNoLicenses() throws Exception {
    try {
      controller.setLicenseService(new InactiveLicenseTestService());

      MockMultipartFile mf =
          new MockMultipartFile(
              "xfile", "usersShort.csv", "csv", getTestResourceFileStream("usersShort.csv"));
      mockMvc
          .perform(
              fileUpload("/system/userRegistration/csvUpload")
                  .file(mf)
                  .principal(sysAdminPrincipal))
          .andExpect(exceptionThrown())
          .andReturn();
    } finally {
      // tidy up for next test
      controller.setLicenseService(new NoCheckLicenseService());
    }
  }

  private ResultMatcher exceptionThrown() {
    return model().attributeExists(ControllerExceptionHandler.EXCEPTION_MESSAGE_ATTR_NAME);
  }
}
