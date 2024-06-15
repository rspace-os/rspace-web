package com.researchspace.service;

import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalArgumentException;
import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.Constants;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.*;
import com.researchspace.model.dtos.UserSearchCriteria;
import com.researchspace.model.events.AccountEventType;
import com.researchspace.model.events.UserAccountEvent;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestGroup;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

public class UserManagerTest extends SpringTransactionalTest {

  final int INDIVIDUAL_DEFAULT_VIEWABLE_USERS = 1;
  private static final String USER2 = "user1a";
  private Logger log = LoggerFactory.getLogger(UserManagerTest.class);

  private @Autowired RoleManager roleManager;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void testGetUser() {
    User user = userMgr.getUserByUsername(USER2);
    assertNotNull(user);
    log.debug(user.getUsername());
    assertEquals(2, user.getRoles().size());
  }

  @Test
  public void testSave2UsersWithSamePasswordIsDifferentDueToSalt() throws UserExistsException {
    User u1 = TestFactory.createAnyUser("XXXXXX");
    u1.setPassword("password1");
    userMgr.saveNewUser(u1);

    User u2 = TestFactory.createAnyUser("XXXXXX2");
    u2.setPassword("password1");
    userMgr.saveNewUser(u2);

    u1 = userMgr.get(u1.getId());
    u2 = userMgr.get(u2.getId());
    assertNotNull(u1.getSalt());
    assertNotNull(u2.getSalt());
    assertFalse(u1.getSalt().equals(u2.getSalt()));
    assertFalse(u1.getPassword().equals(u2.getPassword()));
  }

  @Test
  public void testSaveUser() {
    final int n5 = 5;
    User user = userMgr.getUserByUsername(USER2);

    assertEquals(2, user.getRoles().size());
    // has no roles
    User u1 = createAndSaveUserIfNotExists(getRandomName(n5));
    // set role
    u1.addRole(roleManager.getRole(Constants.PI_ROLE));
    u1 = userMgr.save(u1); // handles association to real roles

    // assert that pi has user role as well
    assertTrue(u1.hasRole(Role.PI_ROLE));
    assertTrue(u1.hasRole(Role.USER_ROLE));
  }

  @Test
  public void getAllUsersInAdminsCommunityTest() throws IllegalAddChildOperation {
    User admin = createAndSaveAdminUser();
    User pi1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(pi1);
    logoutAndLoginAs(admin);
    createGroupAndAddToCommunity(admin, pi1);

    assertEquals(pi1, userMgr.getAllUsersInAdminsCommunity(admin.getUsername()).get(0));
  }

  private Group createGroupAndAddToCommunity(User admin, User pi1) throws IllegalAddChildOperation {
    Group g1 = createGroup("any", pi1);
    addUsersToGroup(pi1, g1);
    Community comm = createAndSaveCommunity(admin, "newComm");
    communityMgr.addGroupToCommunity(g1.getId(), comm.getId(), admin);
    return g1;
  }

  @Test(expected = IllegalArgumentException.class)
  public void getAllUsersInAdminsCommunityTestthrowIAEIfNotAdmin() {
    User pi1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    userMgr.getAllUsersInAdminsCommunity(pi1.getUsername());
  }

