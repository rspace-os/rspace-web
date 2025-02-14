package com.researchspace.service;

import static com.researchspace.core.util.TransformerUtils.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.Constants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.Community;
import com.researchspace.model.EditStatus;
import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.DefaultPermissionFactory;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionFactory;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.permissions.RecordPermissionAdapter;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecord.SharedStatus;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.controller.WorkspacePermissionsDTOBuilder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * This is for tests with object relations and multiple transactions that are too complicated to run
 * in a {@link SpringTransactionalTest}
 */
public class RecordSharingIT extends RealTransactionSpringTestBase {

  private @Autowired RecordGroupSharingDao groupShareDao;
  private @Autowired RecordSharingManager recordSharingManager;
  private @Autowired WorkspacePermissionsDTOBuilder permBuilder;

  private Group group;
  private User piUser;
  private User other;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    permBuilder.setRecMgr(recordMgr);
    piUser = doCreateAndInitUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(piUser);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void shareWithUser() throws Exception {
    // tests sharing with an individual user rather than a group.
    StructuredDocument recordToShare = setUpOtherUserAndRecordToShare();
    ShareConfigElement gsCommand = new ShareConfigElement(other.getId(), "write");
    // only one can be set
    gsCommand.setUserId(other.getId());

    ServiceOperationResult<List<RecordGroupSharing>> sharedRecord =
        sharingMgr.shareRecord(
            reloadPiUser(), recordToShare.getId(), new ShareConfigElement[] {gsCommand});
    // test appears as shared
    assertTrue(sharedRecord.isSucceeded());

    openTransaction();
    assertEquals(1, groupShareDao.getRecordsSharedByGroup(other.getId()).size());
    commitTransaction();

    List<RecordGroupSharing> shared = recordSharingManager.getSharedRecordsForUser(piUser);
    assertEquals(1, shared.size());
    RecordGroupSharing rgs = shared.get(0);

    openTransaction();
    Folder sharedIndivid = folderDao.getIndividualSharedFolderForUsers(piUser, other, null);
    // check that the shared folders are created and inserted
    assertNotNull(sharedIndivid);
    assertTrue(
        folderDao
            .getIndividualSharedItemsFolderForUser(piUser)
            .getChildrens()
            .contains(sharedIndivid));
    assertTrue(
        folderDao
            .getIndividualSharedItemsFolderForUser(other)
            .getChildrens()
            .contains(sharedIndivid));
    assertTrue(sharedIndivid.getChildrens().contains(recordToShare));
    assertEquals(1, sharedIndivid.getChildrens().size());
    commitTransaction();

    // share again.. should be unsuccessful
    sharedRecord =
        sharingMgr.shareRecord(
            reloadPiUser(), recordToShare.getId(), new ShareConfigElement[] {gsCommand});
    assertFalse(sharedRecord.isSucceeded());

    openTransaction();
    sharedIndivid = folderDao.getIndividualSharedFolderForUsers(piUser, other, null);
    assertEquals(1, sharedIndivid.getChildrens().size());
    shared = recordSharingManager.getSharedRecordsForUser(piUser);
    assertEquals(1, shared.size());
    commitTransaction();

    sharingMgr.updatePermissionForRecord(rgs.getId(), "read", piUser.getUsername());

    sharingMgr.unshareRecord(
        reloadPiUser(), recordToShare.getId(), new ShareConfigElement[] {gsCommand});

    // test does not appear as shared
    openTransaction();
    assertEquals(0, groupShareDao.getRecordsSharedByGroup(other.getId()).size());
    assertEquals(0, groupShareDao.getSharedRecordsForUser(piUser).size());
    commitTransaction();

    // shared folder still exists
    openTransaction();
    sharedIndivid = folderDao.get(sharedIndivid.getId());
    Set<BaseRecord> childrens = new HashSet();
    childrens.addAll(sharedIndivid.getChildrens());
    assertNotNull(sharedIndivid);
    assertTrue(
        folderDao
            .getIndividualSharedItemsFolderForUser(piUser)
            .getChildrens()
            .contains(sharedIndivid));
    assertTrue(
        folderDao
            .getIndividualSharedItemsFolderForUser(other)
            .getChildrens()
            .contains(sharedIndivid));
    // but shared record is removed.
    assertFalse(sharedIndivid.getChildrens().contains(recordToShare));
    assertTrue(recordToShare.getShortestPathToParent(sharedIndivid).isEmpty());
    assertFalse(recordToShare.getShortestPathToParent(piUser.getRootFolder()).isEmpty());
    commitTransaction();
  }

