package com.researchspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.researchspace.Constants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.export.pdf.ExportToFileConfig;
import com.researchspace.files.service.InternalFileStore;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.PdfWordExportManager;
import com.researchspace.service.archive.export.ExportEcatDocumentResult;
import com.researchspace.service.archive.export.ExportFailureException;
import com.researchspace.service.impl.ContentInitializerForDevRunManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import com.researchspace.testutils.SearchTestUtils;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.apache.lucene.queryparser.classic.ParseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

public class PdfExportManagerTestIT extends RealTransactionSpringTestBase {

  @TempDir public File tempExportFolder;

  private @Autowired ExportImport exportImportMgr;
  private @Autowired InternalFileStore fStore;
  private @Autowired RecordDeletionManager delMgr;
  private @Autowired PdfWordExportManager pdfWordExportMgr;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void exportPdfByOwnerAndSysadmin() throws Exception {
    User exporter = createAndSaveUser(getRandomAlphabeticString("pdf"));
    initUser(exporter);
    logoutAndLoginAs(exporter);

    StructuredDocument sd = createBasicDocumentInRootFolderWithText(exporter, "text");
    sd.setName("special chars in doc name &∅∈∌ and &#x3");
    recordMgr.save(sd, exporter);

    ExportToFileConfig config = new ExportToFileConfig();
    final String exportName = "xxxx";
    config.setExportName(exportName);
    ExportEcatDocumentResult exportEcatDocumentResult =
        exportImportMgr
            .asyncExportSelectionToPdf(
                new Long[] {sd.getId()},
                new String[] {sd.getName()},
                new String[] {RecordType.NORMAL.toString()},
                config,
                exporter)
            .get();
    EcatDocumentFile edf = exportEcatDocumentResult.getEcatDocumentFile();
    assertNotNull(edf);
    assertThat(edf.getName()).contains(exportName);

    assertTrue(fStore.retrieve(edf.getFileProperty()) != null);

    // now login as sysadmin, check he can export: RSPAC425
    User sysadmin = logoutAndLoginAsSysAdmin();
    initUser(sysadmin);
    ExportEcatDocumentResult exportEcatDocumentResult2 =
        exportImportMgr.syncExportSelectionToPdf(
            new Long[] {sd.getId()},
            new String[] {sd.getName()},
            new String[] {RecordType.NORMAL.toString()},
            config,
            sysadmin);
    EcatDocumentFile edf2 = exportEcatDocumentResult2.getEcatDocumentFile();
    assertNotNull(edf2);
    assertThat(edf2.getName()).contains(exportName);
  }

  @Test
  public void RSPAC1160_testExportGroupSharedUserFolderByPI() throws Exception {
    final int NUMBER_OF_PAGES_IN_INITIALIZED_USER_WORK_EXPORT = 18;

    User exporterPi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User user = createAndSaveUser(getRandomAlphabeticString("user"));
    initUsers(true, exporterPi, user);

    User sysadmin = logoutAndLoginAsSysAdmin();
    if (!sysadmin.isContentInitialized()) {
      initUser(sysadmin);
    }
    createGroupForUsers(sysadmin, exporterPi.getUsername(), "", exporterPi, user);

    logoutAndLoginAs(user);
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(user, "test contents");

    logoutAndLoginAs(exporterPi);

    ExportToFileConfig config = new ExportToFileConfig();
    config.setExportName("xxxx");

    // Exporting file of another user
    ExportEcatDocumentResult exportEcatDocumentResult =
        exportImportMgr.syncExportSelectionToPdf(
            new Long[] {sd.getId()},
            new String[] {sd.getName()},
            new String[] {RecordType.NORMAL.toString()},
            config,
            exporterPi);
    EcatDocumentFile ecatDocumentFile = exportEcatDocumentResult.getEcatDocumentFile();
    assertNotNull(ecatDocumentFile);
    PdfReader reader = new PdfReader(ecatDocumentFile.getFileUri());
    assertTrue(reader.getNumberOfPages() > 0);

    // Exporting folder of another user
    BaseRecord folder = searchByName(user.getUsername(), exporterPi).getFirstResult();
    exportEcatDocumentResult =
        exportImportMgr.syncExportSelectionToPdf(
            new Long[] {folder.getId()},
            new String[] {"user folder export"},
            new String[] {RecordType.INDIVIDUAL_SHARED_FOLDER_ROOT.toString()},
            config,
            exporterPi);
    ecatDocumentFile = exportEcatDocumentResult.getEcatDocumentFile();
    assertNotNull(ecatDocumentFile);
    reader = new PdfReader(ecatDocumentFile.getFileUri());
    int pages = reader.getNumberOfPages();
    assertTrue(
        pages >= NUMBER_OF_PAGES_IN_INITIALIZED_USER_WORK_EXPORT, "There were " + pages + " pages");
  }