  @Test
  public void testGetViewableUsersByCommunityAdmin() {
    TestGroup testGroup1 = createTestGroup(3);
    TestGroup testGroup2 = createTestGroup(4);
    User admin = createAndSaveAdminUser();
    Community comm = createAndSaveCommunity(admin, "newComm");
    logoutAndLoginAs(admin);
    communityMgr.addGroupToCommunity(testGroup1.getGroup().getId(), comm.getId(), admin);
    communityMgr.addGroupToCommunity(testGroup2.getGroup().getId(), comm.getId(), admin);

    PaginationCriteria<User> pgcrit = PaginationCriteria.createDefaultForClass(User.class);
    pgcrit.setGetAllResults();
    UserSearchCriteria usc = new UserSearchCriteria();
    pgcrit.setSearchCriteria(usc);

    ISearchResults<User> viewable = userMgr.getViewableUsers(admin, pgcrit);
    assertEquals(9, viewable.getTotalHits().intValue());

    // enabled
    usc.setOnlyEnabled(true);
    assertEquals(9, userMgr.getViewableUsers(admin, pgcrit).getTotalHits().intValue());

    User u1 = testGroup1.u1();
    u1.setEnabled(false);
    userDao.saveUser(u1);
    assertEquals(8, userMgr.getViewableUsers(admin, pgcrit).getTotalHits().intValue());

    // private profiles excluded
    usc.setOnlyPublicProfiles(true);
    assertEquals(8, userMgr.getViewableUsers(admin, pgcrit).getTotalHits().intValue());
    User u2 = testGroup1.u2();
    u2.setPrivateProfile(true);
    userDao.saveUser(u2);
    assertEquals(7, userMgr.getViewableUsers(admin, pgcrit).getTotalHits().intValue());

    // temp account
    usc.setTempAccountsOnly(true);
    assertEquals(0, userMgr.getViewableUsers(admin, pgcrit).getTotalHits().intValue());
    usc.setTempAccountsOnly(false);

    usc.setLastLoginEarlierThan(LocalDate.of(2022, 1, 1));
    // no-one has logged in, so all lastLoginDates are null, and null is included.
    assertEquals(7, userMgr.getViewableUsers(admin, pgcrit).getTotalHits().intValue());

    // simulate last Login before cut-off - will be included
    User u4 = testGroup2.u1();
    u4.setLastLogin(new Date(Instant.now().minus(10_000, ChronoUnit.DAYS).toEpochMilli()));
    assertEquals(7, userMgr.getViewableUsers(admin, pgcrit).getTotalHits().intValue());

    // simulate lastLogin after cut-off
    u4.setLastLogin(new Date(Instant.now().toEpochMilli()));
    assertEquals(6, userMgr.getViewableUsers(admin, pgcrit).getTotalHits().intValue());

    // creationDate
    usc.setCreationDateEarlierThan(LocalDate.of(2022, 1, 1));
    assertEquals(0, userMgr.getViewableUsers(admin, pgcrit).getTotalHits().intValue());
    usc.setCreationDateEarlierThan(LocalDate.of(2032, 1, 1));
    assertEquals(6, userMgr.getViewableUsers(admin, pgcrit).getTotalHits().intValue());

    // exclude sso backdoor admins
    usc.setWithoutBackdoorSysadmins(false);
    assertEquals(6, userMgr.getViewableUsers(admin, pgcrit).getTotalHits().intValue());
    User backDoorLogin = testGroup2.u3();
    usc.setWithoutBackdoorSysadmins(true);
    backDoorLogin.setSignupSource(SignupSource.SSO_BACKDOOR);
    assertEquals(5, userMgr.getViewableUsers(admin, pgcrit).getTotalHits().intValue());
  }

