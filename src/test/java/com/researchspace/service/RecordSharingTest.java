package com.researchspace.service;

import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.testutils.RSpaceTestUtils.logoutCurrUserAndLoginAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.dao.FormDao;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.UserOrGroup;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.audittrail.HistoricalEvent;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.ACLPropagationPolicy;
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
import com.researchspace.properties.IMutablePropertyHolder;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

public class RecordSharingTest extends SpringTransactionalTest {

  private static final String OTHER_USER_PWD = "user";

  private @Autowired RecordGroupSharingDao regGrpDao;
  private @Autowired FormDao formDao;
  private @Autowired IPermissionUtils permissionUtils;
  private @Autowired AuditTrailService auditService;
  private @Autowired IMutablePropertyHolder propertyHolder;

  private Group group;
  private User piUser;
  private User other;

  @BeforeEach
  public void setUp() throws IllegalAddChildOperation {
    piUser = createAndSaveAPi();
    initialiseContentWithEmptyContent(piUser);
  }

  @AfterEach
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

    assertThat(toShare.getShortestPathToParent(toShareInto)).hasSize(2);

    // now lets share another record into a folder that is not descended from group folder
    Folder other = folderMgr.createNewFolder(pi.getRootFolder().getId(), "Wrong", pi);
    StructuredDocument otherRecord = createBasicDocumentInRootFolderWithText(pi, "any");
    cfg.setGroupFolderId(other.getId());
    toShare =
        extractDoc(sharingMgr.shareRecord(pi, otherRecord.getId(), new ShareConfigElement[] {cfg}));

    // we share into group folder by default.
    assertThat(toShare.getShortestPathToParent(groupFolder)).hasSize(2);

    // now let's login as regular user, ensure can create new shared subfolder and share into
    // folder:
    logoutAndLoginAs(u1);
    Folder toShareIntoByUser = folderMgr.createNewFolder(groupFolder.getId(), "ToShareInto", pi);
    Record userDoc = createBasicDocumentInRootFolderWithText(u1, "any");
    cfg.setGroupFolderId(toShareIntoByUser.getId());
    userDoc =
        extractDoc(sharingMgr.shareRecord(u1, userDoc.getId(), new ShareConfigElement[] {cfg}));

    assertThat(userDoc.getShortestPathToParent(toShareIntoByUser)).hasSize(2);
    assertThat(userDoc.getShortestPathToParent(groupFolder)).hasSize(3);
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
    assertThat(
            folderDao.getChildren(
                folderDao.getIndividualSharedFolderForUsers(u1Owner, u2Owner, null)))
        .isEmpty();
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
    assertThat(regGrpDao.getUsersOrGroupsWithRecordAccess(toShare.getId())).hasSize(2);

    logoutAndLoginAs(piUser);
    // this should delete record for EVeryone, in both groups.
    recordDeletionMgr.deleteRecord(piUser.getRootFolder().getId(), toShare.getId(), piUser);

    // should not be in any shared group now
    assertThat(regGrpDao.getUsersOrGroupsWithRecordAccess(toShare.getId())).isEmpty();
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
    assertThat(regGrpDao.getRecordsSharedByGroup(group.getId())).contains(toShare);
    logoutCurrUserAndLoginAs(other.getUsername(), OTHER_USER_PWD);
    assertTrue(permissionUtils.isPermitted(toShare, PermissionType.READ, other));
    final Folder sharedFolder = folderDao.getSharedFolderForGroup(group);
    int NUM_CHILDREN_IN_GRPFOLDERS = folderDao.getChildren(sharedFolder).size();

    // deletion attempt by non-auth user should throw exception
    Long sharedFolderId = sharedFolder.getId();
    Long recordId = toShare.getId();
    assertThrows(
        AuthorizationException.class,
        () -> recordDeletionMgr.deleteRecord(sharedFolderId, recordId, other));