  @Test
  public void RSPAC1160_testPDFExportGroupSharedUserFolderByLabAdminWithViewPrivileges()
      throws Exception {
    final int NUMBER_OF_PAGES_IN_INITIALIZED_USER_WORK_EXPORT = 18;

    User exporterPi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User user = createAndSaveUser(getRandomAlphabeticString("user"));
    User labAdmin = createAndSaveUser(getRandomAlphabeticString("labadmin"));
    initUsers(true, exporterPi, user, labAdmin);

    User sysadmin = logoutAndLoginAsSysAdmin();
    if (!sysadmin.isContentInitialized()) {
      initUser(sysadmin);
    }
    Group group =
        createGroupForUsers(
            sysadmin, exporterPi.getUsername(), labAdmin.getUsername(), exporterPi, user, labAdmin);

    logoutAndLoginAs(exporterPi);
    grpMgr.authorizeLabAdminToViewAll(labAdmin.getId(), exporterPi, group.getId(), true);

    logoutAndLoginAs(user);
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(user, "test contents");

    logoutAndLoginAs(labAdmin);

    ExportToFileConfig config = new ExportToFileConfig();
    config.setExportName("xxxx");

    // Exporting file of another user
    ExportEcatDocumentResult exportEcatDocumentResult =
        exportImportMgr.syncExportSelectionToPdf(
            new Long[] {sd.getId()},
            new String[] {sd.getName()},
            new String[] {RecordType.NORMAL.toString()},
            config,
            labAdmin);
    EcatDocumentFile ecatDocumentFile = exportEcatDocumentResult.getEcatDocumentFile();
    assertNotNull(ecatDocumentFile);
    PdfReader reader = new PdfReader(ecatDocumentFile.getFileUri());
    assertTrue(reader.getNumberOfPages() > 0);

    // Exporting folder of another user
    BaseRecord folder = searchByName(user.getUsername(), labAdmin).getFirstResult();
    exportEcatDocumentResult =
        exportImportMgr.syncExportSelectionToPdf(
            new Long[] {folder.getId()},
            new String[] {"user folder export"},
            new String[] {RecordType.INDIVIDUAL_SHARED_FOLDER_ROOT.toString()},
            config,
            labAdmin);
    ecatDocumentFile = exportEcatDocumentResult.getEcatDocumentFile();
    assertNotNull(ecatDocumentFile);
    reader = new PdfReader(ecatDocumentFile.getFileUri());
    int pages = reader.getNumberOfPages();
    assertTrue(
        pages >= NUMBER_OF_PAGES_IN_INITIALIZED_USER_WORK_EXPORT, "There were " + pages + " pages");
  }

  @Test
  public void exportGroupPdf() throws Exception {
    final User pi =
        createAndSaveUser(getRandomAlphabeticString("pi"), com.researchspace.Constants.PI_ROLE);
    final User u1 = createAndSaveUser(getRandomAlphabeticString("u1"));
    User u2 = createAndSaveUser(getRandomAlphabeticString("u2"));
    initUsers(true, pi, u1, u2);
    User sysadmin = logoutAndLoginAsSysAdmin();
    if (!sysadmin.isContentInitialized()) {
      initUser(sysadmin);
    }
    final Group grp = createGroupForUsers(sysadmin, pi.getUsername(), "", pi, u1, u2);
    ExportToFileConfig config = new ExportToFileConfig();
    config.setExportName("xxxx");
    logoutAndLoginAs(pi);
    ExportEcatDocumentResult exportResult =
        exportImportMgr.asyncExportGroupToPdf(config, pi, grp.getId()).get();
    EcatDocumentFile export = exportResult.getEcatDocumentFile();
    assertNotNull(export);

    PdfReader reader = new PdfReader(export.getFileUri());
    int b4DeletedPageCount = reader.getNumberOfPages();
    assertTrue(b4DeletedPageCount > 0);
    // now delete LabBook
    BaseRecord toDelete =
        searchByNameAndOwner(ContentInitializerForDevRunManager.EXAMPLE_EXPERIMENT_RECORD_NAME, pi)
            .getFirstResult();
    delMgr.deleteRecord(folderMgr.getRootFolderForUser(pi).getId(), toDelete.getId(), pi);

    exportResult = exportImportMgr.asyncExportGroupToPdf(config, pi, grp.getId()).get();
    export = exportResult.getEcatDocumentFile();
    reader = new PdfReader(export.getFileUri());
    int afterDeletedPageCount = reader.getNumberOfPages();

    assertTrue(afterDeletedPageCount < b4DeletedPageCount);
  }