  @Test
  public void testGetViewableUsers() {
    User sysadmin1 =
        createAndSaveUserIfNotExists(
            getRandomAlphabeticString("sysadmin"), Constants.SYSADMIN_ROLE);
    User toDisable = createAndSaveUserIfNotExists("toDisable1");
    int totalUsers = userDao.getAll().size();
    // as ssyadmin
    logoutAndLoginAs(sysadmin1);
    PaginationCriteria<User> pgcrit = PaginationCriteria.createDefaultForClass(User.class);
    pgcrit.setGetAllResults();
    configureGetEnabledUsersOnly(pgcrit);
    ISearchResults<User> viewable = userMgr.getViewableUsers(sysadmin1, pgcrit);
    // anonymous user excluded
    assertEquals(totalUsers - 1, viewable.getTotalHits().intValue());

    // now disable a user - viewable count should decrease
    toDisable.setEnabled(false);
    userDao.save(toDisable);
    ISearchResults<User> viewable2 = userMgr.getViewableUsers(sysadmin1, pgcrit);
    // anonymous user excluded
    assertEquals(totalUsers - 2, viewable2.getTotalHits().intValue());

    // as admin
    User admin = createAndSaveAdminUser();
    User pi1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(pi1);
    logoutAndLoginAs(admin);
    Group group = createGroupAndAddToCommunity(admin, pi1);

    ISearchResults<User> adminable2 = userMgr.getViewableUsers(admin, pgcrit);
    assertEquals(1, adminable2.getTotalHits().intValue());
    // now disable pi - viewable count should decrease
    pi1.setEnabled(false);
    userDao.save(pi1);
    ISearchResults<User> viewable3 = userMgr.getViewableUsers(admin, pgcrit);
    assertEquals(0, viewable3.getTotalHits().intValue());

    // as pi
    // add another user 2 group
    logoutAndLoginAs(pi1);
    User another = createAndSaveUserIfNotExists("another1");
    initialiseContentWithEmptyContent(another);
    grpMgr.addUserToGroup(another.getUsername(), group.getId(), RoleInGroup.DEFAULT);
    pi1 = userDao.getUserByUsername(pi1.getUniqueName());
    ISearchResults<User> adminable5 = userMgr.getViewableUsers(pi1, pgcrit);
    assertEquals(2, adminable5.getTotalHits().intValue());

    // as user
    User anyuser = createAndSaveUserIfNotExists("any1");
    logoutAndLoginAs(anyuser);
    ISearchResults<User> adminable3 = userMgr.getViewableUsers(anyuser, pgcrit);
    assertEquals(1, adminable3.getTotalHits().intValue());
  }

  private void configureGetEnabledUsersOnly(PaginationCriteria<User> pgcrit) {
    UserSearchCriteria srchCrit = new UserSearchCriteria();
    srchCrit.setOnlyEnabled(true);
    pgcrit.setSearchCriteria(srchCrit);
  }

  @Test
  public void setTokenComplete() {
    User u = createAndSaveUserIfNotExists("token");
    TokenBasedVerification token =
        userMgr.createTokenBasedVerificationRequest(
            u, u.getEmail(), "anything", TokenBasedVerificationType.PASSWORD_CHANGE);
    assertFalse(token.isResetCompleted());
    token = userMgr.setTokenCompleted(token);
    assertTrue(token.isResetCompleted());
  }

  @Test
  public void testViewableUserListSingleUser() throws IllegalAddChildOperation {
    User user = createAndSaveUserIfNotExists(getRandomName(10), Constants.USER_ROLE);
    initialiseContentWithEmptyContent(user);
    assertEquals(1, userMgr.getViewableUserList(user).size());
  }

