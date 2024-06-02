package com.researchspace.webapp.controller;

import static org.junit.Assert.*;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.*;
import com.researchspace.service.RecordManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.ExtendedModelMap;

/** Specific test for asserting the behaviour or notebook sharing/ unsharing */
public class NotebookSharingTest extends SpringTransactionalTest {

  private User pi, owner, sharee;
  private Group grp;
  private Notebook nb;
  private StructuredDocument notebookentry;
  @Autowired private RecordManager recordManager;
  @Autowired private RecordGroupSharingDao grpSharingDao;
  @Autowired private WorkspacePermissionsDTOBuilder permBuilder;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    permBuilder.setRecMgr(recordManager);
    User sysadmin = logoutAndLoginAsSysAdmin();
    pi = createAndSaveAPi();
    owner = createAndSaveRandomUser();
    sharee = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(pi, owner, sharee);
    grp = createGroupForUsers(sysadmin, pi.getUsername(), "", owner, sharee, pi);
    assertTrue(grp.getPiusers().contains(pi));
    nb = null;
    notebookentry = null;
  }

  @Test
  public void basicShareUnshareWithIndividualAffectsShareesViewPermissions()
      throws InterruptedException {
    setupFixture();

    // login as owner and share notebook
    logoutAndLoginAs(owner);
    nb = shareNotebookWithGroupMember(owner, nb, sharee).get();
    // should not acquire shared folder status
    assertFalse(nb.hasType(RecordType.SHARED_FOLDER));
    assertTrue(nb.hasType(RecordType.NOTEBOOK));

    // sharee can see notebook and entry
    logoutAndLoginAs(sharee);
    userCanViewNotebookAndEntry(sharee, nb, notebookentry);

    // owner unshares the record
    logoutAndLoginAs(owner);
    unshareNotebookWithGroupMember(owner, nb, sharee);
    logoutAndLoginAs(sharee);
    userCannotViewNotebookOrEntry(sharee, nb, notebookentry);
  }

  @Test
  public void basicShareUnshareWithGroupAffectsShareesViewPermissions()
      throws InterruptedException {

    setupFixture();

    // login as owner and share notebook
    logoutAndLoginAs(owner);
    nb = shareNotebookWithGroup(owner, nb, grp, "write").get();
    // should not acquire shared folder status
    assertFalse(nb.hasType(RecordType.SHARED_FOLDER));
    assertTrue(nb.hasType(RecordType.NOTEBOOK));

    // sharee can see notebook and entry
    logoutAndLoginAs(sharee);
    userCanViewNotebookAndEntry(sharee, nb, notebookentry);

    // owner unshares the record
    logoutAndLoginAs(owner);
    unshareRecordORNotebookWithGroup(owner, nb, grp, "write");
    logoutAndLoginAs(sharee);
    userCannotViewNotebookOrEntry(sharee, nb, notebookentry);
  }

  private void setupFixture() throws InterruptedException {
    logoutAndLoginAs(owner);
    nb = createNotebookWithNEntries(owner.getRootFolder().getId(), "any", 2, owner);
    notebookentry = (StructuredDocument) nb.getChildren().iterator().next().getRecord();
    // sharee can't see owner's notebook or entry because it's not shared
    // yet
    logoutAndLoginAs(sharee);
    userCannotViewNotebookOrEntry(sharee, nb, notebookentry);
  }

  @Test
  public void ownerDeletesNotebookAndShareeCanNoLongerView() throws Exception {

    // login as owner, create a notebook and share it
    logoutAndLoginAs(owner);
    Notebook nb = createNotebookWithNEntries(owner.getRootFolder().getId(), "any", 2, owner);
    StructuredDocument notebookentry = getFirstEntryInNotebook(nb);
    shareNotebookWithGroupMember(owner, nb, sharee);

    // sharee can see notebook and entry
    logoutAndLoginAs(sharee);
    userCanViewNotebookAndEntry(sharee, nb, notebookentry);

    // owner *deletes* the record
    logoutAndLoginAs(owner);
    deleteNotebook(owner, nb);

    // sharee cannot see notebook and entry->deletion == unsharing as well
    logoutAndLoginAs(sharee);
    userCannotViewNotebookOrEntry(sharee, nb, notebookentry);
  }

  @Test
  public void ownerDeletesNotebookAndGroupCanNoLongerView() throws Exception {

    // login as owner, create a notebook and share it
    logoutAndLoginAs(owner);
    Notebook nb = createNotebookWithNEntries(owner.getRootFolder().getId(), "any", 2, owner);
    StructuredDocument notebookentry = getFirstEntryInNotebook(nb);
    shareNotebookWithGroup(owner, nb, grp, "write");

    // sharee can see notebook and entry
    logoutAndLoginAs(sharee);
    userCanViewNotebookAndEntry(sharee, nb, notebookentry);

    // owner *deletes* the record
    logoutAndLoginAs(owner);
    deleteNotebook(owner, nb);

    // sharee cannot see notebook and entry->deletion == unsharing as well
    logoutAndLoginAs(sharee);
    userCannotViewNotebookOrEntry(sharee, nb, notebookentry);
  }

  @Test
  public void shareeDeletesNotebookFromSharedFolder() throws Exception {
    // login as owner, create a notebook and share it
    logoutAndLoginAs(owner);
    Notebook nb = createNotebookWithNEntries(owner.getRootFolder().getId(), "any", 2, owner);
    StructuredDocument notebookentry = getFirstEntryInNotebook(nb);
    shareNotebookWithGroupMember(owner, nb, sharee);

    // sharee cannot see notebook and entry->deletion == unsharing as well
    logoutAndLoginAs(sharee);
    Folder ownershareeShared = folderDao.getIndividualSharedFolderForUsers(owner, sharee, null);
    recordDeletionMgr.deleteFolder(ownershareeShared.getId(), nb.getId(), sharee);
    assertEquals(0, sharingMgr.getSharedRecordsForUser(owner).size());
    nb = folderMgr.getNotebook(nb.getId());
    userCanViewNotebookAndEntry(owner, nb, notebookentry);
    userCannotViewNotebookOrEntry(sharee, nb, notebookentry);
  }

  private StructuredDocument getFirstEntryInNotebook(Notebook nb) {
    return (StructuredDocument) nb.getChildren().iterator().next().getRecord();
  }

  @Test
  public void shareUnshareNotebookAndNotebookEntry() throws InterruptedException {
    // login as owner, create a notebook
    logoutAndLoginAs(owner);
    Notebook nb = createNotebookWithNEntries(owner.getRootFolder().getId(), "any", 2, owner);
    StructuredDocument notebookentry = getFirstEntryInNotebook(nb);
    assertEquals(0, getSharedRecordCountForGroup(grp));
    // share an entry
    shareRecordWithGroup(owner, grp, notebookentry);
    assertSharedRecordInSharedFolder(grp, notebookentry);
    // check shared entry can be moved by pi.
    logoutAndLoginAs(pi);
    Folder shared = folderDao.getSharedFolderForGroup(grp);
    List<BaseRecord> reslist = Arrays.asList(new BaseRecord[] {notebookentry});
    ISearchResults<BaseRecord> res = new SearchResultsImpl<BaseRecord>(reslist, 0, 1L, 1);
    pi = userDao.get(pi.getId()); // refresh
    ActionPermissionsDTO dto =
        permBuilder.addCreateAndOptionsMenuPermissions(
            shared, pi, new ExtendedModelMap(), res.getResults(), null, false);
    assertTrue(
        dto.getInstancePermissions().get(notebookentry.getId()).get(PermissionType.SEND.name()));
    // share the whole notebook
    logoutAndLoginAs(owner);
    shareNotebookWithGroup(owner, nb, grp, "write");
    assertSharedRecordInSharedFolder(grp, nb);
    assertEquals(2, getSharedRecordCountForGroup(grp));
    // now unshare the notebook.. should unshare the entry as well
    unshareRecordORNotebookWithGroup(owner, nb, grp, "write");
    assertEquals(0, getSharedRecordCountForGroup(grp));
    // neither are shared
    assertFalse(
        grpSharingDao.isRecordAlreadySharedInGroup(grp.getId(), nb.getId(), notebookentry.getId()));
    nb = folderMgr.getNotebook(nb.getId());
    assertEquals(2, nb.getChildren().size()); // still has 2 entries in
    // original notebook
  }

  @Test
  public void moveNotebookEntryCase1_RSPAC1814() throws InterruptedException {
    // subject, doc owner and notebook owner are all the same person.
    setupFixture();
    assertTrue(
        recordMgr
            .move(notebookentry.getId(), owner.getRootFolder().getId(), nb.getId(), owner)
            .isSucceeded());
  }

  @Test
  public void moveNotebookEntryCase2_RSPAC1814() throws InterruptedException {
    // subject, doc owner and notebook owner are all the same person. notebook shared with group
    setupFixture();
    logoutAndLoginAs(owner);
    shareNotebookWithGroup(owner, nb, grp, "edit");
    assertTrue(
        recordMgr
            .move(notebookentry.getId(), owner.getRootFolder().getId(), nb.getId(), owner)
            .isSucceeded());
  }

  @Test
  public void moveNotebookEntryCase3_RSPAC1814() throws InterruptedException {
    // doc owner is different from notebook owner/subject . notebook shared with
    // group
    User u1 = sharee; // alias
    logoutAndLoginAs(u1);
    nb = createNotebookWithNEntries(u1.getRootFolder().getId(), "any", 1, u1);
    logoutAndLoginAs(owner);
    Record doc = createBasicDocumentInRootFolderWithText(owner, "docText");
    Optional<RecordGroupSharing> resultOptional = shareRecordIntoGroupNotebook(doc, nb, grp, owner);
    assertTrue(resultOptional.isPresent()); // sanity check sharing worked
    assertFalse(
        recordMgr
            .move(doc.getId(), owner.getRootFolder().getId(), nb.getId(), owner)
            .isSucceeded());
  }

  @Test
  public void moveNotebookEntryCase4_RSPAC1814() throws InterruptedException {
    // doc owner/subject is different from notebook owne. notebook shared with group
    User u1 = sharee; // alias
    logoutAndLoginAs(u1);
    nb = createNotebookWithNEntries(u1.getRootFolder().getId(), "any", 1, u1);
    logoutAndLoginAs(owner);
    Record doc = createBasicDocumentInRootFolderWithText(owner, "docText");
    Optional<RecordGroupSharing> resultOptional = shareRecordIntoGroupNotebook(doc, nb, grp, owner);
    assertTrue(resultOptional.isPresent()); // sanity check sharing worked
    logoutAndLoginAs(u1);
    assertFalse(
        recordMgr.move(doc.getId(), owner.getRootFolder().getId(), nb.getId(), u1).isSucceeded());
  }

  @Test
  public void moveNotebookEntryCase5_RSPAC1814() throws InterruptedException {
    // doc owner, subject and notebook owner  are all different
    User u1 = sharee; // alias
    logoutAndLoginAs(u1);
    nb = createNotebookWithNEntries(u1.getRootFolder().getId(), "any", 1, u1);
    logoutAndLoginAs(owner);
    Record doc = createBasicDocumentInRootFolderWithText(owner, "docText");
    Optional<RecordGroupSharing> resultOptional = shareRecordIntoGroupNotebook(doc, nb, grp, owner);
    assertTrue(resultOptional.isPresent()); // sanity check sharing worked
    logoutAndLoginAs(pi);
    assertFalse(
        recordMgr.move(doc.getId(), owner.getRootFolder().getId(), nb.getId(), pi).isSucceeded());
  }

  private int getSharedRecordCountForGroup(Group group) {
    return folderDao.getSharedFolderForGroup(group).getChildrens().size();
  }

  private void assertSharedRecordInSharedFolder(Group group, BaseRecord notebookentry) {
    assertTrue(folderDao.getSharedFolderForGroup(group).getChildrens().contains(notebookentry));
  }

  private void deleteNotebook(User owner, Notebook nb) throws Exception {
    recordDeletionMgr.deleteFolder(owner.getRootFolder().getId(), nb.getId(), owner);
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
}
