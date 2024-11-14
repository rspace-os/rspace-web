package com.researchspace.service;

import static com.researchspace.core.util.progress.ProgressMonitor.NULL_MONITOR;
import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchiveResult;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.RecordCopyResult;
import com.researchspace.model.views.ServiceOperationResultCollection;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.ImportArchiveReport;
import com.researchspace.service.archive.ImportStrategy;
import com.researchspace.service.archive.PostArchiveCompletion;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import com.researchspace.testutils.TestGroup;
import java.io.File;
import java.net.URI;
import java.util.concurrent.Future;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class AutoshareManagerIT extends RealTransactionSpringTestBase {

  private @Autowired AutoshareManager autoshareMgr;
  private @Autowired ExportImport exportMgr;
  private @Autowired RecordDeletionManager recordDeletionManager;
  private @Autowired AuditManager auditManager;
  public @Rule TemporaryFolder tempExportFolder = new TemporaryFolder();
  public @Rule TemporaryFolder tempImportFolder = new TemporaryFolder();

  @Autowired
  @Qualifier("standardPostExportCompletionImpl")
  private PostArchiveCompletion standardPostExport;

  private @Autowired @Qualifier("importUsersAndRecords") ImportStrategy importStrategy;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void enableAutoshare() throws Exception {
    TestGroup testGroup = createTestGroup(1);
    Group grp = testGroup.getGroup();
    User u1 = testGroup.getUserByPrefix("u1");
    assertFalse(u1.hasAutoshareGroups());
    String fName = "autoshare-" + RandomStringUtils.randomAlphabetic(5);
    grp = grpMgr.enableAutoshareForUser(u1, grp.getId());
    Folder autoshareFolder = grpMgr.createAutoshareFolder(u1, grp, fName);

    User updated = userMgr.getUserByUsername(u1.getUsername());
    assertTrue(updated.hasAutoshareGroups());
    assertEquals(grp, updated.getAutoshareGroups().iterator().next());
    // autoshared folder is set up in correct location
    final Group grp2 = grp;
    Folder grpFolder = doInTransaction(() -> folderDao.getSharedFolderForGroup(grp2));
    assertEquals(grpFolder, autoshareFolder.getParent());
  }

  @Test
  public void autoshareAfterDocumentCreateCopy() {
    AutoshareTestGroup atg = setUpGroupWithUIAutosharingLoginU1();
    TestGroup testGroup = atg.getTestGroup();
    User u1 = testGroup.u1();
    StructuredDocument document = createBasicDocumentInRootFolderWithText(u1, "some text");
    assertDistinctSharedRecordCountForU1(1, testGroup);

    // copy should trigger share
    recordMgr.copy(document.getId(), "copy", u1, getRootFolderForUser(u1).getId());
    assertRecordCountInAutoshareFolderForU1(2, atg);
    assertDistinctSharedRecordCountForU1(2, testGroup);
  }

  @Test
  public void autoshareDeepFolderCopy() throws Exception {
    AutoshareTestGroup atg = setUpGroupWithUIAutosharingLoginU1();
    TestGroup testGroup = atg.getTestGroup();
    User u1 = testGroup.u1();
    Folder subFolder = setUpFolderTree(u1);
    assertDistinctSharedRecordCountForU1(2, testGroup);

    // we copy the top folder. The copied notebook and document should be shared.
    RecordCopyResult result = folderMgr.copy(subFolder.getId(), u1, "foldercopy");
    assertTrue(result.isFolderCopy());
    assertDistinctSharedRecordCountForU1(4, testGroup);
  }

  // set up folder with this structure
  // folder (doc, child (notebook(e1,e2,e3)))
  private Folder setUpFolderTree(User u1) throws Exception, InterruptedException {
    Folder subFolder = doInTransaction(() -> createFolder(getRootFolderForUser(u1), u1, "to-Copy"));
    StructuredDocument doc = createBasicDocumentInFolder(u1, subFolder, "doc not in notebook");
    Folder childFolder = doInTransaction(() -> createFolder(subFolder, u1, "to-Copy"));

    Notebook notebook = createNotebookWithNEntries(childFolder.getId(), "nb", 3, u1);
    return subFolder;
  }

  @Test
  public void templatesAreNotAutoshared() throws Exception {
    AutoshareTestGroup atg = setUpGroupWithUIAutosharingLoginU1();
    TestGroup testGroup = atg.getTestGroup();
    User u1 = testGroup.getUserByPrefix("u1");
    StructuredDocument originalDoc = createBasicDocumentInRootFolderWithText(u1, "some text");
    openTransaction();

    // making a template, should NOT be shared
    StructuredDocument template =
        createTemplateFromDocumentAndAddtoTemplateFolder(originalDoc.getId(), u1);
    commitTransaction();
    assertRecordCountInAutoshareFolderForU1(1, atg);
    assertDistinctSharedRecordCountForU1(1, testGroup);

    // now make doc from the template, the doc should  be shared
    createFromTemplate(u1, template, "fromTemplate");
    assertRecordCountInAutoshareFolderForU1(2, atg);
    assertDistinctSharedRecordCountForU1(2, testGroup);

    // now copy the template, the template-copy should NOT be shared
    recordMgr.copy(template.getId(), "copy-template", u1, getRootFolderForUser(u1).getId());
    assertRecordCountInAutoshareFolderForU1(2, atg);
    assertDistinctSharedRecordCountForU1(2, testGroup);
  }

  @Test
  public void templatesArentAutosharedByBulkAutoshare() throws DocumentAlreadyEditedException {
    // create a template
    AutoshareTestGroup atg = setUpGroupWithUIAutosharingLoginU1();
    TestGroup testGroup = atg.getTestGroup();

    User u1 = testGroup.getUserByPrefix("u1");
    StructuredDocument originalDoc = createBasicDocumentInRootFolderWithText(u1, "some text");

    openTransaction();
    createTemplateFromDocumentAndAddtoTemplateFolder(originalDoc.getId(), u1);
    commitTransaction();

    // now re-enable, template shouldn't be shared
    grpMgr.disableAutoshareForUser(u1, testGroup.getGroup().getId());
    grpMgr.enableAutoshareForUser(u1, testGroup.getGroup().getId());
    Folder autoshareFolder = grpMgr.createAutoshareFolder(u1, testGroup.getGroup(), "folder");
    atg.setAutoshareFolder(autoshareFolder);

    // just the original document should be shared
    assertRecordCountInAutoshareFolderForU1(1, atg);
  }

  @Getter
  @Setter
  @AllArgsConstructor
  static class AutoshareTestGroup {
    private TestGroup testGroup;
    private Folder autoshareFolder;
  }

  private AutoshareTestGroup setUpGroupWithUIAutosharingLoginU1() {
    TestGroup testGroup = createTestGroup(1);
    Group grp = testGroup.getGroup();
    User u1 = testGroup.getUserByPrefix("u1");
    grp = grpMgr.enableAutoshareForUser(u1, grp.getId());
    Folder autoshareFolder = grpMgr.createAutoshareFolder(u1, grp, "autoshare");
    u1 = userMgr.getUserByUsername(u1.getUsername());
    testGroup.getUnameToUser().put(u1.getUsername(), u1);
    testGroup.setGroup(grp);
    logoutAndLoginAs(u1);

    return new AutoshareTestGroup(testGroup, autoshareFolder);
  }

  @Test
  public void shareDocsAndNotebooks() throws Exception {
    TestGroup testGroup = createTestGroup(1);
    Group grp = testGroup.getGroup();
    User u1 = testGroup.getUserByPrefix("u1");

    logoutAndLoginAs(u1);
    Folder u1RootFolder = getRootFolderForUser(u1);
    // with autosharing OFF we create 2 notebooks and share one of them.
    Notebook sharedNb = createNotebookWithNEntries(u1RootFolder.getId(), "anynb-preshared", 3, u1);
    StructuredDocument presharedDocument = createBasicDocumentInFolder(u1, u1RootFolder, "text");
    StructuredDocument unshareDocument = createBasicDocumentInFolder(u1, u1RootFolder, "text");
    Notebook unshared = createNotebookWithNEntries(u1RootFolder.getId(), "anynb-notshared", 2, u1);
    // share 1 notebook and 1 doc before enabling autosharing
    shareNotebookWithGroup(u1, sharedNb, grp, "read");
    shareRecordWithGroup(u1, grp, presharedDocument);
    assertEquals(2, sharingMgr.getSharedRecordsForUser(u1).size());

    // enable autoshare
    grp = grpMgr.enableAutoshareForUser(u1, grp.getId());
    Folder autoshareFolder = grpMgr.createAutoshareFolder(u1, grp, "autoshare");
    u1 = userMgr.getUserByUsername(u1.getUsername());

    ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> result =
        autoshareMgr.bulkShareAllRecords(u1, grp, autoshareFolder);
    // the unshared doc and unshared notebook were shared
    assertEquals(2, result.getResultCount());
    assertEquals(0, result.getFailureCount());
    assertEquals(unshared.getId(), result.getResults().get(0).getShared().getId());
    // the 2 notebooks & 2 docs are shared now
    assertEquals(4, sharingMgr.getSharedRecordsForUser(u1).size());

    // now, create a new entry in the notebook.
    // It shouldn't be shared as its parent notebook is already shared.
    createBasicDocumentInFolder(u1, sharedNb, "text");
    assertEquals(4, sharingMgr.getSharedRecordsForUser(u1).size());
  }

  @Test
  public void deletedAndTempRecordsAreNotAutoshared() throws Exception {
    TestGroup testGroup = createTestGroup(1);
    Group grp = testGroup.getGroup();
    User u1 = testGroup.getUserByPrefix("u1");

    logoutAndLoginAs(u1);
    Folder u1RootFolder = getRootFolderForUser(u1);

    // with autosharing OFF we create docs and notebook
    StructuredDocument presharedDocument = createBasicDocumentInFolder(u1, u1RootFolder, "text");
    StructuredDocument autosharedDocument = createBasicDocumentInFolder(u1, u1RootFolder, "text");
    StructuredDocument deletedDocument = createBasicDocumentInFolder(u1, u1RootFolder, "text");
    recordDeletionMgr.deleteRecord(u1RootFolder.getId(), deletedDocument.getId(), u1);
    Notebook deletedNotebook = createNotebookWithNEntries(u1RootFolder.getId(), "anynb", 3, u1);
    recordDeletionMgr.deleteFolder(u1RootFolder.getId(), deletedNotebook.getId(), u1);

    // create and start editing another document, so temp record is created
    StructuredDocument midEditDocument = createBasicDocumentInFolder(u1, u1RootFolder, "text");
    Field field = midEditDocument.getFields().get(0);
    recordMgr.saveTemporaryDocument(field, u1, "new data");

    // share 1 doc before enabling autosharing, otherwise some group configuration problem happens
    shareRecordWithGroup(u1, grp, presharedDocument);
    assertEquals(1, sharingMgr.getSharedRecordsForUser(u1).size());

    // enable autoshare
    grp = grpMgr.enableAutoshareForUser(u1, grp.getId());
    Folder autoshareFolder = grpMgr.createAutoshareFolder(u1, grp, "autoshare");
    u1 = userMgr.getUserByUsername(u1.getUsername());

    ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> result =
        autoshareMgr.bulkShareAllRecords(u1, grp, autoshareFolder);
    // basic doc was already shared
    assertEquals(2, result.getResultCount());
    assertEquals(autosharedDocument.getId(), result.getResults().get(0).getShared().getId());
    assertEquals(0, result.getFailureCount());
  }

  @Test
  public void testImportArchive() throws Exception {
    AutoshareTestGroup atg = setUpGroupWithUIAutosharingLoginU1();
    TestGroup testGroup = atg.getTestGroup();
    User u1 = testGroup.u1();
    final User u = u1;
    Folder subFolder = setUpFolderTree(u1);
    assertDistinctSharedRecordCountForU1(2, testGroup);
    // export top-level folder
    ArchiveExportConfig cfg = createDefaultArchiveConfig(u1, tempExportFolder.getRoot());
    ExportSelection exportSelection =
        ExportSelection.createRecordsExportSelection(
            new Long[] {subFolder.getId()}, new String[] {"FOLDER"});
    Future<ArchiveResult> result =
        exportMgr.asyncExportSelectionToArchive(
            exportSelection, cfg, u1, new URI("http://www.google.com"), standardPostExport);
    File zipFile = result.get().getExportFile();
    // update user
    u1 = userMgr.getUserByUsername(u1.getUsername());
    ArchivalImportConfig iconfig = createDefaultArchiveImportConfig(u1, tempImportFolder.getRoot());
    ImportArchiveReport report =
        exportMgr.importArchive(
            fileToMultipartfile(zipFile.getName(), zipFile),
            u1.getUsername(),
            new ArchivalImportConfig(),
            NULL_MONITOR,
            importStrategy::doImport);

    assertDistinctSharedRecordCountForU1(4, testGroup);
    // assert the imported notebook is in the shared list
    assertTrue(
        getSharedDocsForU1(testGroup).getResults().stream()
            .map(RecordGroupSharing::getShared)
            .anyMatch(br -> report.getImportedNotebooks().contains(br)));
  }

  @Test
  public void testRestoreDeleted() throws Exception {
    AutoshareTestGroup atg = setUpGroupWithUIAutosharingLoginU1();
    TestGroup testGroup = atg.getTestGroup();
    User u1 = testGroup.u1();
    Folder subFolder = setUpFolderTree(u1);

    assertDistinctSharedRecordCountForU1(2, testGroup);

    recordDeletionManager.deleteFolder(getRootFolderForUser(u1).getId(), subFolder.getId(), u1);

    // should be unshared after deletion
    assertDistinctSharedRecordCountForU1(0, testGroup);

    // now restore, notebooks & docs should be shared.
    RestoreDeletedItemResult result = auditManager.fullRestore(subFolder.getId(), u1);
    assertEquals(7, result.getRestoredItemCount());
    assertDistinctSharedRecordCountForU1(2, testGroup);
  }

  void assertRecordCountInAutoshareFolderForU1(int expected, AutoshareTestGroup atg) {
    assertEquals(expected, getRecordCountInFolderForUser(atg.getAutoshareFolder().getId()));
  }

  void assertDistinctSharedRecordCountForU1(int expected, TestGroup testGroup) {
    assertEquals(
        expected,
        getSharedDocsForU1(testGroup).getResults().stream()
            .map(RecordGroupSharing::getShared)
            .collect(toSet())
            .size());
  }

  ISearchResults<RecordGroupSharing> getSharedDocsForU1(TestGroup testGroup) {
    PaginationCriteria<RecordGroupSharing> pgCrit =
        PaginationCriteria.createDefaultForClass(RecordGroupSharing.class);
    ISearchResults<RecordGroupSharing> results =
        sharingMgr.listSharedRecordsForUser(testGroup.u1(), pgCrit);
    return results;
  }
}