  @Test
  public void shareDocument() throws Exception {

    final StructuredDocument recordToShare = setUpOtherUserAndRecordToShare();
    group = reloadGroup(group);

    openTransaction();
    Folder labGrpShared = folderDao.getSharedFolderForGroup(group);
    int b4 = labGrpShared.getChildren().size();
    // group can't edit record before it's shared
    logoutAndLoginAs(other);
    assertFalse(groupCanEditRecord(group, recordToShare));
    commitTransaction();

    // now we'll login as other an attempt to share, this should be blocked - only owner can share:
    final ShareConfigElement gsCommand = new ShareConfigElement(group.getId(), "write");
    logoutAndLoginAs(other);

    assertAuthorisationExceptionThrown(
        () ->
            sharingMgr.shareRecord(
                reloadPiUser(), recordToShare.getId(), new ShareConfigElement[] {gsCommand}));

    // now do sharing as the *owner*, which is permitted...
    logoutAndLoginAs(piUser);

    openTransaction();
    sharingMgr.shareRecord(
        reloadPiUser(), recordToShare.getId(), new ShareConfigElement[] {gsCommand});
    assertEquals(b4 + 1, folderDao.get(labGrpShared.getId()).getChildren().size());
    assertEquals(1, groupShareDao.getRecordsSharedByGroup(group.getId()).size());
    assertEquals(1, groupShareDao.getSharedRecordsForUser(piUser).size());
    commitTransaction();

    // now lets share with other user - beacuse they're already shared with group, we should not
    // share again
    // with group, but will share with individual
    gsCommand.setUserId(other.getId());
    sharingMgr.shareRecord(
        reloadPiUser(), recordToShare.getId(), new ShareConfigElement[] {gsCommand});

    // all these assertions should be the same as above
    openTransaction();
    assertEquals(b4 + 1, folderDao.get(labGrpShared.getId()).getChildren().size());
    assertEquals(1, groupShareDao.getRecordsSharedByGroup(group.getId()).size());
    assertEquals(1, groupShareDao.getSharedRecordsForUser(piUser).size());
    // but should be shared with user as well:
    assertEquals(
        1, folderDao.getIndividualSharedFolderForUsers(piUser, other, null).getChildren().size());
    gsCommand.setGroupid(group.getId()); // reset for group
    commitTransaction();
    ;

    // login as group member, should have edit permission
    logoutAndLoginAs(other);
    assertTrue(groupCanEditRecord(group, recordToShare));
    logoutAndLoginAs(piUser);

    // try to share again - should not be shared twice
    sharingMgr.shareRecord(
        reloadPiUser(), recordToShare.getId(), new ShareConfigElement[] {gsCommand});

    openTransaction();
    assertEquals(b4 + 1, folderDao.get(labGrpShared.getId()).getChildren().size());
    assertEquals(1, groupShareDao.getRecordsSharedByGroup(group.getId()).size());
    commitTransaction();

    // now unshare, should revert to original situation
    sharingMgr.unshareRecord(
        reloadPiUser(), recordToShare.getId(), new ShareConfigElement[] {gsCommand});

    openTransaction();
    assertEquals(b4, folderDao.get(labGrpShared.getId()).getChildren().size());
    assertEquals(0, groupShareDao.getRecordsSharedByGroup(group.getId()).size());
    commitTransaction();

    // and unshare from individual as well
    gsCommand.setUserId(other.getId());
    sharingMgr.unshareRecord(
        reloadPiUser(), recordToShare.getId(), new ShareConfigElement[] {gsCommand});
    // and the original sharee should no longer be able to access the record
    logoutAndLoginAs(other);
    assertEquals(
        EditStatus.ACCESS_DENIED,
        recordMgr.requestRecordEdit(recordToShare.getId(), other, anySessionTracker()));
    logoutAndLoginAs(piUser);

    // now share record again, but this time with read-only permission:
    gsCommand.setOperation("read");
    sharingMgr.shareRecord(
        reloadPiUser(), recordToShare.getId(), new ShareConfigElement[] {gsCommand});

    piUser = userMgr.getUserByUsername(piUser.getUsername(), true);
    logoutAndLoginAs(piUser);

    // now we'll add another user to the group and check that they can see the shared record too:
    User newGrpMember = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(newGrpMember);
    // sanity check new user does not see group folder
    assertFalse(userHasGroupFolderForGroup(newGrpMember, group));
    grpMgr.addUserToGroup(newGrpMember.getUsername(), group.getId(), RoleInGroup.DEFAULT);
    // now should be added OK
    assertTrue(userHasGroupFolderForGroup(newGrpMember, group));

    logoutAndLoginAs(piUser);
    // now let's remove the new user from the group, he should no longer see shared record
    grpMgr.removeUserFromGroup(newGrpMember.getUsername(), group.getId(), piUser);
    assertFalse(userHasGroupFolderForGroup(newGrpMember, group));

    // now let's delete the owner of the record from the group; they should still see the records
    // in their folder as they are the original owner.
    // user should not share with themselves, original record should still be in root
    int numRecordsInRootFolderB4deletionFromGroup = getNumChildrenInRootFolder(piUser);
    User sysadmin = logoutAndLoginAsSysAdmin();
    grpMgr.removeUserFromGroup(piUser.getUsername(), group.getId(), sysadmin);
    // i.e., their original record is still there.
    assertEquals(numRecordsInRootFolderB4deletionFromGroup, getNumChildrenInRootFolder(piUser));
    assertFalse(userHasGroupFolderForGroup(newGrpMember, group));

    // now delete group
    grpMgr.removeGroup(group.getId(), sysadmin);
    // 1 shared entry is removed
    openTransaction();
    assertEquals(0, groupShareDao.getRecordsSharedByGroup(group.getId()).size());
    commitTransaction();
  }

