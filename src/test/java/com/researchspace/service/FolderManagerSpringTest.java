package com.researchspace.service;

import static com.researchspace.core.util.MediaUtils.IMAGES_MEDIA_FLDER_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.license.InactiveLicenseTestService;
import com.researchspace.licensews.LicenseExpiredException;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.controller.ServiceLoggerAspct;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FolderManagerSpringTest extends SpringTransactionalTest {
  User user;

  private @Autowired RecordManager recordManager;
  private @Autowired ServiceLoggerAspct aspect;

  @Before
  public void setUp() throws Exception {
    user = createAndSaveUserIfNotExists(getRandomAlphabeticString("fuser"));
    initialiseContentWithEmptyContent(user);
    assertTrue(user.isContentInitialized());
  }

  @After
  public void tearDown() throws Exception {
    aspect.setLicenseService(licenseService); // restore default
    super.tearDown();
  }

  @Test
  public void testGetMediaTypeFolder() throws Exception {
    // create a new folder
    assertNotNull(recordManager.getGallerySubFolderForUser("unknown", user));
    // should be already present
    assertNotNull(recordManager.getGallerySubFolderForUser(IMAGES_MEDIA_FLDER_NAME, user));
  }

  @Test
  public void testRemoveFolder() throws Exception {
    // we'll delete the image folder from the media folder
    Folder toRemove = recordManager.getGallerySubFolderForUser(IMAGES_MEDIA_FLDER_NAME, user);
    Folder parent = folderDao.getGalleryFolderForUser(user);
    int numChildrenB4 = parent.getChildren().size();
    // remove child
    parent = folderMgr.removeBaseRecordFromFolder(toRemove, parent.getId());

    // get new child count
    int numChildrenAfter = parent.getChildren().size();
    int fromDB = folderDao.getGalleryFolderForUser(user).getChildren().size();
    // check there is now 1 less child
    assertEquals(numChildrenB4 - 1, numChildrenAfter);
    assertEquals(numChildrenB4 - 1, fromDB);
  }

  @Test
  public void testGetDeletedFolder() throws Exception {
    logoutAndLoginAs(user);
    Folder folder =
        folderMgr.createNewFolder(
            user.getRootFolder().getId(), getRandomAlphabeticString("F"), user);
    folderMgr.removeBaseRecordFromFolder(folder, folder.getParent().getId());
    assertAuthorisationExceptionThrown(() -> folderMgr.getFolder(folder.getId(), user));
  }

  @Test
  public void testGetFolderPermissions() throws Exception {
    final User u1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("u1"));
    final User u2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("u2"));
    initialiseContentWithEmptyContent(u1, u2);

    logoutAndLoginAs(u1);
    final Folder f1 = folderMgr.getRootRecordForUser(u1, u1);
    assertAuthorisationExceptionThrown(() -> folderMgr.getRootRecordForUser(u1, u2));

    logoutAndLoginAs(u2);
    final Folder f2 = folderMgr.getRootRecordForUser(u2, u2);
    assertAuthorisationExceptionThrown(() -> folderMgr.getRootRecordForUser(u1, u2));

    // now try getFolder method
    folderMgr.getFolder(f2.getId(), u2);
    assertAuthorisationExceptionThrown(() -> folderMgr.getFolder(f1.getId(), u2));
  }

  @Test
  public void testRemoveRecord() {
    int b4 = user.getRootFolder().getChildren().size();
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(user, "any");
    assertEquals(b4 + 1, getNumChldrenInRootFolder(user));

    // remove child
    Folder uRoot = folderMgr.removeBaseRecordFromFolder(sd, user.getRootFolder().getId());

    // get new child count
    int numChildrenAfter = uRoot.getChildren().size();
    int fromDB = getNumChldrenInRootFolder(user);
    // check there is now 1 less child
    assertEquals(b4, numChildrenAfter);
    assertEquals(b4, fromDB);
  }

  @Test
  public void getGroupFolderRootFromSharedSubfolderTest() throws IllegalAddChildOperation {
    Folder userLabGroupFolder = folderDao.getLabGroupFolderForUser(user);

    // create a dummmy shared group folder with a shared folder underne
    Folder grpFolder = recordFactory.createFolder("agroup", user);
    grpFolder.addType(RecordType.SHARED_GROUP_FOLDER_ROOT);
    folderMgr.addChild(userLabGroupFolder.getId(), grpFolder, user);

    Folder grpSubFolder = recordFactory.createFolder("agroupSubFolder", user);
    folderMgr.addChild(grpFolder.getId(), grpSubFolder, user);
    assertEquals(
        grpFolder,
        folderMgr
            .getGroupOrIndividualShrdFolderRootFromSharedSubfolder(grpSubFolder.getId(), user)
            .get());
    // a folder not a subfolder of group folder will return null.
    assertFalse(
        folderMgr
            .getGroupOrIndividualShrdFolderRootFromSharedSubfolder(userLabGroupFolder.getId(), user)
            .isPresent());

    // now check the same type of assertion, but for individual shared folders
    Folder individSharedFolderRoot = folderDao.getIndividualSharedItemsFolderForUser(user);
    Folder individSharedFolder = recordFactory.createFolder("userA-userB", user);
    individSharedFolder.addType(RecordType.INDIVIDUAL_SHARED_FOLDER_ROOT);
    folderMgr.addChild(individSharedFolderRoot.getId(), individSharedFolder, user);

    Folder sharedWork = recordFactory.createFolder("sharedFolder", user);
    folderMgr.addChild(individSharedFolder.getId(), sharedWork, user);
    assertEquals(
        individSharedFolder,
        folderMgr
            .getGroupOrIndividualShrdFolderRootFromSharedSubfolder(sharedWork.getId(), user)
            .get());
  }

  @Test(expected = LicenseExpiredException.class)
  public void folderCreationRequireActiveLicense() {
    aspect.setLicenseService(new InactiveLicenseTestService());
    logoutAndLoginAs(user);
    folderMgr.createNewFolder(user.getRootFolder().getId(), "any", user);
  }

  @Test(expected = LicenseExpiredException.class)
  public void notebookCreationRequireActiveLicense() {
    aspect.setLicenseService(new InactiveLicenseTestService());
    logoutAndLoginAs(user);
    folderMgr.createNewNotebook(
        user.getRootFolder().getId(), "any", new DefaultRecordContext(), user);
  }

  @Test
  public void userNeedsPermissionsToCreateNotebookAndFolder() throws Exception {
    final User userA = createAndSaveUserIfNotExists(getRandomAlphabeticString("A"));
    final User userB = createAndSaveUserIfNotExists(getRandomAlphabeticString("B"));
    initialiseContentWithEmptyContent(userA, userB);
    logoutAndLoginAs(userA);
    // ok, has permission to create stuff in their own folder
    folderMgr.createNewFolder(userA.getRootFolder().getId(), "any", userA);
    // but can't create stuff in someone elses folder
    assertAuthorisationExceptionThrown(
        () -> folderMgr.createNewFolder(userB.getRootFolder().getId(), "any", userA));

    // same for notebook
    folderMgr.createNewNotebook(
        userA.getRootFolder().getId(), "any", new DefaultRecordContext(), userA);
    assertAuthorisationExceptionThrown(
        () ->
            folderMgr.createNewNotebook(
                userB.getRootFolder().getId(), "any", new DefaultRecordContext(), userA));
  }
}
