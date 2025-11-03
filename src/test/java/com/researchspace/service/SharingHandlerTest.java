package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.Record;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SharingHandlerTest extends SpringTransactionalTest {

  private @Autowired SharingHandler sharingHandler;

  @Test
  public void shareIntoSharedFolder() {
    User admin = createAndSaveAPi();
    initialiseContentWithEmptyContent(admin);
    logoutAndLoginAs(admin);
    RSForm form = formMgr.create(admin);

    User user = createAndSaveUserIfNotExists(CoreTestUtils.getRandomName(10));
    initialiseContentWithEmptyContent(user, admin);

    Group group = new Group(CoreTestUtils.getRandomName(10), admin);
    group.setDisplayName(group.getUniqueName());
    group = grpMgr.saveGroup(group, user);

    group = grpMgr.addUserToGroup(admin.getUsername(), group.getId(), RoleInGroup.PI);
    group = grpMgr.addUserToGroup(user.getUsername(), group.getId(), RoleInGroup.DEFAULT);
    flushDatabaseState();

    Folder sharedFolder =
        grpMgr.createSharedCommunalGroupFolders(group.getId(), admin.getUsername());
    flushDatabaseState();

    Record newDoc =
        recordMgr.createNewStructuredDocument(
            admin.getRootFolder().getId(), form.getId(), admin, true);
    assertFalse(newDoc.isShared());
    sharingHandler.shareIntoSharedFolderOrNotebook(admin, sharedFolder, newDoc.getId(), null);
    newDoc = recordMgr.get(newDoc.getId());
    assertTrue(newDoc.isShared());
  }

  @Test
  public void shareIntoSharedNotebook() throws InterruptedException {
    User admin = createAndSaveAPi();
    initialiseContentWithEmptyContent(admin);
    logoutAndLoginAs(admin);
    RSForm form = formMgr.create(admin);

    User user = createAndSaveUserIfNotExists(CoreTestUtils.getRandomName(10));
    initialiseContentWithEmptyContent(user, admin);

    Group group = new Group(CoreTestUtils.getRandomName(10), admin);
    group.setDisplayName(group.getUniqueName());
    group = grpMgr.saveGroup(group, admin);

    group = grpMgr.addUserToGroup(admin.getUsername(), group.getId(), RoleInGroup.PI);
    group = grpMgr.addUserToGroup(user.getUsername(), group.getId(), RoleInGroup.DEFAULT);
    flushDatabaseState();

    Folder sharedGroupFolder =
        grpMgr.createSharedCommunalGroupFolders(group.getId(), admin.getUsername());
    flushDatabaseState();

    long notebookId =
        createNotebookWithNEntries(sharedGroupFolder.getId(), "notebook", 0, admin).getId();
    flushDatabaseState();

    sharingHandler.shareIntoSharedFolderOrNotebook(admin, sharedGroupFolder, notebookId, null);
    flushDatabaseState();

    final Notebook sharedNotebook = folderMgr.getNotebook(notebookId);
    assertTrue(sharedNotebook.isShared());

    Record newDoc =
        recordMgr.createNewStructuredDocument(sharedNotebook.getId(), form.getId(), user, true);
    assertFalse(newDoc.isShared());
    sharingHandler.shareIntoSharedFolderOrNotebook(
        user, sharedNotebook, newDoc.getId(), sharedNotebook.getParent().getId());
    flushDatabaseState();

    newDoc = recordMgr.get(newDoc.getId());
    assertEquals(2, newDoc.getParentFolders().size());
    assertTrue(newDoc.getParentFolders().stream().anyMatch(f -> f.equals(sharedNotebook)));
    assertTrue(newDoc.getParentFolders().stream().anyMatch(f -> f.getName().equals("notebook")));
    assertTrue(newDoc.isShared());
  }
}
