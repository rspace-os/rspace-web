package com.researchspace.service.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.Constants;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.User;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.RoleManager;
import com.researchspace.service.UserExistsException;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.CommunityTestContext;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CommunityTestContext
public class CloudServiceManagerIT extends RealTransactionSpringTestBase {

  private @Autowired UserManager userManager;
  private @Autowired RoleManager roleManager;
  private @Autowired CommunityUserManager cloudUserManager;
  private @Autowired CloudGroupManager cloudGroupManager;
  private @Autowired CloudNotificationManager cloudNotificationManager;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void promoteCreatorToPITest() throws IllegalAddChildOperation {
    User creator = createAndSaveUser(getRandomAlphabeticString("user"), Constants.USER_ROLE);
    initUsers(creator);
    User sysadmin = logoutAndLoginAsSysAdmin();
    cloudGroupManager.promoteUserToPI(creator, sysadmin);
    // Update the object (User)
    creator = userManager.get(creator.getId());
    assertTrue(creator.hasRole(roleManager.getRole(Constants.PI_ROLE)));
  }

  /*
   * This has to run in cloud configuration as this is the only way 2 users
   * can share outside of a group
   */
  @Test
  public void testViewableUserListAfterIndividualShareOutsideGroup()
      throws IllegalAddChildOperation {
    User user1 = createAndSaveUser(getRandomAlphabeticString("u"));
    User user2 = createAndSaveUser(getRandomAlphabeticString("u"));
    initUsers(user1, user2);
    logoutAndLoginAs(user1);
    StructuredDocument docD1 = createBasicDocumentInRootFolderWithText(user1, "any");
    // neither are in a group, simulate user2 accepting a share request  on cloud
    // i.e., user1 shares with user2 is the end result
    logoutAndLoginAs(user2);
    shareRecordWithUser(user1, docD1, user2);
    assertEquals(2, userMgr.getViewableUserList(user2).size());
  }