  // RSPAC-1120
  @Test
  public void shareDocumentWithGroupHavingOneMember() {
    logoutAndLoginAs(piUser);

    StructuredDocument recordToShare =
        recordMgr.createBasicDocument(piUser.getRootFolder().getId(), piUser);

    openTransaction();
    Group oneMemberGroup = createGroup("oneMemberGroup", piUser);
    oneMemberGroup = addUsersToGroup(piUser, oneMemberGroup);

    Folder labGrpShared = folderDao.getSharedFolderForGroup(oneMemberGroup);
    // document not shared, group folder empty
    assertEquals(0, sharingMgr.getRecordSharingInfo(recordToShare.getId()).size());
    assertEquals(0, labGrpShared.getChildren().size());
    permissionUtils.refreshCache();
    commitTransaction();

    // sharing document with a group
    piUser = reloadPiUser();
    shareRecordWithGroup(piUser, oneMemberGroup, recordToShare);

    // document should be shared, group folder should have one element
    assertEquals(1, sharingMgr.getRecordSharingInfo(recordToShare.getId()).size());
    openTransaction();
    labGrpShared = folderDao.get(labGrpShared.getId());
    assertEquals(1, labGrpShared.getChildren().size());
    commitTransaction();
  }

  @Test
  public void deletionOfDocumentSharedIntoOtherNotebook() throws Exception {

    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi2"), Constants.PI_ROLE);
    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("u1"), Constants.USER_ROLE);
    initialiseContentWithEmptyContent(pi, user);

    // create lab group
    Group labGroup = createGroup(getRandomAlphabeticString("g1"), pi);
    addUsersToGroup(pi, labGroup, user);
    Group labGroup2 = createGroup(getRandomAlphabeticString("g2"), pi);
    addUsersToGroup(pi, labGroup2, user);

    // user creates a notebook and shares it for write with both groups
    user = reloadUser(user);
    logoutAndLoginAs(user);
    Long userRootId = user.getRootFolder().getId();