  @Test
  public void testConnectedUsersAndGroups() throws IllegalAddChildOperation {

    // init users and group
    User pi = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User user1 = createAndSaveUserIfNotExists("user1" + getRandomName(10));
    User user2 = createAndSaveUserIfNotExists("user2" + getRandomName(10));
    User user3 = createAndSaveUserIfNotExists("user3" + getRandomName(10));

    initialiseContentWithEmptyContent(pi, user1, user2, user3);

    // initially user1 is only connected to themselves
    List<User> connectedUsers = userMgr.populateConnectedUserList(user1);
    assertEquals(1, connectedUsers.size());
    assertEquals(user1.getUsername(), connectedUsers.get(0).getUsername());
    assertTrue(user1.isConnectedToUser(user1));
    assertFalse(user1.isConnectedToUser(user2));

    // create two groups, one belonging to a community
    User admin = createAndSaveAdminUser();
    logoutAndLoginAs(admin);
    Group group1 = createGroupAndAddToCommunity(admin, pi);
    addUsersToGroup(pi, group1, user1, user2);
    Group group2 = createGroup("group2", pi);
    addUsersToGroup(pi, group2, user3);

    // user is connected to first group and members, even if nothing is shared
    connectedUsers = userMgr.populateConnectedUserList(user1);
    assertEquals(3, connectedUsers.size());
    assertTrue(user1.isConnectedToUser(pi));
    assertTrue(user1.isConnectedToUser(user2));
    assertFalse(user1.isConnectedToUser(user3));

    userMgr.populateConnectedGroupList(user1);
    assertTrue(user1.isConnectedToGroup(group1));
    assertFalse(user1.isConnectedToGroup(group2));

    // pi of both groups is connected to all users, but not to community admin
    connectedUsers = userMgr.populateConnectedUserList(pi);
    assertEquals(4, connectedUsers.size());
    assertTrue(pi.isConnectedToUser(user1));
    assertTrue(pi.isConnectedToUser(user2));
    assertTrue(pi.isConnectedToUser(user3));
    assertFalse(pi.isConnectedToUser(admin));

    userMgr.populateConnectedGroupList(pi);
    assertTrue(pi.isConnectedToGroup(group1));
    assertTrue(pi.isConnectedToGroup(group2));

    // user who made an individual share is also added to connected users list
    logoutAndLoginAs(user3);
    StructuredDocument docD1 = createBasicDocumentInRootFolderWithText(user3, "test");
    shareRecordWithUser(user3, docD1, user1);

    connectedUsers = userMgr.populateConnectedUserList(user1);
    assertEquals(4, connectedUsers.size());
    assertTrue(user1.isConnectedToUser(user3)); // connected now through share

    // community admin can see all users in their community, but not one from other
    connectedUsers = userMgr.populateConnectedUserList(admin);
    assertEquals(4, connectedUsers.size());
    assertTrue(admin.isConnectedToUser(pi));
    assertTrue(admin.isConnectedToUser(user1));
    assertTrue(admin.isConnectedToUser(user2));
    assertFalse(admin.isConnectedToUser(user3));

    userMgr.populateConnectedGroupList(admin);
    assertTrue(admin.isConnectedToGroup(group1));
    assertFalse(admin.isConnectedToGroup(group2));

    // sysadmin always connected to everyone, but connection list is not really populated
    User sysadmin = createAndSaveSysadminUser();
    List<User> sysadminConnections = userMgr.populateConnectedUserList(sysadmin);
    assertEquals(1, sysadminConnections.size()); // just sysadmin
    assertTrue(sysadmin.isConnectedToUser(pi));
    assertTrue(sysadmin.isConnectedToUser(user1));
    assertTrue(sysadmin.isConnectedToUser(user2));
    assertTrue(sysadmin.isConnectedToUser(user3));
    assertTrue(sysadmin.isConnectedToUser(admin));

    userMgr.populateConnectedGroupList(sysadmin);
    assertTrue(sysadmin.isConnectedToGroup(group1));
    assertTrue(sysadmin.isConnectedToGroup(group2));
  }

  @Test
  public void testViewableUserListGroup() throws IllegalAddChildOperation {

    User pi = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User user1 = createAndSaveUserIfNotExists("user1");
    User user2 = createAndSaveUserIfNotExists("user2");
    User user3 = createAndSaveUserIfNotExists("user3");

    initialiseContentWithEmptyContent(pi, user1, user2, user3);

    Group group = createGroup("group", pi);
    addUsersToGroup(pi, group, user1, user2, user3);
    final int TOTAL_GROUP_SIZE = 4;

    assertEquals(TOTAL_GROUP_SIZE, userMgr.getViewableUserList(pi).size());
    // individual users can only see themselves
    assertEquals(INDIVIDUAL_DEFAULT_VIEWABLE_USERS, userMgr.getViewableUserList(user1).size());
    assertEquals(INDIVIDUAL_DEFAULT_VIEWABLE_USERS, userMgr.getViewableUserList(user2).size());
    assertEquals(INDIVIDUAL_DEFAULT_VIEWABLE_USERS, userMgr.getViewableUserList(user3).size());

    // user1 now shares with user2
    logoutAndLoginAs(user1);
    StructuredDocument docD1 = createBasicDocumentInRootFolderWithText(user1, "any");
    shareRecordWithUser(user1, docD1, user2);

    // This covers sharing a record individually within a group (stand alone and cloud environment)
    assertEquals(2, userMgr.getViewableUserList(user2).size());

    // user 3 now shares with everyone in the group
    logoutAndLoginAs(user3);
    StructuredDocument docD2 = createBasicDocumentInRootFolderWithText(user3, "any");
    shareRecordWithGroup(user3, group, docD2);
    // user 1 + user 3
    assertEquals(2, userMgr.getViewableUserList(user1).size());
    // user1, user2, user3
    assertEquals(3, userMgr.getViewableUserList(user2).size());
    // no-one has shared with user1, so can only see themselves
    assertEquals(INDIVIDUAL_DEFAULT_VIEWABLE_USERS, userMgr.getViewableUserList(user3).size());
  }

