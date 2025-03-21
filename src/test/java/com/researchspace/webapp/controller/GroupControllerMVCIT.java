package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.testutils.TestGroup.LABADMIN_PREFIX;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.fasterxml.jackson.core.type.TypeReference;
import com.researchspace.Constants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.GroupDao;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.dtos.UserRoleView;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.testutils.TestGroup;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.Permission;
import org.hamcrest.Matchers;
import org.hibernate.HibernateException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@WebAppConfiguration
public class GroupControllerMVCIT extends MVCTestBase {

  private static final String CHANGE_ROLE_URL = "/groups/ajax/admin/changeRole/{grpId}/{username}";

  private @Autowired GroupDao grpDao;

  @Autowired GroupController groupController;
  @Autowired SystemPropertyManager sysPropMgr;

  @Autowired
  public void setCtrller(GroupController ctrller) {
    this.groupController = ctrller;
  }

  StructuredDocument docToShare;

  User other;
  Group grp;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    docToShare = null;
    other = null;
    grp = null;
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  String NEW_GROUP_PAGE = "/groups/admin/";

  private final String ADMIN_UNAME = "admin";

  @Test
  public void removeGrpServiceTest() throws Exception {

    // create and save a user
    User pi1 = createAndSaveUser("pi1", Constants.PI_ROLE);
    initUser(pi1);
    Group g1 = createGroupForUsers(pi1, pi1.getUsername(), "", pi1);

    logoutAndLoginAs(pi1);
    assertEquals(1, grpMgr.getGroupEventsForGroup(pi1, g1).size()); // pi added
    User toAdd = createAndSaveUser(getRandomAlphabeticString("other"));
    initUser(toAdd);

    grpMgr.addUserToGroup(pi1.getUsername(), g1.getId(), RoleInGroup.DEFAULT);

    User sysadminUser = logoutAndLoginAsSysAdmin();
    // now we'll add a new user..
    mockMvc
        .perform(
            post("/groups/admin/addUser")
                .principal(sysadminUser::getUsername)
                .param("id", g1.getId() + "")
                .param("memberString", toAdd.getUsername()))
        .andReturn();
    assertEquals(2, grpMgr.getGroupEventsForGroup(pi1, g1).size()); // toAdd added
    // make sure group can be deleted.
    grpMgr.removeGroup(g1.getId(), pi1);
    assertEquals(0, grpMgr.getGroupEventsForGroup(pi1, g1).size()); // no rows after deletion
    assertExceptionThrown(() -> grpMgr.getGroup(g1.getId()), ObjectRetrievalFailureException.class);
  }

  @Test
  public void getAllUsers() throws Exception {
    long expected = getCountOfEntityTable("User");
    User adminUser = logoutAndLoginAsSysAdmin();
    MvcResult result =
        mockMvc
            .perform(get("/groups/admin/ajax/allUsers").principal(adminUser::getUsername))
            .andReturn();
    List<UserRoleView> resultsList =
        mvcUtils.getFromJsonResponseBodyByTypeRef(
            result, new TypeReference<List<UserRoleView>>() {});
    // The anonymous user does not get returned by the getAllUsers endpoint
    assertEquals(expected - 1, resultsList.size());
  }

  @Test
  public void editProfile() throws Exception {
    // create and save a user
    User pi1 = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initUser(pi1);
    Group g1 = createGroupForUsers(pi1, pi1.getUsername(), "", pi1);
    logoutAndLoginAs(pi1);
    // happy case
    String NEW_PROFILE_TEXT = "new profilex";
    MvcResult result = postNewProfile(pi1, g1, NEW_PROFILE_TEXT);
    Map data = parseJSONObjectFromResponseStream(result);
    assertTrue(data.containsKey("data"));
    // assert profile is updated:
    g1 = grpMgr.getGroup(g1.getId());
    assertEquals(NEW_PROFILE_TEXT, g1.getProfileText());

    // now assert someone else can't edit this profile:
    logoutAndLoginAs(piUser);
    MvcResult result3 = postNewProfile(piUser, g1, "hacked");
    g1 = grpMgr.getGroup(g1.getId());
    // unaffected, has not been hacked
    assertEquals(NEW_PROFILE_TEXT, g1.getProfileText());

    logoutAndLoginAs(pi1);
    // no text, rejected
    MvcResult result2 = postNewProfile(pi1, g1, "");
    Map data3 = parseJSONObjectFromResponseStream(result2);
    assertTrue(data3.containsKey("errorMsg"));
  }

