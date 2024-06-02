package com.researchspace.service;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.core.testutil.FileTestUtils.hasFileWithNAmePrefix;
import static com.researchspace.core.util.FieldParserConstants.DATA_TYPE_ANNOTATION;
import static com.researchspace.core.util.progress.ProgressMonitor.NULL_MONITOR;
import static com.researchspace.model.core.RecordType.MEDIA_FILE;
import static com.researchspace.model.core.RecordType.NORMAL;
import static com.researchspace.model.netfiles.NfsElement.FULL_PATH_DATA_ATTR_NAME;
import static com.researchspace.service.archive.ExportImport.NFS_EXPORT_XML;
import static com.researchspace.service.archive.export.HTMLArchiveExporter.NFS_LINKS_HTML;
import static com.researchspace.testutils.ArchiveTestUtils.assertPredicateOnHtmlFile;
import static com.researchspace.testutils.ArchiveTestUtils.getAllHTMLFilesInArchive;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringUtils.join;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.springframework.test.jdbc.JdbcTestUtils.countRowsInTable;

import com.axiope.search.SearchConstants;
import com.researchspace.Constants;
import com.researchspace.archive.ArchivalDocumentParserRef;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchivalNfsFile;
import com.researchspace.archive.ArchiveImportScope;
import com.researchspace.archive.ArchiveManifest;
import com.researchspace.archive.ArchiveResult;
import com.researchspace.archive.ExportScope;
import com.researchspace.archive.IArchiveModel;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.core.testutil.FileTestUtils;
import com.researchspace.core.util.FieldParserConstants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.core.util.ZipUtils;
import com.researchspace.dao.EcatImageDao;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.linkedelements.FieldElementLinkPairs;
import com.researchspace.model.ArchivalCheckSum;
import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.EcatVideo;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.data.ArchiveExportNotificationData;
import com.researchspace.model.comms.data.NotificationData;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.field.Field;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.DocumentFieldAttachmentInitializationPolicy;
import com.researchspace.model.record.DocumentFieldInitializationPolicy;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IconEntity;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFolderDetails;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.properties.IMutablePropertyHolder;
import com.researchspace.service.archive.ArchiveExportServiceManager;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.IArchiveParser;
import com.researchspace.service.archive.ImportArchiveReport;
import com.researchspace.service.archive.ImportStrategy;
import com.researchspace.service.archive.PostArchiveCompletion;
import com.researchspace.service.archive.export.ArchiveRemover;
import com.researchspace.service.archive.export.ExportRemovalPolicy;
import com.researchspace.service.aws.S3Utilities;
import com.researchspace.testutils.ArchiveTestUtils;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import com.researchspace.testutils.SearchTestUtils;
import com.researchspace.testutils.TestGroup;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.lucene.queryparser.classic.ParseException;
import org.hibernate.HibernateException;
import org.hibernate.criterion.Projections;
import org.hibernate.query.NativeQuery;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.MethodSorters;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.http.SdkHttpResponse;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ExportImportManagerTestIT extends RealTransactionSpringTestBase {

  public @Rule TemporaryFolder tempExportFolder = new TemporaryFolder();
  public @Rule TemporaryFolder tempExportFolder2 = new TemporaryFolder();
  public @Rule TemporaryFolder tempImportFolder = new TemporaryFolder();
  public @Rule TemporaryFolder tempImportFolder2 = new TemporaryFolder();
  public @Rule TemporaryFolder spareFolder = new TemporaryFolder();

  private @Autowired ExportImport exportImportMgr;
  private @Autowired Collection<ArchiveExportServiceManager> archiverServiceManagers;
  private @Autowired EcatCommentManager commMgr;
  private @Autowired IArchiveParser archiveParser;
  private @Autowired IconImageManager iconMgr;
  private @Autowired IMutablePropertyHolder properties;
  private @Autowired EcatImageDao imagDao;
  private @Autowired ArchiveRemover archiveRemover;
  private @Autowired JdbcTemplate jdbcTemplate;
  private @Autowired DiskSpaceChecker diskSpaceChecker;
  private @Autowired @Qualifier("importUsersAndRecords") ImportStrategy importStrategy;

  @Mock private S3Utilities s3Utilities;

  @Autowired
  @Qualifier("standardPostExportCompletionImpl")
  @InjectMocks
  private PostArchiveCompletion standardPostExport;

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  private Folder getRootRecordForUser(User user) {
    openTransaction();
    Folder root = folderDao.getRootRecordForUser(user);
    commitTransaction();
    return root;
  }

  @Test
  public void testExportGroup() throws Exception {
    final User pi =
        createAndSaveUser(getRandomAlphabeticString("pi"), com.researchspace.Constants.PI_ROLE);
    final User u1 = createAndSaveUser(getRandomAlphabeticString("u1"));
    User u2 = createAndSaveUser(getRandomAlphabeticString("u2"));
    User importer = createAndSaveUser(getRandomAlphabeticString("importer"));
    initUsers(true, pi, u1, u2, importer);
    User sysadmin = logoutAndLoginAsSysAdmin();

    final Group grp = createGroupForUsers(sysadmin, pi.getUsername(), "", pi, u1, u2);
    final ArchiveExportConfig cfg = createDefaultArchiveConfig(piUser, tempExportFolder.getRoot());
    cfg.setArchiveType(ArchiveExportConfig.XML);
    cfg.setExportScope(ExportScope.GROUP);
    cfg.setUserOrGroupId(grp.getOid());

    ArchiveResult result =
        exportImportMgr
            .exportAsyncGroup(cfg, sysadmin, grp.getId(), anyURI(), standardPostExport)
            .get();
    File zipFile = result.getExportFile();
    logoutAndLoginAs(importer);
    ArchivalImportConfig importCfg = new ArchivalImportConfig();
    ImportArchiveReport report =
        exportImportMgr.importArchive(
            fileToMultipartfile(zipFile.getName(), zipFile),
            importer.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);
    assertTrue(report.isSuccessful());
  }

  private URI anyURI() throws URISyntaxException {
    return new URI("http://a.url.com");
  }

  // Ideally, should be AuthorizationException, but failing async method throws ExecutionException
  @Test(expected = ExecutionException.class)
  public void RSPAC1129_LabAdminWithoutViewAllCannotExportGroupsWork() throws Exception {
    User userPI = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User user = createAndSaveUser(getRandomAlphabeticString("user"));
    User labAdmin = createAndSaveUser(getRandomAlphabeticString("labadmin"));
    initUsers(true, userPI, user, labAdmin);

    User sysadmin = logoutAndLoginAsSysAdmin();
    if (!sysadmin.isContentInitialized()) {
      initUser(sysadmin);
    }
    Group group =
        createGroupForUsers(
            sysadmin, userPI.getUsername(), labAdmin.getUsername(), userPI, user, labAdmin);

    logoutAndLoginAs(labAdmin);

    final ArchiveExportConfig cfg = createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    cfg.setArchiveType(ArchiveExportConfig.XML);
    cfg.setExportScope(ExportScope.GROUP);
    cfg.setUserOrGroupId(group.getOid());

    exportImportMgr
        .exportAsyncGroup(cfg, labAdmin, group.getId(), anyURI(), standardPostExport)
        .get();
  }

  @Test
  public void RSPAC1129_LabAdminGroupsWorkExportShouldNotExportPI() throws Exception {
    User userPI = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User user = createAndSaveUser(getRandomAlphabeticString("user"));
    User labAdmin = createAndSaveUser(getRandomAlphabeticString("labadmin"));
    initUsers(true, userPI, user, labAdmin);

    User sysadmin = logoutAndLoginAsSysAdmin();
    if (!sysadmin.isContentInitialized()) {
      initUser(sysadmin);
    }
    Group group =
        createGroupForUsers(
            sysadmin, userPI.getUsername(), labAdmin.getUsername(), userPI, user, labAdmin);

    logoutAndLoginAs(userPI);

    StructuredDocument privatePIDocument =
        createBasicDocumentInRootFolderWithText(userPI, "test contents");
    grpMgr.authorizeLabAdminToViewAll(labAdmin.getId(), userPI, group.getId(), true);

    logoutAndLoginAs(user);
    StructuredDocument publicUserDocument =
        createBasicDocumentInRootFolderWithText(user, "test contents");

    logoutAndLoginAs(labAdmin);
    StructuredDocument publicLabAdminDocument =
        createBasicDocumentInRootFolderWithText(labAdmin, "test contents");

    final ArchiveExportConfig cfg = createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    cfg.setArchiveType(ArchiveExportConfig.XML);
    cfg.setExportScope(ExportScope.GROUP);
    cfg.setUserOrGroupId(group.getOid());

    ArchiveResult archive =
        exportImportMgr
            .exportAsyncGroup(cfg, labAdmin, group.getId(), anyURI(), standardPostExport)
            .get();
    assertThat(archive.getArchivedRecords(), hasItem(publicUserDocument));
    assertThat(archive.getArchivedRecords(), hasItem(publicLabAdminDocument));
    assertThat(archive.getArchivedRecords(), not(hasItem(privatePIDocument)));
  }

  private ISearchResults<BaseRecord> searchByNameAndOwner(String name, User owner)
      throws IOException, ParseException {
    return searchMgr.searchWorkspaceRecords(
        SearchTestUtils.createByNameAndOwner(name, owner), owner);
  }

  @Test
  public void testArchiveExportRemoval() throws Exception {
    ArchiveExportServiceManager mgr = archiverServiceManagers.iterator().next();
    int initialExportCount = mgr.getCurrentArchiveMetadatas().size();
    User exporter = createAndSaveUser(getRandomAlphabeticString("exporter"));
    // set up an export already made
    File surrogateExport = RSpaceTestUtils.getResource("archives/v22.zip");
    FileUtils.copyFileToDirectory(surrogateExport, tempExportFolder.getRoot(), false);
    // sanity check that copy worked OK
    assertTrue(new File(tempExportFolder.getRoot(), surrogateExport.getName()).exists());
    ArchivalCheckSum acs = TestFactory.createAnArchivalChecksum();
    acs.setExporter(exporter);
    acs.setZipName(surrogateExport.getName());
    acs.setZipSize(surrogateExport.length());
    acs.setUid(getRandomName(10));

    mgr.save(acs);
    assertEquals(initialExportCount + 1, mgr.getCurrentArchiveMetadatas().size());

    // assert should be removed.
    // just ensure it is set to be deleted.
    archiveRemover.setRemovalPolicy(ExportRemovalPolicy.TRUE);
    properties.setExportFolderLocation(tempExportFolder.getRoot().getAbsolutePath());

    exportImportMgr.removeOldArchives();
    // file should be removed, and DB query should be OK
    assertEquals(initialExportCount, mgr.getCurrentArchiveMetadatas().size());
    assertFalse(new File(tempExportFolder.getRoot(), surrogateExport.getName()).exists());
  }

  @Test
  public void testHTMLExportConfig() throws FileNotFoundException, Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "any");
    ArchiveExportConfig cfg = createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    cfg.setArchiveType(ArchiveExportConfig.HTML);
    Future<ArchiveResult> result =
        exportImportMgr.exportRecordSelection(
            getSingleRecordExportSelection(doc.getId(), "NORMAL"),
            cfg,
            user,
            new URI("http://www.google.com"),
            standardPostExport);
  }

  @Test
  public void exportAndReimportGallerySelection() throws FileNotFoundException, Exception {
    User user = createInitAndLoginAnyUser();
    EcatImage imagae = addImageToGallery(user);
    EcatDocumentFile doc = addDocumentToGallery(user);
    Folder imageTopFlder =
        recordMgr.getGallerySubFolderForUser(MediaUtils.IMAGES_MEDIA_FLDER_NAME, user);
    Folder docTopFlder =
        recordMgr.getGallerySubFolderForUser(MediaUtils.DOCUMENT_MEDIA_FLDER_NAME, user);
    assertEquals(
        1, recordMgr.listFolderRecords(docTopFlder.getId(), brPg()).getTotalHits().intValue());
    Folder gallrySubFolder = createSubFolder(imageTopFlder, "subfolder", user);
    recordMgr.move(imagae.getId(), gallrySubFolder.getId(), imageTopFlder.getId(), user);
    // we now have an image in a subfolder; and document in top of Documents folder
    // export a folder and the document
    ArchiveExportConfig cfg = createDefaultArchiveConfig(user, tempExportFolder.getRoot());

    ExportSelection exportSelection =
        ExportSelection.createRecordsExportSelection(
            new Long[] {doc.getId(), gallrySubFolder.getId()},
            new String[] {"MEDIA_FILE", "FOLDER"});
    Future<ArchiveResult> result =
        exportImportMgr.exportRecordSelection(
            exportSelection, cfg, user, new URI("http://www.google.com"), standardPostExport);

    File zipFile = result.get().getExportFile();
    ArchivalImportConfig importCfg =
        createDefaultArchiveImportConfig(user, tempImportFolder.getRoot());
    ImportArchiveReport importReport =
        exportImportMgr.importArchive(
            fileToMultipartfile(zipFile.getName(), zipFile),
            user.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);
    // in documents there should be 2 files now
    assertEquals(
        2, recordMgr.listFolderRecords(docTopFlder.getId(), brPg()).getTotalHits().intValue());
    // and 2 folders in image folder
    assertTrue(
        recordMgr.listFolderRecords(imageTopFlder.getId(), brPg()).getResults().stream()
            .allMatch(br -> br.isFolder()));
  }

  private PaginationCriteria<BaseRecord> brPg() {
    return PaginationCriteria.createDefaultForClass(BaseRecord.class);
  }

  @Test
  public void testArchivalDocumentReimportFolderTree() throws Exception {
    User user = createInitAndLoginAnyUser();

    // create structure f1/f2/f3/simpleDoc
    Folder root = getRootRecordForUser(user);
    Folder f1 = folderMgr.createNewFolder(root.getId(), "folder1", user);
    Folder f2 = folderMgr.createNewFolder(f1.getId(), "folder2", user);
    Folder f3 = folderMgr.createNewFolder(f2.getId(), "folder3", user);
    documentTagManager.saveTag(f3.getId(), "folderTag", user);
    StructuredDocument sd = recordMgr.createBasicDocument(f3.getId(), user);
    final String SIMPLED_DOC_NAME = "simpleDoc";
    sd.setName(SIMPLED_DOC_NAME);
    recordMgr.save(sd, user);
    // create complex doc in root folder,
    StructuredDocument complexDoc = createComplexDocument(user);
    recordMgr.renameRecord("ComplexDoc", complexDoc.getId(), user);
    documentTagManager.saveTag(complexDoc.getId(), "documentTag", user);
    // create notebook in root folder
    Notebook notebook = createNotebookWithNEntries(root.getId(), "testNotebook", 1, user);
    documentTagManager.saveTag(notebook.getId(), "notebookTag", user);

    ArchiveExportConfig cfg = createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    // export top-level folder, complex document, notebook
    ExportSelection exportSelection =
        ExportSelection.createRecordsExportSelection(
            new Long[] {f1.getId(), complexDoc.getId(), notebook.getId()},
            new String[] {"FOLDER", "NORMAL", "NOTEBOOK"});
    Future<ArchiveResult> result =
        exportImportMgr.exportRecordSelection(
            exportSelection, cfg, user, new URI("http://www.google.com"), standardPostExport);
    File zipFile = result.get().getExportFile();

    // reload user and import
    user = userMgr.getUserByUsername(user.getUsername());
    ArchivalImportConfig iconfig =
        createDefaultArchiveImportConfig(user, tempImportFolder.getRoot());
    final int B4ImportCount = totalAllChildrenInFolder(root.getId()).intValue();
    ArchivalImportConfig importCfg = new ArchivalImportConfig();
    ImportArchiveReport report =
        exportImportMgr.importArchive(
            fileToMultipartfile(zipFile.getName(), zipFile),
            user.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);
    WorkspaceListingConfig config1 = createSearchConfigByName("folder1", user, root.getId());
    ISearchResults<BaseRecord> res = searchMgr.searchWorkspaceRecords(config1, user);
    // there should be 4 new records - new 'folder1' and complex doc  + linked record + notebook
    assertEquals(B4ImportCount + 4, totalAllChildrenInFolder(root.getId()).intValue());
    // now delete original f1 folder, so we can search by name for the new ones
    recordDeletionMgr.deleteFolder(root.getId(), f1.getId(), user);
    WorkspaceListingConfig config = createSearchConfigByName("folder1", user, root.getId());
    res = searchMgr.searchWorkspaceRecords(config, user);
    assertEquals(1, res.getTotalHits().intValue());
    // now check each subfolder to check that the structure is the same as was created in the
    // original
    res = recordMgr.listFolderRecords(res.getFirstResult().getId(), DEFAULT_RECORD_PAGINATION);
    assertEquals(1, res.getTotalHits().intValue());
    assertEquals("folder2", res.getFirstResult().getName());
    assertNotEquals(
        "ids are same but should be different", f2.getId(), res.getFirstResult().getId());

    res = recordMgr.listFolderRecords(res.getFirstResult().getId(), DEFAULT_RECORD_PAGINATION);
    assertEquals(1, res.getTotalHits().intValue());
    assertEquals("folder3", res.getFirstResult().getName());
    assertEquals("folderTag", ((Folder) res.getFirstResult()).getDocTag());
    assertNotEquals(
        "ids are same but should be different", f3.getId(), res.getFirstResult().getId());

    res = recordMgr.listFolderRecords(res.getFirstResult().getId(), DEFAULT_RECORD_PAGINATION);
    assertEquals(1, res.getTotalHits().intValue());
    assertEquals(SIMPLED_DOC_NAME, res.getFirstResult().getName());
    assertNotEquals(
        "ids are same but should be different", f3.getId(), res.getFirstResult().getId());

    // now assert that imported doc has form with same field count
    StructuredDocument importedComplex = getImportedComplexDoc(complexDoc, user, root);
    assertEquals("documentTag", importedComplex.getDocTag());
    RSForm importedForm = importedComplex.getForm();
    RSForm originalForm = sd.getForm();
    assertEquals(
        "Imported form has different field count!",
        importedForm.getFieldForms().size(),
        originalForm.getFieldForms().size());

    Field original = complexDoc.getFields().get(0);
    Field importedData = importedComplex.getFields().get(0);
    doInTransaction(
        () -> {
          FieldContents origcontents =
              fieldParser.findFieldElementsInContent(original.getFieldData());
          FieldContents importedContents =
              fieldParser.findFieldElementsInContent(importedData.getFieldData());
          assertEquals(
              "Original and imported have different linked field count",
              origcontents.getAllLinks().size(),
              importedContents.getAllLinks().size());
        });

    // find created notebook
    Set<Notebook> importedNotebooks = report.getImportedNotebooks();
    assertEquals(1, importedNotebooks.size());
    Notebook importedNotebook = importedNotebooks.stream().findFirst().get();
    assertEquals("testNotebook", importedNotebook.getName());
    assertEquals("notebookTag", importedNotebook.getDocTag());
  }

  private StructuredDocument getImportedComplexDoc(
      StructuredDocument original, User user, Folder root) throws IOException {
    WorkspaceListingConfig config = createSearchConfigByName("ComplexDoc", user, root.getId());
    ISearchResults<BaseRecord> res = searchMgr.searchWorkspaceRecords(config, user);
    assertEquals(2, res.getTotalHits().intValue());
    for (BaseRecord br : res.getResults()) {
      if (br.getId() != original.getId()) {
        return recordMgr.getRecordWithFields(br.getId(), user).asStrucDoc();
      }
    }
    return null;
  }

  @Test
  public void testExportUserHTML() throws Exception {
    final User userToExport = createAndSaveUser(getRandomAlphabeticString("user"));
    initUsers(true, userToExport);
    final ArchiveExportConfig cfg = createDefaultArchiveConfig(piUser, tempExportFolder.getRoot());
    cfg.setArchiveType(ArchiveExportConfig.HTML);
    cfg.setDescription("a description");
    logoutAndLoginAs(userToExport);

    int notficationCount = getNewNotificationCount(userToExport);
    ArchiveResult result =
        exportImportMgr
            .exportArchiveAsyncUserWork(
                cfg, userToExport, anyURI(), userToExport, standardPostExport)
            .get();
    File zipFolder = extractZipArchive(result);
    Collection<File> archiveContents =
        FileUtils.listFiles(zipFolder, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
    ArchiveManifest am = ArchiveTestUtils.getManifest(zipFolder);
    assertTrue(am.stringify().contains("a description"));
    assertNoUnsubstitutedVelocityVariables(zipFolder);
    assertTrue(hasFileWithNAmePrefix(userToExport.getUsername(), zipFolder));
    assertEquals(notficationCount + 1, getNewNotificationCount(userToExport));
  }

  private File extractZipArchive(ArchiveResult result) throws IOException {
    return extractZipArchive(result, tempImportFolder);
  }

  private File extractZipArchive(ArchiveResult result, TemporaryFolder tmpFolder)
      throws IOException {
    File zipFolder = tmpFolder.getRoot();
    ZipUtils.extractZip(result.getExportFile(), zipFolder);
    return zipFolder;
  }

  @Test
  public void testExportImportUserXML() throws Exception {
    final User userToExport = createAndSaveUser(getRandomAlphabeticString("user"));
    final User msgRecipient = createAndSaveUser(getRandomAlphabeticString("other"));
    initUsers(true, userToExport);

    final ArchiveExportConfig cfg = createDefaultArchiveConfig(piUser, tempExportFolder.getRoot());
    cfg.setArchiveType(ArchiveExportConfig.XML);
    cfg.setExportScope(ExportScope.USER);
    cfg.setUserOrGroupId(userToExport.getOid());
    logoutAndLoginAs(userToExport);
    String randomTxt = getRandomAlphabeticString("search");
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(userToExport, randomTxt);
    doc.setName(getRandomName(BaseRecord.DEFAULT_VARCHAR_LENGTH));
    recordMgr.save(doc, userToExport);
    sendSimpleMessage(userToExport, "a message", msgRecipient);
    ArchiveResult result =
        exportImportMgr
            .exportArchiveAsyncUserWork(
                cfg, userToExport, anyURI(), userToExport, standardPostExport)
            .get();
    File zipFile = result.getExportFile();
    ZipUtils.extractZip(zipFile, spareFolder.getRoot());

    assertTrue(ArchiveTestUtils.archiveContainsFile(spareFolder.getRoot(), ExportImport.MESSAGES));
    assertTrue(
        ArchiveTestUtils.archiveFileHasContent(spareFolder.getRoot(), ExportImport.MESSAGES));

    ArchivalImportConfig importCfg = new ArchivalImportConfig();

    ImportArchiveReport report =
        exportImportMgr.importArchive(
            fileToMultipartfile(zipFile.getName(), zipFile),
            userToExport.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);
    assertTrue(report.isSuccessful());
    ISearchResults<BaseRecord> results =
        searchMgr.searchWorkspaceRecords(
            SearchTestUtils.createSimpleFullTextSearchCfg(randomTxt), userToExport);
    assertEquals(2, results.getTotalHits().intValue());
  }

  @Test
  public void testExportImportUserXMLAndImportAsNewUser() throws Exception {
    int b4ITest = getFieldAttachmentCount();
    final User userToExport = createAndSaveUser(getRandomAlphabeticString("user"));
    initUsers(true, userToExport);
    final ArchiveExportConfig cfg = createDefaultArchiveConfig(piUser, tempExportFolder.getRoot());
    cfg.setArchiveType(ArchiveExportConfig.XML);
    cfg.setExportScope(ExportScope.USER);
    cfg.setUserOrGroupId(userToExport.getOid());
    logoutAndLoginAs(userToExport);
    int b4ImportCount = getFieldAttachmentCount() - b4ITest;
    ArchiveResult result =
        exportImportMgr
            .exportArchiveAsyncUserWork(
                cfg, userToExport, anyURI(), userToExport, standardPostExport)
            .get();
    File zipFile = result.getExportFile();
    ArchivalImportConfig importCfg = new ArchivalImportConfig();
    importCfg.setScope(ArchiveImportScope.CREATE_USERS_AND_GROUPS);
    ImportArchiveReport report =
        exportImportMgr.importArchive(
            fileToMultipartfile(zipFile.getName(), zipFile),
            userToExport.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);
    assertTrue(report.isSuccessful());
    assertTrue(
        report
            .getInfoList()
            .getAllErrorMessagesAsStringsSeparatedBy(",")
            .contains(userToExport.getUsername()));
    int addedCount = getFieldAttachmentCount() - b4ImportCount;

    // RSPAC-1081
    assertTrue(
        "after import there should be twice as many fieldAttachments - but was "
            + b4ImportCount
            + ",  after import was "
            + addedCount,
        addedCount >= 2 * b4ImportCount && addedCount != 0);
  }

  private Integer getFieldAttachmentCount() {
    openTransaction();
    Long count =
        (Long)
            sessionFactory
                .getCurrentSession()
                .createQuery("select count(*) from FieldAttachment")
                .uniqueResult();
    commitTransaction();
    return count.intValue();
  }

  @Test
  public void checkOldAndNewChemicalImport() throws Exception {
    // want to check that document with external links is imported OK
    final User userToImport = createAndSaveUser(getRandomAlphabeticString("user"));
    initUser(userToImport);
    logoutAndLoginAs(userToImport);

    File toImport = RSpaceTestUtils.getResource("archives/RSPAC-871-1-33test.zip");
    ArchivalImportConfig importCfg = new ArchivalImportConfig();
    importCfg.setScope(ArchiveImportScope.IGNORE_USERS_AND_GROUPS);
    final int initialStructureCount = countRowsInTable(jdbcTemplate, "RSChemElement");
    ImportArchiveReport report =
        exportImportMgr.importArchive(
            fileToMultipartfile(toImport.getName(), toImport),
            userToImport.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);
    assertTrue(report.isSuccessful());
    Set<BaseRecord> imported = report.getImportedRecords();
    assertEquals(1, imported.size());
    assertChemElementCreated(initialStructureCount);
    RSChemElement importedMolChem =
        assertImportedChemElementCanBeFieldParsed(imported, ChemElementsFormat.MOL);
    assertNull(importedMolChem.getImageFileProperty());

    // check if chemical structure exported in RSpace 1.61 re-imports fine
    File toImport_v61 = RSpaceTestUtils.getResource("archives/RSPAC-1917-1-61chemtest.zip");
    final int currentStructureCount = countRowsInTable(jdbcTemplate, "RSChemElement");
    ImportArchiveReport report_v61 =
        exportImportMgr.importArchive(
            fileToMultipartfile(toImport_v61.getName(), toImport_v61),
            userToImport.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);
    assertTrue(report_v61.isSuccessful());
    Set<BaseRecord> imported_v61 = report_v61.getImportedRecords();
    assertEquals(1, imported_v61.size());
    assertChemElementCreated(currentStructureCount);
    RSChemElement importedMrvChem =
        assertImportedChemElementCanBeFieldParsed(imported_v61, ChemElementsFormat.MRV);
    assertNotNull(importedMrvChem.getImageFileProperty());
  }

  private RSChemElement assertImportedChemElementCanBeFieldParsed(
      Set<BaseRecord> imported, ChemElementsFormat expectedFormat) throws Exception {
    FieldContents fieldContents =
        doInTransaction(
            () -> fieldParser.findFieldElementsInContent(getFirstFieldTextFromFirstDoc(imported)));
    FieldElementLinkPairs<RSChemElement> fieldChems =
        fieldContents.getElements(RSChemElement.class);
    assertEquals(1, fieldChems.size());
    RSChemElement firstFieldChem = fieldChems.getElements().get(0);
    assertEquals(expectedFormat, firstFieldChem.getChemElementsFormat());
    return firstFieldChem;
  }

  private void assertChemElementCreated(final int initialStructureCount) {
    final int afterImportStructureCount = countRowsInTable(jdbcTemplate, "RSChemElement");
    assertEquals(initialStructureCount + 1, afterImportStructureCount);
  }

  private String getFirstFieldTextFromFirstDoc(Set<BaseRecord> imported) {
    return imported.iterator().next().asStrucDoc().getFields().get(0).getFieldData();
  }

  @Test
  public void RSPAC_924ExportTemplates() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(anyUser, "any");
    StructuredDocument template =
        createTemplateFromDocumentAndAddtoTemplateFolder(doc.getId(), anyUser);
    ArchiveExportConfig cfg = createDefaultArchiveConfig(piUser, tempExportFolder.getRoot());
    cfg.setArchiveType(ArchiveExportConfig.HTML);

    ArchiveResult result =
        exportImportMgr
            .exportRecordSelection(
                getSingleRecordExportSelection(template.getId(), "NORMAL:TEMPLATE"),
                cfg,
                anyUser,
                new URI("http:/x.y.z.com"),
                standardPostExport)
            .get();
    File zipFolder = extractZipArchive(result);
    // 3 index files + 1 exported
    assertEquals(4, ArchiveTestUtils.getAllHTMLFilesInArchive(zipFolder).size());
  }

  @Test
  public void RobRSpaceTrainingandCertification() throws Exception {
    User u2 = createInitAndLoginAnyUser();
    File archiveToImport = RSpaceTestUtils.getResource("Rob-RSpaceTrainingandCertification.zip");
    ArchivalImportConfig importCfg = new ArchivalImportConfig();

    ImportArchiveReport report =
        exportImportMgr.importArchive(
            fileToMultipartfile(archiveToImport.getName(), archiveToImport),
            u2.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);
    assertTrue(report.isSuccessful());
  }

  @Test
  public void importArchiveWithComment() throws Exception {
    User u2 = createInitAndLoginAnyUser();
    File archiveToImport = RSpaceTestUtils.getResource("archives/ArchiveWithComment.zip");
    ArchivalImportConfig importCfg = new ArchivalImportConfig();

    ImportArchiveReport report =
        exportImportMgr.importArchive(
            fileToMultipartfile(archiveToImport.getName(), archiveToImport),
            u2.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);
    assertTrue(report.isSuccessful());
    final int EXPECTED_COMMENT_ITEMS = 1;
    int commentCount = 0;
    // count comment items
    for (BaseRecord br : report.getImportedRecords()) {
      for (Field f : br.asStrucDoc().getFields()) {
        commentCount +=
            commMgr.getCommentAll(f.getId()).stream()
                .map(a -> a.getItems().size())
                .reduce(0, (a, b) -> a + b);
      }
    }
    assertEquals(EXPECTED_COMMENT_ITEMS, commentCount);
  }

  @Test
  public void RSPAC_924ImportPre1_36ExportWithGalleryTemplates() throws Exception {
    User u2 = createInitAndLoginAnyUser();
    // 2 folders in Gallery; f1 and f2 wit
    File archiveToImport = RSpaceTestUtils.getResource("archives/v1-35.zip");
    ArchivalImportConfig importCfg = new ArchivalImportConfig();
    ImportArchiveReport report =
        exportImportMgr.importArchive(
            fileToMultipartfile(archiveToImport.getName(), archiveToImport),
            u2.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);
    assertTrue(report.isSuccessful());

    Folder templateFolder = folderMgr.getTemplateFolderForUser(u2);
    assertEquals(u2.getUsername(), templateFolder.getParent().getName());
    ISearchResults<BaseRecord> results =
        recordMgr.listFolderRecords(templateFolder.getId(), brPg());
    assertEquals(2, results.getTotalHits().intValue());
    for (BaseRecord br : results.getResults()) {
      assertTrue(br.hasType(RecordType.TEMPLATE));
    }
  }

  @Test
  @Ignore
  public void RSPAC_1807() throws Exception {
    User u2 = createInitAndLoginAnyUser();
    // 2 folders in Gallery; f1 and f2 wit
    File archiveToImport = RSpaceTestUtils.getResource("archives/demo-export2.zip");
    ArchivalImportConfig importCfg = new ArchivalImportConfig();
    final int initialFieldCount = countRowsInTable(jdbcTemplate, "Field");
    ImportArchiveReport report =
        exportImportMgr.importArchive(
            fileToMultipartfile(archiveToImport.getName(), archiveToImport),
            u2.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);
    assertTrue(report.isSuccessful());
    Set<BaseRecord> records = report.getImportedRecords();
    final int finalFieldCount = countRowsInTable(jdbcTemplate, "Field");
    assertEquals(
        " was " + finalFieldCount + " fields added", initialFieldCount + 37, finalFieldCount);
  }

  @Test
  public void RSPAC_890ImportUnlinkedGalleryItems() throws Exception {
    // this is export from p8085 jausten account from 1.47 snapshot
    User u2 = createInitAndLoginAnyUser();
    // 2 folders in Gallery; f1 and f2 wit
    File archiveToImport = RSpaceTestUtils.getResource("archives/RSPAC-890.zip");
    ArchivalImportConfig importCfg = new ArchivalImportConfig();

    ImportArchiveReport report =
        exportImportMgr.importArchive(
            fileToMultipartfile(archiveToImport.getName(), archiveToImport),
            u2.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);
    assertTrue(report.isSuccessful());

    ISearchResults<BaseRecord> results = searchByNameAndOwner("apostropheError.PNG", u2);
    assertEquals(3, results.getTotalHits().intValue());
    assertTrue(results.getFirstResult().hasAncestorOfType(RecordType.ROOT_MEDIA, false));
    // unlinked file in gallery is imported.
    results = searchByNameAndOwner("mets-handwritten.xml", u2);
    assertEquals(1, results.getTotalHits().intValue());
    // assert gallery items added to correct folders
    assertTrue(results.getFirstResult().hasAncestorOfType(RecordType.ROOT_MEDIA, false));
  }

  @Test
  public void attachmentsInLinkedDocAreIncludedInSelectionExport() throws Exception {
    User u1 = createInitAndLoginAnyUser();
    StructuredDocument mainDoc = createBasicDocumentInRootFolderWithText(u1, "main-doc");
    StructuredDocument linkedTo = createBasicDocumentInRootFolderWithText(u1, "linked-doc");
    addImageToField(linkedTo.getFields().get(0), u1);
    addLinkToOtherRecord(mainDoc.getFields().get(0), linkedTo, false);
    ExportSelection selection = getSingleRecordExportSelection(mainDoc.getId(), "NORMAL");
    ArchiveExportConfig cfg = createDefaultArchiveConfig(u1, tempExportFolder.getRoot());
    ArchiveResult result =
        exportImportMgr
            .exportRecordSelection(selection, cfg, u1, anyURI(), standardPostExport)
            .get();
    File zipFolder = extractZipArchive(result);

    // u2Doc and attachments should be added, but linked doc in u2Doc shouldn't be.
    FileTestUtils.assertFolderHasFile(zipFolder, "u1Complex");
    // there are 2 doc_ subfolders, not 3
    assertEquals(
        2,
        FileUtils.listFilesAndDirs(zipFolder, FalseFileFilter.FALSE, TrueFileFilter.TRUE).stream()
            .filter(f -> f.getName().startsWith("doc"))
            .count());
    // ensure attachment is included in export
    assertTrue(FileTestUtils.hasFileWithNAmePrefix("Picture1", zipFolder));
  }

  @Test
  public void exportIgnoresUnpermittedItems() throws Exception {
    TestGroup tg = createTestGroup(2);
    User u1 = tg.u1();
    User u2 = tg.u2();
    // u1 has a complex document and share it.
    logoutAndLoginAs(u1);
    StructuredDocument u1Doc = createComplexDocumentInFolder(u1, getRootFolderForUser(u1));
    // rename and update
    recordMgr.renameRecord("u1Complex", u1Doc.getId(), u1);
    u1Doc = recordMgr.get(u1Doc.getId()).asStrucDoc();
    shareRecordWithGroup(u1, tg.getGroup(), u1Doc);

    // login as u2, create a doc and link to u1Doc
    logoutAndLoginAs(u2);
    StructuredDocument u2Doc = createBasicDocumentInRootFolderWithText(u2, "u2doc");
    addLinkToOtherRecord(u2Doc.getFields().get(0), u1Doc);

    final ArchiveExportConfig cfg = createDefaultArchiveConfig(u2, tempExportFolder.getRoot());

    cfg.setArchiveType(ArchiveExportConfig.HTML);
    cfg.configureUserExport(u2);
    // make sure to follow all links in test.
    cfg.setMaxLinkLevel(5);
    ArchiveResult result =
        exportImportMgr.exportArchiveAsyncUserWork(cfg, u2, anyURI(), u2, standardPostExport).get();
    File zipFolder = extractZipArchive(result);

    // u2Doc and attachments should be added, but linked doc in u2Doc shouldn't be.
    FileTestUtils.assertFolderHasFile(zipFolder, "u1Complex");
    // there are 2 doc_ subfolders, not 3
    assertEquals(
        2,
        FileUtils.listFilesAndDirs(zipFolder, FalseFileFilter.FALSE, TrueFileFilter.TRUE).stream()
            .filter(f -> f.getName().startsWith("doc"))
            .count());
  }

  @Test
  public void testExportGroupHTMLArchive() throws Exception {
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
    final ArchiveExportConfig cfg = createDefaultArchiveConfig(piUser, tempExportFolder.getRoot());
    cfg.setArchiveType(ArchiveExportConfig.HTML);

    // removed as perm checking is done higher up now

    // now login as sysadmin, who does have permission
    sysadmin = logoutAndLoginAsSysAdmin();

    ArchiveResult result =
        exportImportMgr.exportAsyncGroup(cfg, pi, grp.getId(), anyURI(), standardPostExport).get();
    File zipFolder = extractZipArchive(result);
    Collection<File> archiveContents = ArchiveTestUtils.getAllFilesInArchive(zipFolder);

    final int RECORDS_PER_USER = 8;
    final int EXPECTED_OTHER_FILES = 68;
    assertTrue(archiveContents.size() >= RECORDS_PER_USER * 3 + EXPECTED_OTHER_FILES);
    // check each user has an HTML file.
    for (User member : grp.getMembers()) {
      assertTrue(hasFileWithNAmePrefix(member.getUsername(), zipFolder));
    }
    assertNoUnsubstitutedVelocityVariables(zipFolder);
  }

  private void assertNoUnsubstitutedVelocityVariables(File zipFolder) throws IOException {
    Collection<File> htmlFiles = ArchiveTestUtils.getAllHTMLFilesInArchive(zipFolder);
    assertTrue(htmlFiles.size() > 0);
    for (File htmlFile : htmlFiles) {
      boolean placeholder = FileTestUtils.fileContainsContent(htmlFile, "\\$");
      if (placeholder) {
        log.error(htmlFile + " contains unsubstitued VElocity variables");
      }
      assertFalse(placeholder);
    }
  }

  private WorkspaceListingConfig createSearchConfigByName(String term, User user, Long fId) {
    PaginationCriteria<BaseRecord> pgCrit = brPg();
    WorkspaceListingConfig config =
        new WorkspaceListingConfig(
            pgCrit,
            new String[] {SearchConstants.NAME_SEARCH_OPTION},
            new String[] {term},
            fId,
            false);
    return config;
  }

  @Test
  public void RSPAC1123linkedDocumentsAreNotIncludedInHTMLIfNoPermissionToView() throws Exception {
    User u1 = createInitAndLoginAnyUser();
    RSpaceTestUtils.logout();
    User u2 = createInitAndLoginAnyUser();
    StructuredDocument orig = createBasicDocumentInRootFolderWithText(u2, "original");
    StructuredDocument linked = createBasicDocumentInRootFolderWithText(u2, "linked");
    addLinkToOtherRecord(orig.getFields().get(0), linked);
    orig = recordMgr.getRecordWithFields(orig.getId(), u2).asStrucDoc();

    logoutAndLoginAs(u1);
    StructuredDocument u1DocToExport = createBasicDocumentInRootFolderWithText(u1, "toExport");
    String htmlToAdd = orig.getFields().get(0).getFieldData();
    appendContentToField(u1DocToExport.getFields().get(0), htmlToAdd, u1);

    final ArchiveExportConfig cfg = createDefaultArchiveConfig(u1, tempExportFolder.getRoot());
    cfg.setArchiveType(ArchiveExportConfig.HTML);
    ArchiveResult result =
        exportImportMgr
            .exportRecordSelection(
                getSingleRecordExportSelection(u1DocToExport.getId(), RecordType.NORMAL.toString()),
                cfg,
                u1,
                anyURI(),
                standardPostExport)
            .get();
    File zipFolder = extractZipArchive(result);
    int EXPECTED_FILE_COUNT = 8; // 6 default files + 1 data file + Comment.gif
    Collection<File> archiveContents = ArchiveTestUtils.getAllFilesInArchive(zipFolder);
    assertEquals(EXPECTED_FILE_COUNT, archiveContents.size());
  }

  private ExportSelection getSingleRecordExportSelection(Long id, String type) {
    return ExportSelection.createRecordsExportSelection(new Long[] {id}, new String[] {type});
  }

  @Test
  public void exportRevisionHistoryOfDocWithAttachments() throws Exception {
    User u1 = createInitAndLoginAnyUser();
    StructuredDocument sdoc = createBasicDocumentInRootFolderWithText(u1, "source");
    StructuredDocument linkedTarget = createBasicDocumentInRootFolderWithText(u1, "target");
    // create some revisions
    renameDocumentNTimes(sdoc, 5);
    Field field = sdoc.getFields().get(0);
    addLinkToOtherRecord(field, linkedTarget);
    EcatImage image = addImageToField(field, u1);
    EcatImage updatedImage = updateImageInGallery(image.getId(), u1);

    final ArchiveExportConfig cfg = createDefaultArchiveConfig(u1, tempExportFolder.getRoot());
    cfg.setArchiveType(ArchiveExportConfig.XML);
    cfg.setHasAllVersion(true);

    ArchiveResult result =
        exportImportMgr
            .exportRecordSelection(
                getSingleRecordExportSelection(sdoc.getId(), RecordType.NORMAL.toString()),
                cfg,
                u1,
                anyURI(),
                standardPostExport)
            .get();
    File zipFolder = extractZipArchive(result);
    ArchivalImportConfig importCfg =
        createDefaultArchiveImportConfig(u1, tempImportFolder2.getRoot());
    IArchiveModel model =
        doInTransaction(
            () -> {
              return archiveParser.loadArchive(
                  result.getExportFile(), new ImportArchiveReport(), importCfg);
            });

    assertEquals(2, model.getCurrentVersions().size());
    // rspac-1265 - check that linked document is the correct version
    ArchivalDocumentParserRef ref = model.findCurrentDocArchiveByName(sdoc.getName()).get(0);
    ArchivalGalleryMetadata meta =
        ref.getArchivalDocument().getListFields().get(0).getLinkMeta().get(0);
    assertTrue(
        ArchiveTestUtils.archiveContainsFile(
            tempImportFolder2.getRoot(), meta.getLinkFile() + ".xml"));

    Collection<File> archiveContents = ArchiveTestUtils.getAllFilesInArchive(zipFolder);
    assertEquals(28, archiveContents.size());
    assertTrue(
        ArchiveTestUtils.archiveContainsFile(tempImportFolder2.getRoot(), image.getFileName()));
    assertTrue(
        ArchiveTestUtils.archiveContainsFile(
            tempImportFolder2.getRoot(), updatedImage.getFileName()));
  }

  @Test
  public void testExportImportImagesAndAnnotations() throws Exception {
    User u1 = createInitAndLoginAnyUser();
    StructuredDocument sdoc = createBasicDocumentInRootFolderWithText(u1, "source");
    Field textField = sdoc.getFields().get(0);
    final long initialImageCount = getImageCount();
    final long initialAnnotationCount = getImageAnnotationCount();
    // add mixed images and annotations
    ContentBuilder builder = new ContentBuilder(u1, textField);
    builder.addImage().addMath().addImageAnnotation().addImageAnnotation().addImage();
    assertEquals(initialImageCount + 4, getImageCount().intValue());
    assertEquals(initialAnnotationCount + 2, getImageAnnotationCount().intValue());

    final ArchiveExportConfig cfg = createDefaultArchiveConfig(u1, tempExportFolder.getRoot());
    ArchiveResult result =
        exportImportMgr
            .exportRecordSelection(
                getSingleRecordExportSelection(sdoc.getId(), RecordType.NORMAL.toString()),
                cfg,
                u1,
                anyURI(),
                standardPostExport)
            .get();

    ArchivalImportConfig importCfg =
        createDefaultArchiveImportConfig(u1, tempImportFolder2.getRoot());
    ImportArchiveReport report =
        exportImportMgr.importArchive(
            fileToMultipartfile(result.getExportFile().getName(), result.getExportFile()),
            u1.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);
    assertTrue(report.isSuccessful());
    // database checks
    assertEquals(initialImageCount + 8, getImageCount().intValue());
    assertEquals(initialAnnotationCount + 4, getImageAnnotationCount().intValue());
    // now check field content
    StructuredDocument imported = report.getImportedRecords().iterator().next().asStrucDoc();
    String content = imported.getFields().get(0).getFieldData();

    FieldContents fc =
        doInTransaction(
            () -> {
              return fieldParser.findFieldElementsInContent(content);
            });
    assertEquals(2, fc.getImageAnnotations().size());
    assertEquals(4, fc.getElements(EcatImage.class).size()); // each annotation has its own image.
    // finally check ordering.
    Elements imgElements = getImageTagsInDocument(content);
    // correct ordering of imported
    assertFalse(DATA_TYPE_ANNOTATION.equals(imgElements.get(0).attr("data-type")));
    assertTrue(DATA_TYPE_ANNOTATION.equals(imgElements.get(1).attr("data-type")));
    assertTrue(DATA_TYPE_ANNOTATION.equals(imgElements.get(2).attr("data-type")));
    assertFalse(DATA_TYPE_ANNOTATION.equals(imgElements.get(3).attr("data-type")));
  }

  private Elements getImageTagsInDocument(String content) {
    Document d = Jsoup.parse(content);
    return d.getElementsByTag(FieldParserConstants.TAG_IMG);
  }

  // tests exporting a user's work who has documents with attachments, the attachments are imported
  // OK
  @Test
  public void exportImportUserWithAttachments() throws Exception {
    // create doc with various gallery media attachments
    User user = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "firstDoc");
    Field docField = doc.getFields().get(0);
    EcatImage image = addImageToField(docField, user);
    EcatMediaFile audio = addAudioFileToField(docField, user);
    EcatMediaFile video = addVideoFileToField(docField, user);
    EcatDocumentFile txtFile = addFileAttachmentToField(docField, user);
    final int attachmentCount = 4;
    ArchiveExportConfig cfg = createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    cfg.setExportScope(ExportScope.USER);
    cfg.setUserOrGroupId(user.getOid());
    ArchiveResult result =
        exportImportMgr
            .exportArchiveAsyncUserWork(cfg, user, anyURI(), user, standardPostExport)
            .get();
    // check archive content - should contain updated attachments, not the original ones
    long mediaFileCountb4Import = getCountOfEntityTable("EcatMediaFile");
    ArchivalImportConfig importCfg =
        createDefaultArchiveImportConfig(user, tempImportFolder.getRoot());
    File exportZip = result.getExportFile();
    ImportArchiveReport report =
        exportImportMgr.importArchive(
            fileToMultipartfile(exportZip.getName(), exportZip),
            user.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);
    assertEquals(
        mediaFileCountb4Import + attachmentCount,
        getCountOfEntityTable("EcatMediaFile").intValue());
  }

  @Test
  public void exportAllUserWorkWithSharedAttachments_RSPAC2493() throws Exception {
    TestGroup group = createTestGroup(2);
    User userA = group.u1();
    User userB = group.u2();
    logoutAndLoginAs(userA);
    // as user A, create doc with attachment, and share
    StructuredDocument docA = createBasicDocumentInRootFolderWithText(userA, "createdByUserA");
    EcatDocumentFile fileA =
        addAttachmentDocumentToField(
            RSpaceTestUtils.getAnyAttachment(), docA.getFields().get(0), userA);
    shareRecordWithGroup(userA, group.getGroup(), docA);

    // as userB, create doc with link to docA
    logoutAndLoginAs(userB);
    StructuredDocument docB = createBasicDocumentInRootFolderWithText(userB, "createdByUserB");
    addLinkToOtherRecord(docB.getFields().get(0), docA);

    // now export all userB work to XML
    ArchiveExportConfig cfg = createDefaultArchiveConfig(userB, tempExportFolder.getRoot());

    cfg.setExportScope(ExportScope.USER);
    cfg.setUserOrGroupId(userB.getOid());
    ArchiveResult result =
        exportImportMgr
            .exportArchiveAsyncUserWork(cfg, userB, anyURI(), userB, standardPostExport)
            .get();
    File zipFile = result.getExportFile();
    ZipUtils.extractZip(zipFile, spareFolder.getRoot());

    // the linked attached file should be included as a top-level media file.
    assertEquals(1, ArchiveTestUtils.getTopLevelMediaFileCount(spareFolder.getRoot()));
  }

  /*
   * RSPAC-178: testing export of signed/unsigned documents that are referencing
   * gallery attachments in their content.
   *
   * For signed document exported archive should contain attachments from the time
   * of signing, for unsigned - the latest version.
   */
  @Test
  public void testExportImportDocsWithUpdatedMediaFiles() throws Exception {

    // create doc with various gallery media attachments
    User user = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "firstDoc");
    Field docField = doc.getFields().get(0);
    EcatImage image = addImageToField(docField, user);
    EcatMediaFile audio = addAudioFileToField(docField, user);
    EcatMediaFile video = addVideoFileToField(docField, user);
    EcatDocumentFile txtFile = addFileAttachmentToField(docField, user);

    // make a copy of the first doc, sign the copy
    BaseRecord signedDoc = recordMgr.copy(doc.getId(), "signedDoc", user, null).getUniqueCopy();
    signingManager.signRecord(signedDoc.getId(), user, null, "signing doc2");

    // upload new version of image/audio/video/txt
    EcatImage updatedImage = updateImageInGallery(image.getId(), user);
    EcatAudio updatedAudio = updateAudioInGallery(audio.getId(), user);
    EcatVideo updatedVideo = updateVideoInGallery(video.getId(), user);
    EcatDocumentFile updatedTxtFile = updateFileAttachmentInGallery(txtFile.getId(), user);

    /*
     * unsigned doc export
     */
    final ArchiveExportConfig cfg = createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    ArchiveResult firstDocExport =
        exportImportMgr
            .exportRecordSelection(
                getSingleRecordExportSelection(doc.getId(), RecordType.NORMAL.toString()),
                cfg,
                user,
                anyURI(),
                standardPostExport)
            .get();

    // check archive content - should contain updated attachments, not the original ones
    ArchivalImportConfig importCfg =
        createDefaultArchiveImportConfig(user, tempImportFolder.getRoot());
    doInTransaction(
        () -> {
          return archiveParser.loadArchive(
              firstDocExport.getExportFile(), new ImportArchiveReport(), importCfg);
        });
    // archive should contains updated versions of image/audio/video/txt
    assertFalse(
        ArchiveTestUtils.archiveContainsFile(tempImportFolder.getRoot(), image.getFileName()));
    assertTrue(
        ArchiveTestUtils.archiveContainsFile(
            tempImportFolder.getRoot(), updatedImage.getFileName()));
    assertFalse(
        ArchiveTestUtils.archiveContainsFile(tempImportFolder.getRoot(), audio.getFileName()));
    assertTrue(
        ArchiveTestUtils.archiveContainsFile(
            tempImportFolder.getRoot(), updatedAudio.getFileName()));
    assertFalse(
        ArchiveTestUtils.archiveContainsFile(tempImportFolder.getRoot(), video.getFileName()));
    assertTrue(
        ArchiveTestUtils.archiveContainsFile(
            tempImportFolder.getRoot(), updatedVideo.getFileName()));
    assertFalse(
        ArchiveTestUtils.archiveContainsFile(tempImportFolder.getRoot(), txtFile.getFileName()));
    assertTrue(
        ArchiveTestUtils.archiveContainsFile(
            tempImportFolder.getRoot(), updatedTxtFile.getFileName()));

    // unsigned doc import
    ImportArchiveReport report =
        exportImportMgr.importArchive(
            fileToMultipartfile(
                firstDocExport.getExportFile().getName(), firstDocExport.getExportFile()),
            user.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);
    assertTrue(report.isSuccessful());
    assertEquals(1, report.getRecordInfo().size());

    // check attachments of imported unsigned doc
    Long importedDocId = report.getRecordInfo().get(0).getId();
    StructuredDocument importedDoc =
        recordMgr
            .getRecordWithLazyLoadedProperties(
                importedDocId,
                user,
                new DocumentFieldInitializationPolicy(
                    new DocumentFieldAttachmentInitializationPolicy()),
                false)
            .asStrucDoc();
    Field importedField = importedDoc.getFields().get(0);
    assertEquals(4, importedField.getLinkedMediaFiles().size());

    /*
     * signed doc export
     */
    final ArchiveExportConfig cfg2 = createDefaultArchiveConfig(user, tempExportFolder2.getRoot());
    ArchiveResult signedDocExport =
        exportImportMgr
            .exportRecordSelection(
                getSingleRecordExportSelection(signedDoc.getId(), RecordType.NORMAL.toString()),
                cfg2,
                user,
                anyURI(),
                standardPostExport)
            .get();

    // check archive content - should contain updated attachments, not the original ones
    ArchivalImportConfig importCfg2 =
        createDefaultArchiveImportConfig(user, tempImportFolder2.getRoot());
    doInTransaction(
        () -> {
          return archiveParser.loadArchive(
              signedDocExport.getExportFile(), new ImportArchiveReport(), importCfg2);
        });
    // archive should contains initial versions of image/audio/video/txt
    assertTrue(
        ArchiveTestUtils.archiveContainsFile(tempImportFolder2.getRoot(), image.getFileName()));
    assertFalse(
        ArchiveTestUtils.archiveContainsFile(
            tempImportFolder2.getRoot(), updatedImage.getFileName()));
    assertTrue(
        ArchiveTestUtils.archiveContainsFile(tempImportFolder2.getRoot(), audio.getFileName()));
    assertFalse(
        ArchiveTestUtils.archiveContainsFile(
            tempImportFolder2.getRoot(), updatedAudio.getFileName()));
    assertTrue(
        ArchiveTestUtils.archiveContainsFile(tempImportFolder2.getRoot(), video.getFileName()));
    assertFalse(
        ArchiveTestUtils.archiveContainsFile(
            tempImportFolder2.getRoot(), updatedVideo.getFileName()));
    assertTrue(
        ArchiveTestUtils.archiveContainsFile(tempImportFolder2.getRoot(), txtFile.getFileName()));
    assertFalse(
        ArchiveTestUtils.archiveContainsFile(
            tempImportFolder2.getRoot(), updatedTxtFile.getFileName()));

    // signed doc import
    ImportArchiveReport report2 =
        exportImportMgr.importArchive(
            fileToMultipartfile(
                signedDocExport.getExportFile().getName(), signedDocExport.getExportFile()),
            user.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);
    assertTrue(report2.isSuccessful());

    // check attachments of imported signed doc
    Long importedDocId2 = report2.getRecordInfo().get(0).getId();
    StructuredDocument importedDoc2 =
        recordMgr
            .getRecordWithLazyLoadedProperties(
                importedDocId2,
                user,
                new DocumentFieldInitializationPolicy(
                    new DocumentFieldAttachmentInitializationPolicy()),
                false)
            .asStrucDoc();
    Field importedField2 = importedDoc2.getFields().get(0);
    assertEquals(4, importedField2.getLinkedMediaFiles().size());
  }

  @Test
  public void testExportImportNotebookPreservesOrder_RSPAC_1313() throws Exception {
    User u1 = createInitAndLoginAnyUser();
    final int NUM_NOTEBOOK_ENTRIES = 10;
    // has to be 1 seconds delay, as DB stores only to second granularity.
    Notebook createdNotebook =
        createNotebookWithNEntriesAndDelayBetweenEntries(
            getRootFolderForUser(u1).getId(), "nb1", NUM_NOTEBOOK_ENTRIES, u1, 1000);
    final ArchiveExportConfig cfg = createDefaultArchiveConfig(u1, tempExportFolder.getRoot());
    ArchiveResult result =
        exportImportMgr
            .exportRecordSelection(
                getSingleRecordExportSelection(
                    createdNotebook.getId(), RecordType.NOTEBOOK.toString()),
                cfg,
                u1,
                anyURI(),
                standardPostExport)
            .get();

    ArchivalImportConfig importCfg =
        createDefaultArchiveImportConfig(u1, tempImportFolder2.getRoot());
    exportImportMgr.importArchive(
        fileToMultipartfile(result.getExportFile().getName(), result.getExportFile()),
        u1.getUsername(),
        importCfg,
        NULL_MONITOR,
        importStrategy::doImport);
    // now get copied notebook and compare names are in same order:
    doInTransaction(
        () -> {
          Notebook copy = getNotebookNotById(createdNotebook, u1);
          List<BaseRecord> copiedEntries = new ArrayList<>(copy.getChildrens());
          copiedEntries.sort(creationDateSorter());
          Notebook original = getNotebookById(createdNotebook);
          assertFalse(original.getId().equals(copy.getId()));
          List<BaseRecord> originalEntries = new ArrayList<>(original.getChildrens());
          originalEntries.sort(creationDateSorter());
          String copies = join(copiedEntries.stream().map(r -> r.getName()).collect(toList()), ",");
          String originals =
              join(originalEntries.stream().map(r -> r.getName()).collect(toList()), ",");
          log.info("Copies is " + copies);
          log.info("originals is " + originals);
          IntStream.range(0, NUM_NOTEBOOK_ENTRIES)
              .forEach(
                  i ->
                      assertTrue(
                          String.format(
                              "Looking at entry %d. copy is %s but original is %s",
                              i, copiedEntries.get(i).getName(), originalEntries.get(i).getName()),
                          copiedEntries.get(i).getName().equals(originalEntries.get(i).getName())));
        });
  }

  private Notebook getNotebookNotById(Notebook notebook, User user) {
    return (Notebook)
        sessionFactory
            .getCurrentSession()
            .createQuery("from Notebook nb where id != :id and nb.owner=:owner")
            .setParameter("id", notebook.getId())
            .setParameter("owner", user)
            .uniqueResult();
  }

  private Notebook getNotebookById(Notebook notebook) {
    return (Notebook)
        sessionFactory
            .getCurrentSession()
            .createQuery("from Notebook where id = :id")
            .setParameter("id", notebook.getId())
            .uniqueResult();
  }

  private Comparator<BaseRecord> creationDateSorter() {
    return (a, b) -> a.getCreationDateMillis().compareTo(b.getCreationDateMillis());
  }

  @Test
  public void testMultipleLinkedDocsInFieldImportedCorrectlyRSPAC_1321() throws Exception {
    User u1 = createInitAndLoginAnyUser();
    // a links to b and c
    // d links to b and a
    final String A_text = "aaaaaaa";
    StructuredDocument sdocA = createBasicDocumentInRootFolderWithText(u1, A_text);
    final String B_text = "bbbbbbb";
    StructuredDocument sdocB = createBasicDocumentInRootFolderWithText(u1, B_text);
    final String C_text = "ccccccc";
    StructuredDocument sdocC = createBasicDocumentInRootFolderWithText(u1, C_text);
    final String D_text = "ddddddd";
    StructuredDocument sdocD = createBasicDocumentInRootFolderWithText(u1, D_text);

    addLinkToOtherRecord(sdocA.getFields().get(0), sdocB);
    addLinkToOtherRecord(sdocA.getFields().get(0), sdocC);
    addLinkToOtherRecord(sdocD.getFields().get(0), sdocB);
    addLinkToOtherRecord(sdocD.getFields().get(0), sdocA);
    // export A and D
    final ArchiveExportConfig cfg = createDefaultArchiveConfig(u1, tempExportFolder.getRoot());

    ExportSelection exportSelection =
        ExportSelection.createRecordsExportSelection(
            new Long[] {sdocA.getId(), sdocD.getId()},
            new String[] {NORMAL.toString(), NORMAL.toString()});
    ArchiveResult result =
        exportImportMgr
            .exportRecordSelection(exportSelection, cfg, u1, anyURI(), standardPostExport)
            .get();

    ArchivalImportConfig importCfg =
        createDefaultArchiveImportConfig(u1, tempImportFolder2.getRoot());
    ImportArchiveReport importReport =
        exportImportMgr.importArchive(
            fileToMultipartfile(result.getExportFile().getName(), result.getExportFile()),
            u1.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);

    StructuredDocument importeddocA = getImportedByTextContent(A_text, sdocA.getId(), u1);
    StructuredDocument importeddocB = getImportedByTextContent(B_text, sdocB.getId(), u1);
    StructuredDocument importeddocC = getImportedByTextContent(C_text, sdocC.getId(), u1);
    StructuredDocument importeddocD = getImportedByTextContent(D_text, sdocD.getId(), u1);

    FieldContents contents =
        fieldParser.findFieldElementsInContent(importeddocA.getFields().get(0).getFieldData());
    List<Long> a_linkedIds =
        contents.getLinkedRecordsWithRelativeUrl().getElements().stream()
            .map(ri -> ri.getId())
            .collect(Collectors.toList());
    // imported A links to imported B and C
    assertTrue(a_linkedIds.stream().anyMatch(id -> id.equals(importeddocB.getId())));
    assertTrue(a_linkedIds.stream().anyMatch(id -> id.equals(importeddocC.getId())));
    FieldContents dcontents =
        fieldParser.findFieldElementsInContent(importeddocD.getFields().get(0).getFieldData());
    List<Long> d_linkedIds =
        dcontents.getLinkedRecordsWithRelativeUrl().getElements().stream()
            .map(ri -> ri.getId())
            .collect(Collectors.toList());
    // imported D links to imported B and A
    assertTrue(d_linkedIds.stream().anyMatch(id -> id.equals(importeddocB.getId())));
    assertTrue(d_linkedIds.stream().anyMatch(id -> id.equals(importeddocA.getId())));
  }

  // gets preloaded fields
  private StructuredDocument getImportedByTextContent(String text, Long oldId, User owner)
      throws Exception {
    return doInTransaction(
        () -> {
          return (StructuredDocument)
              sessionFactory
                  .getCurrentSession()
                  .createQuery(
                      "from StructuredDocument sd left  join fetch sd.fields fd where fd.rtfData"
                          + " like '%"
                          + text
                          + "%' and "
                          + " sd.id != :oldId and sd.owner=:owner")
                  .setParameter("oldId", oldId)
                  .setParameter("owner", owner)
                  .uniqueResult();
        });
  }

  @Test
  public void testFormIconExportImportRSPAC_1310() throws Exception {
    User u1 = createInitAndLoginAnyUser();
    // create form with icon entity
    RSForm originalForm = createAnyForm(u1);
    BufferedImage anImage = RSpaceTestUtils.getImageFromTestResourcesFolder("mainLogoN2.png");
    IconEntity originalIcon =
        IconEntity.createIconEntityFromImage(originalForm.getId(), anImage, "png");
    originalIcon = iconMgr.saveIconEntity(originalIcon, true);
    originalForm.setIconId(originalIcon.getId());
    formMgr.save(originalForm, u1);
    StructuredDocument sdoc = createDocumentInFolder(getRootFolderForUser(u1), originalForm, u1);
    long iconCount = getIconEntityCount();
    final ArchiveExportConfig cfg = createDefaultArchiveConfig(u1, tempExportFolder.getRoot());
    ArchiveResult result =
        exportImportMgr
            .exportRecordSelection(
                getSingleRecordExportSelection(sdoc.getId(), RecordType.NORMAL.toString()),
                cfg,
                u1,
                anyURI(),
                standardPostExport)
            .get();

    ArchivalImportConfig importCfg =
        createDefaultArchiveImportConfig(u1, tempImportFolder2.getRoot());
    ImportArchiveReport importReport =
        exportImportMgr.importArchive(
            fileToMultipartfile(result.getExportFile().getName(), result.getExportFile()),
            u1.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);
    assertEquals(iconCount + 1, getIconEntityCount().intValue());
    StructuredDocument imported = importReport.getImportedRecords().iterator().next().asStrucDoc();
    RSForm importedForm = imported.getForm();
    assertTrue(importedForm.getIconId() > 0);
    assertTrue(importedForm.getIconId() != originalIcon.getId());
  }

  @Test
  public void linkGenerationUsesAbsoluteURLForHTMlExportIfNotInExportRSPAC_1330() throws Exception {
    User u1 = createInitAndLoginAnyUser();

    final String A_text = "aaaaaaa";
    StructuredDocument sdocA = createBasicDocumentInRootFolderWithText(u1, A_text);
    final String B_text = "bbbbbbb";
    StructuredDocument sdocB = createBasicDocumentInRootFolderWithText(u1, B_text);
    // create bidirectional link a<->b
    addLinkToOtherRecord(sdocA.getFields().get(0), sdocB);
    addLinkToOtherRecord(sdocB.getFields().get(0), sdocA);
    final ArchiveExportConfig cfg = createDefaultArchiveConfig(u1, tempExportFolder.getRoot());
    // B is linked, but depth is 0 and B not selected. So A-B link should be absolute link, as B
    // not included in export.
    // this is action 4 in RSPAC-1330
    cfg.setMaxLinkLevel(0);
    cfg.setArchiveType(ArchiveExportConfig.HTML);
    ArchiveResult result =
        exportImportMgr
            .exportRecordSelection(
                getSingleRecordExportSelection(sdocA.getId(), RecordType.NORMAL.toString()),
                cfg,
                u1,
                anyURI(),
                standardPostExport)
            .get();
    File zipFolder = extractZipArchive(result);
    Collection<File> htmlFiles = ArchiveTestUtils.getAllHTMLFilesInArchive(zipFolder);
    assertEquals(4, htmlFiles.size()); // index + nfs -z + exported, i.e not 4
    assertTrue(
        assertPredicateOnHtmlFile(
            zipFolder,
            sdocA.getName(),
            "a[href^=http]",
            el -> el.absUrl("href").contains(sdocB.getGlobalIdentifier())));

    // now export both files. Even though link depth 0, internal link will be generated, as B is
    // selected.
    // this is action 2 in RSPAC-1330
    cfg.setTopLevelExportFolder(spareFolder.getRoot()); // use new folder for clean results
    ExportSelection exportSelection =
        ExportSelection.createRecordsExportSelection(
            new Long[] {sdocA.getId(), sdocB.getId()},
            new String[] {RecordType.NORMAL.toString(), RecordType.NORMAL.toString()});
    ArchiveResult result2 =
        exportImportMgr
            .exportRecordSelection(exportSelection, cfg, u1, anyURI(), standardPostExport)
            .get();

    File zipFolder2 = extractZipArchive(result2, tempImportFolder2);
    Collection<File> htmlFiles2 = getAllHTMLFilesInArchive(zipFolder2);
    assertEquals(5, htmlFiles2.size()); // A+B exported, internal link made
    assertTrue(
        assertPredicateOnHtmlFile(
            zipFolder2,
            sdocA.getName(),
            "a[href^=../doc]",
            el -> el.attr("href").contains("../doc_" + sdocB.getName())));
  }

  @Test
  public void AAexportGallerySelectionOnlyAndReimportRSPAC_1333_1406_1394_733() throws Exception {
    User u1 = createInitAndLoginAnyUser();
    EcatAudio audio = addAudioFileToGallery(u1);
    EcatImage image = addImageToGallery(u1);
    final String caption = "Caption-123";
    final String newName = "XYZ.doc";
    image.setDescription(caption);
    image.setName(newName);
    doInTransaction(() -> imagDao.save(image));
    EcatImage editedImage =
        editImageInGallery(image.getId(), u1); // RSPAC-733 export includes original image

    Long initImgCount = getImageCount();
    Long initAudioCount = getCountOfEntityTable("EcatAudio");
    final ArchiveExportConfig cfg = createDefaultArchiveConfig(u1, tempExportFolder.getRoot());
    cfg.setArchiveType(ArchiveExportConfig.XML);
    ExportSelection exportSelection =
        ExportSelection.createRecordsExportSelection(
            new Long[] {audio.getId(), editedImage.getId()},
            new String[] {MEDIA_FILE.toString(), MEDIA_FILE.toString()});
    ArchiveResult result =
        exportImportMgr
            .exportRecordSelection(exportSelection, cfg, u1, anyURI(), standardPostExport)
            .get();
    File zipFolder = extractZipArchive(result);
    Collection<File> xmlFiles = ArchiveTestUtils.getAllXMLFilesInArchive(zipFolder);
    assertEquals(5, xmlFiles.size()); // 2 default + 3 for exported media items

    ArchivalImportConfig importCfg =
        createDefaultArchiveImportConfig(u1, tempImportFolder2.getRoot());
    ImportArchiveReport importReport =
        exportImportMgr.importArchive(
            fileToMultipartfile(result.getExportFile().getName(), result.getExportFile()),
            u1.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);
    assertTrue(importReport.isSuccessful());
    assertEquals(initImgCount + 2, getImageCount().intValue());
    assertEquals(initAudioCount + 1, getCountOfEntityTable("EcatAudio").intValue());
    // assert RSPAC-1406 and RSPAC-733
    List<EcatImage> allImages = getImagesForUser(u1);
    assertEquals(2, allImages.stream().filter(img -> caption.equals(img.getDescription())).count());
    assertEquals(2, allImages.stream().filter(img -> (img.getOriginalImage() != null)).count());
    // assert RSPAC-1394 and RSPAC-733
    assertEquals(
        2, allImages.stream().filter(img -> img.getName().equals(image.getName())).count());
    assertEquals(
        2, allImages.stream().filter(img -> img.getName().equals(editedImage.getName())).count());
  }

  @SuppressWarnings("unchecked")
  private List<EcatImage> getImagesForUser(final User user) throws Exception {
    return doInTransaction(
        () ->
            sessionFactory
                .getCurrentSession()
                .createQuery("from EcatImage img where img.owner =:owner")
                .setParameter("owner", user)
                .list());
  }

  @Test
  public void reimportingRSpaceLinksWithAbsoluteURLs() throws Exception {

    User user = createInitAndLoginAnyUser();

    // create 2 docs with links going: firstDoc -> targetDoc
    String initialDocText = "internal links for re-import test:";
    StructuredDocument firstDoc = createBasicDocumentInRootFolderWithText(user, initialDocText);
    StructuredDocument targetDoc = createBasicDocumentInRootFolderWithText(user, "targetDoc");
    Field docField = firstDoc.getFields().get(0);
    addLinkToOtherRecord(docField, targetDoc);

    // create empty folder and a notebook, link them from the firstDoc
    Folder folder = createSubFolder(firstDoc.getParent(), "folderForExport", user);
    Notebook notebook =
        createNotebookWithNEntries(firstDoc.getParent().getId(), "notebookForExport", 1, user);
    addLinkToOtherRecord(docField, folder);
    addLinkToOtherRecord(docField, notebook);

    // make link pointing to another instance, that looks almost like link to firstDoc link
    String externalRSpaceLinkStr = richTextUpdater.generateURLStringForInternalLink(firstDoc);
    String externalServerUrl = "http://anotherInstance.com";
    externalRSpaceLinkStr =
        externalRSpaceLinkStr.replace("/globalId/", externalServerUrl + "/globalId/");

    // now make an absolute URL link to firstDoc
    String absoluteUrlToTargetDoc = richTextUpdater.generateURLStringForInternalLink(firstDoc);
    String currentServerUrl = properties.getServerUrl();
    absoluteUrlToTargetDoc =
        absoluteUrlToTargetDoc.replace("/globalId/", currentServerUrl + "/globalId/");

    docField.setFieldData(docField.getData() + externalRSpaceLinkStr + absoluteUrlToTargetDoc);
    recordMgr.save(firstDoc, user);

    // export firstDoc only. link level 0, so all links will be changed to absolute URLs
    final ArchiveExportConfig cfg = createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    cfg.setMaxLinkLevel(0);
    ArchiveResult result =
        exportImportMgr
            .exportRecordSelection(
                getSingleRecordExportSelection(firstDoc.getId(), RecordType.NORMAL.toString()),
                cfg,
                user,
                anyURI(),
                standardPostExport)
            .get();

    ArchivalImportConfig importCfg =
        createDefaultArchiveImportConfig(user, tempImportFolder2.getRoot());
    exportImportMgr.importArchive(
        fileToMultipartfile(result.getExportFile().getName(), result.getExportFile()),
        user.getUsername(),
        importCfg,
        NULL_MONITOR,
        importStrategy::doImport);

    /* When internal links with absolute URLs are imported to the same RSpace instance,
     * the URLs should be made relative, and internal link relation should be added to database.
     * (RSPAC-1357) */

    StructuredDocument importedDoc =
        getImportedByTextContent(initialDocText, firstDoc.getId(), user);
    assertNotNull(importedDoc);
    String fieldData = importedDoc.getFirstFieldData();
    // document links should be made relative again
    String expectedTargetDocLink = "href=\"/globalId/" + targetDoc.getGlobalIdentifier();
    assertTrue(fieldData, fieldData.contains(expectedTargetDocLink));
    String expectedTargetFolderLink = "href=\"/globalId/" + folder.getGlobalIdentifier();
    assertTrue(fieldData, fieldData.contains(expectedTargetFolderLink));
    String expectedTargetNotebookLink = "href=\"/globalId/" + notebook.getGlobalIdentifier();
    assertTrue(fieldData, fieldData.contains(expectedTargetNotebookLink));

    // the absolute URL in external link should be imported, and the span.internalLinkAbsoluteUrl
    // added
    assertTrue(
        fieldData,
        fieldData.contains(externalServerUrl + "/globalId/" + firstDoc.getGlobalIdentifier()));

    // the absolute link to itself should be made relative
    String expectedFirstDocLink = "href=\"/globalId/" + firstDoc.getGlobalIdentifier();
    assertTrue(fieldData, fieldData.contains(expectedFirstDocLink));
    // there should be no more references to current server url left in field content
    assertFalse(fieldData, fieldData.contains(currentServerUrl));
  }

  @Test
  public void reimportDocumentFolderAndNotebookSelectionWithInternalLinks() throws Exception {

    User user = createInitAndLoginAnyUser();
    Folder rootFolder = user.getRootFolder();

    // create a doc, empty folder and a notebook, link them from the doc
    String initialDocText = "internal links to folder and notebook for re-import test:";
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, initialDocText);
    Folder folder = createSubFolder(doc.getParent(), "folderForExport", user);
    Notebook notebook =
        createNotebookWithNEntries(doc.getParent().getId(), "notebookForExport", 0, user);
    Field docField = doc.getFields().get(0);
    addLinkToOtherRecord(docField, folder);
    addLinkToOtherRecord(docField, notebook);

    String orgFolderLinkFragment = "href=\"/globalId/" + folder.getGlobalIdentifier();
    String orgNotebookLinkFragment = "href=\"/globalId/" + notebook.getGlobalIdentifier();
    String orgFieldData = docField.getData();
    assertTrue(orgFieldData, orgFieldData.contains(orgFolderLinkFragment));
    assertTrue(orgFieldData, orgFieldData.contains(orgNotebookLinkFragment));

    int initialRecords =
        recordMgr.listFolderRecords(rootFolder.getId(), brPg()).getTotalHits().intValue();

    // export firstDoc, folder and notebook
    final ArchiveExportConfig cfg = createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    cfg.setMaxLinkLevel(0);
    ExportSelection exportSelection =
        ExportSelection.createRecordsExportSelection(
            new Long[] {doc.getId(), folder.getId(), notebook.getId()},
            new String[] {
              NORMAL.toString(), RecordType.FOLDER.toString(), RecordType.NOTEBOOK.toString()
            });
    ArchiveResult result =
        exportImportMgr
            .exportRecordSelection(exportSelection, cfg, user, anyURI(), standardPostExport)
            .get();
    assertEquals(1, result.getArchivedRecords().size()); // one record
    assertEquals(2, result.getArchivedFolders().size()); // folder and notebook
    List<Long> originalWorkspaceIds =
        recordMgr.listFolderRecords(rootFolder.getId(), brPg()).getResults().stream()
            .map(BaseRecord::getId)
            .collect(Collectors.toList());
    // import
    ArchivalImportConfig importCfg =
        createDefaultArchiveImportConfig(user, tempImportFolder2.getRoot());
    exportImportMgr.importArchive(
        fileToMultipartfile(result.getExportFile().getName(), result.getExportFile()),
        user.getUsername(),
        importCfg,
        NULL_MONITOR,
        importStrategy::doImport);

    ISearchResults<BaseRecord> rootFolderResults =
        recordMgr.listFolderRecords(rootFolder.getId(), brPg());
    // imported doc, folder and notebook
    assertEquals(initialRecords + 3, rootFolderResults.getTotalHits().intValue());
    List<BaseRecord> newDocs =
        rootFolderResults.getResults().stream()
            .filter(br -> !originalWorkspaceIds.contains(br.getId()))
            .collect(Collectors.toList());

    // retrieve imported doc, folder and notebook. They maintain original creation date as
    // originals,
    // so order may not be always consistent

    StructuredDocument importedDoc =
        newDocs.stream().filter(BaseRecord::isStructuredDocument).findFirst().get().asStrucDoc();
    Notebook importedNotebook =
        (Notebook) newDocs.stream().filter(BaseRecord::isNotebook).findFirst().get();
    Folder importedFolder =
        (Folder) newDocs.stream().filter(BaseRecord::isFolder).findFirst().get();

    String importedFieldData = importedDoc.getFirstFieldData();

    /* internal links should point to newly created folder and notebook, and not to old ids */
    String expectedTargetFolderLink = "href=\"/globalId/" + importedFolder.getGlobalIdentifier();
    assertTrue(importedFieldData, importedFieldData.contains(expectedTargetFolderLink));
    assertFalse(importedFieldData, importedFieldData.contains(orgFolderLinkFragment));

    String expectedTargetNotebookLink =
        "href=\"/globalId/" + importedNotebook.getGlobalIdentifier();
    assertTrue(importedFieldData, importedFieldData.contains(expectedTargetNotebookLink));
    assertFalse(importedFieldData, importedFieldData.contains(orgNotebookLinkFragment));
  }

  @Test
  public void testStoppingTooLargeExportRSPAC_1733() throws Exception {

    // override max archive limit with lower value
    int orgMaxArchiveLimit = diskSpaceChecker.getMaxArchiveSizeMB();
    diskSpaceChecker.setMaxArchiveSizeMB("2");

    // create a document with large file
    User user = createInitAndLoginAnyUser();
    File largeFile2MB = RSpaceTestUtils.getResource("PowerPasteTesting_RSpace.docx"); // 2.1MB
    StructuredDocument sdocA = createBasicDocumentInRootFolderWithText(user, "any text");
    Field field = sdocA.getFields().get(0);
    addAttachmentDocumentToField(largeFile2MB, field, user);

    // another, with a bit smaller file
    File smallerFile1MB = RSpaceTestUtils.getResource("oneMegabyteFile.txt"); // 1MB
    StructuredDocument sdocB = createBasicDocumentInRootFolderWithText(user, "any text");
    Field fieldB = sdocB.getFields().get(0);
    addAttachmentDocumentToField(smallerFile1MB, fieldB, user);

    // try exporting
    final ArchiveExportConfig cfg = createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    cfg.setArchiveType(ArchiveExportConfig.HTML);
    ExportSelection exportSelection =
        ExportSelection.createRecordsExportSelection(
            new Long[] {sdocA.getId(), sdocB.getId()},
            new String[] {NORMAL.toString(), NORMAL.toString()});

    try {
      exportImportMgr
          .exportRecordSelection(exportSelection, cfg, user, anyURI(), standardPostExport)
          .get();
      fail("expected to fail as archive size above the limit");
    } catch (Exception e) {
      assertTrue(
          "unexpected msg: " + e.getMessage(), e.getMessage().contains("DiskSpaceLimitException"));
    }

    // restore max archive limit
    diskSpaceChecker.setMaxArchiveSizeMB("" + orgMaxArchiveLimit);
  }

  private Long getIconEntityCount() throws HibernateException, Exception {
    return doInTransaction(
        () ->
            (Long)
                sessionFactory
                    .getCurrentSession()
                    .createCriteria(IconEntity.class)
                    .setProjection(Projections.rowCount())
                    .uniqueResult());
  }

  @Test
  public void nfsPathInfoExportedWithRevisionHistory() throws Exception {
    User u1 = createInitAndLoginAnyUser();
    StructuredDocument sdocA = createBasicDocumentInRootFolderWithText(u1, "any text");
    NfsElement element = addNfsFileStoreAndLink(sdocA.getFields().get(0), u1, "/anyFile.txt");
    addNfsFileStoreAndLink(sdocA.getFields().get(0), u1, "/anyFile2.txt");
    NfsFileStore store = nfsMgr.getNfsFileStore(element.getFileStoreId());

    // HTML EXPORT
    final ArchiveExportConfig cfg = createDefaultArchiveConfig(u1, tempExportFolder.getRoot());
    cfg.setArchiveType(ArchiveExportConfig.HTML);
    ArchiveResult result =
        exportImportMgr
            .exportRecordSelection(
                getSingleRecordExportSelection(sdocA.getId(), RecordType.NORMAL.toString()),
                cfg,
                u1,
                anyURI(),
                standardPostExport)
            .get();
    File zipFolder = extractZipArchive(result);
    // assert we have full path attribute
    assertTrue(
        ArchiveTestUtils.assertPredicateOnHtmlFile(
            zipFolder,
            sdocA.getName(),
            "a.nfs_file",
            el ->
                el.hasAttr(FULL_PATH_DATA_ATTR_NAME)
                    && el.attr(FULL_PATH_DATA_ATTR_NAME)
                        .equalsIgnoreCase(store.getFullPath(element).toFullPath())));
    // assertThat  NfsMetaData table is added at foot of document - there should be 1 row
    final String nfsTableRowSelector = "tr.nfsElementMeta";
    assertTrue(
        ArchiveTestUtils.assertPredicateOnHtmlFile(
            zipFolder,
            sdocA.getName(),
            nfsTableRowSelector,
            // we just want to assert number of matches > 1, not make any assertions about them
            el -> true));
    // assert we have an HTML summary file
    ArchiveTestUtils.archiveFileHasContent(zipFolder, NFS_LINKS_HTML);
    assertTrue(
        ArchiveTestUtils.assertPredicateOnHtmlFile(
            zipFolder,
            NFS_LINKS_HTML,
            nfsTableRowSelector,
            // we just want to assert number of matches > 1, not make any assertions about them
            el -> true));

    // XML EXPORT with revision history to test RSPAC-1387
    renameDocumentNTimes(sdocA, 3); // create some revisions
    final ArchiveExportConfig cfgXml = createDefaultArchiveConfig(u1, tempExportFolder2.getRoot());

    cfgXml.setHasAllVersion(true);
    ArchiveResult resultXml =
        exportImportMgr
            .exportRecordSelection(
                getSingleRecordExportSelection(sdocA.getId(), RecordType.NORMAL.toString()),
                cfgXml,
                u1,
                anyURI(),
                standardPostExport)
            .get();
    File zipFolderXml = extractZipArchive(resultXml);
    ArchiveTestUtils.archiveFileHasContent(zipFolderXml, NFS_EXPORT_XML);
  }

  @Test
  public void testIncludingNfsFileInExport() throws Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument sdocA = createBasicDocumentInRootFolderWithText(user, "any text");

    // add nfs file link
    NfsElement nfsFileElem = addNfsFileStoreAndLink(sdocA.getFields().get(0), user, "/anyFile.txt");
    NfsFileStore fileStore = nfsMgr.getNfsFileStore(nfsFileElem.getFileStoreId());
    NfsFileSystem fileSystem = fileStore.getFileSystem();

    File testAnyFile = RSpaceTestUtils.getAnyAttachment();
    NfsFileDetails testNfsFileDetails = new NfsFileDetails(testAnyFile.getName());
    testNfsFileDetails.setFileSystemFullPath(fileStore.getAbsolutePath(nfsFileElem.getPath()));
    testNfsFileDetails.setRemoteInputStream(new FileInputStream(testAnyFile));

    // add nfs folder link, to folder containing two more files
    NfsElement nfsFolderElem =
        addNfsFileStoreLink(sdocA.getFields().get(0), user, fileStore.getId(), "/nfsFolder", true);

    File testPdfFile = RSpaceTestUtils.getAnyPdf();
    NfsFileDetails subfolderNfsFile1 = new NfsFileDetails(testPdfFile.getName());
    subfolderNfsFile1.setFileSystemId(fileSystem.getId());
    subfolderNfsFile1.setFileSystemFullPath(
        fileStore.getAbsolutePath("/nfsFolder/" + testPdfFile.getName()));
    subfolderNfsFile1.setRemoteInputStream(new FileInputStream(testPdfFile));

    File testLogsFile = RSpaceTestUtils.getResource("sampleLogs/RSLogs.txt.1");
    NfsFileDetails subfolderNfsFile2 = new NfsFileDetails(testLogsFile.getName());
    subfolderNfsFile2.setFileSystemId(fileSystem.getId());
    subfolderNfsFile2.setFileSystemFullPath(
        fileStore.getAbsolutePath("/nfsFolder/" + testLogsFile.getName()));
    subfolderNfsFile2.setRemoteInputStream(new FileInputStream(testLogsFile));

    NfsFolderDetails testNfsFolderDetails = new NfsFolderDetails("nfsFolder");
    testNfsFolderDetails.setFileSystemFullPath(fileStore.getAbsolutePath(nfsFolderElem.getPath()));
    testNfsFolderDetails.getContent().add(subfolderNfsFile1);
    testNfsFolderDetails.getContent().add(subfolderNfsFile2);

    NfsTarget testNfsFileTarget = new NfsTarget(testNfsFileDetails.getFileSystemFullPath(), null);
    NfsTarget subfolderNfsFile1Target =
        new NfsTarget(subfolderNfsFile1.getFileSystemFullPath(), null);
    NfsTarget subfolderNfsFile2Target =
        new NfsTarget(subfolderNfsFile2.getFileSystemFullPath(), null);
    NfsTarget testFolderTarget = new NfsTarget(testNfsFolderDetails.getFileSystemFullPath(), null);
    // mock nfs client, returning files and folder details when queried
    NfsClient mockNfsClient = Mockito.mock(NfsClient.class);
    when(mockNfsClient.isUserLoggedIn()).thenReturn(true);
    when(mockNfsClient.queryNfsFileForDownload(testNfsFileTarget)).thenReturn(testNfsFileDetails);
    when(mockNfsClient.queryNfsFileForDownload(subfolderNfsFile1Target))
        .thenReturn(subfolderNfsFile1);
    when(mockNfsClient.queryNfsFileForDownload(subfolderNfsFile2Target))
        .thenReturn(subfolderNfsFile2);
    when(mockNfsClient.queryForNfsFolder(testFolderTarget)).thenReturn(testNfsFolderDetails);
    Map<Long, NfsClient> nfsClientMap = Collections.singletonMap(fileSystem.getId(), mockNfsClient);

    // ensure no notifications at first
    assertEquals(0, getNewNotificationCount(user));

    // HTML EXPORT
    final ArchiveExportConfig cfgHtml =
        createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    cfgHtml.setArchiveType(ArchiveExportConfig.HTML);
    cfgHtml.setIncludeNfsLinks(true);
    cfgHtml.setAvailableNfsClients(nfsClientMap);
    ArchiveResult resultHtml =
        exportImportMgr
            .exportRecordSelection(
                getSingleRecordExportSelection(sdocA.getId(), RecordType.NORMAL.toString()),
                cfgHtml,
                user,
                anyURI(),
                standardPostExport)
            .get();

    // check link included in results
    assertEquals(2, resultHtml.getArchivedNfsFiles().size());
    Iterator<ArchivalNfsFile> htmlNfsFilesIterator = resultHtml.getArchivedNfsFiles().iterator();
    ArchivalNfsFile nfsFileInHtml = htmlNfsFilesIterator.next();
    assertTrue(nfsFileInHtml.isAddedToArchive());
    assertNull(nfsFileInHtml.getErrorMsg());
    // verify the folder added
    ArchivalNfsFile nfsFolderInHtml = htmlNfsFilesIterator.next();
    assertTrue(nfsFolderInHtml.isAddedToArchive());
    assertNull(nfsFolderInHtml.getErrorMsg());

    String nfsFileHtmlArchiveName = nfsFileInHtml.getArchivePath();
    assertTrue(nfsFileHtmlArchiveName.endsWith(testAnyFile.getName()));

    // assert nfs file is part of html archive
    File zipFolderHtml = extractZipArchive(resultHtml);
    ArchiveTestUtils.archiveFileHasContent(zipFolderHtml, testNfsFileDetails.getName());
    ArchiveTestUtils.archiveFileHasContent(zipFolderHtml, subfolderNfsFile1.getName());
    ArchiveTestUtils.archiveFileHasContent(zipFolderHtml, subfolderNfsFile2.getName());

    // check notification and its content
    ISearchResults<Notification> newNotifications =
        communicationMgr.getNewNotificationsForUser(
            user.getUsername(),
            PaginationCriteria.createDefaultForClass(CommunicationTarget.class));
    assertEquals(Integer.valueOf(1), newNotifications.getHits());
    Notification htmlNotification = newNotifications.getFirstResult();
    String htmlExportNotificationMsg = htmlNotification.getNotificationMessage();
    assertTrue(htmlExportNotificationMsg, htmlExportNotificationMsg.contains("-html-"));
    assertTrue(htmlExportNotificationMsg, htmlExportNotificationMsg.contains("/export/report/"));
    assertTrue(
        htmlExportNotificationMsg, htmlExportNotificationMsg.contains("2 linked filestore items"));
    NotificationData htmlNotificationData = htmlNotification.getNotificationDataObject();
    assertNotNull(htmlNotificationData);
    String htmlDownloadLink =
        ((ArchiveExportNotificationData) htmlNotificationData).getDownloadLink();
    assertTrue(
        "unexpected download link: " + htmlDownloadLink,
        htmlDownloadLink.startsWith("http") && htmlDownloadLink.contains("-html-"));

    // XML EXPORT
    final ArchiveExportConfig cfgXml =
        createDefaultArchiveConfig(user, tempExportFolder2.getRoot());
    cfgXml.setIncludeNfsLinks(true);
    cfgXml.setAvailableNfsClients(nfsClientMap);
    // reset input stream on test file returned by nfsClient mock
    testNfsFileDetails.setRemoteInputStream(new FileInputStream(testAnyFile));

    ArchiveResult resultXml =
        exportImportMgr
            .exportRecordSelection(
                getSingleRecordExportSelection(sdocA.getId(), RecordType.NORMAL.toString()),
                cfgXml,
                user,
                anyURI(),
                standardPostExport)
            .get();

    // check link included in results
    assertEquals(2, resultXml.getArchivedNfsFiles().size());
    ArchivalNfsFile nfsFileInXml = resultXml.getArchivedNfsFiles().iterator().next();
    assertTrue(nfsFileInXml.isAddedToArchive());
    String nfsFileXmlArchiveName = nfsFileInHtml.getArchivePath();
    assertTrue(nfsFileXmlArchiveName.endsWith(testAnyFile.getName()));

    File zipFolderXml = extractZipArchive(resultXml);
    // assert nfs file is part of xml archive
    ArchiveTestUtils.archiveFileHasContent(zipFolderXml, testNfsFileDetails.getName());
    ArchiveTestUtils.archiveFileHasContent(zipFolderXml, subfolderNfsFile1.getName());
    ArchiveTestUtils.archiveFileHasContent(zipFolderXml, subfolderNfsFile2.getName());

    // check notification for xml export
    newNotifications =
        communicationMgr.getNewNotificationsForUser(
            user.getUsername(),
            PaginationCriteria.createDefaultForClass(CommunicationTarget.class));
    assertEquals(Integer.valueOf(2), newNotifications.getHits());
    Notification xmlNotification = newNotifications.getLastResult();
    String xmlExportNotificationMsg = xmlNotification.getNotificationMessage();
    assertTrue(xmlExportNotificationMsg, xmlExportNotificationMsg.contains("-xml-"));
    assertTrue(xmlExportNotificationMsg, xmlExportNotificationMsg.contains("/export/report/"));
    assertTrue(
        xmlExportNotificationMsg, xmlExportNotificationMsg.contains("2 linked filestore items"));

    NotificationData xmlNotificationData = xmlNotification.getNotificationDataObject();
    assertNotNull(xmlNotificationData);
    String xmlDownloadLink =
        ((ArchiveExportNotificationData) xmlNotificationData).getDownloadLink();
    assertTrue(
        "unexpected download link: " + xmlDownloadLink,
        xmlDownloadLink.startsWith("http") && xmlDownloadLink.contains("-xml-"));
  }

  @Test
  public void htmlExportIncludesExternalLinks_rspac2481()
      throws IOException, URISyntaxException, ExecutionException, InterruptedException {
    String anyImageLink =
        "https://ichef.bbci.co.uk/news/976/cpsprodpb/102EA/production/_124128266_gettyimages-124206022.jpg";
    String linkedImageHtmlF = "<img src=\"%s\">";
    String imgHtml = String.format(linkedImageHtmlF, anyImageLink);
    User user = createInitAndLoginAnyUser();
    StructuredDocument sdocA = createBasicDocumentInRootFolderWithText(user, imgHtml);
    final ArchiveExportConfig cfgHtml =
        createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    cfgHtml.setArchiveType(ArchiveExportConfig.HTML);
    ArchiveResult resultHtml =
        exportImportMgr
            .exportRecordSelection(
                getSingleRecordExportSelection(sdocA.getId(), RecordType.NORMAL.toString()),
                cfgHtml,
                user,
                anyURI(),
                standardPostExport)
            .get();
    File zipFolderXml = extractZipArchive(resultHtml);
    // img href is unchanged
    File exportedDoc =
        getAllHTMLFilesInArchive(zipFolderXml).stream()
            .filter(f -> f.getName().startsWith("doc"))
            .findFirst()
            .get();

    String html = FileUtils.readFileToString(exportedDoc, Charset.defaultCharset());
    assertTrue(html.contains(imgHtml));
  }

  @Test
  public void htmlExportRemovesJoveAccessParams() throws Exception {
    String joveAccessHtml =
        "<div class=\"embedIframeDiv mceNonEditable\"><iframe id=\"embed-iframe\""
            + " src=\"https://www.jove.com/embed/player?id=3923&amp;t=1&amp;s=1&amp;fpv=1&amp;access=9XoFHn1w&amp;utm_source=JoVE_RSpace\""
            + " width=\"460\" height=\"440\" frameborder=\"0\" marginwidth=\"0\" scrolling=\"no\""
            + " allowfullscreen=\"\"></iframe></div>";
    User user = createInitAndLoginAnyUser();
    StructuredDocument sdocA = createBasicDocumentInRootFolderWithText(user, joveAccessHtml);
    final ArchiveExportConfig cfgHtml =
        createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    cfgHtml.setArchiveType(ArchiveExportConfig.HTML);
    ArchiveResult resultHtml =
        exportImportMgr
            .exportRecordSelection(
                getSingleRecordExportSelection(sdocA.getId(), RecordType.NORMAL.toString()),
                cfgHtml,
                user,
                anyURI(),
                standardPostExport)
            .get();
    File zipFolderXml = extractZipArchive(resultHtml);
    // img href is unchanged
    File exportedDoc =
        getAllHTMLFilesInArchive(zipFolderXml).stream()
            .filter(f -> f.getName().startsWith("doc"))
            .findFirst()
            .get();

    String html = FileUtils.readFileToString(exportedDoc, Charset.defaultCharset());
    assertFalse(html.contains("access=9XoFHn1w"));
  }

  @Test
  public void testFilteringNfsFilesInExport() throws Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument sdocA = createBasicDocumentInRootFolderWithText(user, "any text");

    // add three nfs file links, first to txt file
    NfsElement nfsTxtFileElem =
        addNfsFileStoreAndLink(sdocA.getFields().get(0), user, "/anyFile.txt");
    NfsFileStore fileStore = nfsMgr.getNfsFileStore(nfsTxtFileElem.getFileStoreId());
    NfsFileSystem fileSystem = fileStore.getFileSystem();

    File testAnyFile = RSpaceTestUtils.getAnyAttachment();
    NfsFileDetails txtNfsFileDetails = new NfsFileDetails(testAnyFile.getName());
    txtNfsFileDetails.setFileSystemId(fileSystem.getId());
    txtNfsFileDetails.setFileSystemFullPath(fileStore.getAbsolutePath(nfsTxtFileElem.getPath()));
    txtNfsFileDetails.setRemoteInputStream(new FileInputStream(testAnyFile));
    txtNfsFileDetails.setSize(testAnyFile.length());

    // second to .png file, which will be on list of excluded extensions
    NfsElement nfsPngFileElem =
        addNfsFileStoreLink(
            sdocA.getFields().get(0), user, fileStore.getId(), "/anyFile.png", false);
    File testExcludedExtensionFile = RSpaceTestUtils.getResource("Picture1.png");
    NfsFileDetails pngNfsFileDetails = new NfsFileDetails(testExcludedExtensionFile.getName());
    pngNfsFileDetails.setFileSystemId(fileSystem.getId());
    pngNfsFileDetails.setFileSystemFullPath(fileStore.getAbsolutePath(nfsPngFileElem.getPath()));
    pngNfsFileDetails.setRemoteInputStream(new FileInputStream(testExcludedExtensionFile));
    pngNfsFileDetails.setSize(testExcludedExtensionFile.length());

    // third to larger file, above the 1MB limit set in filter
    NfsElement nfsTiffFileElem =
        addNfsFileStoreLink(
            sdocA.getFields().get(0), user, fileStore.getId(), "/anyFile.tif", false);
    File testLargeFile = RSpaceTestUtils.getResource("Picture2.tif");
    NfsFileDetails largeNfsFileDetails = new NfsFileDetails(testLargeFile.getName());
    largeNfsFileDetails.setFileSystemId(fileSystem.getId());
    largeNfsFileDetails.setFileSystemFullPath(fileStore.getAbsolutePath(nfsTiffFileElem.getPath()));
    largeNfsFileDetails.setRemoteInputStream(new FileInputStream(testLargeFile));
    largeNfsFileDetails.setSize(testLargeFile.length());

    NfsTarget txtTarget = new NfsTarget(txtNfsFileDetails.getFileSystemFullPath(), null);
    // mock nfs client, returning files and folder details when queried
    NfsClient mockNfsClient = Mockito.mock(NfsClient.class);
    when(mockNfsClient.isUserLoggedIn()).thenReturn(true);
    when(mockNfsClient.queryForNfsFile(new NfsTarget(txtNfsFileDetails.getFileSystemFullPath())))
        .thenReturn(txtNfsFileDetails);
    when(mockNfsClient.queryNfsFileForDownload(txtTarget)).thenReturn(txtNfsFileDetails);
    when(mockNfsClient.queryForNfsFile(new NfsTarget(pngNfsFileDetails.getFileSystemFullPath())))
        .thenReturn(pngNfsFileDetails);
    when(mockNfsClient.queryForNfsFile(new NfsTarget(largeNfsFileDetails.getFileSystemFullPath())))
        .thenReturn(largeNfsFileDetails);
    Map<Long, NfsClient> nfsClientMap = Collections.singletonMap(fileSystem.getId(), mockNfsClient);

    // ensure no notifications at first
    assertEquals(0, getNewNotificationCount(user));

    // HTML EXPORT
    final ArchiveExportConfig cfgHtml =
        createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    cfgHtml.setArchiveType(ArchiveExportConfig.HTML);
    cfgHtml.setIncludeNfsLinks(true);
    cfgHtml.setMaxNfsFileSize(1_000_000);
    cfgHtml.setExcludedNfsFileExtensions(new HashSet<>(Arrays.asList("png", "log")));
    cfgHtml.setAvailableNfsClients(nfsClientMap);
    ArchiveResult resultHtml =
        exportImportMgr
            .exportRecordSelection(
                getSingleRecordExportSelection(sdocA.getId(), RecordType.NORMAL.toString()),
                cfgHtml,
                user,
                anyURI(),
                standardPostExport)
            .get();

    // check links included in results
    assertEquals(3, resultHtml.getArchivedNfsFiles().size());
    Iterator<ArchivalNfsFile> htmlNfsFilesIterator = resultHtml.getArchivedNfsFiles().iterator();
    // txt file should be added
    ArchivalNfsFile txtFileInHtml = htmlNfsFilesIterator.next();
    assertTrue(txtFileInHtml.isAddedToArchive());
    assertNull(txtFileInHtml.getErrorMsg());
    // png file should be omitted - excluded extension
    ArchivalNfsFile pngFileInHtml = htmlNfsFilesIterator.next();
    assertFalse(pngFileInHtml.isAddedToArchive());
    assertEquals("file skipped (file extension 'png' excluded)", pngFileInHtml.getErrorMsg());
    // tif file should be omitted - too large
    ArchivalNfsFile largeFileInHtml = htmlNfsFilesIterator.next();
    assertFalse(largeFileInHtml.isAddedToArchive());
    assertEquals(
        "file skipped (file larger than provided size limit)", largeFileInHtml.getErrorMsg());

    // assert nfs file is part of html archive
    File zipFolderHtml = extractZipArchive(resultHtml);
    ArchiveTestUtils.archiveFileHasContent(zipFolderHtml, testAnyFile.getName());

    // check notification and its content
    ISearchResults<Notification> newNotifications =
        communicationMgr.getNewNotificationsForUser(
            user.getUsername(),
            PaginationCriteria.createDefaultForClass(CommunicationTarget.class));
    assertEquals(Integer.valueOf(1), newNotifications.getHits());
    Notification htmlNotification = newNotifications.getFirstResult();
    String htmlExportNotificationMsg = htmlNotification.getNotificationMessage();
    assertTrue(htmlExportNotificationMsg, htmlExportNotificationMsg.contains("-html-"));
    assertTrue(htmlExportNotificationMsg, htmlExportNotificationMsg.contains("/export/report/"));
    assertTrue(
        htmlExportNotificationMsg, htmlExportNotificationMsg.contains("1 linked filestore item"));
    NotificationData htmlNotificationData = htmlNotification.getNotificationDataObject();
    assertNotNull(htmlNotificationData);
    String htmlDownloadLink =
        ((ArchiveExportNotificationData) htmlNotificationData).getDownloadLink();
    assertTrue(
        "unexpected download link: " + htmlDownloadLink,
        htmlDownloadLink.startsWith("http") && htmlDownloadLink.contains("-html-"));
  }

  // rspac-2240
  @Test
  public void testLinkLevel0IncludesAttachments()
      throws IOException, URISyntaxException, ExecutionException, InterruptedException {
    User user = createInitAndLoginAnyUser();

    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "any");
    EcatImage ecatImage = addImageToField(doc.getFields().get(0), user);
    ArchiveExportConfig cfg = createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    cfg.setMaxLinkLevel(0);
    ArchiveResult resultXml =
        exportImportMgr
            .exportRecordSelection(
                getSingleRecordExportSelection(doc.getId(), RecordType.NORMAL.toString()),
                cfg,
                user,
                anyURI(),
                standardPostExport)
            .get();
    File zipFolder = extractZipArchive(resultXml);
    Collection<File> allFiles = ArchiveTestUtils.getAllFilesInArchive(zipFolder);
    assertTrue(allFiles.stream().anyMatch(f -> matchesGalleryFile(ecatImage, f)));
  }

  // asserts that a gallery file like 'abc.png' matches a file in the export like 'abc_1234467.png',
  // ignoring the timestamp part.
  boolean matchesGalleryFile(EcatMediaFile emf, File file) {
    return file.getName().startsWith(FilenameUtils.getBaseName(emf.getName()))
        && file.getName().endsWith(FilenameUtils.getExtension(emf.getName()));
  }

  @Test
  public void testExportNotificationWithMockAwsS3() throws Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "any");
    ArchiveExportConfig cfg = createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    cfg.setArchiveType(ArchiveExportConfig.HTML);
    // Mock S3 Utilities
    SdkHttpResponse sdkHttpResponse = SdkHttpResponse.builder().statusCode(200).build();
    // PutObjectResponse putObjectResponse = Mockito.mock(PutObjectResponse.class);
    Function mockExporter = Mockito.mock(Function.class);

    when(s3Utilities.getPresignedUrlForArchiveDownload(anyString()))
        .thenReturn(new URL("http://www.google.com"));
    when(s3Utilities.isArchiveInS3(anyString())).thenReturn(true);
    when(s3Utilities.getS3Uploader(any(File.class))).thenReturn(mockExporter);
    when(mockExporter.apply(any(File.class))).thenReturn(sdkHttpResponse);
    ReflectionTestUtils.setField(standardPostExport, "hasS3Access", true);

    File file =
        exportImportMgr
            .exportRecordSelection(
                getSingleRecordExportSelection(doc.getId(), "NORMAL"),
                cfg,
                user,
                new URI("http://www.google.com"),
                standardPostExport)
            .get()
            .getExportFile();

    // Check notification and its content
    ISearchResults<Notification> newNotifications =
        communicationMgr.getNewNotificationsForUser(
            user.getUsername(),
            PaginationCriteria.createDefaultForClass(CommunicationTarget.class));
    assertEquals(Integer.valueOf(1), newNotifications.getHits());
    Notification htmlNotification = newNotifications.getFirstResult();
    String htmlExportNotificationMsg = htmlNotification.getNotificationMessage();
    assertTrue(htmlExportNotificationMsg, htmlExportNotificationMsg.contains("-html-"));
    assertTrue(htmlExportNotificationMsg, htmlExportNotificationMsg.contains("/export/report/"));
    assertTrue(
        htmlExportNotificationMsg, htmlExportNotificationMsg.contains("http://www.google.com"));
    NotificationData htmlNotificationData = htmlNotification.getNotificationDataObject();
    assertNotNull(htmlNotificationData);

    // Check file has actually been deleted
    assertFalse(file.exists());
    ReflectionTestUtils.setField(standardPostExport, "hasS3Access", false);
  }

  // rspac-2300 and rspac-2761
  @Test
  public void importMaintainsCreationDate() throws Exception {

    User user = createInitAndLoginAnyUser();

    // create original items
    Folder newFolder = createSubFolder(getRootFolderForUser(user), "F", user);
    Notebook nb = createNotebookWithNEntries(newFolder.getId(), "N", 1, user);
    StructuredDocument sd = createBasicDocumentInFolder(user, newFolder, "something");
    EcatDocumentFile attachment =
        addAttachmentDocumentToField(
            RSpaceTestUtils.getAnyAttachment(), sd.getFields().get(0), user);

    Instant twoYearsAgo = Instant.now().minus(720, ChronoUnit.DAYS);
    Long twoYearsAgoL = twoYearsAgo.toEpochMilli();
    Instant oneYearAgo = Instant.now().minus(360, ChronoUnit.DAYS);
    Long oneYearAgoL = oneYearAgo.toEpochMilli();
    List<Long> ids =
        TransformerUtils.toList(newFolder.getId(), nb.getId(), sd.getId(), attachment.getId());
    doInTransaction(
        () -> {
          NativeQuery<?> q =
              sessionFactory
                  .getCurrentSession()
                  .createSQLQuery(
                      "update BaseRecord set creationDate = :cd, creationDateMillis=:cdm, "
                          + "modificationDate = :md, modificationDateMillis=:mdm where id in :ids");
          q.setParameter("cd", new Date(twoYearsAgoL));
          q.setParameter("cdm", twoYearsAgoL);
          q.setParameter("md", new Date(oneYearAgoL));
          q.setParameter("mdm", oneYearAgoL);
          q.setParameter("ids", ids);
          q.executeUpdate();
        });

    // export and reimport. Leave a short wait, as there is some loss of precision with millis being
    // rounded to nearest second.
    ArchiveExportConfig cfg = createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    ArchiveResult archiveResult =
        exportImportMgr
            .exportRecordSelection(
                getSingleRecordExportSelection(newFolder.getId(), "FOLDER"),
                cfg,
                user,
                new URI("http://www.google.com"),
                standardPostExport)
            .get();

    ArchivalImportConfig importCfg =
        createDefaultArchiveImportConfig(user, tempImportFolder.getRoot());
    ImportArchiveReport report =
        exportImportMgr.importArchive(
            fileToMultipartfile(
                archiveResult.getExportFile().getName(), archiveResult.getExportFile()),
            user.getUsername(),
            importCfg,
            NULL_MONITOR,
            importStrategy::doImport);

    assertTrue(report.isSuccessful());
    // now assert that imported creation timestamps are within tolerance - ie not 1500 later.
    Folder importedFolder =
        (Folder)
            recordMgr
                .listFolderRecords(getRootFolderForUser(user).getId(), brPg())
                .getResults()
                .stream()
                .filter(br -> br.getName().equals("F") && !br.getId().equals(newFolder.getId()))
                .findFirst()
                .get();

    assertTimeStampsIdenticalWithinTolerance(
        importedFolder.getCreationDate().toInstant(), twoYearsAgo, 1000);
    assertTimeStampsIdenticalWithinTolerance(
        importedFolder.getModificationDateAsDate().toInstant(), oneYearAgo, 1000);

    Notebook importedNotebook =
        (Notebook)
            recordMgr.listFolderRecords(importedFolder.getId(), brPg()).getResults().stream()
                .filter(br -> br.getName().equals("N"))
                .findFirst()
                .get();
    assertTimeStampsIdenticalWithinTolerance(
        importedNotebook.getCreationDate().toInstant(), twoYearsAgo, 1000);
    assertTimeStampsIdenticalWithinTolerance(
        importedNotebook.getModificationDateAsDate().toInstant(), oneYearAgo, 1000);

    StructuredDocument importedDoc =
        recordMgr.listFolderRecords(importedFolder.getId(), brPg()).getResults().stream()
            .filter(br -> br.getName().equals(sd.getName()) && !br.getId().equals(sd.getId()))
            .findFirst()
            .get()
            .asStrucDoc();
    importedDoc = recordMgr.getRecordWithFields(importedDoc.getId(), user).asStrucDoc();
    assertTimeStampsIdenticalWithinTolerance(
        importedDoc.getCreationDate().toInstant(), twoYearsAgo, 1000);
    assertTimeStampsIdenticalWithinTolerance(
        importedDoc.getModificationDateAsDate().toInstant(), oneYearAgo, 1000);
    assertTimeStampsIdenticalWithinTolerance(
        importedDoc.getFields().get(0).getModificationDateAsDate().toInstant(), oneYearAgo, 1000);

    BaseRecord importedAttachment =
        searchMgr.searchUserRecordsWithSimpleQuery(user, "genFilesi.txt", 10).getResults().stream()
            .filter(br -> !br.getId().equals(attachment.getId()))
            .findFirst()
            .get();
    assertTimeStampsIdenticalWithinTolerance(
        importedAttachment.getCreationDate().toInstant(), twoYearsAgo, 1000);
    assertTimeStampsIdenticalWithinTolerance(
        importedAttachment.getModificationDateAsDate().toInstant(), oneYearAgo, 1000);
  }

  void assertTimeStampsIdenticalWithinTolerance(
      Instant expected, Instant actual, int toleranceMillis) {
    assertTrue(Duration.between(expected, actual).abs().toMillis() < toleranceMillis);
  }
}