  @Test
  public void testViewableUserListGroupCollaborationGroup() throws IllegalAddChildOperation {

    String random = getRandomName(10);

    User piA = createAndSaveUserIfNotExists("piA", Constants.PI_ROLE);
    User user1 = createAndSaveUserIfNotExists("user1");
    User user2 = createAndSaveUserIfNotExists("user2");

    User piB = createAndSaveUserIfNotExists("piB", Constants.PI_ROLE);
    User user3 = createAndSaveUserIfNotExists("user3");
    User user4 = createAndSaveUserIfNotExists("user4");
    initialiseContentWithEmptyContent(piA, piB, user1, user2, user3, user4);

    Group groupA = createGroup("groupA", piA);
    addUsersToGroup(piA, groupA, user1, user2);

    Group groupB = createGroup("groupB", piB);
    addUsersToGroup(piB, groupB, user3, user4);

    logoutAndLoginAs(piA);
    Group collabGroup = createCollabGroupBetweenGroups(groupA, groupB);
    addUsersToGroup(piA, collabGroup, user1, user3);

    // piA can see user1 and user2 (Group Right) => BUG FIXED
    assertEquals(3, userMgr.getViewableUserList(piA).size());

    // piB can see user3 and user4 (Group Right) => BUG FIXED
    assertEquals(3, userMgr.getViewableUserList(piB).size());

    assertEquals(INDIVIDUAL_DEFAULT_VIEWABLE_USERS, userMgr.getViewableUserList(user1).size());
    assertEquals(INDIVIDUAL_DEFAULT_VIEWABLE_USERS, userMgr.getViewableUserList(user2).size());
    assertEquals(INDIVIDUAL_DEFAULT_VIEWABLE_USERS, userMgr.getViewableUserList(user3).size());
    assertEquals(INDIVIDUAL_DEFAULT_VIEWABLE_USERS, userMgr.getViewableUserList(user4).size());

    // user1 shares with collab group
    logoutAndLoginAs(user1);

    StructuredDocument docD1 = createBasicDocumentInRootFolderWithText(user1, random);
    shareRecordWithGroup(user1, collabGroup, docD1);

    // After sharing a record with the collaboration group.
    assertEquals(3, userMgr.getViewableUserList(piA).size());
    assertEquals(4, userMgr.getViewableUserList(piB).size());
    assertEquals(INDIVIDUAL_DEFAULT_VIEWABLE_USERS, userMgr.getViewableUserList(user1).size());
    assertEquals(INDIVIDUAL_DEFAULT_VIEWABLE_USERS, userMgr.getViewableUserList(user2).size());
    // user3 is in collab group with user1 so can now potentially see his documents
    assertEquals(INDIVIDUAL_DEFAULT_VIEWABLE_USERS + 1, userMgr.getViewableUserList(user3).size());
    assertEquals(1, userMgr.getViewableUserList(user4).size());
  }

  @Test
  public void updatePreferenceHappyCase() {
    User user = createAndSaveRandomUser();
    UserPreference userPreference =
        userMgr.setPreference(Preference.BOX, "true", user.getUsername());
    assertEquals(true, userPreference.getValueAsBoolean());
    userPreference = userMgr.getPreferenceForUser(user, Preference.BOX);
    assertEquals(true, userPreference.getValueAsBoolean());
    userPreference = userMgr.setPreference(Preference.BOX, "false", user.getUsername());
    assertEquals(false, userPreference.getValueAsBoolean());
    userPreference = userMgr.getPreferenceForUser(user, Preference.BOX);
    assertEquals(false, userPreference.getValueAsBoolean());
  }

  @Test
  public void updatePreferenceInvalidValue() {
    User user = userMgr.getUserByUsername(USER2);
    assertIllegalArgumentException(
        () -> userMgr.setPreference(Preference.UI_PDF_PAGE_SIZE, "INVALID", user.getUsername()));
  }