  @Test
  public void createCloudGroupTest() throws IllegalAddChildOperation {
    User creator = createAndSaveUser(getRandomAlphabeticString("user"), Constants.USER_ROLE);
    initUsers(creator);
    logoutAndLoginAs(creator);
    cloudGroupManager.promoteUserToPI(creator, creator);
    // Update the object (User)
    creator = userManager.get(creator.getId());
    try {
      Group group =
          cloudGroupManager.createAndSaveGroup(
              "initialGroupName", creator, creator, GroupType.LAB_GROUP, creator);
      assertTrue(group.isLabGroup());
      assertTrue(group.isOnlyGroupPi(creator.getUsername()));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void checkInternalNotificationExistingUsersTest() throws IllegalAddChildOperation {

    User creator = createAndSaveUser(getRandomAlphabeticString("user"), Constants.PI_ROLE);
    User existingUser =
        createAndSaveUser(getRandomAlphabeticString("existingUser"), Constants.USER_ROLE);
    initUsers(existingUser, creator);
    logoutAndLoginAs(creator);

    Group group =
        cloudGroupManager.createAndSaveGroup(
            "initialGroupName", creator, creator, GroupType.LAB_GROUP, creator);

    List<User> list = userManager.getUserByEmail(existingUser.getEmail());
    assertTrue(!list.isEmpty());

    if (!list.isEmpty()) {

      User invitedUser = list.get(0);
      List<String> newUserList = new ArrayList<String>();
      newUserList.add(invitedUser.getUsername());
      group.setMemberString(newUserList);

      try {
        cloudNotificationManager.sendJoinGroupRequest(creator, group);
      } catch (Exception exception) {
        log.warn("Reseach Space couldn't notify user by message !");
      }
    }

    assertEquals(1, getActiveRequestCountForUser(existingUser));
  }

  @Test
  public void checkInternalNotificationNonExistingUsersTest() throws IllegalAddChildOperation {

    User creator = createAndSaveUser(getRandomAlphabeticString("user"), Constants.PI_ROLE);
    initUsers(creator);
    logoutAndLoginAs(creator);

    Group group =
        cloudGroupManager.createAndSaveGroup(
            "initialGroupName", creator, creator, GroupType.LAB_GROUP, creator);

    User invitedTempUser = cloudUserManager.createInvitedUser("tempUser1@mail.com");
    List<String> newUserList = new ArrayList<String>();
    newUserList.add(invitedTempUser.getUsername());
    group.setMemberString(newUserList);

    try {
      cloudNotificationManager.sendJoinGroupRequest(creator, group);
    } catch (Exception exception) {
      log.warn("Reseach Space couldn't notify user by message !");
    }

    assertEquals(1, getActiveRequestCountForUser(invitedTempUser));
  }

  @Test
  public void checkInternalNotificationNonExistingUsersAndSignupTest()
      throws IllegalAddChildOperation, UserExistsException {

    User creator = createAndSaveUser(getRandomAlphabeticString("user"), Constants.PI_ROLE);
    initUsers(creator);
    logoutAndLoginAs(creator);

    Group group =
        cloudGroupManager.createAndSaveGroup(
            "initialGroupName", creator, creator, GroupType.LAB_GROUP, creator);

    User invitedTempUser = cloudUserManager.createInvitedUser("tempUser2@mail.com");
    List<String> newUserList = new ArrayList<String>();
    newUserList.add(invitedTempUser.getUsername());
    group.setMemberString(newUserList);

    try {
      cloudNotificationManager.sendJoinGroupRequest(creator, group);
    } catch (Exception exception) {
      log.warn("Reseach Space couldn't notify user by message !");
    }

    assertEquals(1, getActiveRequestCountForUser(invitedTempUser));

    User signupUser = new User();
    String signupUsername = getRandomAlphabeticString("signupUser");
    signupUser.setUsername(signupUsername);
    signupUser.setPassword("123456789");
    signupUser.setEmail("tempUser2@mail.com");
    signupUser.setFirstName("Firstname");
    signupUser.setLastName("Lastname");
    signupUser.setEnabled(true);
    signupUser.setRole(Constants.USER_ROLE);
    signupUser.addRole(roleManager.getRole(Constants.USER_ROLE));

    if (cloudUserManager.checkTempCloudUser(signupUser.getEmail())) {
      signupUser = cloudUserManager.mergeSignupFormWithTempUser(signupUser, signupUser.getEmail());
      userManager.saveUser(signupUser);
    }

    assertEquals(1, getActiveRequestCountForUser(signupUser));
    assertTrue(userManager.userExists(invitedTempUser.getUsername()));
    assertFalse(userManager.userExists(signupUsername));
  }

  @Test
  public void checkInternalNotificationNonExistingUsersAndSignup2Test()
      throws IllegalAddChildOperation, UserExistsException {

    User creator = createAndSaveUser(getRandomAlphabeticString("user"), Constants.PI_ROLE);
    initUsers(creator);
    logoutAndLoginAs(creator);

    Group group =
        cloudGroupManager.createAndSaveGroup(
            "<initialGroupName>", creator, creator, GroupType.LAB_GROUP, creator);

    /** Create a temporary user using userManager.save(tempUser); */
    User invitedTempUser = cloudUserManager.createInvitedUser("tempUser3@mail.com");
    List<String> newUserList = new ArrayList<String>();
    newUserList.add(invitedTempUser.getUsername());
    group.setMemberString(newUserList);

    try {
      cloudNotificationManager.sendJoinGroupRequest(creator, group);
    } catch (Exception exception) {
      log.warn("Reseach Space couldn't notify user by message !");
    }

    assertEquals(1, getActiveRequestCountForUser(invitedTempUser));

    User signupUser = new User();
    String signupUsername = getRandomAlphabeticString("signupUsername");
    signupUser.setUsername(signupUsername);
    signupUser.setPassword("123456789");
    signupUser.setEmail("tempUser3@mail.com");
    signupUser.setFirstName("User Firstname");
    signupUser.setLastName("User Lastname");
    signupUser.setEnabled(true);
    signupUser.setRole(Constants.USER_ROLE);
    signupUser.addRole(roleManager.getRole(Constants.USER_ROLE));

    if (cloudUserManager.checkTempCloudUser(signupUser.getEmail())) {
      signupUser = cloudUserManager.mergeSignupFormWithTempUser(signupUser, signupUser.getEmail());
      userManager.saveUser(signupUser);
      signupUser.setUsername(signupUsername);
      signupUser = userManager.save(signupUser);
    }

    assertEquals(1, getActiveRequestCountForUser(signupUser));
    assertFalse(userManager.userExists(invitedTempUser.getUsername()));
    assertTrue(userManager.userExists(signupUsername));

    Group refreshedGroup = grpMgr.getGroup(group.getId());
    assertFalse(refreshedGroup.getDisplayName().contains("<"));
  }
}
