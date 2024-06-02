package com.researchspace.service;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.common.collect.Lists;
import com.researchspace.Constants;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.webapp.controller.GroupController;
import com.researchspace.webapp.controller.MVCTestBase;
import java.security.Principal;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/***
 *  This MVCIT test replicates the issue that is described in the jira RSDEV-142
 */
public class DuplicateAndAutoshareMVCIT extends MVCTestBase {

  private static final String SYSTEM_USER_REGISTRATION_BATCH_CREATE =
      "/system/userRegistration/batchCreate";
  private static final String USERS_ADDED_SUCCESS = "Users added to the group.";
  private static final String REMOVE_USER_SUCCESS = "redirect:/groups/view/";

  private @Autowired GroupController groupController;
  private @Autowired RecordSharingManager recordSharingManager;

  private Principal sysAdminPrincipal;
  private User sysAdmin;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    sysAdmin = createAndSaveUser(getRandomName(10), Constants.SYSADMIN_ROLE);
    initUser(sysAdmin);
    sysAdminPrincipal = new MockPrincipal(sysAdmin.getUsername());
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testDuplicateAndAutoshareIsSuccessfull() throws Exception {
    // GIVEN

    // 1. create users and groups
    logoutAndLoginAs(sysAdmin);
    createUsersAndsGroups();

    User nikdenik1 = userMgr.getUserByUsername("nikdenik1", true);
    User nikdenik2 = userMgr.getUserByUsername("nikdenik2", true);
    User nikdenik3 = userMgr.getUserByUsername("nikdenik3", true);
    User nikdenik4 = userMgr.getUserByUsername("nikdenik4", true);

    Set<Group> labs = nikdenik2.getGroups();
    Group lab1 = labs.stream().filter(g -> g.getUniqueName().startsWith("Lab1")).findFirst().get();
    Group lab2 = labs.stream().filter(g -> g.getUniqueName().startsWith("Lab2")).findFirst().get();
    Group lab3 = labs.stream().filter(g -> g.getUniqueName().startsWith("Lab3")).findFirst().get();

    // 2. nikdenik1 enables autoshare for himself on Lab1 and Lab3
    assertAndEnableAutoshareForSelfUserInGroup(nikdenik1, lab1.getId());

    // 3. nikdenik2 enables autoshare for himself on Lab1
    assertAndEnableAutoshareForSelfUserInGroup(nikdenik2, lab1.getId());

    // 4. nikdenik2 enables autoshare for himself on Lab1
    assertAndEnableAutoshareForSelfUserInGroup(nikdenik2, lab3.getId());

    // 5. sysadmin change the PI on Lab2 to become nikdenik1
    logoutAndLoginAsSysAdmin();
    assertNull(groupController.swapPi(lab2.getId(), nikdenik1.getId()).getErrorMsg());

    // 6. sysadmin removes nikdenik2 from Lab1, Lab2, Lab3
    assertEquals(
        REMOVE_USER_SUCCESS + lab1.getId(),
        groupController.removeUser(model, lab1.getId(), nikdenik2.getId()));
    assertEquals(
        REMOVE_USER_SUCCESS + lab2.getId(),
        groupController.removeUser(model, lab2.getId(), nikdenik2.getId()));
    assertEquals(
        REMOVE_USER_SUCCESS + lab3.getId(),
        groupController.removeUser(model, lab3.getId(), nikdenik2.getId()));

    // 7. nikdenik1 (as PI) enables full group autoshare on Lab1
    logoutAndLoginAs(nikdenik1);
    assertNull(groupController.enableAutoshare(lab1.getId()).getErrorMsg()); // / error here

    // 8. sysadmin adds nikdenik2 to Lab2
    logoutAndLoginAsSysAdmin();
    lab2.setMemberString(Lists.newArrayList(nikdenik2.getUsername()));
    assertEquals(USERS_ADDED_SUCCESS, groupController.addUser(sysAdminPrincipal, lab2, null));

    // 9. sysadmin adds nikdenik4 to Lab3
    lab3.setMemberString(Lists.newArrayList(nikdenik4.getUsername()));
    assertEquals(USERS_ADDED_SUCCESS, groupController.addUser(sysAdminPrincipal, lab3, null));

    // 10. nikdenik3 (as PI) enables user autoshare on nikdenik4 for Lab3
    assertAndEnableAutoshareForUserInGroup(nikdenik3, nikdenik4, lab3.getId());

    // 11. sysadmin adds nikdenik4 to Lab1
    logoutAndLoginAsSysAdmin();
    lab1.setMemberString(Lists.newArrayList(nikdenik4.getUsername()));
    assertEquals(USERS_ADDED_SUCCESS, groupController.addUser(sysAdminPrincipal, lab1, null));

    // 12. nikdenik1 (as PI) enables full group autoshare on Lab2
    logoutAndLoginAs(nikdenik1);
    groupController.enableAutoshare(lab2.getId());

    // WHEN
    // nikdenik4 (that already has the autoshare option enabled)
    // tries to DUPLICATE a document from his workspace
    int initialSize = recordSharingManager.getSharedRecordsForUser(nikdenik4).size();
    userCreateDocumentAndDuplicate(nikdenik4); // HTTP call

    // THEN
    assertEquals(
        "The file has not been autoshared after being duplicated",
        initialSize + 1,
        recordSharingManager.getSharedRecordsForUser(nikdenik4).size());
  }