  @Test
  public void saveUserAccountEvent() throws InterruptedException {
    // given
    User user = createAndSaveRandomUser();
    assertEquals(0, userMgr.getAccountEventsForUser(user).size());
    assertFalse(user.isLoginDisabled());
    Instant b4Save = Instant.now().minus(1, ChronoUnit.SECONDS);
    UserAccountEvent toSave = new UserAccountEvent(user, AccountEventType.DISABLED);

    // when
    UserAccountEvent savedAccountEvent = userMgr.saveUserAccountEvent(toSave);
    Instant afterSave = Instant.now().plus(1, ChronoUnit.SECONDS);

    // then
    assertNotNull(savedAccountEvent.getId());
    assertNotNull(savedAccountEvent.getTimestamp());
    // assert timestamp is 'now'
    assertTrue(savedAccountEvent.getTimestamp().before(new Date(afterSave.toEpochMilli())));
    assertTrue(savedAccountEvent.getTimestamp().after(new Date(b4Save.toEpochMilli())));

    assertEquals(1, userMgr.getAccountEventsForUser(user).size());
  }

  @Test
  public void getOriginalUserForOperateAs() {
    User user = createInitAndLoginAnyUser();
    // returns same user;
    assertEquals(user, userMgr.getOriginalUserForOperateAs(user));

    // now run as, make sure we get sysadmin back.
    logoutAndLoginAsSysAdmin();
    permissionUtils.doRunAs(new MockHttpSession(), getSysAdminUser(), user);
    assertEquals(getSysAdminUser(), userMgr.getOriginalUserForOperateAs(user));
    RSpaceTestUtils.logout();

    // don't allow admin imposters to operate As!
    User adminImposter = createInitAndLoginAnyUser();
    permissionUtils.doRunAs(new MockHttpSession(), adminImposter, user);
    CoreTestUtils.assertIllegalStateExceptionThrown(
        () -> userMgr.getOriginalUserForOperateAs(user));
  }

  @Test
  public void changeUsernameAlias() throws UserExistsException {
    final String testUsername = "testUsernameAliasUser";
    final String testAlias = "testAlias";

    User firstUser = doCreateAndInitUser(testUsername);
    assertNull(firstUser.getUsernameAlias());

    // user findable by username, not by alias
    assertEquals(testUsername, userMgr.findUsernameByUsernameOrAlias(testUsername));
    assertNull(userMgr.findUsernameByUsernameOrAlias(testAlias));

    // cannot save alias to an existing username
    UserExistsException exception =
        assertThrows(
            UserExistsException.class,
            () -> userMgr.changeUsernameAlias(firstUser.getId(), testUsername));
    assertEquals(
        "There is already a user with username [testUsernameAliasUser]", exception.getMessage());

    // set unique alias, search again
    userMgr.changeUsernameAlias(firstUser.getId(), testAlias);
    assertEquals(testUsername, userMgr.findUsernameByUsernameOrAlias(testUsername));
    assertEquals(testUsername, userMgr.findUsernameByUsernameOrAlias(testAlias));

    // cannot save new user with username matching existing alias
    User secondUser = TestFactory.createAnyUser(testAlias);
    assertThrows(UserExistsException.class, () -> userMgr.saveNewUser(secondUser));

    // cannot save new user with alias matching existing username
    secondUser.setUsername(testUsername + ".2");
    secondUser.setUsernameAlias(testUsername);
    assertThrows(UserExistsException.class, () -> userMgr.saveNewUser(secondUser));

    // cannot save new user with alias matching existing alias
    secondUser.setUsernameAlias(testAlias);
    assertThrows(UserExistsException.class, () -> userMgr.saveNewUser(secondUser));

    // can save with unique username and alias
    secondUser.setUsernameAlias(testAlias + ".2");
    userMgr.saveNewUser(secondUser);
    assertEquals(testUsername + ".2", userMgr.findUsernameByUsernameOrAlias(testAlias + ".2"));
  }
}