    openTransaction();
    Notebook sharedNotebook = createNotebookWithNEntries(userRootId, "shared nbook", 1, user);
    StructuredDocument usersEntry = (StructuredDocument) sharedNotebook.getChildrens().toArray()[0];
    commitTransaction();

    //    openTransaction();
    shareNotebookWithGroup(user, sharedNotebook, labGroup, "write");
    shareNotebookWithGroup(user, sharedNotebook, labGroup2, "write");
    //    commitTransaction();

    // pi creates a document and shares it into the notebook, for both groups
    logoutAndLoginAs(pi);

    BaseRecord doc = createBasicDocumentInRootFolderWithText(pi, "any");
    sharedNotebook = folderMgr.getNotebook(sharedNotebook.getId());
    labGroup = grpMgr.getGroup(labGroup.getId());
    labGroup2 = grpMgr.getGroup(labGroup2.getId());
    pi = reloadUser(pi);

    shareRecordIntoGroupNotebook(doc, sharedNotebook, labGroup, pi);
    shareRecordIntoGroupNotebook(doc, sharedNotebook, labGroup2, pi);
    // no need to commit() since the transaction is already closed

    // record should be shared only once
    List<RecordGroupSharing> docSharings = sharingMgr.getRecordSharingInfo(doc.getId());
    assertEquals(1, docSharings.size());

    // notebook should have two entries now
    sharedNotebook = folderMgr.getNotebook(sharedNotebook.getId());
    assertEquals(2, sharedNotebook.getEntryCount());

    // pi can delete own entry, but not user's entry (RSPAC-993)
    assertTrue(permissionUtils.isPermitted(doc, PermissionType.DELETE, pi));
    assertFalse(permissionUtils.isPermitted(usersEntry, PermissionType.DELETE, pi));

    // pi deletes the document from their shared folder
    recordDeletionMgr.deleteEntry(
        labGroup.getCommunalGroupFolderId(), sharedNotebook.getId(), doc.getId(), pi);

    // the entry should be unshared from notebook, but shouldn't be deleted
    sharedNotebook = folderMgr.getNotebook(sharedNotebook.getId());
    assertEquals(1, sharedNotebook.getEntryCount());
    docSharings = sharingMgr.getRecordSharingInfo(doc.getId());
    assertEquals(0, docSharings.size());

    doc = (StructuredDocument) recordMgr.get(doc.getId());
    assertFalse(doc.isDeleted());

    // pi shares again
    shareRecordIntoGroupNotebook(doc, sharedNotebook, labGroup, pi);

    // user open notebook in their home folder, and deletes the entry
    logoutAndLoginAs(user);

    openTransaction();
    assertEquals(1, groupShareDao.getRecordGroupSharingsForRecord(doc.getId()).size());
    recordDeletionMgr.deleteEntry(userRootId, sharedNotebook.getId(), doc.getId(), user);
    assertEquals(0, groupShareDao.getRecordGroupSharingsForRecord(doc.getId()).size());
    commitTransaction();