  @Test
  public void exportTagRetrievalSkipsContentOfDeletedSelectedFolder() throws Exception {
    User owner = createAndSaveUser(getRandomAlphabeticString("export"));
    Folder root = initUser(owner);
    logoutAndLoginAs(owner);
    Folder deleted = folderMgr.createNewFolder(root.getId(), "deleted", owner);
    StructuredDocument excluded = createBasicDocumentInFolder(owner, deleted, "excluded content");
    deleted.setRecordDeleted(true);
    folderMgr.save(deleted, owner);
    assertFalse(recordMgr.get(excluded.getId()).isDeleted());

    List<Long> ids =
        pdfWordExportMgr.getExportIDsForTagRetrievalFromFilesAndFolders(
            List.of(deleted.getId()), new ExportToFileConfig(), owner);

    assertThat(ids).containsOnly(deleted.getId());
  }

  @Test
  public void pdfExportSkipsContentOfDeletedSelectedFolder() throws Exception {
    User owner = createAndSaveUser(getRandomAlphabeticString("export"));
    Folder root = initUser(owner);
    logoutAndLoginAs(owner);
    Folder deleted = folderMgr.createNewFolder(root.getId(), "deleted", owner);
    StructuredDocument excluded = createBasicDocumentInFolder(owner, deleted, "HIDDENEXPORTMARKER");
    Folder active = folderMgr.createNewFolder(root.getId(), "active", owner);
    createBasicDocumentInFolder(owner, active, "VISIBLEEXPORTMARKER");
    deleted.setRecordDeleted(true);
    folderMgr.save(deleted, owner);
    assertFalse(recordMgr.get(excluded.getId()).isDeleted());

    ExportEcatDocumentResult result =
        pdfWordExportMgr.doExport(
            owner,
            new Long[] {deleted.getId(), active.getId()},
            new String[] {deleted.getName(), active.getName()},
            new String[] {RecordType.FOLDER.name(), RecordType.FOLDER.name()},
            new ExportToFileConfig(),
            owner);

    assertNotNull(result.getEcatDocumentFile());
    try (PdfReader reader = new PdfReader(result.getEcatDocumentFile().getFileUri())) {
      PdfTextExtractor extractor = new PdfTextExtractor(reader);
      StringBuilder text = new StringBuilder();
      for (int page = 1; page <= reader.getNumberOfPages(); page++) {
        text.append(extractor.getTextFromPage(page));
      }
      assertThat(text.toString())
          .contains("VISIBLEEXPORTMARKER")
          .doesNotContain("HIDDENEXPORTMARKER");
    }
  }

  @Test
  public void deletedFolderExportsStillRequireReadAccess() throws Exception {
    User owner = createAndSaveUser(getRandomAlphabeticString("owner"));
    Folder root = initUser(owner);
    logoutAndLoginAs(owner);
    Folder deleted = folderMgr.createNewFolder(root.getId(), "deleted", owner);
    deleted.setRecordDeleted(true);
    folderMgr.save(deleted, owner);
    User outsider = createAndSaveUser(getRandomAlphabeticString("outsider"));
    initUser(outsider);
    logoutAndLoginAs(outsider);
    ExportToFileConfig config = new ExportToFileConfig();

    assertThrows(
        ExportFailureException.class,
        () ->
            pdfWordExportMgr.getExportIDsForTagRetrievalFromFilesAndFolders(
                List.of(deleted.getId()), config, outsider));
    assertThrows(
        ExportFailureException.class,
        () ->
            pdfWordExportMgr.doExport(
                outsider,
                new Long[] {deleted.getId()},
                new String[] {deleted.getName()},
                new String[] {RecordType.FOLDER.name()},
                config,
                outsider));
  }

  private ISearchResults<BaseRecord> searchByNameAndOwner(String name, User owner)
      throws IOException {
    return searchMgr.searchWorkspaceRecords(
        SearchTestUtils.createByNameAndOwner(name, owner), owner);
  }

  private ISearchResults<BaseRecord> searchByName(String name, User owner)
      throws IOException, ParseException {
    return searchMgr.searchWorkspaceRecords(SearchTestUtils.createSimpleNameSearchCfg(name), owner);
  }
}