  private void createUsersAndsGroups() throws Exception {

    String nikdenik1 = getUserRegistrationInfoJson("nikdenik1", TESTPASSWD, Constants.PI_ROLE);
    String nikdenik2 = getUserRegistrationInfoJson("nikdenik2", TESTPASSWD, Constants.PI_ROLE);
    String nikdenik3 = getUserRegistrationInfoJson("nikdenik3", TESTPASSWD, Constants.PI_ROLE);
    String nikdenik4 = getUserRegistrationInfoJson("nikdenik4", TESTPASSWD, Constants.USER_ROLE);

    String group1 = getGroupPublicInfoJson("Lab1", "Lab1", "nikdenik1", "nikdenik2", "nikdenik3");
    String group2 = getGroupPublicInfoJson("Lab2", "Lab2", "nikdenik2", "nikdenik1");
    String group3 = getGroupPublicInfoJson("Lab3", "Lab3", "nikdenik3", "nikdenik2");

    String json =
        "{\"parsedUsers\":["
            + nikdenik1
            + ","
            + nikdenik2
            + ","
            + nikdenik3
            + ","
            + nikdenik4
            + "],"
            + "\"parsedGroups\":["
            + group1
            + ","
            + group2
            + ","
            + group3
            + "]}";

    // check call with no users or groups
    mockMvc
        .perform(
            post(SYSTEM_USER_REGISTRATION_BATCH_CREATE)
                .principal(sysAdminPrincipal)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(json))
        .andExpect(status().is2xxSuccessful());
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
      String uniqueName, String name, String piUsername, String... otherMemberUsernames) {
    String json =
        "{\"uniqueName\":\""
            + uniqueName
            + "\",\"displayName\":\""
            + name
            + "\","
            + "\"pi\":\""
            + piUsername
            + "\"";
    if (otherMemberUsernames != null && otherMemberUsernames.length > 0) {
      String membersString = "\"" + StringUtils.join(otherMemberUsernames, "\",\"") + "\"";
      json += ",\"otherMembers\":[" + membersString + "]";
    }
    return json + "}";
  }

  private void userCreateDocumentAndDuplicate(User user) throws Exception {
    initUser(user);
    logoutAndLoginAs(user);
    mockPrincipal = new MockPrincipal(user.getUsername());
    Folder root = folderMgr.getRootRecordForUser(user, user);
    // note that the created document is not autoshared since the method is a test
    // util that just create a document without triggering autoshare
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "any filled text");

    mockMvc
        .perform(
            post("/workspace/ajax/copy")
                .param("parentFolderId", root.getId() + "")
                .param("idToCopy[]", doc.getId() + "")
                .param("newName[]", doc.getId() + "")
                .principal(mockPrincipal))
        .andExpect(status().is2xxSuccessful());
  }

  private void assertAndEnableAutoshareForUserInGroup(
      User userPi, User userToEnableAutoshare, Long groupId) throws Exception {
    logoutAndLoginAs(userPi);
    // create 2  documents that will be autoshared
    createBasicDocumentInRootFolderWithText(userToEnableAutoshare, "any text1");

    String folderName = "folderNameTest";
    mockMvc
        .perform(
            post(
                    "/userform/ajax/enableAutoshare/{groupId}/{userId}",
                    groupId,
                    userToEnableAutoshare.getId())
                .principal(userPi::getUsername)
                .content(String.format("{\"autoshareFolderName\":\"%s\"}", folderName))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());
  }

  private void assertAndEnableAutoshareForSelfUserInGroup(User user, Long groupId)
      throws Exception {
    assertAndEnableAutoshareForUserInGroup(user, user, groupId);
  }
}
