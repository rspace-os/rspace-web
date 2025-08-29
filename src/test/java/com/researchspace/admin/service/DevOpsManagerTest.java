package com.researchspace.admin.service;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** Unit tests covering DevOps fixes. */
@TestPropertySource(properties = {"rs.dev.unsafeMove.allowed=true"})
public class DevOpsManagerTest extends SpringTransactionalTest {

  @Autowired private DevOpsManager devOpsMgr;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void checkRecordFixNoProblemFound() {
    User user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);
    StructuredDocument testDoc = createBasicDocumentInRootFolderWithText(user, "test");
    assertEquals(
        "No checks nor fixes currently implemented for records of type 'SD'.",
        devOpsMgr.fixRecord(testDoc.getOid(), user, false));

    Folder userWorkspace = testDoc.getParent();
    assertEquals(
        "Found a folder '"
            + user.getUsername()
            + "' (type: FOLDER:ROOT).<br/>\nIt seems fine.<br/>\n",
        devOpsMgr.fixRecord(userWorkspace.getOid(), user, false));
  }

  @Test
  public void checkRecordFixFolderMovedFromSharedIntoWorkspace() {

    // create users and group
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), "ROLE_PI");
    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("u1"));
    initialiseContentWithEmptyContent(pi, user);
    Group group = createGroup("g1", pi);
    addUsersToGroup(pi, group, user);

    // create shared subfolder with content shared by  pi and user
    Folder sharedSubfolder = setUpTestSharedSubfolderWithContent(group);
    Folder groupFolder = sharedSubfolder.getParent();
    assertTrue(sharedSubfolder.isSharedFolder());
    assertTrue(sharedSubfolder.isShared());
    assertEquals(2, folderMgr.getRecordIds(sharedSubfolder).size());
    String originalAcl = sharedSubfolder.getSharingACL().getAcl();

    // operate as pi, from now on
    logoutAndLoginAs(pi);

    // call fix method for shared subfolder - should say nothing is wrong
    String fixMethodMessage = devOpsMgr.fixRecord(sharedSubfolder.getOid(), pi, false);
    assertEquals(
        "Found a folder 'ToShareInto' (type: FOLDER:SHARED_FOLDER).<br/>\n"
            + "It seems fine.<br/>\n",
        fixMethodMessage);

    // move shared subfolder folder into pi's workspace (SUPPORT-526)
    ServiceOperationResult<Folder> folderMoveResult =
        folderMgr.move(
            sharedSubfolder.getId(), pi.getRootFolder().getId(), groupFolder.getId(), pi);
    assertTrue(folderMoveResult.isSucceeded());
    sharedSubfolder = folderMgr.getFolder(sharedSubfolder.getId(), pi);
    assertTrue(sharedSubfolder.isSharedFolder());
    assertFalse(sharedSubfolder.isShared());
    assertFalse(sharedSubfolder.getParent().isSharedFolder());
    assertNotEquals(originalAcl, sharedSubfolder.getSharingACL().getAcl());

    // call fix method for shared subfolder - should suggest moving folder back to shared
    fixMethodMessage = devOpsMgr.fixRecord(sharedSubfolder.getOid(), pi, false);
    String expectedFoundFixMessage =
        String.format(
            "Found a folder 'ToShareInto' (type: FOLDER:SHARED_FOLDER).<br/>\n"
                + "This is a shared folder, but is outside of 'Shared' hierarchy, which seems"
                + " wrong.<br/>\n"
                + "Found one possible target for moving the folder: '%s' (%s).<br/>\n"
                + "To move current folder %s into folder %s call the current URL with"
                + " '?update=true' suffix.<br/>\n",
            groupFolder.getName(),
            groupFolder.getOid(),
            sharedSubfolder.getGlobalIdentifier(),
            groupFolder.getGlobalIdentifier());
    assertEquals(expectedFoundFixMessage, fixMethodMessage);

    // as a pi, create regular docB in the moved folder (it is possible as it's in your Workspace)
    StructuredDocument docC = createBasicDocumentInRootFolderWithText(pi, "docC");
    assertFalse(docC.isShared());
    ServiceOperationResult<BaseRecord> recordMoveResult =
        recordMgr.move(docC.getId(), sharedSubfolder.getId(), docC.getParent().getId(), pi);
    assertTrue(recordMoveResult.isSucceeded());
    assertEquals(3, folderMgr.getRecordIds(sharedSubfolder).size());

    // call fix method - should say no fix possible as folder has mixed content
    fixMethodMessage = devOpsMgr.fixRecord(sharedSubfolder.getOid(), pi, false);
    assertEquals(
        "Found a folder 'ToShareInto' (type: FOLDER:SHARED_FOLDER).<br/>\n"
            + "This is a shared folder, but is outside of 'Shared' hierarchy, which seems"
            + " wrong.<br/>\n"
            + "Couldn't find any good new location for the folder.<br/>\n",
        fixMethodMessage);

    // move docC out of the moved folder
    recordMoveResult =
        recordMgr.move(docC.getId(), pi.getRootFolder().getId(), sharedSubfolder.getId(), pi);
    assertTrue(recordMoveResult.isSucceeded());
    assertEquals(2, folderMgr.getRecordIds(sharedSubfolder).size());

    // call fix method - should suggest moving folder back to shared
    fixMethodMessage = devOpsMgr.fixRecord(sharedSubfolder.getOid(), pi, false);
    assertEquals(expectedFoundFixMessage, fixMethodMessage);

    // call fix method with confirmation - should move folder back to shared
    fixMethodMessage = devOpsMgr.fixRecord(sharedSubfolder.getOid(), pi, true);
    String expectedAppliedFixMessage =
        String.format(
            "Found a folder 'ToShareInto' (type: FOLDER:SHARED_FOLDER).<br/>\n"
                + "This is a shared folder, but is outside of 'Shared' hierarchy, which seems"
                + " wrong.<br/>\n"
                + "Found one possible target for moving the folder: '%s' (%s).<br/>\n"
                + "Attempting to apply the fix... the folder moved successfully.<br/>\n",
            groupFolder.getName(), groupFolder.getOid());
    assertEquals(expectedAppliedFixMessage, fixMethodMessage);

    // note folder's permission - should be back to original
    sharedSubfolder = folderMgr.getFolder(sharedSubfolder.getId(), pi);
    assertTrue(sharedSubfolder.isSharedFolder());
    assertTrue(sharedSubfolder.isShared());
    assertTrue(sharedSubfolder.getParent().isSharedFolder());
    assertEquals(originalAcl, sharedSubfolder.getSharingACL().getAcl());
  }

  private Folder setUpTestSharedSubfolderWithContent(Group group) {

    // as a pi, create shared subfolder within a group, share a document into it with 'write'
    User pi = group.getPiusers().stream().findFirst().get();
    logoutAndLoginAs(pi);
    Folder groupFolder = folderDao.getSharedFolderForGroup(group);
    Folder toShareInto = folderMgr.createNewFolder(groupFolder.getId(), "ToShareInto", pi);
    StructuredDocument piDoc = createBasicDocumentInRootFolderWithText(pi, "docA");
    piDoc =
        shareRecordIntoGroupSubfolder(pi, piDoc, group, toShareInto, "write")
            .getShared()
            .asStrucDoc();
    assertTrue(piDoc.isShared());

    // as a regular user, create docB and share into subfolder with 'read'
    User user = group.getAllNonPIMembers().stream().findFirst().get();
    logoutAndLoginAs(user);
    StructuredDocument userDoc = createBasicDocumentInRootFolderWithText(user, "any");
    userDoc =
        shareRecordIntoGroupSubfolder(user, userDoc, group, toShareInto, "read")
            .getShared()
            .asStrucDoc();
    assertTrue(userDoc.isShared());

    logoutCurrentUser();
    return folderMgr.getFolder(toShareInto.getId(), pi);
  }
}