    // the entry should be unshared from notebook, but shouldn't be deleted
    sharedNotebook = folderMgr.getNotebook(sharedNotebook.getId());
    assertEquals(1, sharedNotebook.getEntryCount());
    doc = (StructuredDocument) recordMgr.get(doc.getId());
    assertFalse(doc.isDeleted());
  }

  @Test
  public void shareWithUserAndGroupContainingUser() throws Exception {
    StructuredDocument recordToShare = setUpOtherUserAndRecordToShare();
    // check status is not shared
    assertEquals(SharedStatus.UNSHARED, recordToShare.getSharedStatus());
    ShareConfigElement individCommand = new ShareConfigElement(other.getId(), "write");
    individCommand.setUserId(other.getId());
    ShareConfigElement groupCommand2 = new ShareConfigElement(group.getId(), "write");
    groupCommand2.setGroupid(group.getId());

    // share with individual, then group
    ShareConfigElement[] shringComands = new ShareConfigElement[] {individCommand, groupCommand2};

    sharingMgr.shareRecord(reloadPiUser(), recordToShare.getId(), shringComands);
    sharingMgr.updateSharedStatusOfRecords(toList(recordToShare), piUser);
    // now is updated, should be shared.
    assertEquals(SharedStatus.SHARED, recordToShare.getSharedStatus());

    openTransaction();
    assertEquals(1, groupShareDao.getRecordsSharedByGroup(other.getId()).size());
    Folder sharedIndivid = folderDao.getIndividualSharedFolderForUsers(piUser, other, null);
    // will be in idividual folder and in group folder
    assertEquals(1, sharedIndivid.getChildren().size());
    group = reloadGroup(group);
    assertEquals(1, folderDao.getSharedFolderForGroup(group).getChildren().size());
    commitTransaction();

    sharingMgr.unshareRecord(reloadPiUser(), recordToShare.getId(), shringComands);

    // everything should now be unshared
    openTransaction();
    assertEquals(0, groupShareDao.getRecordsSharedByGroup(other.getId()).size());
    sharedIndivid = folderDao.getIndividualSharedFolderForUsers(piUser, other, null);
    // will be in idividual folder and in group folder
    assertEquals(0, sharedIndivid.getChildren().size());
    group = reloadGroup(group);
    assertEquals(0, folderDao.getSharedFolderForGroup(group).getChildren().size());
    commitTransaction();
  }

  @Test
  public void movePermissionsInGrpFolder() throws Exception {
    StructuredDocument toShare = setUpOtherUserAndRecordToShare();
    group = reloadGroup(group);
    openTransaction();
    Folder grpFolder = folderDao.getSharedFolderForGroup(group);
    commitTransaction();
    // pi can't move from outside grp folder to grp flder
    assertFalse(
        recordMgr
            .move(toShare.getId(), grpFolder.getId(), toShare.getParent().getId(), piUser)
            .isSucceeded());
    // neither can other user
    assertFalse(
        recordMgr
            .move(toShare.getId(), grpFolder.getId(), toShare.getParent().getId(), other)
            .isSucceeded());

    piUser = reloadPiUser();
    // now share record
    sharingMgr.shareRecord(piUser, toShare.getId(), getGrpShareCommand(group, "write"));
    // and pi creates a subfolder of group folder
    Folder subf = folderMgr.createNewFolder(grpFolder.getId(), "group_sub", piUser);
    // other can't move within group folder
    assertFalse(
        recordMgr.move(toShare.getId(), subf.getId(), grpFolder.getId(), other).isSucceeded());
    // but pi can
    assertTrue(
        recordMgr.move(toShare.getId(), subf.getId(), grpFolder.getId(), piUser).isSucceeded());

    logoutAndLoginAs(other);
    // after move, is still editable by other
    assertEquals(
        EditStatus.EDIT_MODE,
        recordMgr.requestRecordEdit(toShare.getId(), other, anySessionTracker()));

    // now share 'other's.
    StructuredDocument others = createBasicDocumentInRootFolderWithText(other, "any");
    // can't move
    assertFalse(
        recordMgr
            .move(others.getId(), grpFolder.getId(), others.getParent().getId(), other)
            .isSucceeded());
    // but can share - this will go into grp folder
    sharingMgr.shareRecord(other, others.getId(), getGrpShareCommand(group, "write"));
    //  other can't move stuff around the group folder
    assertFalse(
        recordMgr.move(others.getId(), subf.getId(), grpFolder.getId(), other).isSucceeded());
    // but user can - he's the PI
    logoutAndLoginAs(piUser);
    assertTrue(
        recordMgr.move(others.getId(), subf.getId(), grpFolder.getId(), piUser).isSucceeded());
  }

  @Test
  public void ownerDeletesNotebookAndGroupCanNoLongerView() throws Exception {
    User owner =
        createAndSaveUserIfNotExists(getRandomAlphabeticString("owner"), Constants.USER_ROLE);
    User sharee =
        createAndSaveUserIfNotExists(getRandomAlphabeticString("sharee"), Constants.USER_ROLE);

    openTransaction();
    initialiseContentWithEmptyContent(owner, sharee);
    commitTransaction();

    User sysadmin = logoutAndLoginAsSysAdmin();
    group = createGroupForUsers(sysadmin, piUser.getUsername(), "", owner, sharee, piUser);
    assertTrue(group.getPiusers().contains(piUser));

    // login as owner, create a notebook and share it
    logoutAndLoginAs(owner);
    owner = reloadUser(owner);
    openTransaction();
    Notebook notebook = createNotebookWithNEntries(owner.getRootFolder().getId(), "any", 2, owner);
    StructuredDocument notebookEntry = getFirstEntryInNotebook(notebook);
    commitTransaction();
    shareNotebookWithGroup(owner, notebook, group, "write");

    // sharee can see notebook and entry
    logoutAndLoginAs(sharee);
    sharee = reloadUser(sharee);

    openTransaction();
    notebook = folderMgr.getNotebook(notebook.getId());
    notebookEntry = getFirstEntryInNotebook(notebook);
    userCanViewNotebookAndEntry(sharee, notebook, notebookEntry);
    commitTransaction();

    // owner *deletes* the record
    logoutAndLoginAs(owner);
    openTransaction();
    owner = reloadUser(owner);
    recordDeletionMgr.deleteFolder(owner.getRootFolder().getId(), notebook.getId(), owner);
    commitTransaction();

    // sharee cannot see notebook and entry->deletion == unsharing as well
    logoutAndLoginAs(sharee);
    sharee = reloadUser(sharee);

    openTransaction();
    notebook = folderMgr.getNotebook(notebook.getId());
    notebookEntry = getFirstEntryInNotebook(notebook);
    userCannotViewNotebookOrEntry(sharee, notebook, notebookEntry);
    commitTransaction();
  }

  @Test
  public void shareUnshareNotebookAndNotebookEntry() throws InterruptedException {
    User owner =
        createAndSaveUserIfNotExists(getRandomAlphabeticString("owner"), Constants.USER_ROLE);
    User sharee =
        createAndSaveUserIfNotExists(getRandomAlphabeticString("sharee"), Constants.USER_ROLE);

    openTransaction();
    initialiseContentWithEmptyContent(owner, sharee);
    commitTransaction();

    User sysadmin = logoutAndLoginAsSysAdmin();
    group = createGroupForUsers(sysadmin, piUser.getUsername(), "", owner, sharee, piUser);
    assertTrue(group.getPiusers().contains(piUser));

    // login as owner, create a notebook
    logoutAndLoginAs(owner);
    owner = reloadUser(owner);

    openTransaction();
    Notebook nb = createNotebookWithNEntries(owner.getRootFolder().getId(), "any", 2, owner);
    StructuredDocument notebookentry = getFirstEntryInNotebook(nb);
    assertEquals(0, folderDao.getSharedFolderForGroup(group).getChildrens().size());
    commitTransaction();

    // share an entry
    shareRecordWithGroup(owner, group, notebookentry);

    openTransaction();
    assertTrue(folderDao.getSharedFolderForGroup(group).getChildrens().contains(notebookentry));
    commitTransaction();

    // check shared entry can be moved by pi.
    logoutAndLoginAs(piUser);

    openTransaction();
    Folder shared = folderDao.getSharedFolderForGroup(group);
    commitTransaction();
    ;

    List<BaseRecord> reslist = Arrays.asList(new BaseRecord[] {notebookentry});
    ISearchResults<BaseRecord> res = new SearchResultsImpl<BaseRecord>(reslist, 0, 1L, 1);
    piUser = reloadPiUser(); // refresh

    // share the whole notebook
    logoutAndLoginAs(owner);
    shareNotebookWithGroup(owner, nb, group, "write");

    openTransaction();
    assertTrue(folderDao.getSharedFolderForGroup(group).getChildrens().contains(notebookentry));
    assertEquals(2, folderDao.getSharedFolderForGroup(group).getChildrens().size());
    commitTransaction();

    // now unshare the notebook.. should unshare the entry as well
    unshareRecordORNotebookWithGroup(owner, nb, group, "write");

    openTransaction();
    assertEquals(0, folderDao.getSharedFolderForGroup(group).getChildrens().size());
    // neither are shared
    assertFalse(
        groupShareDao.isRecordAlreadySharedInGroup(
            group.getId(), nb.getId(), notebookentry.getId()));
    nb = folderMgr.getNotebook(nb.getId());
    assertEquals(2, nb.getChildren().size()); // still has 2 entries in
    commitTransaction();
    // original notebook
  }

  @Test
  public void basicShareUnshareWithGroupAffectsShareesViewPermissions()
      throws InterruptedException {
    User owner =
        createAndSaveUserIfNotExists(getRandomAlphabeticString("owner"), Constants.USER_ROLE);
    User sharee =
        createAndSaveUserIfNotExists(getRandomAlphabeticString("sharee"), Constants.USER_ROLE);

    openTransaction();
    initialiseContentWithEmptyContent(owner, sharee);
    commitTransaction();

    User sysadmin = logoutAndLoginAsSysAdmin();
    group = createGroupForUsers(sysadmin, piUser.getUsername(), "", owner, sharee, piUser);
    assertTrue(group.getPiusers().contains(piUser));

    logoutAndLoginAs(owner);
    owner = reloadUser(owner);

    openTransaction();
    Notebook notebook = createNotebookWithNEntries(owner.getRootFolder().getId(), "any", 2, owner);
    StructuredDocument notebookentry =
        (StructuredDocument) notebook.getChildren().iterator().next().getRecord();
    commitTransaction();

    // sharee can't see owner's notebook or entry because it's not shared
    // yet
    logoutAndLoginAs(sharee);
    sharee = reloadUser(sharee);
    userCannotViewNotebookOrEntry(sharee, notebook, notebookentry);

    // login as owner and share notebook
    logoutAndLoginAs(owner);
    notebook = shareNotebookWithGroup(owner, notebook, group, "write").get();
    // should not acquire shared folder status
    assertFalse(notebook.hasType(RecordType.SHARED_FOLDER));
    assertTrue(notebook.hasType(RecordType.NOTEBOOK));

    // sharee can see notebook and entry
    logoutAndLoginAs(sharee);

    openTransaction();
    sharee = reloadUser(sharee);
    notebook = folderMgr.getNotebook(notebook.getId());
    notebookentry = (StructuredDocument) notebook.getChildren().iterator().next().getRecord();
    userCanViewNotebookAndEntry(sharee, notebook, notebookentry);
    commitTransaction();

    // owner unshares the record
    logoutAndLoginAs(owner);
    unshareRecordORNotebookWithGroup(owner, notebook, group, "write");
    logoutAndLoginAs(sharee);

    openTransaction();
    sharee = reloadUser(sharee);
    notebook = folderMgr.getNotebook(notebook.getId());
    notebookentry = (StructuredDocument) notebook.getChildren().iterator().next().getRecord();
    userCannotViewNotebookOrEntry(sharee, notebook, notebookentry);
    commitTransaction();
  }

  private User reloadUser(User userToReload) {
    return userMgr.get(userToReload.getId());
  }

  private User reloadPiUser() {
    return reloadUser(piUser);
  }

  private StructuredDocument getFirstEntryInNotebook(Notebook nb) {
    return (StructuredDocument) nb.getChildren().iterator().next().getRecord();
  }

  private void userCanViewNotebookAndEntry(
      User subject, Notebook nb, StructuredDocument notebookentry) {
    assertTrue(permissionUtils.isPermitted(notebookentry, PermissionType.READ, subject));
    assertTrue(permissionUtils.isPermitted(nb, PermissionType.READ, subject));
  }

  private void userCannotViewNotebookOrEntry(
      User sharee, Notebook nb, StructuredDocument notebookentry) {
    assertFalse(permissionUtils.isPermitted(nb, PermissionType.READ, sharee));
    assertFalse(permissionUtils.isPermitted(notebookentry, PermissionType.READ, sharee));
  }

  private ShareConfigElement[] getGrpShareCommand(Group g, String perm) {
    ShareConfigElement gsCommand = new ShareConfigElement(g.getId(), perm);
    return new ShareConfigElement[] {gsCommand};
  }

  protected StructuredDocument createBasicDocumentInRootFolderWithText(
      User user, String fieldText) {
    return super.createBasicDocumentInRootFolderWithText(user, fieldText);
  }

  private int getNumChildrenInRootFolder(User user) {
    openTransaction();
    Folder toCheck = user.getRootFolder();
    int childrenSize = folderDao.get(toCheck.getId()).getChildren().size();
    commitTransaction();
    return childrenSize;
  }

  private boolean userHasGroupFolderForGroup(User user, Group grp) {
    openTransaction();
    Folder toCheck = folderDao.getLabGroupFolderForUser(user);

    Long grpfFolderid = grp.getCommunalGroupFolderId();
    if (toCheck.getChildren().isEmpty()) {
      commitTransaction();
      return false;
    }
    for (BaseRecord grpFolder : toCheck.getChildrens()) {
      if (grpFolder.getId().equals(grpfFolderid)) {
        commitTransaction();
        return true;
      }
    }
    commitTransaction();
    return false;
  }

  private boolean groupCanEditRecord(Group group, StructuredDocument recordToShare) {
    Group updated = grpMgr.getGroup(group.getId());
    RecordPermissionAdapter rpa = new RecordPermissionAdapter(recordToShare);
    rpa.setAction(PermissionType.WRITE);
    rpa.setDomain(PermissionDomain.RECORD);
    return updated.isPermitted(rpa, true);
  }

  protected User createAndSaveRandomUser() {
    return doCreateAndInitUser(getRandomAlphabeticString("user"), Constants.USER_ROLE);
  }

  private StructuredDocument setUpOtherUserAndRecordToShare() {
    openTransaction();
    RSForm anyForm = formMgr.getAll().get(0);
    StructuredDocument recordToShare =
        recordMgr.createNewStructuredDocument(
            piUser.getRootFolder().getId(), anyForm.getId(), piUser);
    other = createAndSaveRandomUser();
    initUserFolder(other);
    group = createGroup(getRandomAlphabeticString("grp1"), piUser);
    addLabGroup(group);
    addUsersToGroup(piUser, group, other);
    commitTransaction();
    return recordToShare;
  }

  protected Group createGroup(String groupName, User groupPi) {
    Group group = new Group(groupName, groupPi);
    group.setDisplayName(groupName);
    if (!groupPi.hasRole(Role.PI_ROLE)) {
      throw new IllegalArgumentException("Owner is not a PI!!");
    }
    PermissionFactory permFctry = new DefaultPermissionFactory();
    for (ConstraintBasedPermission cbp : permFctry.createDefaultGlobalGroupPermissions(group)) {
      group.addPermission(cbp);
    }

    group = grpMgr.saveGroup(group, groupPi);

    return group;
  }

  private void addLabGroup(Group group) {
    if (group.getCommunityId() != null) {
      Community comm = communityMgr.get(group.getCommunityId());
      comm.addLabGroup(group);
      communityMgr.save(comm);
    }
  }

  private void initUserFolder(User newGrpMember) throws IllegalAddChildOperation {
    contentInitializer.setCustomInitActive(false);
    contentInitializer.init(newGrpMember.getId());
    contentInitializer.setCustomInitActive(true);
  }

  private User createAndSaveUserIfNotExists(String uname, String role) {
    User user = TestFactory.createAnyUser(uname);
    user.setEmail(uname + "@m.com");
    user.addRole(roleMgr.getRole(role));
    if (role.equals(Constants.PI_ROLE) || role.equals(Constants.GROUP_OWNER_ROLE)) {
      user.addRole(roleMgr.getRole(Constants.USER_ROLE));
    }
    return createAndSaveUserIfNotExists(user);
  }

  private User createAndSaveUserIfNotExists(User user) {
    User dbUser;
    boolean exists = userMgr.userExists(user.getUsername());
    if (exists) {
      dbUser = userMgr.getUserByUsername(user.getUsername());
    } else {
      dbUser = userMgr.save(user);
    }
    return dbUser;
  }

  private List<Folder> initialiseContentWithEmptyContent(User... users)
      throws IllegalAddChildOperation {
    List<Folder> rootFolders = new LinkedList<>();
    contentInitializer.setCustomInitActive(false);
    for (User user : users) {
      rootFolders.add(doInit(user));
    }
    return rootFolders;
  }

  private Folder doInit(User user) throws IllegalAddChildOperation {
    RSpaceTestUtils.login(user.getUsername(), TESTPASSWD);
    // replace application init for tests
    return contentInitializer.init(user.getId()).getUserRoot();
  }
}