  private MvcResult postNewProfile(User user, Group g1, String profile) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/groups/editProfile/{groupId}", g1.getId())
                    .principal(user::getUsername)
                    .param("newProfile", profile))
            .andReturn();
    return result;
  }

  @Test
  public void newGroupRequiresPermissions() throws Exception {
    // sysadmin is authorised to create a new group
    logoutAndLoginAsSysAdmin();

    MvcResult result =
        mockMvc
            .perform(get(NEW_GROUP_PAGE).param("new", "").principal(() -> SYS_ADMIN_UNAME))
            .andExpect(model().attributeHasNoErrors())
            .andExpect(view().name(GroupController.EDIT_GROUP_VIEW_NAME))
            .andReturn();

    // but regular user isn't and will throw Authorisation exception
    User user = createAndSaveUser(getRandomName(10));
    logoutAndLoginAs(user);
    MvcResult result2 =
        mockMvc
            .perform(get(NEW_GROUP_PAGE).param("new", "").principal(user::getUsername))
            .andExpect(modelHasError())
            .andReturn();
  }

  @Test
  public void saveNewGroupRequiresPermissions() throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    User piOfNewGroup = createAndSaveUser("pi" + getRandomName(8), Constants.PI_ROLE);
    User user = createAndSaveUser("member" + getRandomName(8));
    initUsers(piOfNewGroup, user);
    final String NEW_GROUP_PAGE = "/groups/admin/";
    Group g = createNewGroupViaURL(NEW_GROUP_PAGE);

    final String GROUP_DISPLAY_NAME = "groupnameXXX";
    final String GROUP_DISPLAY_NAME2 = "groupnameXXX2";
    final int initialGrpCount = getGroupCount();
    final int initialGroupEvents = 0;
    MvcResult result = postSuccessfully(piOfNewGroup, user, GROUP_DISPLAY_NAME, SYS_ADMIN_UNAME);
    Group savedGroup = getGroupByName(GROUP_DISPLAY_NAME);
    assertEquals(initialGrpCount + 1, getGroupCount());
    assertEquals(
        initialGroupEvents + 2, grpMgr.getGroupEventsForGroup(sysadmin, savedGroup).size());

    User admin = createAndSaveUser(getRandomAlphabeticString("admin"), Role.ADMIN_ROLE.getName());
    User otherAdmin =
        createAndSaveUser(getRandomAlphabeticString("admin"), Role.ADMIN_ROLE.getName());
    // admin must be attached to community to acquire group:Create behaviour
    Community comm = createAndSaveCommunity(admin, getRandomAlphabeticString("Comm"));
    initUsers(admin, otherAdmin);
    // we need to add 2 CAs here so we can remove 1 later on in the test
    communityMgr.addAdminsToCommunity(new Long[] {otherAdmin.getId()}, comm.getId());
    logoutAndLoginAs(admin);
    // rspac-1216
    postSuccessfully(piOfNewGroup, user, GROUP_DISPLAY_NAME2, admin.getUsername());
    assertEquals(initialGrpCount + 2, getGroupCount());

    // now remove admin from community:
    communityMgr.removeAdminFromCommunity(admin.getId(), comm.getId());
    mockMvc
        .perform(
            postCreateNewGroup(
                piOfNewGroup, user, getRandomAlphabeticString("grp"), user.getUsername()))
        .andExpect(modelHasError());

    Group g2 = createNewGroupViaURL(NEW_GROUP_PAGE);
    logoutAndLoginAs(user);
    // now try to edit its membership as a user
    mockMvc
        .perform(postCreateNewGroup(piOfNewGroup, user, GROUP_DISPLAY_NAME, user.getUsername()))
        .andExpect(modelHasError());
    assertEquals(initialGrpCount + 2, getGroupCount());

    Group toDelete = getGroupByName(GROUP_DISPLAY_NAME);
    // now let's try to delete a group; can't do it as a user
    mockMvc
        .perform(
            post("/groups/admin/removeGroup/{id}", toDelete.getId()).principal(user::getUsername))
        .andExpect(modelHasError());
    assertEquals(initialGrpCount + 2, getGroupCount());

    // sysadmin or admin can delete a group
    logoutAndLoginAsSysAdmin();

    mockMvc
        .perform(
            post("/groups/admin/removeGroup/{id}", toDelete.getId())
                .principal(() -> SYS_ADMIN_UNAME))
        .andExpect(model().hasNoErrors());
    assertEquals(initialGrpCount + 1, getGroupCount());
  }

  private int getGroupCount() throws Exception {
    return doInTransaction(
        () -> {
          return grpDao.getAll().size();
        });
  }

  private ResultMatcher modelHasError() {
    return model().attributeExists(ControllerExceptionHandler.EXCEPTION_MESSAGE_ATTR_NAME);
  }

  private MvcResult postSuccessfully(
      User piOfNewGroup, User user, final String groupDisplayName, String principalName)
      throws Exception {
    return mockMvc
        .perform(postCreateNewGroup(piOfNewGroup, user, groupDisplayName, principalName))
        .andExpect(model().hasNoErrors())
        .andExpect(view().name(containsString("redirect:/groups/view/")))
        .andReturn();
  }

  private MockHttpServletRequestBuilder postCreateNewGroup(
      User piOfNewGroup, User user, final String groupDisplayName, String principalName) {
    return post(NEW_GROUP_PAGE)
        .param(Group.DEFAULT_ORDERBY_FIELD, groupDisplayName)
        .param("pis", piOfNewGroup.getUsername())
        .param("memberString", piOfNewGroup.getUsername(), user.getUsername())
        .principal(() -> principalName);
  }

  private Group getGroupByName(String string) throws HibernateException, Exception {
    return doInTransaction(
        () -> {
          return (Group)
              sessionFactory
                  .getCurrentSession()
                  .createQuery("from Group where displayName=:name")
                  .setString("name", string)
                  .uniqueResult();
        });
  }

  private Group createNewGroupViaURL(String NEW_GROUP_PAGE) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get(NEW_GROUP_PAGE).param("new", "").principal(() -> ADMIN_UNAME))
            .andReturn();
    Group grp = (Group) result.getModelAndView().getModel().get("group");
    return grp;
  }

  @Test
  public void addNewUserViaRequest() throws Exception {

    GroupSetUp setup = setUpDocumentGroupForPIUserAndShareRecord();
    User newUser = createAndSaveUser(getRandomName(5) + "newuser");
    initUser(newUser);

    // check empty user is rejected
    MvcResult emptyUserResult =
        mockMvc
            .perform(
                post("/groups/admin/addUser")
                    .principal(mockPrincipal)
                    .param("id", setup.group.getId() + ""))
            .andReturn();
    assertEquals(
        "The request couldn't be processed (members string empty)",
        emptyUserResult.getResponse().getContentAsString());

    // Happy case
    mockMvc
        .perform(
            post("/groups/admin/addUser")
                .principal(mockPrincipal)
                .param("id", setup.group.getId() + "")
                .param("memberString", newUser.getUsername()))
        .andReturn();
    logoutAndLoginAs(newUser);
    ISearchResults<MessageOrRequest> newrequests =
        communicationMgr.getActiveMessagesAndRequestsForUserTarget(
            newUser.getUsername(),
            PaginationCriteria.createDefaultForClass(CommunicationTarget.class));
    assertEquals(1, newrequests.getTotalHits().intValue());
    MessageOrRequest mor = newrequests.getResults().get(0);

    // Simulate acceptance of request by newuser
    reqUpdateMgr.updateStatus(
        newUser.getUsername(), CommunicationStatus.COMPLETED, mor.getId(), "Added");
    doInTransaction(
        () -> {
          // now check that grp folder is added to newusers LabGroups folder
          Folder grpFolder = folderDao.getLabGroupFolderForUser(newUser);
          assertEquals(1, grpFolder.getChildrens().size());
          Folder piRoot = folderDao.getLabGroupFolderForUser(piUser);
          assertEquals(2, piRoot.getChildrens().size()); // shared + newUser's home folders
        });
  }

  @Test
  public void sysadminDirectAddUsersToGroup() throws Exception {
    // this adds 2 users
    GroupSetUp setup = setUpDocumentGroupForPIUserAndShareRecord();
    assertEquals(2, grpMgr.getGroupEventsForGroup(null, setup.group).size());

    // let's add two users at once
    User newUser = createAndSaveUser(getRandomName(5) + "newuser");
    User newUser2 = createAndSaveUser(getRandomName(5) + "newuser2");
    initUsers(newUser, newUser2);

    // add 2 users directly, without invitation flow
    User sysadminUser = logoutAndLoginAsSysAdmin();
    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("memberString", newUser.getUsername());
    params.add("memberString", newUser2.getUsername());
    mockMvc
        .perform(
            post("/groups/admin/addUser")
                .principal(() -> SYS_ADMIN_UNAME)
                .param("id", setup.group.getId() + "")
                .params(params))
        .andExpect(status().isOk())
        .andReturn();
    // now query event log either through manager...
    final int expectedGroupEventCount = 4;
    assertEquals(
        expectedGroupEventCount, grpMgr.getGroupEventsForGroup(sysadminUser, setup.group).size());
    // or via GET
    MvcResult result =
        mockMvc
            .perform(
                get("/groups/ajax/membershipEventsByGroup/{groupId}", setup.group.getId())
                    .principal(() -> SYS_ADMIN_UNAME))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertEquals(expectedGroupEventCount, getJsonPathValue(result, "$.data.length()"));

    MvcResult resultByUser =
        mockMvc
            .perform(
                get("/groups/ajax/membershipEventsByUser/{userId}", newUser.getId())
                    .principal(() -> SYS_ADMIN_UNAME))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertEquals(1, getJsonPathValue(resultByUser, "$.data.length()"));

    // verify that users are added to group and shared folders are created
    doInTransaction(
        () -> {
          Group updatedGrp = grpDao.get(setup.group.getId());
          assertTrue("user should be in group", updatedGrp.getMembers().contains(newUser));
          assertTrue("user should be in group", updatedGrp.getMembers().contains(newUser2));
          Folder grpFolderU1 = folderDao.getLabGroupFolderForUser(newUser);
          assertEquals(
              "labgroup shared folder should be present", 1, grpFolderU1.getChildrens().size());
          Folder grpFolderU2 = folderDao.getLabGroupFolderForUser(newUser2);
          assertEquals(
              "labgroup shared folder should be present", 1, grpFolderU2.getChildrens().size());
        });
  }

  @Test
  public void viewGroupLoadsCommunity() throws Exception {
    GroupSetUp setup = setUpDocumentGroupForPIUserAndShareRecord();
    logoutAndLoginAs(piUser);
    MvcResult res =
        mockMvc
            .perform(get("/groups/view/{id}", setup.group.getId()).principal(piUser::getUsername))
            .andReturn();
    Group group = (Group) res.getModelAndView().getModelMap().get("group");
    // no lazy loading exception
    assertNotNull(group.getCommunity().getDisplayName());

    // now load an unknoown group, check exception thrown with message
    MvcResult res2 =
        mockMvc.perform(get("/groups/view/{id}", "-2").principal(piUser::getUsername)).andReturn();
    assertTrue(res2.getResolvedException() instanceof IllegalStateException);
  }

  @Test
  public void addRemoveUserFromGroupAddsAndRemovesSharedGroupFolder() throws Exception {

    GroupSetUp setup = setUpDocumentGroupForPIUserAndShareRecord();
    Folder otherRoot =
        doInTransaction(
            () -> {
              return folderDao.getRootRecordForUser(setup.user);
            });

    grpMgr.createSharedCommunalGroupFolders(setup.group.getId(), piUser.getUsername());

    Group grp = grpMgr.getGroup(setup.group.getId());
    logoutAndLoginAs(setup.user);
    // we're a group member, should see communal group folder
    Folder sharedFolder = folderMgr.getFolder(grp.getCommunalGroupFolderId(), setup.user);
    assertTrue(
        sharedFolder.getShortestPathToParent(setup.user.getRootFolder()).contains(sharedFolder));
    PaginationCriteria<BaseRecord> pgCrit =
        PaginationCriteria.createDefaultForClass(BaseRecord.class);
    pgCrit.setResultsPerPage(100);
    ISearchResults<BaseRecord> recs = recordMgr.listFolderRecords(otherRoot.getId(), pgCrit);

    doInTransaction(
        () -> {
          Folder otherLabGroupFolder = folderDao.getLabGroupFolderForUser(setup.user);
          assertEquals(1, otherLabGroupFolder.getChildren().size());
        });

    // now user removes other from group
    logoutAndLoginAs(piUser);
    groupController.removeUser(model, grp.getId(), setup.user.getId());

    // from now other shouldn't see or be able to read group folder
    doInTransaction(
        () -> {
          Folder otherLabGroupFolder = folderDao.getLabGroupFolderForUser(setup.user);
          assertEquals(0, otherLabGroupFolder.getChildren().size());
        });

    logoutAndLoginAs(setup.user);
    assertAuthorisationExceptionThrown(
        () -> folderMgr.getFolder(grp.getCommunalGroupFolderId(), setup.user));
  }

  @Test
  public void getLabPiGroup() throws Exception {
    User pi1 = createAndSaveUser("pi" + getRandomName(7), Constants.PI_ROLE);
    logoutAndLoginAs(pi1);
    // pi without group
    mockMvc
        .perform(get("/groups/viewPIGroup").principal(pi1::getUsername))
        .andExpect(view().name(containsString("redirect:/userform")))
        .andReturn();

    // regular user
    User user1 = createAndSaveUser("user" + getRandomName(7));
    logoutAndLoginAs(user1);
    mockMvc
        .perform(get("/groups/viewPIGroup").principal(pi1::getUsername))
        .andExpect(view().name(containsString("redirect:/userform")))
        .andReturn();

    // pi with group
    logoutAndLoginAs(pi1);
    initUser(pi1);
    createGroupForUsers(pi1, pi1.getUsername(), "", pi1);
    mockMvc
        .perform(get("/groups/viewPIGroup").principal(pi1::getUsername))
        .andExpect(view().name(containsString("redirect:/groups/view/")))
        .andReturn();
  }

  @Test
  public void createProjectGroupViaRequest() throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    User newUser = createAndSaveUser(getRandomName(5) + "newuser");
    initUser(newUser);
    mockMvc
        .perform(
            post(NEW_GROUP_PAGE)
                .param(Group.DEFAULT_ORDERBY_FIELD, "test Project Group")
                .param("groupOwners", newUser.getUsername())
                .param("memberString", newUser.getUsername())
                .param("groupType", "PROJECT_GROUP")
                .principal(sysadmin::getUsername))
        .andExpect(view().name(containsString("redirect:/groups/view/")))
        .andExpect(
            model()
                .attribute(
                    "group",
                    Matchers.hasProperty("groupType", Matchers.equalTo(GroupType.PROJECT_GROUP))));
  }

  @Test
  public void cycleUsers() throws Exception {
    TestGroup t1 = createTestGroup(3, new TestGroupConfig(true));
    logoutAndLoginAs(t1.getPi());
    grpMgr.authorizeLabAdminToViewAll(
        t1.getUserByPrefix(LABADMIN_PREFIX).getId(), t1.getPi(), t1.getGroup().getId(), true);

    logoutAndLoginAsSysAdmin();
    User pi2 = createAndSaveUser("pi2" + getRandomName(7), Constants.PI_ROLE);

    User member3 = createAndSaveUser("member3" + getRandomName(7));
    initUsers(pi2, member3);

    Group group2 =
        createGroupForUsers(
            pi2,
            pi2.getUsername(),
            t1.u1().getUsername(),
            pi2,
            t1.u1(),
            t1.getUserByPrefix(LABADMIN_PREFIX),
            member3);
    logoutAndLoginAs(t1.getPi());

    // this should expose a cycle, and the ChildAddException should be caught and handled
    // rather than thrown out of this method
    logoutAndLoginAs(pi2);
    grpMgr.authorizeLabAdminToViewAll(t1.u1().getId(), pi2, group2.getId(), true);
  }

  @Test
  public void changeRoleInputValidation() throws Exception {
    User pi1 = createAndSaveUser("pi" + getRandomName(7), Constants.PI_ROLE);
    User admin1 = createAndSaveUser("Admin" + getRandomName(7));
    User member1 = createAndSaveUser("member" + getRandomName(7));
    initUsers(pi1, admin1, member1);
    logoutAndLoginAs(pi1);
    Group g1 =
        createGroupForUsers(pi1, pi1.getUsername(), admin1.getUsername(), pi1, admin1, member1);
    // sanity check that setup is OK
    assertEquals(3, g1.getMembers().size());
    assertTrue(g1.getAdminUsers().contains(admin1));
    assertTrue(g1.getPiusers().contains(pi1));
    assertTrue(g1.getDefaultUsers().contains(member1));

    // member can't change role:
    logoutAndLoginAs(member1);
    // try to change admin role to user:
    MvcResult res =
        mockMvc
            .perform(
                post(CHANGE_ROLE_URL, g1.getId(), admin1.getId())
                    .param("role", "DEFAULT")
                    .principal(member1::getUsername))
            .andReturn();
    assertTrue(res.getResolvedException() instanceof AuthorizationException);
    // admin can't change role either
    logoutAndLoginAs(admin1);
    // try to change user role to lab group admin:
    MvcResult res2 =
        mockMvc
            .perform(
                post(CHANGE_ROLE_URL, g1.getId(), member1.getId())
                    .param("role", RoleInGroup.RS_LAB_ADMIN.name())
                    .principal(admin1::getUsername))
            .andReturn();
    assertTrue(res2.getResolvedException() instanceof AuthorizationException);

    // PI has permission to change roles but can't change to unknown role or PI role

    logoutAndLoginAs(pi1);
    // try to change user role to PI:
    MvcResult res3 =
        mockMvc
            .perform(
                post(CHANGE_ROLE_URL, g1.getId(), member1.getId())
                    .param("role", RoleInGroup.PI.name())
                    .principal(pi1::getUsername))
            .andReturn();
    Map json3 = parseJSONObjectFromResponseStream(res3);
    assertTrue(json3.get("errorMsg") != null);
    assertTrue(
        res3.getResponse()
            .getContentAsString()
            .contains(getMsgFromResourceBundler("group.edit.invalidrolename.error.msg")));

    // try to change user role to unknown role, fails gracefully:
    MvcResult res4 =
        mockMvc
            .perform(
                post(CHANGE_ROLE_URL, g1.getId(), member1.getId())
                    .param("role", "UNKNOWN_ROLE")
                    .principal(pi1::getUsername))
            .andReturn();
    Map json4 = parseJSONObjectFromResponseStream(res4);
    assertTrue(json4.get("errorMsg") != null);
    assertTrue(
        res4.getResponse()
            .getContentAsString()
            .contains(getMsgFromResourceBundler("group.edit.invalidrolename.error.msg")));

    // finally, we should succeed - pi can alter user->lab admin role, for
    // example
    MvcResult res5 =
        mockMvc
            .perform(
                post(CHANGE_ROLE_URL, g1.getId(), member1.getId())
                    .param("role", RoleInGroup.RS_LAB_ADMIN.name())
                    .principal(pi1::getUsername))
            .andReturn();
    Map json5 = parseJSONObjectFromResponseStream(res5);
    assertTrue(json5.get("data") != null);

    // a ROLE_SYSADMIN user can also modify group roles -RSPAC-563
    User sysadmin = logoutAndLoginAsSysAdmin();
    MvcResult res6 =
        mockMvc
            .perform(
                post(CHANGE_ROLE_URL, g1.getId(), member1.getId())
                    .param("role", RoleInGroup.DEFAULT.name())
                    .principal(sysadmin::getUsername))
            .andReturn();
    Map json6 = parseJSONObjectFromResponseStream(res6);
    assertTrue(json5.get("data") != null);

    // the last PI cn't be removed
    MvcResult res7 =
        mockMvc
            .perform(
                post(CHANGE_ROLE_URL, g1.getId(), pi1.getId())
                    .param("role", RoleInGroup.DEFAULT.name())
                    .principal(sysadmin::getUsername))
            .andReturn();
    Map json7 = parseJSONObjectFromResponseStream(res7);
    assertTrue(json7.get("errorMsg") != null);
    assertTrue(
        res7.getResponse()
            .getContentAsString()
            .contains(getMsgFromResourceBundler("group.edit.mustbe1.admin.error.msg")));

    // now let's update to a labdmin with view all permissions
    logoutAndLoginAs(pi1);
    MvcResult res8 =
        mockMvc
            .perform(
                post(CHANGE_ROLE_URL, g1.getId(), member1.getId())
                    .param("role", RoleInGroup.RS_LAB_ADMIN.name())
                    .param("isAuthorized", "true")
                    .principal(pi1::getUsername))
            .andReturn();
    Map json8 = parseJSONObjectFromResponseStream(res8);
    assertTrue(json8.get("data") != null);
    // now reload user and check has view all permission:
    member1 = userMgr.get(member1.getId());
    g1 = grpMgr.getGroup(g1.getId());
    assertTrue(g1.getUserGroupForUser(member1).isAdminViewDocsEnabled());
    // now demote to user .. hould no longet have adminViewPermissions
    MvcResult res9 =
        mockMvc
            .perform(
                post(CHANGE_ROLE_URL, g1.getId(), member1.getId())
                    .param("role", RoleInGroup.DEFAULT.name())
                    .param("isAuthorized", "false")
                    .principal(pi1::getUsername))
            .andReturn();
    Map json9 = parseJSONObjectFromResponseStream(res9);
    assertTrue(json9.get("data") != null);
    member1 = userMgr.get(member1.getId());
    g1 = grpMgr.getGroup(g1.getId());
    assertFalse(g1.getUserGroupForUser(member1).isAdminViewDocsEnabled());
  }

  @Test
  public void collabGroupMgt() throws Exception {
    User pi1 = createAndSaveUser(getRandomName(7), Constants.PI_ROLE);
    User pi2 = createAndSaveUser(getRandomName(7), Constants.PI_ROLE);
    final User member1 = createAndSaveUser(getRandomName(7));
    initUsers(pi1, pi2, member1);
    logoutAndLoginAs(pi1);
    Group g1 = createGroupForUsers(pi1, pi1.getUsername(), "", pi1, member1);
    Group g2 = createGroupForUsers(pi2, pi2.getUsername(), "", pi2);
    final Group collabGrp = createCollabGroupBetweenGroups(g1, g2);

    // member 1 is in the collab group.
    grpMgr.addUserToGroup(member1.getUsername(), collabGrp.getId(), RoleInGroup.DEFAULT);
    doInTransaction(
        () -> {
          // assert that member1 has collab group folder appear in his collabGroups folder
          assertEquals(
              1, folderDao.getCollaborationGroupsSharedFolderForUser(member1).getChildren().size());

          // and assert that member 1.s home folder doesn't NOT appear in other PIs
          // folder.
          Folder member1Root = folderDao.getRootRecordForUser(member1);
          assertEquals(1, member1Root.getParents().size()); //
          for (Folder parent : member1Root.getParentFolders()) {
            assertFalse(parent.getOwner().equals(pi2));
          }
        });

    // member 1 doesnt have permission to remove him and pi1 from collab group
    logoutAndLoginAs(member1);
    assertAuthorisationExceptionThrown(
        () -> grpMgr.removeLabGroupMembersFromCollabGroup(collabGrp.getId(), member1));

    // butpi can delete him and member1
    logoutAndLoginAs(pi1);
    grpMgr.removeLabGroupMembersFromCollabGroup(collabGrp.getId(), pi1);

    // now it only has 1 member:
    Group collabGRou2 = grpMgr.getGroup(collabGrp.getId());
    assertEquals(1, collabGRou2.getMembers().size());
    assertEquals(pi2, collabGRou2.getMembers().iterator().next());

    // now pi2 will remove himself; this will delete the group:
    logoutAndLoginAs(pi2);
    grpMgr.removeLabGroupMembersFromCollabGroup(collabGrp.getId(), pi2);
    assertExceptionThrown(
        () -> grpMgr.getGroup(collabGrp.getId()), ObjectRetrievalFailureException.class);
  }

  @Test
  public void changeToNewPiOK() throws Exception {
    TestGroup testgroup = createTestGroup(1);
    logoutAndLoginAs(testgroup.getUserByPrefix("u1"));
    TestGroup other = createTestGroup(1);
    TestGroup group3 = createTestGroup(1);
    User origPi = testgroup.getPi();
    User newPi = other.getPi();
    User sysadmin = logoutAndLoginAsSysAdmin();
    // make pi with edit permission
    enablePiEditAll(sysadmin);

    grpMgr.addUserToGroup(newPi.getUsername(), testgroup.getGroup().getId(), RoleInGroup.DEFAULT);
    Group updated = grpMgr.setNewPi(testgroup.getGroup().getId(), newPi.getId(), sysadmin);
    assertEquals("New PI should be the owner", newPi, updated.getOwner());
    newPi = userMgr.getUserByUsername(newPi.getUsername(), true);
    Set<ConstraintBasedPermission> piGroupPermissions = assertNewPIHasGRoupPIPerms(newPi, updated);
    assertEquals(newPi, updated.getPiusers().iterator().next());
    assertFalse(updated.getPiusers().contains(testgroup.getPi()));

    User oldPi = userMgr.get(testgroup.getPi().getId());
    assertTrue(oldPi.hasRoleInGroup(updated, RoleInGroup.DEFAULT));
    logoutAndLoginAs(newPi);
    // new pi can create collab groups.
    Group collabGroup = createCollabGroupBetweenGroups(updated, group3.getGroup());
    assertEquals(2, collabGroup.getMembers().size());
    // check new PI has acquired permissions
    newPi = userMgr.getUserByUsername(newPi.getUsername(), true);

    // .. and old PI has lost group permissions
    logoutAndLoginAs(origPi);
    origPi = userMgr.getUserByUsername(origPi.getUsername(), true);
    assertDemotedPiHasLostGrpPermissions(origPi, piGroupPermissions);

    // now test with global edit permission:
    logoutAndLoginAs(newPi);
    grpMgr.authorizePIToEditAll(testgroup.getGroup().getId(), newPi, true);
    // revert back to original PI
    logoutAndLoginAsSysAdmin();
    Group reverted = grpMgr.setNewPi(testgroup.getGroup().getId(), origPi.getId(), sysadmin);
    logoutAndLoginAs(origPi);
    permissionUtils.assertIsPermitted(
        "RECORD:WRITE:group=" + reverted.getUniqueName(), "not permitted");
    assertTrue(reverted.getUserGroupForUser(origPi).isPiCanEditWork());
    assertFalse(reverted.getUserGroupForUser(newPi).isPiCanEditWork());
  }

  @Test
  public void getInvitableUsers() throws Exception {
    TestGroup testgroup = createTestGroup(2);
    final int totalUsers = userMgr.getAllUsersView().size();
    List<String> existingUsernamesInGroup =
        testgroup.getGroup().getMembers().stream()
            .map(User::getUsername)
            .collect(Collectors.toList());
    final int existingGroupSize = existingUsernamesInGroup.size();
    User groupPi = testgroup.getPi();
    logoutAndLoginAs(groupPi);
    MvcResult result =
        mockMvc
            .perform(
                get("/groups/ajax/invitableUsers/{groupId}", testgroup.getGroup().getId())
                    .principal(groupPi::getUsername))
            .andReturn();
    List<Map<String, Object>> toInvite = getFromJsonAjaxReturnObject(result, List.class);
    // list doesn't have already existing members
    assertFalse(
        toInvite.stream()
            .anyMatch(user -> existingUsernamesInGroup.contains(user.get("username"))));
    assertEquals(totalUsers - existingGroupSize, toInvite.size());

    // non-PI gets Auth error
    User groupMember = testgroup.getUserByPrefix("u1");
    logoutAndLoginAs(groupMember);
    result =
        mockMvc
            .perform(
                get("/groups/ajax/invitableUsers/{groupId}", testgroup.getGroup().getId())
                    .principal(groupMember::getUsername))
            .andExpect(status().isUnauthorized())
            .andReturn();
    ErrorList el = getErrorListFromAjaxReturnObject(result);
    assertTrue(el.getAllErrorMessagesAsStringsSeparatedBy(",").length() > 0);
  }

  private void enablePiEditAll(User sysadmin) {
    SystemPropertyValue val = sysPropMgr.findByName(Preference.PI_CAN_EDIT_ALL_WORK_IN_LABGROUP);
    val.setValue(HierarchicalPermission.ALLOWED.name());
    sysPropMgr.save(val, sysadmin);
  }

  private void assertDemotedPiHasLostGrpPermissions(
      User origPi, Set<ConstraintBasedPermission> piGroupPermissions) {
    final Set<Permission> reducedPerms = origPi.getAllPermissions(true, true);
    // ignore FORM:CREATE as all users have this.
    List<Permission> totest =
        piGroupPermissions.stream()
            .filter(cbp -> !cbp.getString().matches("FORM:CREATE:?"))
            .collect(Collectors.toList());
    for (Permission cbp : totest) {
      assertFalse(reducedPerms.contains(cbp));
    }
  }

  private Set<ConstraintBasedPermission> assertNewPIHasGRoupPIPerms(User newPi, Group updated) {
    Set<ConstraintBasedPermission> piGroupPermissions =
        perFactory.createDefaultPermissionsForGroupPI(updated);
    Set<Permission> allPerms = newPi.getAllPermissions(true, true);
    for (ConstraintBasedPermission cbp : piGroupPermissions) {
      if (!allPerms.contains(cbp)) {
        log.warn("new pi does not have permission {}", cbp);
      }
      assertTrue(allPerms.contains(cbp));
    }
    return piGroupPermissions;
  }

  @Test
  public void groupWideAutoshare() throws Exception {
    // Setup and enable group-wide autoshare

    TestGroup tg = createTestGroup(1, new TestGroupConfig(true));
    Group group = tg.getGroup();

    User pi = group.getOwner();
    User labAdminViewAll = doCreateAndInitUser(getRandomName(10), Constants.USER_ROLE);
    User labAdminNoViewAll = doCreateAndInitUser(getRandomName(10), Constants.USER_ROLE);

    logoutAndLoginAs(pi);
    group =
        grpMgr.addUserToGroup(
            labAdminViewAll.getUsername(), group.getId(), RoleInGroup.RS_LAB_ADMIN);
    grpMgr.authorizeLabAdminToViewAll(labAdminViewAll.getId(), pi, group.getId(), true);
    group =
        grpMgr.addUserToGroup(
            labAdminNoViewAll.getUsername(), group.getId(), RoleInGroup.RS_LAB_ADMIN);
    group = grpMgr.saveGroup(group, false, pi);

    requestAndAssertAutoshare(group, pi, true, true);
    group = grpMgr.getGroup(group.getId());

    for (User member : group.getAllNonPIMembers()) {
      assertTrue(group.getUserGroupForUser(member).isAutoshareEnabled());
    }

    // Add a regular user and a PI to the group as a sysadmin

    User piInvitedByAdmin = doCreateAndInitUser(getRandomName(10), Constants.PI_ROLE);
    User userInvitedByAdmin = doCreateAndInitUser(getRandomName(10), Constants.USER_ROLE);

    logoutAndLoginAs(getSysAdminUser(), SYS_ADMIN_PWD);
    mockMvc.perform(
        post("/groups/admin/addUser")
            .principal(getSysAdminUser()::getUsername)
            .param("id", group.getId() + "")
            .param("memberString", userInvitedByAdmin.getUsername()));

    userInvitedByAdmin = userMgr.getUserByUsername(userInvitedByAdmin.getUsername(), true);
    assertEquals(1, userInvitedByAdmin.getAutoshareGroups().size());

    mockMvc.perform(
        post("/groups/admin/addUser")
            .principal(getSysAdminUser()::getUsername)
            .param("id", group.getId() + "")
            .param("memberString", piInvitedByAdmin.getUsername()));

    piInvitedByAdmin = userMgr.getUserByUsername(piInvitedByAdmin.getUsername(), true);
    assertEquals(0, piInvitedByAdmin.getAutoshareGroups().size());

    // Add a regular user and a PI to the group as a PI

    User piInvitedByPi = doCreateAndInitUser(getRandomName(10), Constants.PI_ROLE);
    User userInvitedByPi = doCreateAndInitUser(getRandomName(10), Constants.USER_ROLE);

    logoutAndLoginAs(pi);
    mockMvc.perform(
        post("/groups/admin/addUser")
            .principal(pi::getUsername)
            .param("id", group.getId() + "")
            .param("memberString", userInvitedByPi.getUsername()));
    logoutAndLoginAs(userInvitedByPi);
    MessageOrRequest mor =
        communicationMgr
            .getActiveMessagesAndRequestsForUserTarget(
                userInvitedByPi.getUsername(),
                PaginationCriteria.createDefaultForClass(CommunicationTarget.class))
            .getResults()
            .get(0);
    reqUpdateMgr.updateStatus(
        userInvitedByPi.getUsername(), CommunicationStatus.COMPLETED, mor.getId(), "Added");
    userInvitedByPi = userMgr.getUserByUsername(userInvitedByPi.getUsername(), true);
    assertEquals(1, userInvitedByPi.getAutoshareGroups().size());

    logoutAndLoginAs(pi);
    mockMvc.perform(
        post("/groups/admin/addUser")
            .principal(pi::getUsername)
            .param("id", group.getId() + "")
            .param("memberString", piInvitedByPi.getUsername()));
    logoutAndLoginAs(piInvitedByPi);
    MessageOrRequest mor2 =
        communicationMgr
            .getActiveMessagesAndRequestsForUserTarget(
                piInvitedByPi.getUsername(),
                PaginationCriteria.createDefaultForClass(CommunicationTarget.class))
            .getResults()
            .get(0);
    reqUpdateMgr.updateStatus(
        piInvitedByPi.getUsername(), CommunicationStatus.COMPLETED, mor2.getId(), "Added");
    piInvitedByPi = userMgr.getUserByUsername(piInvitedByPi.getUsername(), true);
    assertEquals(0, piInvitedByPi.getAutoshareGroups().size());

    // Disable group-wide autoshare

    requestAndAssertAutoshare(group, pi, false, false);
    group = grpMgr.getGroup(group.getId());

    for (User member : group.getAllNonPIMembers()) {
      assertFalse(group.getUserGroupForUser(member).isAutoshareEnabled());
    }
  }

  @Test
  public void groupWideAutosharePermissions() throws Exception {
    TestGroup tg = createTestGroup(1, new TestGroupConfig(true));
    Group group = tg.getGroup();

    User pi = group.getOwner();
    User standardUser = tg.u1();
    User labAdminViewAll = doCreateAndInitUser(getRandomName(10), Constants.USER_ROLE);
    User labAdminNoViewAll = doCreateAndInitUser(getRandomName(10), Constants.USER_ROLE);

    logoutAndLoginAs(pi);
    group =
        grpMgr.addUserToGroup(
            labAdminViewAll.getUsername(), group.getId(), RoleInGroup.RS_LAB_ADMIN);
    labAdminViewAll =
        grpMgr.authorizeLabAdminToViewAll(labAdminViewAll.getId(), pi, group.getId(), true);
    group =
        grpMgr.addUserToGroup(
            labAdminNoViewAll.getUsername(), group.getId(), RoleInGroup.RS_LAB_ADMIN);
    group = grpMgr.saveGroup(group, false, pi);

    // Initial state of group-wide autoshare is false. Each of these asserts is
    // stateful and depends on the previous assert.
    requestAndAssertAutoshare(group, standardUser, true, false);
    requestAndAssertAutoshare(group, labAdminNoViewAll, true, false);
    requestAndAssertAutoshare(group, pi, true, true);
    requestAndAssertAutoshare(group, standardUser, false, true);
    requestAndAssertAutoshare(group, labAdminNoViewAll, false, true);
    requestAndAssertAutoshare(group, pi, false, false);
    requestAndAssertAutoshare(group, labAdminViewAll, true, true);
    requestAndAssertAutoshare(group, labAdminViewAll, false, false);
  }

  @Test
  public void testAutoshareSystemProperty() throws Exception {
    TestGroup tg = createTestGroup(1, new TestGroupConfig(false));
    Group group = tg.getGroup();

    User pi = group.getOwner();

    logoutAndLoginAs(getSysAdminUser(), SYS_ADMIN_PWD);
    sysPropMgr.save(
        SystemPropertyName.GROUP_AUTOSHARING_AVAILABLE,
        HierarchicalPermission.DENIED,
        getSysAdminUser());

    // PIs and lab admins with view all can no longer change group-wide autoshare status
    requestAndAssertAutoshare(group, pi, true, false);

    // revert back, as the setting persists between test runs
    logoutAndLoginAs(getSysAdminUser(), SYS_ADMIN_PWD);
    sysPropMgr.save(
        SystemPropertyName.GROUP_AUTOSHARING_AVAILABLE,
        HierarchicalPermission.ALLOWED,
        getSysAdminUser());
  }

  @Test
  public void testEnableGroupPublicationAllowedAsPi() throws Exception {
    TestGroup tg = createTestGroup(1, new TestGroupConfig(false));
    Group group = tg.getGroup();
    User pi = group.getOwner();
    requestAndAssertPublicationAllowed(group, pi, true, true);
  }

  @Test
  public void testDisableGroupPublicationAllowedAsPi() throws Exception {
    TestGroup tg = createTestGroup(1, new TestGroupConfig(false));
    Group group = tg.getGroup();
    User pi = group.getOwner();
    requestAndAssertPublicationAllowed(group, pi, false, false);
  }

  @Test
  public void testSetGroupPublicationAllowedAsStandardUserIsRejected() throws Exception {
    TestGroup tg = createTestGroup(1, new TestGroupConfig(false));
    Group group = tg.getGroup();
    User pi = group.getOwner();
    requestPublicationAllowedAndCheckForErrorsAndPublicationStatusUnchanged(group, true, false);
  }

  // Attempt to set group autoshare status to targetAutoshareStatus and assert
  // that the resulting status is the expectedAutoshareStatus. This method is
  // stateful - the initial status depends on the previous group autoshare status!
  private void requestAndAssertAutoshare(
      Group group, User subject, Boolean targetAutoshareStatus, Boolean expectedAutoshareStatus)
      throws Exception {

    logoutAndLoginAs(subject);

    String urlTemplate =
        (targetAutoshareStatus)
            ? "/groups/ajax/enableAutoshare/{groupId}"
            : "/groups/ajax/disableAutoshare/{groupId}";

    MockHttpServletRequestBuilder requestBuilder =
        post(urlTemplate, group.getId()).principal(subject::getUsername);

    mockMvc.perform(requestBuilder).andExpect(status().isOk());

    group = grpMgr.getGroup(group.getId());
    assertEquals(expectedAutoshareStatus, group.isAutoshareEnabled());
  }

  private void requestAndAssertPublicationAllowed(
      Group group,
      User subject,
      Boolean targetPublicationAllowedStatus,
      Boolean expectedPublicationAllowedStatus)
      throws Exception {

    logoutAndLoginAs(subject);

    String urlTemplate =
        (targetPublicationAllowedStatus)
            ? "/groups/ajax/allowGroupPublications/{groupId}"
            : "/groups/ajax/disableGroupPublications/{groupId}";

    MockHttpServletRequestBuilder requestBuilder =
        post(urlTemplate, group.getId()).principal(subject::getUsername);

    mockMvc.perform(requestBuilder).andExpect(status().isOk());

    group = grpMgr.getGroup(group.getId());
    assertEquals(expectedPublicationAllowedStatus, group.isPublicationAllowed());
  }

  private void requestPublicationAllowedAndCheckForErrorsAndPublicationStatusUnchanged(
      Group group, Boolean targetPublicationAllowedStatus, Boolean expectedPublicationAllowedStatus)
      throws Exception {
    User subject = group.getAllNonPIMembers().iterator().next();
    logoutAndLoginAs(subject);

    String urlTemplate =
        (targetPublicationAllowedStatus)
            ? "/groups/ajax/allowGroupPublications/{groupId}"
            : "/groups/ajax/disableGroupPublications/{groupId}";

    MockHttpServletRequestBuilder requestBuilder =
        post(urlTemplate, group.getId()).principal(subject::getUsername);

    MvcResult result = mockMvc.perform(requestBuilder).andExpect(status().isOk()).andReturn();
    assertTrue(
        result.getResponse().getContentAsString().contains("Only PI can allow group publication"));
    group = grpMgr.getGroup(group.getId());
    assertEquals(expectedPublicationAllowedStatus, group.isPublicationAllowed());
  }
}
