package com.researchspace.service;

import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.testutils.RSpaceTestUtils.assertAuthExceptionThrown;
import static com.researchspace.testutils.RSpaceTestUtils.logoutCurrUserAndLoginAs;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecord.SharedStatus;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.CompositeRecordOperationResult;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class RecordSharingTest extends SpringTransactionalTest {

  private static final String OTHER_USER_PWD = "user";

  private @Autowired RecordGroupSharingDao regGrpDao;

  private Group group;
  private User piUser;
  private User other;

  @Before
  public void setUp() throws IllegalAddChildOperation {
    piUser = createAndSaveAPi();
    initialiseContentWithEmptyContent(piUser);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  private User createOtherUser() {
    return createAndSaveRandomUser();
  }

  // RSPAC-636
  @Test
  public void shareIntoSubFolderOfSharedFolder() throws Exception {
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), "ROLE_PI");
    User u1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("u1"));
    initialiseContentWithEmptyContent(pi, u1);
    Group labGroup = createGroup("g1", pi);
    addUsersToGroup(pi, labGroup, u1);

    logoutAndLoginAs(pi);
    StructuredDocument toShare = createBasicDocumentInRootFolderWithText(pi, "any");
    Folder groupFolder = folderDao.getSharedFolderForGroup(labGroup);
    Folder toShareInto = folderMgr.createNewFolder(groupFolder.getId(), "ToShareInto", pi);
    ShareConfigElement cfg = new ShareConfigElement();
    cfg.setGroupFolderId(toShareInto.getId());
    cfg.setGroupid(labGroup.getId());
    cfg.setOperation("write");
    toShare =
        extractDoc(sharingMgr.shareRecord(pi, toShare.getId(), new ShareConfigElement[] {cfg}));

    assertEquals(2, toShare.getShortestPathToParent(toShareInto).size());

    // now lets share another record into a folder that is not descended from group folder
    Folder other = folderMgr.createNewFolder(pi.getRootFolder().getId(), "Wrong", pi);
    StructuredDocument otherRecord = createBasicDocumentInRootFolderWithText(pi, "any");
    cfg.setGroupFolderId(other.getId());
    toShare =
        extractDoc(sharingMgr.shareRecord(pi, otherRecord.getId(), new ShareConfigElement[] {cfg}));

    // we share into group folder by default.
    assertEquals(2, toShare.getShortestPathToParent(groupFolder).size());

    // now let's login as regular user, ensure can create new shared subfolder and share into
    // folder:
    logoutAndLoginAs(u1);
    Folder toShareIntoByUser = folderMgr.createNewFolder(groupFolder.getId(), "ToShareInto", pi);
    Record userDoc = createBasicDocumentInRootFolderWithText(u1, "any");
    cfg.setGroupFolderId(toShareIntoByUser.getId());
    userDoc =
        extractDoc(sharingMgr.shareRecord(u1, userDoc.getId(), new ShareConfigElement[] {cfg}));

    assertEquals(2, userDoc.getShortestPathToParent(toShareIntoByUser).size());
    assertEquals(3, userDoc.getShortestPathToParent(groupFolder).size());
  }

  private StructuredDocument extractDoc(
      ServiceOperationResult<List<RecordGroupSharing>> shareRecord) {
    return shareRecord.getEntity().get(0).getShared().asStrucDoc();
  }

  @Test
  public void shareIntoNotebook_RSPAC_913() throws InterruptedException {
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), "ROLE_PI");
    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("u1"));
    User secondUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("u2"));
    User thirdUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("u3"));
    User secondGroupPi = createAndSaveUserIfNotExists(getRandomAlphabeticString("sgpi"), "ROLE_PI");
    User secondGroupUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("sgu"));
    initialiseContentWithEmptyContent(
        pi, user, secondUser, thirdUser, secondGroupPi, secondGroupUser);

    Group labGroup = createGroup("g1", pi);
    addUsersToGroup(pi, labGroup, user, secondUser, thirdUser);

    Group secondLabGroup = createGroup("g2", secondGroupPi);
    addUsersToGroup(secondGroupPi, secondLabGroup, user, secondGroupUser);

    // user creates a notebook and shares it with both groups for read
    logoutAndLoginAs(user);
    Long userRootId = user.getRootFolder().getId();
    Notebook sharedNotebook = createNotebookWithNEntries(userRootId, "shared nbook", 0, user);
    shareNotebookWithGroup(user, sharedNotebook, labGroup, "read");
    shareNotebookWithGroup(user, sharedNotebook, secondLabGroup, "read");

    // user can't share into their own notebook
    BaseRecord usersDoc = createBasicDocumentInRootFolderWithText(user, "any");
    try {
      shareRecordIntoGroupNotebook(usersDoc, sharedNotebook, labGroup, user);
      fail("was expecting IllegalAddChildOperation when trying to share into own notebook");
    } catch (IllegalAddChildOperation e) {
      // expected
    }

    // secondUser creates a document and tries to share it into user's notebook
    logoutAndLoginAs(secondUser);
    BaseRecord doc = createBasicDocumentInRootFolderWithText(secondUser, "any");

    try {
      // sharing should fail, as notebook was shared for "read", not for "write"
      shareRecordIntoGroupNotebook(doc, sharedNotebook, labGroup, secondUser);
      fail(
          "was expecting AuthorizationException exception when trying to share into read-only"
              + " notebook");
    } catch (AuthorizationException e) {
      // expected
    }

    // notebook should still be empty
    sharedNotebook = folderMgr.getNotebook(sharedNotebook.getId());
    assertEquals(0, sharedNotebook.getEntryCount());

    // user shares notebook again with first group, this time for write
    logoutAndLoginAs(user);
    unshareRecordORNotebookWithGroup(user, sharedNotebook, labGroup, "read");
    shareNotebookWithGroup(user, sharedNotebook, labGroup, "write");

    // secondUser is now able to share the document into notebook
    logoutAndLoginAs(secondUser);
    doc = shareRecordIntoGroupNotebook(doc, sharedNotebook, labGroup, secondUser).get().getShared();

    // notebook should have one entry now
    sharedNotebook = folderMgr.getNotebook(sharedNotebook.getId());
    assertEquals(1, sharedNotebook.getEntryCount());

    // document owner should keep all permissions
    assertTrue(permissionUtils.isPermitted(doc, PermissionType.READ, secondUser));
    assertTrue(permissionUtils.isPermitted(doc, PermissionType.WRITE, secondUser));
    assertTrue(permissionUtils.isPermitted(doc, PermissionType.DELETE, secondUser));

    // notebook owner should also have all the permissions
    logoutAndLoginAs(user);
    assertTrue(permissionUtils.isPermitted(doc, PermissionType.READ, user));
    assertTrue(permissionUtils.isPermitted(doc, PermissionType.WRITE, user));
    assertTrue(permissionUtils.isPermitted(doc, PermissionType.DELETE, user));

    // group pi and another user can edit the entry, but can't delete it
    logoutAndLoginAs(pi);
    assertTrue(permissionUtils.isPermitted(doc, PermissionType.READ, thirdUser));
    assertTrue(permissionUtils.isPermitted(doc, PermissionType.WRITE, thirdUser));
    assertFalse(permissionUtils.isPermitted(doc, PermissionType.DELETE, thirdUser));
    logoutAndLoginAs(thirdUser);
    assertTrue(permissionUtils.isPermitted(doc, PermissionType.READ, thirdUser));
    assertTrue(permissionUtils.isPermitted(doc, PermissionType.WRITE, thirdUser));
    assertFalse(permissionUtils.isPermitted(doc, PermissionType.DELETE, thirdUser));

    // in second group, where notebook was shared for "read", neither group member nor pi can edit
    logoutAndLoginAs(secondGroupUser);
    assertTrue(permissionUtils.isPermitted(doc, PermissionType.READ, secondGroupUser));
    assertFalse(permissionUtils.isPermitted(doc, PermissionType.WRITE, secondGroupUser));
    assertFalse(permissionUtils.isPermitted(doc, PermissionType.DELETE, secondGroupUser));

    logoutAndLoginAs(secondGroupPi);
    assertTrue(permissionUtils.isPermitted(doc, PermissionType.READ, secondGroupPi));
    assertFalse(permissionUtils.isPermitted(doc, PermissionType.WRITE, secondGroupPi));
    assertFalse(permissionUtils.isPermitted(doc, PermissionType.DELETE, secondGroupPi));
  }

  @Test
  public void deletionOfIndividualSharedItemFromOwnersWSUnsharesFromAll() throws Exception {
    // set up lab group with pi and 2 users
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), "ROLE_PI");
    User u1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("u1"));
    User u2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("u1"));
    initialiseContentWithEmptyContent(pi, u1, u2);
    Group labGroup = createGroup("g1", pi);
    addUsersToGroup(pi, labGroup, u1, u2);
    // u1 creates a record and shares with u2
    logoutAndLoginAs(u1);
    Record toShare = createBasicDocumentInRootFolderWithText(u1, "any");

    sharingMgr.shareRecord(u1, toShare.getId(), getIndividualShareCommand(u2, "write"));
    // check that u2 can read the shared record:
    logoutAndLoginAs(u2);
    assertTrue(permissionUtils.isPermitted(toShare, PermissionType.READ, u2));

    // now u1 deletes the record from own folder, with side-effect of unsharing the record
    logoutAndLoginAs(u1);
    recordDeletionMgr.deleteRecord(u1.getRootFolder().getId(), toShare.getId(), u1);
    // and u2 can no longer access
    logoutAndLoginAs(u2);
    assertFalse(permissionUtils.isPermitted(toShare, PermissionType.READ, u2));
  }

  @Test
  public void deletionOfIndividualSharedItemByOwnerOrNonOwnerFromSharedFolderKeepsItemForOwner()
      throws Exception {

    // set up lab group with pi and 2 users
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), "ROLE_PI");
    User u1Owner = createAndSaveUserIfNotExists(getRandomAlphabeticString("u1"));
    User u2Owner = createAndSaveUserIfNotExists(getRandomAlphabeticString("u1"));
    initialiseContentWithEmptyContent(pi, u1Owner, u2Owner);
    Group labGroup = createGroup("g1", pi);
    addUsersToGroup(pi, labGroup, u1Owner, u2Owner);

    // u1 creates a record and shares with u2
    logoutAndLoginAs(u1Owner);
    int INITIAL_COUNT = getNumChildrenInRootFolder(u1Owner);
    Record toShare = createBasicDocumentInRootFolderWithText(u1Owner, "any");
    assertEquals(INITIAL_COUNT + 1, getNumChildrenInRootFolder(u1Owner));

    sharingMgr.shareRecord(u1Owner, toShare.getId(), getIndividualShareCommand(u2Owner, "write"));
    // check that u2 can read the shared record:
    logoutAndLoginAs(u2Owner);
    assertTrue(permissionUtils.isPermitted(toShare, PermissionType.READ, u2Owner));

    // now u1 deletes the record from SHARED folder
    logoutAndLoginAs(u1Owner);
    Folder u2SharedFolder = folderDao.getIndividualSharedFolderForUsers(u1Owner, u2Owner, null);
    recordDeletionMgr.deleteRecord(u2SharedFolder.getId(), toShare.getId(), u1Owner);
    // and u2 can no longer access
    logoutAndLoginAs(u2Owner);
    assertFalse(permissionUtils.isPermitted(toShare, PermissionType.READ, u2Owner));
    // but is not deleted from u1 workspace:
    assertEquals(INITIAL_COUNT + 1, getNumChildrenInRootFolder(u1Owner));

    // now we reshare. This time the *non-owning* individual will delete from the shared folder.
    logoutAndLoginAs(u1Owner);
    sharingMgr.shareRecord(u1Owner, toShare.getId(), getIndividualShareCommand(u2Owner, "write"));
    logoutAndLoginAs(u2Owner);
    recordDeletionMgr.deleteRecord(u2SharedFolder.getId(), toShare.getId(), u2Owner);

    // now we check that this is unshared:
    assertEquals(
        0,
        folderDao.getIndividualSharedFolderForUsers(u1Owner, u2Owner, null).getChildrens().size());
    // and that u2 can no longer see it:
    assertFalse(permissionUtils.isPermitted(toShare, PermissionType.READ, u2Owner));
    logoutAndLoginAs(u1Owner);
  }

  @Test
  public void deletionOfSharedRecordFromOwnersFlderUnsharesForAll() throws Exception {

    StructuredDocument toShare = setUpOtherUserAndRecordToShare();
    sharingMgr.shareRecord(piUser, toShare.getId(), getGrpShareCommand(group, "write"));
    User extra = createAndSaveRandomUser();
    initUserFolder(extra);

    // create and share with a new group.
    Group g = createGroup("group2", piUser);
    addUsersToGroup(piUser, g, extra);
    sharingMgr.shareRecord(piUser, toShare.getId(), getGrpShareCommand(g, "write"));
    logoutAndLoginAs(extra);
    assertTrue(permissionUtils.isPermitted(toShare, PermissionType.READ, extra));
    logoutAndLoginAs(other);
    assertTrue(permissionUtils.isPermitted(toShare, PermissionType.READ, other));

    // sanity check
    assertEquals(2, regGrpDao.getUsersOrGroupsWithRecordAccess(toShare.getId()).size());

    logoutAndLoginAs(piUser);
    // this should delete record for EVeryone, in both groups.
    recordDeletionMgr.deleteRecord(piUser.getRootFolder().getId(), toShare.getId(), piUser);

    // should not be in any shared group now
    assertEquals(0, regGrpDao.getUsersOrGroupsWithRecordAccess(toShare.getId()).size());
    // and should no longer be visible:
    logoutAndLoginAs(extra);
    assertFalse(permissionUtils.isPermitted(toShare, PermissionType.READ, extra));
    logoutAndLoginAs(other);
    assertFalse(permissionUtils.isPermitted(toShare, PermissionType.READ, other));
  }

  public void deletionOfSharedRecordFromSharedFlderUnsharesFromThatGroup() throws Exception {
    final StructuredDocument toShare = setUpOtherUserAndRecordToShare();

    // now share record
    sharingMgr.shareRecord(piUser, toShare.getId(), getGrpShareCommand(group, "write"));
    assertTrue(regGrpDao.getRecordsSharedByGroup(group.getId()).contains(toShare));
    logoutCurrUserAndLoginAs(other.getUsername(), OTHER_USER_PWD);
    assertTrue(permissionUtils.isPermitted(toShare, PermissionType.READ, other));
    final Folder sharedFolder = folderDao.getSharedFolderForGroup(group);
    int NUM_CHILDREN_IN_GRPFOLDERS = sharedFolder.getChildren().size();

    // deletion attempt by non-auth user should throw exception
    assertAuthorisationExceptionThrown(
        () -> recordDeletionMgr.deleteRecord(sharedFolder.getId(), toShare.getId(), other));

    // still should be shared
    assertTrue(regGrpDao.getRecordsSharedByGroup(group.getId()).contains(toShare));
    // user can delete - he;s the owner.
    logoutCurrUserAndLoginAs(piUser.getUsername(), TESTPASSWD);
    recordDeletionMgr.deleteRecord(sharedFolder.getId(), toShare.getId(), piUser);
    // is unshared; back to original state.
    assertFalse(regGrpDao.getRecordsSharedByGroup(group.getId()).contains(toShare));
    assertEquals(
        NUM_CHILDREN_IN_GRPFOLDERS - 1,
        folderDao.getSharedFolderForGroup(group).getChildren().size());
    // now, it is unshared, and inaccessible.
    logoutCurrUserAndLoginAs(other.getUsername(), OTHER_USER_PWD);
    assertFalse(permissionUtils.isPermitted(toShare, PermissionType.READ, other));
  }

  @Test // RSPAC-2032
  public void afterRemovingUserFromGroupRecordsGetUnshared() {
    // set up piUser, other, and a group
    setUpOtherUserAndRecordToShare();

    // other user can see group folder, PI can see both group and other's folder
    assertEquals(1, folderDao.getLabGroupFolderForUser(other).getChildren().size());
    assertEquals(2, folderDao.getLabGroupFolderForUser(piUser).getChildren().size());

    // as other user create two documents, then share with pi individually, and through group
    StructuredDocument userDoc1 = createBasicDocumentInRootFolderWithText(other, "any1");
    sharingMgr.shareRecord(other, userDoc1.getId(), getGrpShareCommand(group, "write"));
    sharingMgr.shareRecord(other, userDoc1.getId(), getIndividualShareCommand(piUser, "read"));

    StructuredDocument userDoc2 = createBasicDocumentInRootFolderWithText(other, "any2");
    sharingMgr.shareRecord(other, userDoc2.getId(), getGrpShareCommand(group, "read"));

    // verify sharing information, and that pi can edit first doc through group share permission
    List<RecordGroupSharing> sharingInfo1 = sharingMgr.getRecordSharingInfo(userDoc1.getId());
    assertEquals(2, sharingInfo1.size());
    assertTrue(permissionUtils.isPermitted(userDoc1, PermissionType.WRITE, piUser));

    List<RecordGroupSharing> sharingInfo2 = sharingMgr.getRecordSharingInfo(userDoc2.getId());
    assertEquals(1, sharingInfo2.size());
    assertFalse(permissionUtils.isPermitted(userDoc2, PermissionType.WRITE, piUser));
    assertTrue(permissionUtils.isPermitted(userDoc2, PermissionType.READ, piUser));

    // remove other user from group
    grpMgr.removeUserFromGroup(other.getUsername(), group.getId(), piUser);

    // other can no longer see group folder, PI can no longer see other's folder
    assertEquals(0, folderDao.getLabGroupFolderForUser(other).getChildren().size());
    assertEquals(1, folderDao.getLabGroupFolderForUser(piUser).getChildren().size());

    // verify sharing information and that pi can still view the first document through individual
    // share
    sharingInfo1 = sharingMgr.getRecordSharingInfo(userDoc1.getId());
    assertEquals(1, sharingInfo1.size());
    assertFalse(permissionUtils.isPermitted(userDoc1, PermissionType.WRITE, piUser));
    assertTrue(permissionUtils.isPermitted(userDoc1, PermissionType.READ, piUser));
    // but pi shouldn't see second doc anymore
    sharingInfo2 = sharingMgr.getRecordSharingInfo(userDoc2.getId());
    assertEquals(0, sharingInfo2.size());
    assertFalse(permissionUtils.isPermitted(userDoc2, PermissionType.READ, piUser));

    // unshare the first doc individually, confirm pi can no longer see
    sharingMgr.unshareRecord(other, userDoc1.getId(), getIndividualShareCommand(piUser, "read"));
    sharingInfo1 = sharingMgr.getRecordSharingInfo(userDoc1.getId());
    assertEquals(0, sharingInfo1.size());
    assertFalse(permissionUtils.isPermitted(userDoc1, PermissionType.READ, piUser));
  }

  @Test
  public void cannotShareWithUnrelatedUserOrGroup() throws Exception {

    // create user and them to own group, with just a piuser
    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("any"));
    initialiseContentWithEmptyContent(user);
    Group userGroup = createGroup("userGroup", piUser);
    addUsersToGroup(piUser, userGroup, user);
    // create new doc and confirm not shared
    StructuredDocument testDoc = createBasicDocumentInRootFolderWithText(user, "testDoc");
    sharingMgr.updateSharedStatusOfRecords(toList(testDoc), user);
    assertEquals(SharedStatus.UNSHARED, testDoc.getSharedStatus());

    // init other user in other group
    setUpOtherUserAndRecordToShare();

    // attempt individual share with other user
    ShareConfigElement indivCommand = new ShareConfigElement(other.getId(), "write");
    indivCommand.setUserId(other.getId());
    ShareConfigElement[] indivSharingCfgElem = new ShareConfigElement[] {indivCommand};

    AuthorizationException authorizationException =
        assertThrows(
            AuthorizationException.class,
            () ->
                sharingMgr.shareRecord(
                    userDao.get(user.getId()), testDoc.getId(), indivSharingCfgElem));
    assertEquals(
        "Unauthorized attempt by ["
            + user.getUsername()
            + "] to share with user ["
            + other.getUsername()
            + "]",
        authorizationException.getMessage());

    // attempt group share, with other group
    ShareConfigElement groupCommand = new ShareConfigElement(group.getId(), "write");
    groupCommand.setGroupid(group.getId());
    ShareConfigElement[] groupSharingCfgElem = new ShareConfigElement[] {groupCommand};
    authorizationException =
        assertThrows(
            AuthorizationException.class,
            () ->
                sharingMgr.shareRecord(
                    userDao.get(user.getId()), testDoc.getId(), groupSharingCfgElem));
    assertEquals(
        "Unauthorized attempt by ["
            + user.getUsername()
            + "] to share with group ["
            + group.getUniqueName()
            + "]",
        authorizationException.getMessage());

    // confirm test record not shared with anyone
    sharingMgr.updateSharedStatusOfRecords(toList(testDoc), piUser);
    assertEquals(SharedStatus.UNSHARED, testDoc.getSharedStatus());
    assertEquals(0, regGrpDao.getRecordsSharedByGroup(other.getId()).size());
  }

  // RSPAC-345
  @Test
  public void newUserCanReadSharedFolderAnd3FixedGroupsubfolders() {
    User newuser = createAndSaveUserIfNotExists(getRandomAlphabeticString("any"));
    initialiseContentWithEmptyContent(newuser);
    Folder sharedRoot = folderDao.getUserSharedFolder(newuser);
    logoutAndLoginAs(newuser);
    assertTrue(permissionUtils.isPermitted(sharedRoot, PermissionType.READ, newuser));

    Folder colllGrp = folderDao.getCollaborationGroupsSharedFolderForUser(newuser);
    assertTrue(permissionUtils.isPermitted(colllGrp, PermissionType.READ, newuser));

    Folder indi = folderDao.getIndividualSharedItemsFolderForUser(newuser);
    assertTrue(permissionUtils.isPermitted(indi, PermissionType.READ, newuser));

    Folder labGrp = folderDao.getLabGroupFolderForUser(newuser);
    assertTrue(permissionUtils.isPermitted(labGrp, PermissionType.READ, newuser));
    RSpaceTestUtils.logout();
  }

  @Test
  public void sharingContinuesWithOtherGroupsIfDocIsSharedWithOneGroup() {
    final StructuredDocument docToShare1 = setUpOtherUserAndRecordToShare();
    group = reloadGroup(group);
    logoutAndLoginAs(piUser);
    shareRecordWithGroup(piUser, group, docToShare1);
    // now share doc *again* with group but also with  a new individual
    User newGrpMember = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(newGrpMember);
    grpMgr.addUserToGroup(newGrpMember.getUsername(), group.getId(), RoleInGroup.DEFAULT);
    ShareConfigElement gsCommand = new ShareConfigElement(group.getId(), "read");
    ShareConfigElement gsCommand2 = new ShareConfigElement(newGrpMember.getId(), "read");
    gsCommand2.setUserId(newGrpMember.getId());
    ShareConfigElement[] cfg = new ShareConfigElement[] {gsCommand, gsCommand2};
    // this shouldn't share again with group, but should share with user
    logoutAndLoginAs(piUser);
    sharingMgr.shareRecord(piUser, docToShare1.getId(), cfg);
    // and assert that doc was shared with individual:
    assertEquals(
        1,
        regGrpDao
            .findRecordsSharedWithUserOrGroup(
                newGrpMember.getId(), TransformerUtils.toList(docToShare1.getId()))
            .size());
  }

  @Test
  public void deletePermissionsInGrpFolder() throws Exception {
    StructuredDocument toShare = setUpOtherUserAndRecordToShare();
    sharingMgr.shareRecord(piUser, toShare.getId(), getGrpShareCommand(group, "write"));
    logoutAndLoginAs(other);

    assertFalse(permissionUtils.isPermitted(toShare, PermissionType.DELETE, other));
    // can't copy in shared folder
    assertFalse(permissionUtils.isPermitted(toShare, PermissionType.COPY, other));
    logoutAndLoginAs(piUser);
    assertTrue(permissionUtils.isPermitted(toShare, PermissionType.DELETE, piUser));
    // cant copy in shared folder
    assertFalse(permissionUtils.isPermitted(toShare, PermissionType.COPY, other));

    User newGrpMember = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(newGrpMember);
    grpMgr.addUserToGroup(newGrpMember.getUsername(), group.getId(), RoleInGroup.DEFAULT);
    // new user can't delete or copy either.
    logoutAndLoginAs(newGrpMember);
    assertFalse(permissionUtils.isPermitted(toShare, PermissionType.DELETE, newGrpMember));
    assertFalse(permissionUtils.isPermitted(toShare, PermissionType.COPY, other));
  }

  @Test
  public void renamePermissionsInGrpFolder() throws Exception {
    final StructuredDocument toShare = setUpOtherUserAndRecordToShare();
    group = reloadGroup(group);
    // now share record
    sharingMgr.shareRecord(piUser, toShare.getId(), getGrpShareCommand(group, "write"));
    logoutAndLoginAs(other);
    // other user can't rename even though is shared with group.
    assertAuthorisationExceptionThrown(() -> recordMgr.renameRecord("XXX", toShare.getId(), other));

    // get user to create a new folder in the group:
    logoutAndLoginAs(piUser);
    Folder grpFolder = folderDao.getSharedFolderForGroup(group);
    final Folder subf = folderMgr.createNewFolder(grpFolder.getId(), "GROUPSF", piUser);
    logoutAndLoginAs(other);

    // other user can't rename a folder created by group admin even though is shared with group.
    assertAuthExceptionThrown(() -> recordMgr.renameRecord("XXX", subf.getId(), other));
  }

  @Test
  public void sharedRecordRenamedByOtherUserHasNameAndModifiedByUpdated() {
    User recordCreator = createOtherUser();
    initUserFolder(recordCreator);

    RSForm anyForm = formDao.getAll().get(0);
    StructuredDocument recordToShare =
        recordMgr.createNewStructuredDocument(
            recordCreator.getRootFolder().getId(), anyForm.getId(), recordCreator);

    group = createGroup("grp1", piUser);
    addUsersToGroup(piUser, group, recordCreator);
    group = reloadGroup(group);

    logoutAndLoginAs(recordCreator);
    sharingMgr.shareRecord(
        recordCreator, recordToShare.getId(), getGrpShareCommand(group, "write"));

    logoutAndLoginAs(piUser);

    String updatedName = "A new name";
    recordMgr.renameRecord(updatedName, recordToShare.getId(), piUser);
    Record renamed = recordMgr.get(recordToShare.getId());

    assertEquals(renamed.getName(), updatedName);
    assertEquals(renamed.getModifiedBy(), piUser.getUsername());
  }

  private ShareConfigElement[] getGrpShareCommand(Group g, String perm) {
    ShareConfigElement gsCommand = new ShareConfigElement(g.getId(), perm);
    return new ShareConfigElement[] {gsCommand};
  }

  private ShareConfigElement[] getIndividualShareCommand(User toShareWith, String perm) {
    ShareConfigElement gsCommand = new ShareConfigElement(toShareWith.getId(), perm);
    gsCommand.setUserId(toShareWith.getId());
    return new ShareConfigElement[] {gsCommand};
  }

  private void initUserFolder(User newGrpMember) throws IllegalAddChildOperation {
    contentInitializer.setCustomInitActive(false);
    contentInitializer.init(newGrpMember.getId());
    contentInitializer.setCustomInitActive(true);
  }

  /**
   * Creates a record owned by user, who is PI, and creates a group called 'grp1' which contains
   * another user, 'other'.
   *
   * @return
   */
  private StructuredDocument setUpOtherUserAndRecordToShare() {
    RSForm anyForm = formDao.getAll().get(0);
    StructuredDocument recordToShare =
        recordMgr.createNewStructuredDocument(
            piUser.getRootFolder().getId(), anyForm.getId(), piUser);
    other = createOtherUser();
    initUserFolder(other);
    group = createGroup("grop1", piUser);
    addUsersToGroup(piUser, group, other);
    return recordToShare;
  }

  @Test
  public void labAdminPermissionsInSharedGroupFolder()
      throws IllegalAddChildOperation, DocumentAlreadyEditedException {
    // set up labgroup with pi, admin and 2 users
    User u1 = createAndSaveRandomUser();
    User u2 = createAndSaveRandomUser();
    User labAdmin = createAndSaveRandomUser();
    User pi = createAndSaveAPi();
    initialiseContentWithEmptyContent(u1, u2, labAdmin, pi);
    User admin = logoutAndLoginAsSysAdmin();
    Group labGroup = createGroup("any", pi);
    addUsersToGroup(pi, labGroup, u1, u2);

    logoutAndLoginAs(u1);
    StructuredDocument toShare = createBasicDocumentInRootFolderWithText(u1, "any");
    // u1 shares doc with group
    shareRecordWithGroup(u1, labGroup, toShare);
    // now we add a lab admin
    grpMgr.addUserToGroup(labAdmin.getUsername(), labGroup.getId(), RoleInGroup.RS_LAB_ADMIN);

    // logout and login as lab admin
    logoutAndLoginAs(labAdmin);
    Folder labFolder = folderDao.getSharedFolderForGroup(labGroup);
    // refresh after sharing
    toShare = (StructuredDocument) recordMgr.get(toShare.getId());
    // assert has delete permission
    assertTrue(permissionUtils.isPermitted(toShare, PermissionType.DELETE, labAdmin));
    // now delete...
    CompositeRecordOperationResult result =
        recordDeletionMgr.deleteRecord(labFolder.getId(), toShare.getId(), labAdmin);
    assertTrue(result.getRecords().contains(toShare));
  }
}
