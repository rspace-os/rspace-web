package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.MockLoggingUtils.assertNoLogging;
import static com.researchspace.session.SessionAttributeUtils.USER_INFO;
import static com.researchspace.session.UserSessionTracker.USERS_KEY;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.researchspace.Constants;
import com.researchspace.admin.service.UsageListingDTO;
import com.researchspace.admin.service.UsageListingDTO.UserInfoListDTO;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.license.InactiveLicenseTestService;
import com.researchspace.licensews.LicenseExceededException;
import com.researchspace.model.Community;
import com.researchspace.model.EcatImage;
import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.UserEnablementUtils;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.ConfigurableLogger;
import com.researchspace.service.impl.license.NoCheckLicenseService;
import com.researchspace.testutils.MockAndStubUtils;
import com.researchspace.testutils.TestGroup;
import com.researchspace.webapp.filter.RemoteUserRetrievalPolicy;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import net.minidev.json.JSONArray;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authz.AuthorizationException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class SysAdminControllerMVCIT extends MVCTestBase {

  private static final String DUPLICATE_EMAILOR_UNAME_MSG =
      "Please try a different username and/or email";

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Mock Logger log;

  private @Autowired MockServletContext servletContext;

  private Principal sysAdminPrincipal;
  private User sysAdmin;

  private @Autowired UserEnablementUtils userEnablementUtils;
  private @Autowired SysAdminController controller;
  private @Autowired UserManager userMgr;

  @After
  public void teardown() throws Exception {
    getBeanOfClass(ConfigurableLogger.class).setLoggerDefault();
    super.tearDown();
  }

  @Before
  public void setup() throws Exception {
    super.setUp();
    sysAdmin = createAndSaveUser(CoreTestUtils.getRandomName(10), Constants.SYSADMIN_ROLE);
    initUser(sysAdmin);
    logoutAndLoginAs(sysAdmin);
    sysAdminPrincipal = new MockPrincipal(sysAdmin.getUsername());
  }

  @Test
  public void testCreateUserLdapAuthChoiceEnabled() throws Exception {
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initUsers(pi);
    Group grp = createGroupForUsers(sysAdmin, pi.getUsername(), "", pi);
    User user = TestFactory.createAnyUser(getRandomAlphabeticString("any"));
    user.setRole(Constants.USER_ROLE);
    MockHttpServletRequestBuilder builder = post("/system/ajax/createUserAccount");
    user.setEmail(getRandomEmail());
    addUserParams(builder, user)
        .principal(sysAdminPrincipal)
        .param("ldapAuthChoice", "true")
        .param("labGroupId", "" + grp.getId());
    // password can be blank if ldapAuth being used
    builder.param("password", "");
    builder.param("passwordConfirmation", "");
    MvcResult result = mockMvc.perform(builder).andReturn();
    assertSuccessResponse(result.getResponse().getContentAsString());
    User createdUser = userMgr.getUserByUsername(user.getUsername());
    assertEquals(SignupSource.LDAP, createdUser.getSignupSource());
  }

  @Test
  public void testUniqueUsernameEmailRequired() throws Exception {
    User user = createAndSaveUser(getRandomAlphabeticString("any"));
    initUser(user);
    user.setRole(Constants.USER_ROLE);
    user.setConfirmPassword(user.getPassword());
    MockHttpServletRequestBuilder builder = post("/system/ajax/createUserAccount");
    // now let' set email different but keep same uname:
    final String initialEmail = user.getEmail();
    user.setEmail(getRandomEmail());
    addUserParams(builder, user).principal(sysAdminPrincipal);
    MvcResult result = mockMvc.perform(builder).andReturn();
    assertDuplicateEmailOrUsername(result);

    // now let' set uname different but keep same email:
    user.setEmail(initialEmail);
    user.setUsername(getRandomAlphabeticString("any"));

    MockHttpServletRequestBuilder builder2 = post("/system/ajax/createUserAccount");
    addUserParams(builder2, user).principal(sysAdminPrincipal);
    MvcResult result2 = mockMvc.perform(builder2).andReturn();
    assertDuplicateEmailOrUsername(result2);
  }

  private void assertDuplicateEmailOrUsername(MvcResult result) throws IOException {
    Map response = parseJSONObjectFromResponseStream(result);
    assertNull(response.get("data"));
    assertTrue(result.getResponse().getContentAsString().contains(DUPLICATE_EMAILOR_UNAME_MSG));
  }

  private MockHttpServletRequestBuilder addUserParams(
      MockHttpServletRequestBuilder builder, User user) {
    builder
        .param("firstName", user.getFirstName())
        .param("lastName", user.getLastName())
        .param("username", user.getUsername())
        .param("email", user.getEmail())
        .param("role", user.getRole());
    if (user.getPassword() != null) {
      builder.param("password", user.getPassword());
    }
    if (user.getConfirmPassword() != null) {
      builder.param("passwordConfirmation", user.getConfirmPassword());
    }
    return builder;
  }

  @Test
  public void testListingSortByCommunityAdmin() throws Exception {
    // create new community with 2 lab groups
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User pi2 = createAndSaveUser(getRandomAlphabeticString("pi2"), Constants.PI_ROLE);
    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    User outsideUser = createAndSaveUser(getRandomAlphabeticString("other"));
    initUsers(true, pi, pi2, admin, outsideUser);
    setUpUserWithInitialisedContent(pi);
    logoutAndLoginAs(admin);
    Group g1 = createGroupForUsers(admin, pi.getUsername(), "", pi);
    Group g2 = createGroupForUsers(admin, pi2.getUsername(), "", pi2);
    Community comm1 = createAndSaveCommunity(admin, "newCommunity");
    communityMgr.addGroupToCommunity(g1.getId(), comm1.getId(), admin);
    communityMgr.addGroupToCommunity(g2.getId(), comm1.getId(), admin);
    // sanity check
    assertEquals(
        2, communityMgr.getCommunityWithAdminsAndGroups(comm1.getId()).getLabGroups().size());
    // basic default listing
    MockPrincipal adminPrincipal = new MockPrincipal(admin.getUsername());
    MvcResult result =
        this.mockMvc.perform(get("/system/ajax/jsonList").principal(adminPrincipal)).andReturn();
    UserInfoListDTO uui = getUserListFromMvcResult(result);
    assertEquals(2, uui.getTotalHits().intValue());
    // now lets order by fileUsage
    MvcResult result2 =
        this.mockMvc
            .perform(
                get("/system/ajax/jsonList?orderBy=fileUsage()&sortOrder=DESC")
                    .principal(adminPrincipal))
            .andReturn();
    UserInfoListDTO uui2 = getUserListFromMvcResult(result2);
    assertEquals(2, uui2.getResults().size());
    assertEquals(2, uui2.getTotalHits().intValue());

    // now lets order by recordcount
    MvcResult result3 =
        this.mockMvc
            .perform(
                get("/system/ajax/jsonList?orderBy=recordCount()&sortOrder=DESC")
                    .principal(adminPrincipal))
            .andReturn();
    UserInfoListDTO uui3 = getUserListFromMvcResult(result3);
    assertEquals(2, uui3.getResults().size());
    assertEquals(2, uui3.getTotalHits().intValue());

    // what happens if admin user is not assigned to community?
    // should be empty
    User newadmin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    initUser(newadmin);
    logoutAndLoginAs(newadmin);
    MvcResult result4 =
        this.mockMvc
            .perform(
                get("/system/ajax/jsonList?orderBy=recordCount()&sortOrder=DESC")
                    .principal(new MockPrincipal(newadmin.getUsername())))
            .andReturn();
    UserInfoListDTO uui4 = getUserListFromMvcResult(result4);
    assertEquals(0, uui4.getResults().size());
    assertEquals(0, uui4.getTotalHits().intValue());

    MvcResult result5 =
        this.mockMvc
            .perform(
                get("/system/ajax/jsonList?orderBy=fileUsage()&sortOrder=DESC")
                    .principal(new MockPrincipal(newadmin.getUsername())))
            .andReturn();
    UserInfoListDTO uui5 = getUserListFromMvcResult(result5);
    assertEquals(0, uui5.getResults().size());
    assertEquals(0, uui5.getTotalHits().intValue());
  }

  private UserInfoListDTO getUserListFromMvcResult(MvcResult result) throws Exception {
    UsageListingDTO responseDTO = getFromJsonResponseBody(result, UsageListingDTO.class);
    return responseDTO.getUserInfo();
  }

  @Test
  public void testListingSortByCreationDate() throws Exception {
    User u1 = createAndSaveUser(getRandomAlphabeticString("u1"));
    User u2 = createAndSaveUser(getRandomAlphabeticString("u2"));
    initUsers(u1, u2);
    u1 =
        MockAndStubUtils.modifyCreationDate(
            userMgr.get(u1.getId()),
            new Date(Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli()),
            User.class);
    u1 = userMgr.save(u1);

    User sysadmin = logoutAndLoginAsSysAdmin();
    mockMvc
        .perform(
            get("/system?orderBy=creationDate&sortOrder=DESC").principal(sysadmin::getUsername))
        .andReturn();
    mockMvc
        .perform(get("/system?orderBy=creationDate&sortOrder=ASC").principal(sysadmin::getUsername))
        .andReturn();
  }

  @Test
  public void testGetAllCommunities() throws Exception {
    openTransaction();
    final int communityCount =
        sessionFactory.getCurrentSession().createQuery("from Community").list().size();
    commitTransaction();

    MvcResult result =
        this.mockMvc
            .perform(
                get("/system/ajax/getAllCommunities").principal(new MockPrincipal(SYS_ADMIN_UNAME)))
            .andReturn();
    Map data = parseJSONObjectFromResponseStream(result);
    assertEquals(communityCount, ((List) data.get("data")).size());
  }

  @Test
  public void testListingSort() throws Exception {
    MvcResult result =
        this.mockMvc
            .perform(
                get("/system/ajax/jsonList?orderBy=fileUsage()&sortOrder=DESC")
                    .principal(sysAdminPrincipal))
            .andExpect(status().isOk())
            .andReturn();
    UserInfoListDTO uui = getUserListFromMvcResult(result);
    // no users with stuff in filesystem
    final int initialList = uui.getTotalHits().intValue();

    // now lets init a new user so they have records in FS
    User initializedUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithInitialisedContent(initializedUser);
    MvcResult result2 =
        this.mockMvc
            .perform(
                get("/system/ajax/jsonList?orderBy=fileUsage()&sortOrder=DESC")
                    .principal(sysAdminPrincipal))
            .andExpect(status().isOk())
            .andReturn();
    UserInfoListDTO uui2 = getUserListFromMvcResult(result2);
    assertTrue(uui2.getResults().stream().anyMatch(info -> info.getCreationDate() != null));
    // should be 1 person in FS now
    assertEquals(initialList + 1, uui2.getTotalHits().intValue());

    // now lets init a new user so they have records in FS
    User initializedUser2 = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithInitialisedContent(initializedUser2);
    MvcResult result3 =
        this.mockMvc
            .perform(
                get("/system/ajax/jsonList?orderBy=recordCount()&sortOrder=DESC")
                    .principal(sysAdminPrincipal))
            .andExpect(status().isOk())
            .andReturn();
    UserInfoListDTO uui3 = getUserListFromMvcResult(result3);
    // should be 3 person with records now
    assertTrue(uui3.getTotalHits().intValue() > 0);
  }

  @Test
  public void testListingSystem() throws Exception {
    this.mockMvc
        .perform(get("/system/").principal(sysAdminPrincipal))
        .andExpect(status().isOk())
        .andExpect(model().attributeExists("publish_allowed"))
        .andReturn();
  }

  @Test
  public void testRunAsOtherUserGet() throws Exception {
    this.mockMvc
        .perform(get("/system/ajax/runAs").principal(sysAdminPrincipal))
        .andExpect(status().isOk())
        .andExpect(model().attributeExists("runAsUserCommand"))
        .andReturn();
  }

  @Test
  public void testRunAsOtherUserAsCommunityAdmin() throws Exception {
    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initUser(pi);
    User notInCommunity = createAndSaveUser(getRandomAlphabeticString("other"));
    logoutAndLoginAs(admin);
    Group grp = createGroupForUsers(admin, pi.getUsername(), "", pi);

    Community comm = createAndSaveCommunity(admin, getRandomAlphabeticString("comm"));
    communityMgr.addGroupToCommunity(grp.getId(), comm.getId(), admin);
    Principal adminPrincipal = new MockPrincipal(admin.getUsername());
    servletContext.setAttribute(USERS_KEY, activeUsers);

    // can't run As other user
    this.mockMvc
        .perform(
            post("/system/ajax/runAs")
                .param("sysadminPassword", TESTPASSWD)
                .param("runAsUsername", createAutocompletStringFormat(notInCommunity))
                .principal(adminPrincipal))
        .andExpect(model().hasErrors());

    this.mockMvc
        .perform(
            post("/system/ajax/runAs")
                .param("sysadminPassword", TESTPASSWD)
                .param("runAsUsername", createAutocompletStringFormat(pi))
                .principal(adminPrincipal))
        .andExpect(model().hasNoErrors());
    // admin can run as pi in group
    assertEquals(SecurityUtils.getSubject().getPrincipal(), pi.getUsername());
    // now revert
    releaseRunAs(adminPrincipal);
  }

  @Test
  public void getLabGroupInfoForForCreateAccountIncludesGroupSize() throws Exception {
    logoutAndLoginAsSysAdmin();
    final int EXPECTED_GROUP_SIZE = 3;
    TestGroup grpGroup = createTestGroup(EXPECTED_GROUP_SIZE - 1); // size = 3 inc PI

    MvcResult result =
        this.mockMvc
            .perform(
                get("/system/ajax/getLabGroups")
                    .param("communityId", "-1")
                    .principal(sysAdminPrincipal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    String groupSizeLocatorFormatString =
        String.format(
            "$.data.[?(@.displayName == '%s')].groupSize", grpGroup.getGroup().getDisplayName());
    JSONArray actualGroupSize = (JSONArray) getJsonPathValue(result, groupSizeLocatorFormatString);
    assertEquals(EXPECTED_GROUP_SIZE, actualGroupSize.get(0));
  }

  @Test
  public void testRunAsOtherUserPost() throws Exception {
    User targetuser = createAndSaveUser(getRandomAlphabeticString("runAs"));
    initUser(targetuser);
    logoutAndLoginAsSysAdmin();
    servletContext.setAttribute(USERS_KEY, activeUsers);

    this.mockMvc
        .perform(
            post("/system/ajax/runAs")
                .param("sysadminPassword", "")
                .param("runAsUsername", "")
                .principal(sysAdminPrincipal))
        .andExpect(
            model()
                .attributeHasFieldErrors("runAsUserCommand", "runAsUsername", "sysadminPassword"));
    // wrong password
    this.mockMvc
        .perform(
            post("/system/ajax/runAs")
                .param("sysadminPassword", "WRONGPWD")
                .param("runAsUsername", createAutocompletStringFormat(targetuser))
                .principal(sysAdminPrincipal))
        .andExpect(model().attributeHasFieldErrors("runAsUserCommand", "sysadminPassword"));
    // unknown user
    this.mockMvc
        .perform(
            post("/system/ajax/runAs")
                .param("sysadminPassword", SYS_ADMIN_PWD)
                .param("runAsUsername", "unknownUser<user@x.com>")
                .principal(sysAdminPrincipal))
        .andExpect(model().attributeHasErrors("runAsUserCommand"));

    // simulate other user is logged in
    addLoggedInUserToActiveUsers(targetuser);
    this.mockMvc
        .perform(
            post("/system/ajax/runAs")
                .param("sysadminPassword", SYS_ADMIN_PWD)
                .param("runAsUsername", createAutocompletStringFormat(targetuser))
                .principal(sysAdminPrincipal))
        .andExpect(model().attributeHasErrors("runAsUserCommand"));
    activeUsers.forceRemoveUser(targetuser.getUsername());
    logoutAndLoginAsSysAdmin();
    // happy case
    getBeanOfClass(ConfigurableLogger.class).setLogger(log);
    MockHttpServletRequestBuilder operateAsPost =
        preparePostOperateAs(targetuser, sysAdminPrincipal);
    this.mockMvc
        .perform(operateAsPost)
        .andExpect(status().isOk())
        .andExpect(
            request()
                .sessionAttribute(
                    USER_INFO,
                    hasProperty(
                        "username", equalToIgnoringCase(targetuser.getUsername())))); // RSPAC-1018
    assertEquals(targetuser.getUsername(), SecurityUtils.getSubject().getPrincipal());

    verify(log, times(1))
        .info(
            Mockito.anyString(),
            Mockito.anyBoolean(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.contains("Hello"));

    // now create a record:
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(targetuser, "any");

    // should be marked that it's modified by sysadmin
    assertEquals(SYS_ADMIN_UNAME, sd.getModifiedBy());
    releaseRunAs(sysAdminPrincipal);

    User sysadmin = logoutAndLoginAsSysAdmin(); // need to do this to flush subject in test
    initUser(sysadmin);
    // and check that sysadmin is back as subject
    assertEquals(SecurityUtils.getSubject().getPrincipal(), SYS_ADMIN_UNAME);
    Mockito.reset(log);
    // happy incognito case
    operateAsPost = preparePostOperateAs(targetuser, sysAdminPrincipal);
    operateAsPost.param("incognito", "true");
    this.mockMvc.perform(operateAsPost).andExpect(status().isOk());
    assertEquals(SecurityUtils.getSubject().getPrincipal(), targetuser.getUsername());
    assertNoLogging(log);

    // now release runAs
    releaseRunAs(sysAdminPrincipal);
  }

  private MockHttpServletRequestBuilder preparePostOperateAs(
      User newuser, Principal sysAdminPrincipal) {
    return post("/system/ajax/runAs")
        .param("sysadminPassword", SYS_ADMIN_PWD)
        .param("runAsUsername", createAutocompletStringFormat(newuser))
        .principal(sysAdminPrincipal);
  }

  private void releaseRunAs(Principal principal) throws Exception {
    this.mockMvc.perform(get("/logout/runAsRelease").principal(principal)).andReturn();
  }

  private String createAutocompletStringFormat(User newuser) {
    return newuser.getUsername() + "<" + newuser.getEmail() + ">";
  }

  @Test
  public void testCreateNewCommunityGet() throws Exception {
    mockMvc
        .perform(get("/system/createCommunity").principal(sysAdminPrincipal))
        .andExpect(view().name(SysAdminController.SYSTEM_CREATE_COMMUNITY_VIEW))
        .andExpect(model().attributeExists("community"));
  }

  @Test
  public void testCreateNewCommunityPost() throws Exception {
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    initUsers(pi, admin);
    Group grp = createGroupForUsers(pi, pi.getUsername(), "", pi);
    String displayName = getRandomAlphabeticString("comm.");
    // happy path
    mockMvc
        .perform(
            post("/system/createCommunity")
                .principal(sysAdminPrincipal)
                .param(Group.DEFAULT_ORDERBY_FIELD, displayName)
                .param("adminIds", admin.getId() + "")
                .param("groupIds", grp.getId() + ""))
        .andExpect(view().name(SysAdminController.REDIRECT_SYSTEM_COMMUNITY_LIST))
        .andReturn();
    Community community = getCommunityByDisplayName(displayName);
    assertNotNull(community.getUniqueName());
    assertEquals(grp, community.getLabGroups().iterator().next());
    assertEquals(admin, community.getAdmins().iterator().next());

    // test validation -see RSPAC69 - missing display name
    mockMvc
        .perform(
            post("/system/createCommunity")
                .principal(sysAdminPrincipal)
                .param("adminIds", admin.getId() + "")
                .param("groupIds", grp.getId() + ""))
        .andExpect(view().name(SysAdminController.SYSTEM_CREATE_COMMUNITY_VIEW))
        .andExpect(model().hasErrors()) // missing name
        .andReturn();
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
  public void createDeleteUserAccountUsingSysAdmin() throws Exception {
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initUsers(pi);
    Group grp = createGroupForUsers(sysAdmin, pi.getUsername(), "", pi);
    User userToCreate = TestFactory.createAnyUser(getRandomAlphabeticString("user"));
    userToCreate.setRole(Constants.USER_ROLE);

    MockHttpServletRequestBuilder builder = post("/system/ajax/createUserAccount");
    addUserParams(builder, userToCreate)
        .param("labGroupId", grp.getId().toString())
        .principal(sysAdminPrincipal);

    MvcResult result = mockMvc.perform(builder).andExpect(status().isOk()).andReturn();
    String resultString = result.getResponse().getContentAsString();
    assertSuccessResponse(resultString);

    User createdUser = userMgr.getUserByUsername(userToCreate.getUsername());
    EcatImage image = addImageToGallery(createdUser);
    File imageFile = new File(new URI(image.getFileUri()));
    assertTrue(imageFile.exists());

    MockHttpServletRequestBuilder removeBuilder =
        post("/system/ajax/removeUserAccount")
            .param("userId", createdUser.getId().toString())
            .principal(sysAdminPrincipal);

    MvcResult removeResult = mockMvc.perform(removeBuilder).andExpect(status().isOk()).andReturn();
    String removeResultString = removeResult.getResponse().getContentAsString();
    assertTrue(
        "expected remove success: " + removeResultString,
        removeResultString.contains("] deleted\""));
    assertFalse(imageFile.exists());
  }

  private String getRandomEmail() {
    return getRandomAlphabeticString("email") + "@email.com";
  }

  @Test
  public void testCreateNewUserAccountUsingRSpaceAdmin() throws Exception {

    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    initUsers(pi, admin);
    logoutAndLoginAs(admin);

    Group grp = createGroupForUsers(admin, pi.getUsername(), "", pi);
    String password = RandomStringUtils.random(8, true, true);
    String newusername = RandomStringUtils.random(8, true, true);

    MvcResult result =
        mockMvc
            .perform(buildOKUserRequest(admin, newusername, password, grp))
            .andExpect(status().isOk())
            .andReturn();
    assertTrue(result.getResponse().getContentAsString().contains("Success"));

    String resultString = result.getResponse().getContentAsString();
    assertSuccessResponse(resultString);
    logoutAndLoginAs(pi);
    doInTransaction(
        () -> {
          Folder grpFolder = folderDao.getLabGroupFolderForUser(pi);
          assertTrue(recordExistsInFolder(newusername, grpFolder));
        });

    // now try with no license
    // now try when license rejects the attempt
    try {
      userEnablementUtils.setLicenseService(new InactiveLicenseTestService());
      result = mockMvc.perform(buildOKUserRequest(admin, newusername, password, grp)).andReturn();
      assertTrue(result.getResolvedException() instanceof LicenseExceededException);
    } finally {
      // tidy up for next test
      userEnablementUtils.setLicenseService(new NoCheckLicenseService());
    }
  }

  private ResultMatcher exceptionThrown() {
    return model().attributeExists(ControllerExceptionHandler.EXCEPTION_MESSAGE_ATTR_NAME);
  }

  RequestBuilder buildOKUserRequest(User admin, String newusername, String password, Group grp) {
    return post("/system/ajax/createUserAccount")
        .principal(new MockPrincipal(admin.getUsername()))
        .param("firstName", "firstNameUser")
        .param("lastName", "lastNameUser")
        .param("username", newusername)
        .param("email", getRandomEmail())
        .param("password", password)
        .param("passwordConfirmation", password)
        .param("role", Constants.USER_ROLE)
        .param("labGroupId", grp.getId().toString());
  }

  private boolean recordExistsInFolder(String newusername, Folder grpFolder) {
    for (BaseRecord child : grpFolder.getChildrens()) {
      if (child.getName().equals(newusername)) {
        return true;
      }
    }
    return false;
  }

  @Test
  public void createNewPIAccountUsingSysAdmin() throws Exception {
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initUsers(pi);

    Group grp = createGroupForUsers(sysAdmin, pi.getUsername(), "", pi);
    Community comm = createAndSaveCommunity(sysAdmin, getRandomAlphabeticString("comm"));
    communityMgr.addGroupToCommunity(grp.getId(), comm.getId(), sysAdmin);

    User piToCreate = TestFactory.createAnyUser(getRandomAlphabeticString("pi"));
    piToCreate.setRole(Constants.PI_ROLE);
    MockHttpServletRequestBuilder builder = post("/system/ajax/createUserAccount");
    addUserParams(builder, piToCreate)
        .param("newLabGroupName", "NewLabGroupName")
        .param("communityId", comm.getId().toString())
        .principal(sysAdminPrincipal);
    MvcResult result = mockMvc.perform(builder).andExpect(status().isOk()).andReturn();

    String resultString = result.getResponse().getContentAsString();
    assertSuccessResponse(resultString);
    pi = userMgr.getUserByUsername(pi.getUsername()); // reload pi
    assertTrue(pi.hasRole(Role.PI_ROLE));
    // can do normal user things as well
    assertTrue(pi.hasRole(Role.USER_ROLE));
  }

  @Test
  public void createNewPIAccountUsingRSpaceAdmin() throws Exception {
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    initUsers(pi, admin);
    logoutAndLoginAs(admin);

    Group grp = createGroupForUsers(admin, pi.getUsername(), admin.getUsername(), pi, admin);
    Community comm = createAndSaveCommunity(admin, getRandomAlphabeticString("comm"));
    communityMgr.addGroupToCommunity(grp.getId(), comm.getId(), admin);

    User piToCreate = TestFactory.createAnyUser(getRandomAlphabeticString("pi"));
    piToCreate.setRole(Constants.PI_ROLE);
    MockHttpServletRequestBuilder builder = post("/system/ajax/createUserAccount");
    addUserParams(builder, piToCreate)
        .param("newLabGroupName", "NewLabGroupName")
        .param("communityId", comm.getId().toString())
        .principal(new MockPrincipal(admin.getUsername()));

    MvcResult result = mockMvc.perform(builder).andExpect(status().isOk()).andReturn();

    String resultString = result.getResponse().getContentAsString();
    assertSuccessResponse(resultString);
  }

  @Test
  public void createNewRSpaceAdminAccountUsingSysAdmin() throws Exception {
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initUsers(pi);

    Group grp = createGroupForUsers(sysAdmin, pi.getUsername(), "", pi);
    Community comm = createAndSaveCommunity(sysAdmin, getRandomAlphabeticString("comm"));
    communityMgr.addGroupToCommunity(grp.getId(), comm.getId(), sysAdmin);

    User adminToCreate = TestFactory.createAnyUser(getRandomAlphabeticString("admin"));
    adminToCreate.setRole(Constants.ADMIN_ROLE);
    MockHttpServletRequestBuilder builder = post("/system/ajax/createUserAccount");
    addUserParams(builder, adminToCreate)
        .param("communityId", comm.getId().toString())
        .principal(sysAdminPrincipal);
    MvcResult result = mockMvc.perform(builder).andExpect(status().isOk()).andReturn();

    String resultString = result.getResponse().getContentAsString();
    assertSuccessResponse(resultString);
  }

  @Test
  public void createNewRSpaceAdminAccountUsingRSpaceAdmin() throws Exception {
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User adminSubject = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    initUsers(pi, adminSubject);
    logoutAndLoginAs(adminSubject);

    Group grp = createGroupForUsers(adminSubject, pi.getUsername(), "", pi);
    Community comm = createAndSaveCommunity(adminSubject, getRandomAlphabeticString("comm"));
    communityMgr.addGroupToCommunity(grp.getId(), comm.getId(), adminSubject);

    User adminToCreate = TestFactory.createAnyUser(getRandomAlphabeticString("admin"));
    adminToCreate.setRole(Constants.ADMIN_ROLE);
    MockHttpServletRequestBuilder builder = post("/system/ajax/createUserAccount");
    addUserParams(builder, adminToCreate)
        .param("communityId", comm.getId().toString())
        .principal(new MockPrincipal(adminSubject.getUsername()));
    MvcResult result = mockMvc.perform(builder).andExpect(status().isOk()).andReturn();

    String resultString = result.getResponse().getContentAsString();
    assertSuccessResponse(resultString);
  }

  @Test
  public void createNewSystemAdminAccountUsingSysAdmin() throws Exception {
    User toCreate = TestFactory.createAnyUser(getRandomAlphabeticString("sysadmin"));
    toCreate.setRole(Constants.SYSADMIN_ROLE);
    MockHttpServletRequestBuilder builder = post("/system/ajax/createUserAccount");
    addUserParams(builder, toCreate).principal(sysAdminPrincipal);
    MvcResult result = mockMvc.perform(builder).andReturn();

    String resultString = result.getResponse().getContentAsString();
    assertSuccessResponse(resultString);
  }

  private void assertSuccessResponse(String resultString) {
    assertTrue("Result string was " + resultString, resultString.contains("Success"));
  }

  @Test
  public void createNewUserAccountForSSO() throws Exception {
    assertEquals("true", controller.properties.getStandalone());

    User ssoUser = TestFactory.createAnyUser(getRandomAlphabeticString("ssoUser"));
    ssoUser.setRole(Constants.USER_ROLE);
    ssoUser.setPassword(null);
    ssoUser.setConfirmPassword(null);

    MockHttpServletRequestBuilder builder = post("/system/ajax/createUserAccount");
    addUserParams(builder, ssoUser).principal(sysAdminPrincipal);

    propertyHolder.setStandalone("false");
    MvcResult result = mockMvc.perform(builder).andReturn();
    propertyHolder.setStandalone("true");

    String resultString = result.getResponse().getContentAsString();
    assertSuccessResponse(resultString);

    /* documenting current behaviour - signup source is 'manual'
     * for users created in sso deployment */
    User createdSsoUser = userMgr.getUserByUsername(ssoUser.getUsername());
    assertEquals(SignupSource.MANUAL, createdSsoUser.getSignupSource());

    // default sso password should be set by user creation process
    logoutAndLoginAs(ssoUser, RemoteUserRetrievalPolicy.SSO_DUMMY_PASSWORD);
  }

  @Test
  public void createNewInternalSysamindWithSSO() throws Exception {
    assertEquals("true", controller.properties.getStandalone());

    // create sysadmin with own password
    User nonSsoSysAdmin = TestFactory.createAnyUser(getRandomAlphabeticString("internalSysadmin"));
    nonSsoSysAdmin.setRole(Constants.SYSADMIN_ROLE);
    final String NON_SSO_SYSADMIN_PASSWORD = "testSysAdminPass";
    nonSsoSysAdmin.setPassword(NON_SSO_SYSADMIN_PASSWORD);
    nonSsoSysAdmin.setConfirmPassword(NON_SSO_SYSADMIN_PASSWORD);

    // create account with "ssoBackdoorAccount" parameter passed along password
    MockHttpServletRequestBuilder builder = post("/system/ajax/createUserAccount");
    builder.param("ssoBackdoorAccount", "true");
    addUserParams(builder, nonSsoSysAdmin).principal(sysAdminPrincipal);
    MvcResult result = mockMvc.perform(builder).andReturn();

    String resultString = result.getResponse().getContentAsString();
    assertSuccessResponse(resultString);

    // assert signup source is 'internal'
    User internalSysAdmin = userMgr.getUserByUsername(nonSsoSysAdmin.getUsername());
    assertEquals(SignupSource.SSO_BACKDOOR, internalSysAdmin.getSignupSource());

    // default sso password shouldn't work
    AuthenticationException ae =
        assertThrows(
            AuthenticationException.class,
            () -> logoutAndLoginAs(nonSsoSysAdmin, RemoteUserRetrievalPolicy.SSO_DUMMY_PASSWORD));
    assertTrue(ae.getMessage().contains("could not be authenticated"));

    // provided non-sso password should be the one to use
    logoutAndLoginAs(nonSsoSysAdmin, NON_SSO_SYSADMIN_PASSWORD);
  }

  @Test
  public void createNewSystemAdminAccountUsingRSpaceAdminNotAllowed() throws Exception {
    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    logoutAndLoginAs(admin);
    User toCreate = TestFactory.createAnyUser(getRandomAlphabeticString("sysadmin"));
    toCreate.setRole(Constants.SYSADMIN_ROLE);
    MockHttpServletRequestBuilder builder = post("/system/ajax/createUserAccount");
    addUserParams(builder, toCreate).principal(new MockPrincipal(admin.getUsername()));
    MvcResult result = mockMvc.perform(builder).andReturn();
    assertAuthorizationExceptionThrown(result);
  }

  @Test
  public void unlockUserAccount() throws Exception {
    // setup to simulate account lockout
    User user = createAndSaveUser(getRandomAlphabeticString("user"), Constants.USER_ROLE);
    user.setAccountLocked(true);
    user.setLoginFailure(new Date());
    userMgr.save(user);

    MvcResult result =
        mockMvc
            .perform(
                post("/system/ajax/unlockAccount")
                    .principal(sysAdminPrincipal)
                    .param("userId", user.getId() + ""))
            .andExpect(status().isOk())
            .andReturn();

    // assert user account unlocked
    User unlocked = userMgr.get(user.getId());
    assertTrue(unlocked.isAccountNonLocked());
    assertNull(unlocked.getLoginFailure());
  }

  @Test
  public void unlockUserAccountFailsForNonSysadmin() throws Exception {
    // setup to simulate account lockout
    User anyUser = createAndSaveUser(getRandomAlphabeticString("user"), Constants.USER_ROLE);
    User otherUser = createAndSaveUser(getRandomAlphabeticString("other"), Constants.USER_ROLE);

    anyUser.setAccountLocked(true);
    anyUser.setLoginFailure(new Date());
    userMgr.save(anyUser);
    logoutAndLoginAs(otherUser);

    MvcResult result =
        mockMvc
            .perform(
                post("/system/ajax/unlockAccount")
                    .principal(otherUser::getUsername)
                    .param("userId", anyUser.getId() + ""))
            .andReturn();
    assertException(result, AuthorizationException.class);
  }

  @Test
  public void disableUserAccount() throws Exception {
    User user = createAndSaveUser(getRandomAlphabeticString("user"), Constants.USER_ROLE);
    MvcResult result =
        mockMvc
            .perform(
                post("/system/ajax/setAccountEnablement")
                    .principal(sysAdminPrincipal)
                    .param("enabled", "false")
                    .param("userId", user.getId() + ""))
            .andExpect(status().isOk())
            .andReturn();

    User updated = userMgr.get(user.getId());
    assertFalse(updated.isEnabled());
    assertNull(result.getResolvedException());

    // now enable again
    MvcResult result2 =
        mockMvc
            .perform(
                post("/system/ajax/setAccountEnablement")
                    .principal(sysAdminPrincipal)
                    .param("enabled", "true")
                    .param("userId", user.getId() + ""))
            .andExpect(status().isOk())
            .andReturn();

    User updated2 = userMgr.get(user.getId());
    assertTrue(updated2.isEnabled());
    assertNull(result2.getResolvedException());
  }

  @Test
  public void disableUserAccountCommunityAdmin() throws Exception {
    User commAdmin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    TestGroup testGroup1 = createTestGroup(2);
    TestGroup testGroup2NotInCommunity = createTestGroup(2);
    Community comm = createAndSaveCommunity(commAdmin, getRandomAlphabeticString("comm"));
    addGroupToCommunity(testGroup1.getGroup(), comm, commAdmin);
    logoutAndLoginAs(commAdmin);
    Principal adminPrincipal = new MockPrincipal(commAdmin.getUsername());
    mockMvc
        .perform(
            post("/system/ajax/setAccountEnablement")
                .principal(adminPrincipal)
                .param("enabled", "true")
                .param("userId", testGroup1.getUserByPrefix("u1").getId() + ""))
        .andExpect(status().isOk());
    MvcResult failed =
        mockMvc
            .perform(
                post("/system/ajax/setAccountEnablement")
                    .principal(adminPrincipal)
                    .param("enabled", "true")
                    .param("userId", testGroup2NotInCommunity.getUserByPrefix("u1").getId() + ""))
            .andExpect(exceptionThrown())
            .andReturn();
    assertAuthorizationException(failed);
  }

  @Test
  public void createNewUserAccountInvalidPermissions() throws Exception {
    User user = createAndSaveUser(getRandomAlphabeticString("user"), Constants.USER_ROLE);
    logoutAndLoginAs(user);
    initUsers(user);
    User userToCreate = TestFactory.createAnyUser(getRandomAlphabeticString("user"));
    userToCreate.setRole(Constants.USER_ROLE);

    MockHttpServletRequestBuilder builder = post("/system/ajax/createUserAccount");
    addUserParams(builder, userToCreate).principal(new MockPrincipal(user.getUsername()));

    MvcResult result = mockMvc.perform(builder).andExpect(status().isOk()).andReturn();
    assertAuthorizationExceptionThrown(result);
  }

  private void assertAuthorizationExceptionThrown(MvcResult result) {
    assertNotNull(result.getResolvedException());
    assertTrue(result.getResolvedException() instanceof AuthorizationException);
  }

  @Test
  public void testGeneratingRandomPassword() throws Exception {
    MvcResult result1 =
        mockMvc
            .perform(get("/system/ajax/generateRandomPassword").principal(sysAdminPrincipal))
            .andExpect(status().isOk())
            .andReturn();

    MvcResult result2 =
        mockMvc
            .perform(get("/system/ajax/generateRandomPassword").principal(sysAdminPrincipal))
            .andExpect(status().isOk())
            .andReturn();
    String password1 = new JSONObject(result1.getResponse().getContentAsString()).getString("data");
    String password2 = new JSONObject(result2.getResponse().getContentAsString()).getString("data");
    assertNotSame(password1, password2);
  }

  @Test
  public void getCommunityById() throws Exception {
    User commAdmin = createAndSaveUser(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
    Community comm = createAndSaveCommunity(commAdmin, getRandomAlphabeticString("comm"));
    Principal mockPrincipal = new MockPrincipal(commAdmin.getUsername());
    mockMvc
        .perform(get("/system/community/{id}", comm.getId() + "").principal(mockPrincipal))
        .andExpect(model().attributeExists("canEdit", "view", "community"))
        .andReturn();
  }

  @Test
  public void getUserListingModelAndJson() throws Exception {
    MvcResult result =
        this.mockMvc
            .perform(get("/system/ajax/jsonList").principal(sysAdminPrincipal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());
    Map data = parseJSONObjectFromResponseStream(result);
    assertEquals(4, data.size());
    assertNotNull(data.get("userStats"));
    assertNotNull(data.get("pgCrit"));
    assertNotNull(data.get("userInfo"));
    assertNotNull(data.get("pagination"));

    UsageListingDTO responseDTO = getFromJsonResponseBody(result, UsageListingDTO.class);
    assertTrue(
        responseDTO
            .getUserStats()
            .getAvailableSeats()
            .startsWith(
                "214748")); // integer.MAX_VALUE minus number of created users, which could vary
    assertEquals(Integer.valueOf(10), responseDTO.getPgCrit().getResultsPerPage());
    assertEquals(10, responseDTO.getUserInfo().getResults().size());
  }
}