    // still should be shared
    assertThat(regGrpDao.getRecordsSharedByGroup(group.getId())).contains(toShare);
    // user can delete - he;s the owner.
    logoutCurrUserAndLoginAs(piUser.getUsername(), TESTPASSWD);
    recordDeletionMgr.deleteRecord(sharedFolder.getId(), toShare.getId(), piUser);
    // is unshared; back to original state.
    assertThat(regGrpDao.getRecordsSharedByGroup(group.getId())).doesNotContain(toShare);
    assertThat(folderDao.getChildren(folderDao.getSharedFolderForGroup(group)))
        .hasSize(NUM_CHILDREN_IN_GRPFOLDERS - 1);
    // now, it is unshared, and inaccessible.
    logoutCurrUserAndLoginAs(other.getUsername(), OTHER_USER_PWD);
    assertFalse(permissionUtils.isPermitted(toShare, PermissionType.READ, other));
  }

  @Test
  public void shareUnshareAndDeleteDoNotLoadOtherItemsOfGroupFolder() throws Exception {
    StructuredDocument toShare = setUpOtherUserAndRecordToShare();
    int alreadyShared = 20;
    for (int i = 0; i < alreadyShared; i++) {
      StructuredDocument doc = createBasicDocumentInRootFolderWithText(piUser, "shared" + i);
      sharingMgr.shareRecord(piUser, doc.getId(), getGrpShareCommand(group, "read"));
    }

    long docsLoadedOnShare =
        countStructuredDocumentLoads(
            () ->
                sharingMgr.shareRecord(piUser, toShare.getId(), getGrpShareCommand(group, "read")));
    assertThat(regGrpDao.getRecordsSharedByGroup(group.getId())).hasSize(alreadyShared + 1);
    long docsLoadedOnUnshare =
        countStructuredDocumentLoads(
            () ->
                sharingMgr.unshareRecord(
                    piUser, toShare.getId(), getGrpShareCommand(group, "read")));
    assertThat(regGrpDao.getRecordsSharedByGroup(group.getId())).hasSize(alreadyShared);
    sharingMgr.shareRecord(piUser, toShare.getId(), getGrpShareCommand(group, "read"));
    long docsLoadedOnDelete =
        countStructuredDocumentLoads(
            () ->
                recordDeletionMgr.deleteRecord(
                    piUser.getRootFolder().getId(), toShare.getId(), piUser));

    assertThat(docsLoadedOnShare).isLessThan(alreadyShared);
    assertThat(docsLoadedOnUnshare).isLessThan(alreadyShared);
    assertThat(docsLoadedOnDelete).isLessThan(alreadyShared);
    assertThat(regGrpDao.getRecordsSharedByGroup(group.getId())).hasSize(alreadyShared);
  }

  @Test
  public void sharedDocumentCanBeMovedAndDeletedInSameTransaction() throws Exception {
    StructuredDocument doc = setUpOtherUserAndRecordToShare();
    sharingMgr.shareRecord(piUser, doc.getId(), getGrpShareCommand(group, "write"));
    Folder groupFolder = folderDao.getSharedFolderForGroup(group);
    Folder subfolder = folderMgr.createNewFolder(groupFolder.getId(), "sub", piUser);

    assertTrue(
        recordMgr.move(doc.getId(), subfolder.getId(), groupFolder.getId(), piUser).isSucceeded());
    assertThat(folderDao.getFolderChildrenIds(subfolder)).containsExactly(doc.getId());
    assertThat(folderDao.getFolderChildrenIds(groupFolder)).doesNotContain(doc.getId());

    recordDeletionMgr.deleteRecord(subfolder.getId(), doc.getId(), piUser);
    assertThat(folderDao.getFolderChildrenIds(subfolder)).isEmpty();
    assertThat(regGrpDao.getRecordsSharedByGroup(group.getId())).isEmpty();
  }

  @Test
  public void sharedNotebookCanBeMovedUnsharedAndDeletedInSameTransaction() throws Exception {
    setUpOtherUserAndRecordToShare();
    logoutAndLoginAs(other);
    Long rootId = other.getRootFolder().getId();
    Notebook notebook = createNotebookWithNEntries(rootId, "nb", 1, other);
    Long entryId = folderDao.getFolderChildrenIds(notebook).get(0);
    List<ACLElement> unshared = storedAcl(entryId);
    shareNotebookWithGroup(other, notebook, group, "write");
    Folder groupFolder = folderDao.getSharedFolderForGroup(group);
    assertThat(folderDao.getFolderChildrenIds(groupFolder)).contains(notebook.getId());
    List<ACLElement> fromShare = new ArrayList<>(storedAcl(entryId));
    fromShare.removeAll(unshared);
    // from the group folder, so only the descendant walk puts it on the entry
    assertThat(fromShare)
        .contains(
            new ACLElement(
                group.getUniqueName(),
                new ConstraintBasedPermission(
                    PermissionDomain.RECORD, PermissionType.READ, PermissionType.CREATE_FOLDER)));

    Folder dest = folderMgr.createNewFolder(rootId, "dest", other);
    assertTrue(folderMgr.move(notebook.getId(), dest.getId(), rootId, other).isSucceeded());
    assertThat(folderDao.getFolderChildrenIds(dest)).containsExactly(notebook.getId());
    assertThat(storedAcl(entryId)).containsAll(fromShare);

    unshareRecordORNotebookWithGroup(other, notebook, group, "write");
    assertThat(folderDao.getFolderChildrenIds(groupFolder)).doesNotContain(notebook.getId());
    assertThat(storedAcl(entryId)).doesNotContainAnyElementsOf(fromShare);
    recordDeletionMgr.deleteFolder(dest.getId(), notebook.getId(), other);
    assertTrue(folderDao.get(notebook.getId()).isDeleted());
    assertTrue(recordMgr.get(entryId).isDeleted());
  }

  @Test
  public void sharingNotebookWithGroupWritesEachEntryOnce() throws Exception {
    setUpOtherUserAndRecordToShare();
    logoutAndLoginAs(other);
    int entries = 3;
    Notebook notebook =
        createNotebookWithNEntries(other.getRootFolder().getId(), "nb", entries, other);
    Long entryId = folderDao.getFolderChildrenIds(notebook).get(0);
    List<ACLElement> unshared = storedAcl(entryId);
    ACLElement groupRead = acl(group, PermissionType.READ);
    ACLElement groupWrite = acl(group, PermissionType.WRITE);

    assertEquals(
        entries,
        countStructuredDocumentUpdates(
            () -> shareNotebookWithGroup(other, notebook, group, "read")));
    assertThat(storedAcl(entryId)).contains(groupRead);

    Long rgsId =
        regGrpDao.findByRecordAndUserOrGroup(group.getId(), notebook.getId()).get().getId();
    assertEquals(
        entries,
        countStructuredDocumentUpdates(
            () -> sharingMgr.updatePermissionForRecord(rgsId, "write", other.getUsername())));
    assertThat(storedAcl(entryId)).contains(groupWrite).doesNotContain(groupRead);

    assertEquals(
        entries,
        countStructuredDocumentUpdates(
            () -> unshareRecordORNotebookWithGroup(other, notebook, group, "write")));
    assertThat(storedAcl(entryId)).containsExactlyInAnyOrderElementsOf(unshared);
  }

  @Test
  public void sharingNotebookWithUserWritesEachEntryOnce() throws Exception {
    setUpOtherUserAndRecordToShare();
    logoutAndLoginAs(other);
    int entries = 3;
    Notebook notebook =
        createNotebookWithNEntries(other.getRootFolder().getId(), "nb", entries, other);
    Long entryId = folderDao.getFolderChildrenIds(notebook).get(0);
    List<ACLElement> unshared = storedAcl(entryId);
    ACLElement piWrite = acl(piUser, PermissionType.WRITE);
    ACLElement piRead = acl(piUser, PermissionType.READ);

    assertEquals(
        entries,
        countStructuredDocumentUpdates(
            () -> shareNotebookWithGroupMember(other, notebook, piUser)));
    assertThat(storedAcl(entryId)).contains(piWrite);

    Long rgsId =
        regGrpDao.findByRecordAndUserOrGroup(piUser.getId(), notebook.getId()).get().getId();
    assertEquals(
        entries,
        countStructuredDocumentUpdates(
            () -> sharingMgr.updatePermissionForRecord(rgsId, "read", other.getUsername())));
    assertThat(storedAcl(entryId)).contains(piRead).doesNotContain(piWrite);

    assertEquals(
        entries,
        countStructuredDocumentUpdates(
            () -> unshareNotebookWithGroupMember(other, notebook, piUser)));
    assertThat(storedAcl(entryId)).containsExactlyInAnyOrderElementsOf(unshared);
  }

  @Test
  public void sharingNotebookAlreadyInGroupFolderStillUpdatesEntries() throws Exception {
    setUpOtherUserAndRecordToShare();
    logoutAndLoginAs(other);
    Notebook notebook = createNotebookWithNEntries(other.getRootFolder().getId(), "nb", 1, other);
    Long entryId = folderDao.getFolderChildrenIds(notebook).get(0);
    Folder groupFolder = folderDao.getSharedFolderForGroup(group);
    folderMgr.addChild(groupFolder.getId(), notebook, other, ACLPropagationPolicy.NULL_POLICY);

    shareNotebookWithGroup(other, notebook, group, "read");
    assertThat(storedAcl(entryId)).contains(acl(group, PermissionType.READ));
  }

  @Test
  public void publishingAndUnpublishingNotebookDoesNotWriteEntries() throws Exception {
    setUpOtherUserAndRecordToShare();
    logoutAndLoginAs(other);
    Notebook notebook = createNotebookWithNEntries(other.getRootFolder().getId(), "nb", 3, other);
    User anonymous = userMgr.getUserByUsername(RecordGroupSharing.ANONYMOUS_USER);
    ShareConfigElement[] publish = getIndividualShareCommand(anonymous, "read");

    assertEquals(
        0,
        countStructuredDocumentUpdates(
            () -> sharingMgr.shareRecord(other, notebook.getId(), publish)));
    assertTrue(folderDao.get(notebook.getId()).isPublished());

    assertEquals(
        0,
        countStructuredDocumentUpdates(
            () -> sharingMgr.unshareRecord(other, notebook.getId(), publish)));
    assertFalse(folderDao.get(notebook.getId()).isPublished());
  }

  private ACLElement acl(UserOrGroup sharee, PermissionType type) {
    return new ACLElement(
        sharee.getUniqueName(), new ConstraintBasedPermission(PermissionDomain.RECORD, type));
  }

  private long countStructuredDocumentUpdates(Executable action) {
    return entityStatisticsFor(StructuredDocument.class, action).getUpdateCount();
  }

  private long countStructuredDocumentLoads(Executable action) {
    return entityStatisticsFor(StructuredDocument.class, action).getLoadCount();
  }

  @Test
  public void removingMemberLeavesAclOfGroupsOtherSharedItems() {
    setUpOtherUserAndRecordToShare();
    StructuredDocument piDoc = createBasicDocumentInRootFolderWithText(piUser, "pi doc");
    sharingMgr.shareRecord(piUser, piDoc.getId(), getGrpShareCommand(group, "read"));
    List<ACLElement> shared = storedAcl(piDoc.getId());

    grpMgr.removeUserFromGroup(other.getUsername(), group.getId(), piUser);
    assertThat(storedAcl(piDoc.getId())).containsExactlyInAnyOrderElementsOf(shared);
  }

  @Test // RSPAC-2032
  public void afterRemovingUserFromGroupRecordsGetUnshared() {
    // set up piUser, other, and a group
    setUpOtherUserAndRecordToShare();

    // other user can see group folder, PI can see both group and other's folder
    assertThat(folderDao.getChildren(folderDao.getLabGroupFolderForUser(other))).hasSize(1);
    assertThat(folderDao.getChildren(folderDao.getLabGroupFolderForUser(piUser))).hasSize(2);

    // as other user create two documents, then share with pi individually, and through group
    logoutAndLoginAs(other);
    StructuredDocument userDoc1 = createBasicDocumentInRootFolderWithText(other, "any1");
    sharingMgr.shareRecord(other, userDoc1.getId(), getGrpShareCommand(group, "write"));
    sharingMgr.shareRecord(other, userDoc1.getId(), getIndividualShareCommand(piUser, "read"));

    StructuredDocument userDoc2 = createBasicDocumentInRootFolderWithText(other, "any2");
    sharingMgr.shareRecord(other, userDoc2.getId(), getGrpShareCommand(group, "read"));
    logoutAndLoginAs(piUser);

    // verify sharing information, and that pi can edit first doc through group share permission
    List<RecordGroupSharing> sharingInfo1 = sharingMgr.getRecordSharingInfo(userDoc1.getId());
    assertThat(sharingInfo1).hasSize(2);
    assertTrue(permissionUtils.isPermitted(userDoc1, PermissionType.WRITE, piUser));

    List<RecordGroupSharing> sharingInfo2 = sharingMgr.getRecordSharingInfo(userDoc2.getId());
    assertThat(sharingInfo2).hasSize(1);
    assertFalse(permissionUtils.isPermitted(userDoc2, PermissionType.WRITE, piUser));
    assertTrue(permissionUtils.isPermitted(userDoc2, PermissionType.READ, piUser));

    // remove other user from group
    grpMgr.removeUserFromGroup(other.getUsername(), group.getId(), piUser);

    // other can no longer see group folder, PI can no longer see other's folder
    assertThat(folderDao.getChildren(folderDao.getLabGroupFolderForUser(other))).isEmpty();
    assertThat(folderDao.getChildren(folderDao.getLabGroupFolderForUser(piUser))).hasSize(1);

    // RSDEV-1429: the individual share ends too, as other and pi share no group any more
    sharingInfo1 = sharingMgr.getRecordSharingInfo(userDoc1.getId());
    assertThat(sharingInfo1).isEmpty();
    assertFalse(permissionUtils.isPermitted(userDoc1, PermissionType.WRITE, piUser));
    assertFalse(permissionUtils.isPermitted(userDoc1, PermissionType.READ, piUser));
    // but pi shouldn't see second doc anymore
    sharingInfo2 = sharingMgr.getRecordSharingInfo(userDoc2.getId());
    assertThat(sharingInfo2).isEmpty();
    assertFalse(permissionUtils.isPermitted(userDoc2, PermissionType.READ, piUser));
  }

  // RSDEV-1429
  @Test
  public void leavingLastCommonGroupRevokesOwnSharesOnlyAndKeepsOtherEntrySharers()
      throws InterruptedException {
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), "ROLE_PI");
    User leaver = createAndSaveUserIfNotExists(getRandomAlphabeticString("lvr"));
    User recipient = createAndSaveUserIfNotExists(getRandomAlphabeticString("rcp"));
    User otherSharer = createAndSaveUserIfNotExists(getRandomAlphabeticString("oth"));
    initialiseContentWithEmptyContent(pi, leaver, recipient, otherSharer);
    Group labGroup = createGroup("g1", pi);
    addUsersToGroup(pi, labGroup, leaver, recipient, otherSharer);
    Group secondGroup = createGroup("g2", pi);
    addUsersToGroup(pi, secondGroup, recipient, otherSharer);

    logoutAndLoginAs(leaver);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(leaver, "doc");
    Notebook notebook = createNotebookWithNEntries(leaver.getRootFolder().getId(), "nb", 2, leaver);
    List<BaseRecord> entries = folderDao.getChildren(notebook);
    sharingMgr.shareRecord(leaver, doc.getId(), getIndividualShareCommand(recipient, "write"));
    sharingMgr.shareRecord(leaver, notebook.getId(), getIndividualShareCommand(recipient, "read"));
    sharingMgr.shareRecord(
        leaver, entries.get(0).getId(), getIndividualShareCommand(recipient, "read"));
    RecordGroupSharing legacyEntryShare =
        extractShare(
            sharingMgr.shareRecord(
                leaver, entries.get(1).getId(), getIndividualShareCommand(recipient, "read")));
    // a legacy row recorded as shared by a user who still shares a group with the recipient
    legacyEntryShare.setSharedBy(otherSharer);
    regGrpDao.save(legacyEntryShare);
    logoutAndLoginAs(recipient);
    StructuredDocument incoming = createBasicDocumentInRootFolderWithText(recipient, "in");
    sharingMgr.shareRecord(recipient, incoming.getId(), getIndividualShareCommand(leaver, "read"));
    RecordGroupSharing docShare = onlyShare(doc);
    List<Long> revokedShares =
        List.of(docShare.getId(), onlyShare(notebook).getId(), onlyShare(entries.get(0)).getId());

    logoutAndLoginAs(pi);
    clearInvocations(auditService);
    grpMgr.removeUserFromGroup(leaver.getUsername(), labGroup.getId(), pi);

    assertThat(sharingMgr.getRecordSharingInfo(doc.getId())).isEmpty();
    assertThat(sharingMgr.getRecordSharingInfo(notebook.getId())).isEmpty();
    assertThat(sharingMgr.getRecordSharingInfo(entries.get(0).getId())).isEmpty();
    assertThat(sharingMgr.getRecordSharingInfo(entries.get(1).getId())).hasSize(1);
    assertThat(sharingMgr.getRecordSharingInfo(incoming.getId())).hasSize(1);
    assertThat(unshareEvents()).containsExactlyInAnyOrderElementsOf(events(revokedShares, pi));
    // a later call for a row already removed emits nothing
    clearInvocations(auditService);
    assertFalse(sharingMgr.revokeIndividualShare(docShare, true));
    assertThat(unshareEvents()).isEmpty();
    Folder pairFolder = folderDao.getIndividualSharedFolderForUsers(leaver, recipient, null);
    assertThat(folderDao.getChildren(pairFolder)).doesNotContain(doc, notebook, entries.get(0));
    logoutAndLoginAs(recipient);
    assertFalse(permissionUtils.isPermitted(doc, PermissionType.READ, recipient));
    assertFalse(permissionUtils.isPermitted(notebook, PermissionType.READ, recipient));
    assertFalse(permissionUtils.isPermitted(entries.get(0), PermissionType.READ, recipient));
    assertTrue(permissionUtils.isPermitted(entries.get(1), PermissionType.READ, recipient));
    assertThat(storedAcl(entries.get(1).getId()))
        .extracting(ACLElement::getUserOrGrpUniqueName)
        .contains(recipient.getUsername());
  }

  // RSDEV-1429
  @Test
  public void directShareKeptWhileUsersShareAnotherGroup() {
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), "ROLE_PI");
    User leaver = createAndSaveUserIfNotExists(getRandomAlphabeticString("lvr"));
    User recipient = createAndSaveUserIfNotExists(getRandomAlphabeticString("rcp"));
    initialiseContentWithEmptyContent(pi, leaver, recipient);
    Group labGroup = createGroup("g1", pi);
    addUsersToGroup(pi, labGroup, leaver, recipient);
    Group secondGroup = createGroup("g2", pi);
    addUsersToGroup(pi, secondGroup, leaver, recipient);
    logoutAndLoginAs(leaver);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(leaver, "doc");
    sharingMgr.shareRecord(leaver, doc.getId(), getIndividualShareCommand(recipient, "read"));

    logoutAndLoginAs(pi);
    clearInvocations(auditService);
    grpMgr.removeUserFromGroup(leaver.getUsername(), labGroup.getId(), pi);

    assertThat(sharingMgr.getRecordSharingInfo(doc.getId())).hasSize(1);
    assertThat(unshareEvents()).isEmpty();
  }

  // RSDEV-1429
  @Test
  public void directShareKeptOnCloudWhenSharerLeavesGroup() {
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), "ROLE_PI");
    User leaver = createAndSaveUserIfNotExists(getRandomAlphabeticString("lvr"));
    User recipient = createAndSaveUserIfNotExists(getRandomAlphabeticString("rcp"));
    initialiseContentWithEmptyContent(pi, leaver, recipient);
    Group labGroup = createGroup("g1", pi);
    addUsersToGroup(pi, labGroup, leaver, recipient);
    logoutAndLoginAs(leaver);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(leaver, "doc");
    sharingMgr.shareRecord(leaver, doc.getId(), getIndividualShareCommand(recipient, "read"));

    logoutAndLoginAs(pi);
    propertyHolder.setCloud("true");
    try {
      grpMgr.removeUserFromGroup(leaver.getUsername(), labGroup.getId(), pi);
    } finally {
      propertyHolder.setCloud("false");
    }

    assertThat(sharingMgr.getRecordSharingInfo(doc.getId())).hasSize(1);
  }

  // RSDEV-1429: every member is judged against the whole membership, whatever the removal order
  @Test
  public void groupDeletionRevokesDirectSharesInBothDirections() {
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), "ROLE_PI");
    User first = createAndSaveUserIfNotExists(getRandomAlphabeticString("u1"));
    User second = createAndSaveUserIfNotExists(getRandomAlphabeticString("u2"));
    initialiseContentWithEmptyContent(pi, first, second);
    Group labGroup = createGroup("g1", pi);
    addUsersToGroup(pi, labGroup, first, second);
    logoutAndLoginAs(first);
    StructuredDocument firstDoc = createBasicDocumentInRootFolderWithText(first, "first");
    RecordGroupSharing firstShare =
        extractShare(
            sharingMgr.shareRecord(
                first, firstDoc.getId(), getIndividualShareCommand(second, "read")));
    logoutAndLoginAs(second);
    StructuredDocument secondDoc = createBasicDocumentInRootFolderWithText(second, "second");
    RecordGroupSharing legacy =
        extractShare(
            sharingMgr.shareRecord(
                second, secondDoc.getId(), getIndividualShareCommand(first, "read")));
    // a legacy row without a recorded sharer counts as shared by the owner
    legacy.setSharedBy(null);
    regGrpDao.save(legacy);

    logoutAndLoginAs(pi);
    clearInvocations(auditService);
    grpMgr.removeGroup(labGroup.getId(), pi);

    assertThat(sharingMgr.getRecordSharingInfo(firstDoc.getId())).isEmpty();
    assertThat(sharingMgr.getRecordSharingInfo(secondDoc.getId())).isEmpty();
    assertThat(unshareEvents())
        .containsExactlyInAnyOrderElementsOf(
            events(List.of(firstShare.getId(), legacy.getId()), pi));
  }

  // RSDEV-1429: group deletion revokes the leaving sharer's notebook but keeps an entry another
  // member shared, who still shares a group with the recipient. The pre-pass visits the members
  // in hash-set order, so each order of the two sharers is set up explicitly.
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void groupDeletionRevokesMixedSharerNotebookButKeepsOtherSharersEntry(
      boolean notebookOwnerVisitedFirst) throws InterruptedException {
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), "ROLE_PI");
    User u1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("u1"));
    User u2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("u2"));
    User recipient = createAndSaveUserIfNotExists(getRandomAlphabeticString("rcp"));
    initialiseContentWithEmptyContent(pi, u1, u2, recipient);
    Group labGroup = createGroup("g1", pi);
    addUsersToGroup(pi, labGroup, u1, u2, recipient);
    List<User> visitOrder = new ArrayList<>(new HashSet<>(labGroup.getMembers()));
    boolean u1VisitedFirst = visitOrder.indexOf(u1) < visitOrder.indexOf(u2);
    User owner = u1VisitedFirst == notebookOwnerVisitedFirst ? u1 : u2;
    User otherSharer = owner.equals(u1) ? u2 : u1;
    Group secondGroup = createGroup("g2", pi);
    addUsersToGroup(pi, secondGroup, recipient, otherSharer);

    logoutAndLoginAs(owner);
    Notebook notebook = createNotebookWithNEntries(owner.getRootFolder().getId(), "nb", 2, owner);
    List<BaseRecord> entries = folderDao.getChildren(notebook);
    sharingMgr.shareRecord(owner, notebook.getId(), getIndividualShareCommand(recipient, "read"));
    sharingMgr.shareRecord(
        owner, entries.get(0).getId(), getIndividualShareCommand(recipient, "read"));
    RecordGroupSharing keptShare =
        extractShare(
            sharingMgr.shareRecord(
                owner, entries.get(1).getId(), getIndividualShareCommand(recipient, "write")));
    // a legacy row recorded as shared by the other member
    keptShare.setSharedBy(otherSharer);
    regGrpDao.save(keptShare);
    List<Long> revokedShares =
        List.of(onlyShare(notebook).getId(), onlyShare(entries.get(0)).getId());
    Folder pairFolder = folderDao.getIndividualSharedFolderForUsers(owner, recipient, null);

    logoutAndLoginAs(pi);
    clearInvocations(auditService);
    grpMgr.removeGroup(labGroup.getId(), pi);

    assertThat(sharingMgr.getRecordSharingInfo(notebook.getId())).isEmpty();
    assertThat(sharingMgr.getRecordSharingInfo(entries.get(0).getId())).isEmpty();
    assertThat(sharingMgr.getRecordSharingInfo(entries.get(1).getId()))
        .extracting(RecordGroupSharing::getId)
        .containsExactly(keptShare.getId());
    assertThat(unshareEvents()).containsExactlyInAnyOrderElementsOf(events(revokedShares, pi));
    assertThat(folderDao.getChildren(pairFolder))
        .contains(entries.get(1))
        .doesNotContain(notebook, entries.get(0));
    logoutAndLoginAs(recipient);
    assertFalse(permissionUtils.isPermitted(notebook, PermissionType.READ, recipient));
    assertFalse(permissionUtils.isPermitted(entries.get(0), PermissionType.READ, recipient));
    assertTrue(permissionUtils.isPermitted(entries.get(1), PermissionType.WRITE, recipient));
  }

  private RecordGroupSharing onlyShare(BaseRecord item) {
    List<RecordGroupSharing> shares = sharingMgr.getRecordSharingInfo(item.getId());
    assertThat(shares).hasSize(1);
    return shares.get(0);
  }

  private RecordGroupSharing extractShare(
      ServiceOperationResult<List<RecordGroupSharing>> shareRecord) {
    return shareRecord.getEntity().get(0);
  }

  // the UNSHARE events as (share row id, subject)
  private List<Map.Entry<Long, String>> unshareEvents() {
    ArgumentCaptor<HistoricalEvent> events = ArgumentCaptor.forClass(HistoricalEvent.class);
    verify(auditService, atLeast(0)).notify(events.capture());
    return events.getAllValues().stream()
        .filter(e -> e instanceof GenericEvent && e.getAuditAction() == AuditAction.UNSHARE)
        .map(
            e ->
                Map.entry(
                    ((RecordGroupSharing) e.getAuditedObject()).getId(),
                    e.getSubject().getUniqueName()))
        .collect(Collectors.toList());
  }

  private static List<Map.Entry<Long, String>> events(List<Long> shareIds, User subject) {
    return shareIds.stream()
        .map(id -> Map.entry(id, subject.getUsername()))
        .collect(Collectors.toList());
  }

  // RSDEV-1636: the item is unlinked from the sharer's folder whoever unshares it
  @Test
  public void shareeRemovingShareUnlinksItFromTheSharersFolder() {
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), "ROLE_PI");
    User owner = createAndSaveUserIfNotExists(getRandomAlphabeticString("own"));
    User sharee = createAndSaveUserIfNotExists(getRandomAlphabeticString("shr"));
    initialiseContentWithEmptyContent(pi, owner, sharee);
    Group labGroup = createGroup("g1", pi);
    addUsersToGroup(pi, labGroup, owner, sharee);
    logoutAndLoginAs(owner);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(owner, "any");
    sharingMgr.shareRecord(owner, doc.getId(), getIndividualShareCommand(sharee, "read"));
    Folder pairFolder = folderDao.getIndividualSharedFolderForUsers(owner, sharee, null);
    assertThat(folderDao.getChildren(pairFolder)).contains(doc);

    logoutAndLoginAs(sharee);
    sharingMgr.unshareRecord(sharee, doc.getId(), getIndividualShareCommand(sharee, "read"));

    assertThat(folderDao.getChildren(pairFolder)).doesNotContain(doc);
    assertFalse(permissionUtils.isPermitted(doc, PermissionType.READ, sharee));
  }

  // RSDEV-1636: a separately shared notebook entry loses its own, higher permission
  @Test
  public void unsharingNotebookRemovesSeparatelySharedEntryWithItsOwnPermission()
      throws InterruptedException {
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), "ROLE_PI");
    User owner = createAndSaveUserIfNotExists(getRandomAlphabeticString("own"));
    User sharee = createAndSaveUserIfNotExists(getRandomAlphabeticString("shr"));
    initialiseContentWithEmptyContent(pi, owner, sharee);
    Group labGroup = createGroup("g1", pi);
    addUsersToGroup(pi, labGroup, owner, sharee);
    logoutAndLoginAs(owner);
    Notebook notebook = createNotebookWithNEntries(owner.getRootFolder().getId(), "nb", 1, owner);
    BaseRecord entry = folderDao.getChildren(notebook).get(0);
    sharingMgr.shareRecord(owner, notebook.getId(), getIndividualShareCommand(sharee, "read"));
    sharingMgr.shareRecord(owner, entry.getId(), getIndividualShareCommand(sharee, "write"));
    Folder pairFolder = folderDao.getIndividualSharedFolderForUsers(owner, sharee, null);

    sharingMgr.unshareRecord(owner, notebook.getId(), getIndividualShareCommand(sharee, "read"));

    assertThat(folderDao.getChildren(notebook)).contains(entry);
    assertThat(folderDao.getChildren(pairFolder)).doesNotContain(entry, notebook);
    assertThat(sharingMgr.getRecordSharingInfo(entry.getId())).isEmpty();
    logoutAndLoginAs(sharee);
    assertFalse(permissionUtils.isPermitted(entry, PermissionType.READ, sharee));
    assertFalse(permissionUtils.isPermitted(entry, PermissionType.WRITE, sharee));
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
    User persistedUser = userDao.get(user.getId());
    Long documentId = testDoc.getId();

    AuthorizationException authorizationException =
        assertThrows(
            AuthorizationException.class,
            () -> sharingMgr.shareRecord(persistedUser, documentId, indivSharingCfgElem));
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
            () -> sharingMgr.shareRecord(persistedUser, documentId, groupSharingCfgElem));
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
    assertThat(regGrpDao.getRecordsSharedByGroup(other.getId())).isEmpty();
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
    assertThat(
            regGrpDao.findRecordsSharedWithUserOrGroup(
                newGrpMember.getId(), TransformerUtils.toList(docToShare1.getId())))
        .hasSize(1);
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
    Long recordId = toShare.getId();
    assertThrows(
        AuthorizationException.class, () -> recordMgr.renameRecord("XXX", recordId, other));

    // get user to create a new folder in the group:
    logoutAndLoginAs(piUser);
    Folder grpFolder = folderDao.getSharedFolderForGroup(group);
    final Folder subf = folderMgr.createNewFolder(grpFolder.getId(), "GROUPSF", piUser);
    logoutAndLoginAs(other);

    // other user can't rename a folder created by group admin even though is shared with group.
    Long subfolderId = subf.getId();
    assertThrows(
        AuthorizationException.class, () -> recordMgr.renameRecord("XXX", subfolderId, other));
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
    assertThat(result.getRecords()).contains(toShare);
  }
}
