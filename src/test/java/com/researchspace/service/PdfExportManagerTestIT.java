package com.researchspace.service;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.lowagie.text.pdf.PdfReader;
import com.researchspace.Constants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.export.pdf.ExportToFileConfig;
import com.researchspace.files.service.InternalFileStore;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.impl.ContentInitializerForDevRunManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import com.researchspace.testutils.SearchTestUtils;
import java.io.IOException;
import org.apache.lucene.queryparser.classic.ParseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.beans.factory.annotation.Autowired;

public class PdfExportManagerTestIT extends RealTransactionSpringTestBase {

  public @Rule TemporaryFolder tempExportFolder = new TemporaryFolder();

  private @Autowired ExportImport exportImportMgr;
  private @Autowired InternalFileStore fStore;
  private @Autowired RecordDeletionManager delMgr;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void exportPdfByOwnerAndSysadmin() throws Exception {
    User exporter = createAndSaveUser(getRandomAlphabeticString("pdf"));
    initUser(exporter);
    logoutAndLoginAs(exporter);
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(exporter, "text");
    ExportToFileConfig config = new ExportToFileConfig();
    final String exportName = "xxxx";
    config.setExportName(exportName);
    EcatDocumentFile edf =
        exportImportMgr
            .asynchExportFromSelection(
                new Long[] {sd.getId()},
                new String[] {sd.getName()},
                new String[] {RecordType.NORMAL.toString()},
                config,
                exporter)
            .get();
    assertNotNull(edf);
    assertTrue(edf.getName().contains(exportName));

    assertTrue(fStore.retrieve(edf.getFileProperty()) != null);

    // now login as sysadmin, check he can export: RSPAC425
    User sysadmin = logoutAndLoginAsSysAdmin();
    initUser(sysadmin);
    EcatDocumentFile edf2 =
        exportImportMgr.synchExportFromSelection(
            new Long[] {sd.getId()},
            new String[] {sd.getName()},
            new String[] {RecordType.NORMAL.toString()},
            config,
            sysadmin);
    assertNotNull(edf2);
    assertTrue(edf2.getName().contains(exportName));
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
    EcatDocumentFile ecatDocumentFile =
        exportImportMgr.synchExportFromSelection(
            new Long[] {sd.getId()},
            new String[] {sd.getName()},
            new String[] {RecordType.NORMAL.toString()},
            config,
            exporterPi);
    assertNotNull(ecatDocumentFile);
    PdfReader reader = new PdfReader(ecatDocumentFile.getFileUri());
    assertTrue(reader.getNumberOfPages() > 0);

    // Exporting folder of another user
    BaseRecord folder = searchByName(user.getUsername(), exporterPi).getFirstResult();
    ecatDocumentFile =
        exportImportMgr.synchExportFromSelection(
            new Long[] {folder.getId()},
            new String[] {"user folder export"},
            new String[] {RecordType.INDIVIDUAL_SHARED_FOLDER_ROOT.toString()},
            config,
            exporterPi);
    assertNotNull(ecatDocumentFile);
    reader = new PdfReader(ecatDocumentFile.getFileUri());
    int pages = reader.getNumberOfPages();
    assertTrue(
        "There were " + pages + " pages", pages >= NUMBER_OF_PAGES_IN_INITIALIZED_USER_WORK_EXPORT);
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
    EcatDocumentFile ecatDocumentFile =
        exportImportMgr.synchExportFromSelection(
            new Long[] {sd.getId()},
            new String[] {sd.getName()},
            new String[] {RecordType.NORMAL.toString()},
            config,
            labAdmin);
    assertNotNull(ecatDocumentFile);
    PdfReader reader = new PdfReader(ecatDocumentFile.getFileUri());
    assertTrue(reader.getNumberOfPages() > 0);

    // Exporting folder of another user
    BaseRecord folder = searchByName(user.getUsername(), labAdmin).getFirstResult();
    ecatDocumentFile =
        exportImportMgr.synchExportFromSelection(
            new Long[] {folder.getId()},
            new String[] {"user folder export"},
            new String[] {RecordType.INDIVIDUAL_SHARED_FOLDER_ROOT.toString()},
            config,
            labAdmin);
    assertNotNull(ecatDocumentFile);
    reader = new PdfReader(ecatDocumentFile.getFileUri());
    int pages = reader.getNumberOfPages();
    assertTrue(
        "There were " + pages + " pages", pages >= NUMBER_OF_PAGES_IN_INITIALIZED_USER_WORK_EXPORT);
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
    EcatDocumentFile export = exportImportMgr.exportGroupPdf(config, pi, grp.getId()).get();
    assertNotNull(export);

    PdfReader reader = new PdfReader(export.getFileUri());
    int b4DeletedPageCount = reader.getNumberOfPages();
    assertTrue(b4DeletedPageCount > 0);
    // now delete LabBook
    BaseRecord toDelete =
        searchByNameAndOwner(ContentInitializerForDevRunManager.EXAMPLE_EXPERIMENT_RECORD_NAME, pi)
            .getFirstResult();
    delMgr.deleteRecord(folderMgr.getRootFolderForUser(pi).getId(), toDelete.getId(), pi);

    export = exportImportMgr.exportGroupPdf(config, pi, grp.getId()).get();
    reader = new PdfReader(export.getFileUri());
    int afterDeletedPageCount = reader.getNumberOfPages();

    assertTrue(afterDeletedPageCount < b4DeletedPageCount);
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
