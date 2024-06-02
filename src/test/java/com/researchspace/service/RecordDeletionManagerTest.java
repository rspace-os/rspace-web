package com.researchspace.service;

import static com.researchspace.testutils.RSpaceTestUtils.logoutCurrUserAndLoginAs;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMP;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.controller.GalleryController;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

public class RecordDeletionManagerTest extends SpringTransactionalTest {

  private User user;
  private @Autowired DMPManager dmpMgr;
  private @Autowired GalleryController galleryController;

  @Before
  public void setUp() throws Exception {

    user = createAndSaveUserIfNotExists(getRandomAlphabeticString("any"));
    initialiseContentWithEmptyContent(user);
    assertTrue(user.isContentInitialized());
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testDeleteTemplateDoesntScrewUpBasicForm() throws Exception {
    logoutAndLoginAs(user);
    StructuredDocument sdoc = createBasicDocumentInRootFolderWithText(user, "any");
    StructuredDocument template =
        createTemplateFromDocumentAndAddtoTemplateFolder(sdoc.getId(), user);
    Folder galeryFolder = template.getParent();
    assertTrue(sdoc.getForm().isSystemForm());
    assertTrue(sdoc.getForm().isCurrent());

    recordDeletionMgr.deleteRecord(galeryFolder.getId(), template.getId(), user);

    // now lets load up the form again:
    RSForm basic = formDao.getBasicDocumentForm();
    assertTrue(basic.isPublishedAndVisible());
    assertTrue(basic.isCurrent());
  }

  @Test
  public void testSimpleDeleteRecord() throws Exception {
    // delete a single record from personal folder
    StructuredDocument sdoc = createBasicDocumentInRootFolderWithText(user, "any");
    Folder root = folderDao.getRootRecordForUser(user);
    recordDeletionMgr.deleteRecord(root.getId(), sdoc.getId(), user);

    StructuredDocument deleted = (StructuredDocument) recordDao.get(sdoc.getId());
    assertTrue(deleted.isDeleted());
    assertTrue(deleted.getParents().iterator().next().isRecordInFolderDeleted());
  }

  @Test
  public void testSimpleDeleteRecordSet() throws Exception {
    logoutAndLoginAs(user);
    // delete a list of records from personal folder
    Set<EcatMediaFile> fileSet = new HashSet<>();
    fileSet.add(baseRecordMgr.retrieveMediaFile(user, uploadImageIntoRspace("image1").getId()));
    fileSet.add(baseRecordMgr.retrieveMediaFile(user, uploadImageIntoRspace("image2").getId()));

    recordDeletionMgr.deleteMediaFileSet(fileSet, user);

    BaseRecord deleted = baseRecordMgr.get(fileSet.iterator().next().getId(), user);
    assertTrue(deleted.isDeleted());
    assertTrue(deleted.getParents().iterator().next().isRecordInFolderDeleted());

    deleted = baseRecordMgr.get(fileSet.iterator().next().getId(), user);
    assertTrue(deleted.isDeleted());
    assertTrue(deleted.getParents().iterator().next().isRecordInFolderDeleted());
  }

  @Test(expected = AuthorizationException.class)
  public void testSimpleDeleteFolderPermissions() throws Exception {
    // delete a single record from personal folder
    StructuredDocument sdoc = createBasicDocumentInRootFolderWithText(user, "any");
    Folder root = folderDao.getRootRecordForUser(user);
    // other user cannot deleted
    User other = createAndSaveUserIfNotExists("other");
    logoutCurrUserAndLoginAs(other.getUsername(), TESTPASSWD);
    try {
      recordDeletionMgr.deleteRecord(root.getId(), sdoc.getId(), other);
    } catch (AuthorizationException ae) {
      // assert is not deleted
      sdoc = (StructuredDocument) recordDao.get(sdoc.getId());
      assertFalse(sdoc.isDeleted());
      assertFalse(sdoc.getParents().iterator().next().isRecordInFolderDeleted());
      throw ae;
    }
  }

  @Test
  public void testSimpleDeleteFolder() throws Exception {
    // delete a single folder from personal folder
    Folder root = folderDao.getRootRecordForUser(user);
    Folder f = createFolder("f1", root, user);

    recordDeletionMgr.deleteFolder(root.getId(), f.getId(), user);

    Folder deleted = folderDao.get(f.getId());
    assertTrue(deleted.isDeleted());
    assertTrue(deleted.getParents().iterator().next().isRecordInFolderDeleted());
  }

  @Test
  public void testDeleteFolderWithMultipleRecords() throws Exception {
    Folder root = folderDao.getRootRecordForUser(user);
    Folder f = createFolder("f1", root, user);
    StructuredDocument sdoc1 = recordMgr.createBasicDocument(f.getId(), user);
    StructuredDocument sdoc2 = recordMgr.createBasicDocument(f.getId(), user);
    recordDeletionMgr.deleteFolder(root.getId(), f.getId(), user);
  }

  @Test
  public void testDeleteNotebook() throws Exception {
    Folder root = folderDao.getRootRecordForUser(user);
    Notebook nbk = createNotebookWithNEntries(root.getId(), "notebook", 2, user);
    for (BaseRecord br : nbk.getChildrens()) {
      assertFalse(br.isDeletedForUser(user));
    }
    recordDeletionMgr.deleteFolder(root.getId(), nbk.getId(), user);
    Notebook deleted = folderMgr.getNotebook(nbk.getId());
    assertTrue(deleted.isDeleted());
    assertTrue(deleted.isDeletedForUser(user));
    for (BaseRecord br : nbk.getChildrens()) {
      assertTrue(br.isDeletedForUser(user));
    }
  }

  @Test
  public void testDeleteNestedFolder() throws Exception {
    Folder root = folderDao.getRootRecordForUser(user);
    // root->f1->f2->doc tree structure
    Folder f = createFolder("f1", root, user);
    Folder f2 = createFolder("f2", f, user);
    StructuredDocument sdoc = recordMgr.createBasicDocument(f2.getId(), user);

    // f and its childred should all should be deleted recursively
    recordDeletionMgr.deleteFolder(root.getId(), f.getId(), user);

    Folder deletedf1 = folderDao.get(f.getId());
    assertEquals(1, deletedf1.getChildren().size()); // still is a child - reln is till there

    Folder deletedf2 = folderDao.get(f2.getId());
    assertEquals(1, deletedf2.getChildren().size()); // still is a child - reln is till there
    Record deletedsdoc = recordDao.get(sdoc.getId());
    assertTrue(deletedsdoc.isDeleted());
    assertTrue(deletedsdoc.getParents().iterator().next().isRecordInFolderDeleted());
    // sysadmin can't view deleted folder RSPAC-1285
    logoutAndLoginAsSysAdmin();
    assertAuthorisationExceptionThrown(() -> folderMgr.getFolder(f.getId(), user));
  }

  @Test
  public void deleteDMPPdfDeletesDMP() throws IOException, DocumentAlreadyEditedException {
    InputStream inputStream =
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("smartscotland3.pdf");
    var ecatDocumentFile = mediaMgr.saveNewDMP("smartscotland3.pdf", inputStream, user, null);
    var dmpUser = new DMPUser(user, new DMP("DMPid23", "somet title"));
    dmpUser.setDmpDownloadPdf(ecatDocumentFile);
    dmpMgr.save(dmpUser);
    assertEquals(1, dmpMgr.findDMPsForUser(user).size());

    recordDeletionMgr.deleteRecord(null, ecatDocumentFile.getId(), user);
    assertEquals(0, dmpMgr.findDMPsForUser(user).size());
  }

  private RecordInformation uploadImageIntoRspace(String name) throws IOException {
    MockMultipartFile mf =
        new MockMultipartFile(
            "xfile", name + ".png", "png", getTestResourceFileStream("Picture1.png"));
    return galleryController.uploadFile(mf, null, null, null).getData();
  }
}
