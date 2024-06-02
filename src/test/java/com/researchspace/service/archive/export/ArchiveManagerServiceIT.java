package com.researchspace.service.archive.export;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.core.util.progress.ProgressMonitor.NULL_MONITOR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.researchspace.Constants;
import com.researchspace.archive.ArchivalDocumentParserRef;
import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchiveManifest;
import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.ExportScope;
import com.researchspace.archive.IArchiveModel;
import com.researchspace.archive.ImmutableExportRecordList;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.archive.model.ArchiveModelFactory;
import com.researchspace.archive.model.ArchiveUsers;
import com.researchspace.archive.model.ArchiveUsersTestData;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.core.testutil.FileTestUtils;
import com.researchspace.core.util.FieldParserConstants;
import com.researchspace.core.util.ZipUtils;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.model.Community;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.Group;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.RSMath;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.AuditManager;
import com.researchspace.service.archive.ArchiveExportServiceManager;
import com.researchspace.service.archive.ArchiveImporterManager;
import com.researchspace.service.archive.IArchiveParser;
import com.researchspace.service.archive.ImportArchiveReport;
import com.researchspace.service.archive.ImportStrategy;
import com.researchspace.service.archive.UserImporter;
import com.researchspace.testutils.ArchiveTestUtils;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.bind.JAXBException;
import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * This test class is outside of the Spring tests transaction environment. This is because auditing
 * only happens after a transaction is really committed to the database, and regular Spring Tests
 * always roll back. <br>
 * Therefore it's really important to ensure that all entries made to the DB during these tests are
 * removed afterwards.
 */
public class ArchiveManagerServiceIT extends RealTransactionSpringTestBase {

  private static final String LINKED_FROM_CONTENT = "toLinkFrom";

  @Autowired private AuditManager auditMgr;
  @Autowired private UserImporter userImporter;
  @Autowired private ArchiveImporterManager importer;
  private @Autowired @Qualifier("importUsersAndRecords") ImportStrategy importStrategy;
  private @Autowired ArchiveExportPlanner archivePlanner;

  private IArchiveParser archivalParser;

  @Autowired
  @Qualifier("archiveManager")
  private ArchiveExportServiceManager archiveService;

  final int EXPECTED_FILE_COUNT_FOR_COMPLEX_DOC = 9;

  @Rule public TemporaryFolder tempExportFolder = new TemporaryFolder();
  @Rule public TemporaryFolder tempImportFolder = new TemporaryFolder();
  @Rule public TemporaryFolder spareFolder = new TemporaryFolder();
  @Rule public TemporaryFolder spareFolder2 = new TemporaryFolder();
  @Rule public TemporaryFolder spareFolder3 = new TemporaryFolder();

  @Before
  public void setUp() throws Exception {
    super.setUp();
    archivalParser = applicationContext.getBean(IArchiveParser.class);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  // see Jira 127 and 273
  @Test
  public void reciprocallyLinkedRecordsDontBlockExport() throws Exception {
    User u1 = createAndSaveUser(getRandomAlphabeticString("user"));
    initUser(u1);
    logoutAndLoginAs(u1);
    StructuredDocument doc1 = createBasicDocumentInRootFolderWithText(u1, "doc1");
    StructuredDocument doc2 = createBasicDocumentInRootFolderWithText(u1, "doc2");
    addLinkToOtherRecord(doc1.getFields().get(0), doc2);
    addLinkToOtherRecord(doc2.getFields().get(0), doc1);
    ArchiveManifest manifest = new ArchiveManifest();
    ArchiveExportConfig expCfg = createDefaultArchiveConfig(u1, tempExportFolder.getRoot());
    ImmutableExportRecordList list = createExportList(doc1.getOid(), expCfg);
    String zipFileName =
        archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();

    // now check that only 2 records were exported, no duplicates
    IArchiveModel archive = parseArchiveFile(zipFileName, tempExportFolder);
    assertEquals(2, archive.getCurrentDocCount());

    File zipFile = new File(tempExportFolder.getRoot(), zipFileName);
    // make sure we only create 2 folders, 1 for each record. I.e., naming is consistent
    // for new files and for linked files
    String expandedFolderPath = ZipUtils.extractZip(zipFile, tempExportFolder.getRoot());
    assertEquals(3, FileTestUtils.getFolderCount(new File(expandedFolderPath)));
  }

  @Test
  public void testMaxLinkDepth() throws Exception {
    User u1 = createAndSaveUser(getRandomAlphabeticString("user"));
    initUser(u1);
    logoutAndLoginAs(u1);
    StructuredDocument doc1 = createBasicDocumentInRootFolderWithText(u1, "doc1");
    StructuredDocument doc2 = createBasicDocumentInRootFolderWithText(u1, "doc2");
    StructuredDocument doc3 = createBasicDocumentInRootFolderWithText(u1, "doc3");
    // links are doc1->doc2->doc3
    addLinkToOtherRecord(doc1.getFields().get(0), doc2);
    addLinkToOtherRecord(doc2.getFields().get(0), doc3);
    ArchiveManifest manifest = new ArchiveManifest();
    ArchiveExportConfig expCfg = createDefaultArchiveConfig(u1, tempExportFolder.getRoot());
    // there are 3 records, so we can only follow 2 links
    for (int i = 0; i <= 2; i++) {
      expCfg.setMaxLinkLevel(i);
      ImmutableExportRecordList list = createExportList(doc1.getOid(), expCfg);
      assertEquals(i + 1, list.getRecordsToExportSize());
      String zipFileName =
          archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
      // now check that only i +1 records were exported, default is to follow one link
      IArchiveModel archive = parseArchiveFile(zipFileName, tempExportFolder);
      // e.g., link level of 0 means just export selected record
      assertEquals(i + 1, archive.getCurrentDocCount());
    }
  }

  @Test
  public void testComplexDocArchivalToHTML() throws Exception {
    User user = createAndSaveUser(getRandomAlphabeticString("exporter"));
    initUser(user);
    StructuredDocument doc = createComplexDocument(user);
    ArchiveManifest manifest = new ArchiveManifest();
    ArchiveExportConfig expCfg = createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    ImmutableExportRecordList list = createExportList(doc.getOid(), expCfg);
    expCfg.setArchiveType(ArchiveExportConfig.HTML);
    archiveService =
        applicationContext.getBean("htmlexportWriter", ArchiveExportServiceManager.class);
    String zipFileName =
        archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
    assertTrue(zipFileName.indexOf("zip") > 0); // successfully created a zip file
    File zipFile = new File(tempExportFolder.getRoot(), zipFileName);
    ZipUtils.extractZip(zipFile, tempImportFolder.getRoot());

    Collection<File> htmlFiles =
        FileUtils.listFiles(tempImportFolder.getRoot(), new String[] {"html"}, true);
    // created file and link file and 2 index files + nfs file if exists
    assertEquals(5, htmlFiles.size());
    Collection<File> mathSVGFiles =
        FileUtils.listFiles(tempImportFolder.getRoot(), new String[] {"svg"}, true);
    assertEquals(1, mathSVGFiles.size());
  }

  @Test
  public void htmlExportWithVersionedLink() throws Exception {
    User user = createInitAndLoginAnyUser();

    /*
     * create doc linking other docs in three ways: with normal link,
     * versioned link to latest version, and versioned link to previous version
     */
    StructuredDocument firstDoc =
        createBasicDocumentInRootFolderWithText(user, "linked records doc");
    rename(user, firstDoc, "firstDoc");
    StructuredDocument secondDoc = createBasicDocumentInRootFolderWithText(user, "target doc");
    Field docField = firstDoc.getFields().get(0);
    Long prevVersion = secondDoc.getUserVersion().getVersion();
    addLinkToOtherRecord(docField, secondDoc, true); // versioned link to prev version
    rename(user, secondDoc, "secondDoc");
    addLinkToOtherRecord(docField, secondDoc, true); // versioned link to latest version
    addLinkToOtherRecord(docField, secondDoc); // non-versioned internal link

    // now export document with default link depth 1
    ArchiveManifest manifest = new ArchiveManifest();
    ArchiveExportConfig expCfg = createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    expCfg.setArchiveType(ArchiveExportConfig.HTML);
    ImmutableExportRecordList list = createExportList(firstDoc.getOid(), expCfg);

    String zipFileName =
        archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
    assertTrue(zipFileName.indexOf("zip") > 0); // successfully created a zip file

    // and check archive contents
    File zipFile = new File(tempExportFolder.getRoot(), zipFileName);
    ZipUtils.extractZip(zipFile, tempImportFolder.getRoot());

    Collection<File> htmlFiles =
        FileUtils.listFiles(tempImportFolder.getRoot(), new String[] {"html"}, true);
    // should be 3 files: exported doc and two versions of linked doc (latest and prev version)
    assertEquals(3, htmlFiles.size());
    // sort the names, first should be firstDoc, then secondDoc-rev and then secondDoc
    List<File> htmlFilesByName =
        htmlFiles.stream()
            .sorted((f1, f2) -> f1.getName().compareTo(f2.getName()))
            .collect(Collectors.toList());
    assertFalse(
        "unexpected file: " + htmlFilesByName.get(0),
        htmlFilesByName.get(0).getName().contains("rev"));
    assertTrue(
        "unexpected file: " + htmlFilesByName.get(1),
        htmlFilesByName.get(1).getName().contains("rev"));
    assertFalse(
        "unexpected file: " + htmlFilesByName.get(2),
        htmlFilesByName.get(2).getName().contains("rev"));
    // assert exported version file contains correct version
    String revisionedFileContent =
        FileUtils.readFileToString(htmlFilesByName.get(1), StandardCharsets.UTF_8);
    assertTrue(revisionedFileContent, revisionedFileContent.contains("version " + prevVersion));
  }

  @Test
  public void testExportComplexDocumentWithNoRevisions() throws Exception {
    User user = createInitAndLoginAnyUser();

    StructuredDocument doc = createComplexDocument(user);
    rename(user, doc, "complexDoc");

    // now export
    ArchiveManifest manifest = new ArchiveManifest();
    ArchiveExportConfig expCfg = createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    ImmutableExportRecordList list = createExportList(doc.getOid(), expCfg);

    String zipFileName =
        archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
    assertTrue(zipFileName.indexOf("zip") > 0); // successfully created a zip file

    // and check archive contents
    IArchiveModel archive = parseArchiveFile(zipFileName, tempExportFolder);

    List<ArchivalDocumentParserRef> current = archive.getCurrentVersions();
    List<ArchivalDocumentParserRef> all = archive.getAllVersions();
    assertEquals(2, archive.getCurrentDocCount());
    assertEquals(2, all.size());
    assertEquals(
        EXPECTED_FILE_COUNT_FOR_COMPLEX_DOC,
        getAssociatedFilesFromDoc("complexDoc", archive).size());
    // now lets check links were replaced:
    assertRawImageLinksAreReplaced(current);
    assertLinkedRecordLinksAreReplaced(current);

    // now lets make a copy and export and check links are OK RSPAC-676

    User u2 = createAndSaveUser(getRandomAlphabeticString("u2"));
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initUsers(u2, pi);
    logoutAndLoginAs(pi);
    Group group = createGroupForUsers(pi, pi.getUsername(), "", user, u2);
    logoutAndLoginAs(user);
    StructuredDocument copy =
        recordMgr
            .copy(doc.getId(), "any", user, folderMgr.getRootRecordForUser(user, user).getId())
            .getUniqueCopy()
            .asStrucDoc();
    rename(user, copy, "complexDoc2");

    expCfg = createDefaultArchiveConfig(user, spareFolder.getRoot());
    list = createExportList(copy.getOid(), expCfg);

    zipFileName = archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
    assertTrue(zipFileName.indexOf("zip") > 0); // successfully created a zip file
    archive = parseArchiveFile(zipFileName, spareFolder);

    current = archive.getCurrentVersions();
    assertRawImageLinksAreReplaced(current);

    // now share and check sharee can export OK too
    shareRecordWithGroup(user, group, copy);
    logoutAndLoginAs(u2);

    expCfg = createDefaultArchiveConfig(u2, spareFolder2.getRoot());

    zipFileName = archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
    assertTrue(zipFileName.indexOf("zip") > 0); // successfully created a zip file
    archive = parseArchiveFile(zipFileName, spareFolder2);
    // check all atachments are exported fine too.
    current = archive.getCurrentVersions();
    assertRawImageLinksAreReplaced(current);
    assertEquals(
        EXPECTED_FILE_COUNT_FOR_COMPLEX_DOC,
        getAssociatedFilesFromDoc("complexDoc2", archive).size());

    // now we'll create a new, alien user and paste content into their document.
    // exports shouldn't include these attachments
    // RSPAC-757
    User alien = createAndSaveUser(getRandomAlphabeticString("alien"));
    initUser(alien);
    logoutAndLoginAs(alien);
    StructuredDocument alienDoc =
        createBasicDocumentInRootFolderWithText(alien, doc.getFields().get(0).getFieldData());
    rename(alien, alienDoc, "alienDoc");
    expCfg = createDefaultArchiveConfig(alien, spareFolder3.getRoot());
    list = createExportList(alienDoc.getOid(), expCfg);
    zipFileName = archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
    archive = parseArchiveFile(zipFileName, spareFolder3);
    assertEquals(0, getAssociatedFilesFromDoc("alienDoc", archive).size());
  }

  private void rename(User user, StructuredDocument copy, String newName) {
    copy.setName(newName);
    recordMgr.save(copy, user);
  }

  private List<File> getAssociatedFilesFromDoc(String name, IArchiveModel archive) {
    for (ArchivalDocumentParserRef doc : archive.getCurrentVersions()) {
      if (doc.getArchivalDocument().getName().equals(name)) {
        return doc.getFileList();
      }
    }
    fail("no archive entry for " + name);
    return Collections.emptyList();
  }

  private IArchiveModel parseArchiveFile(String zipFileName, TemporaryFolder folder)
      throws IOException {
    File zipFile = new File(folder.getRoot(), zipFileName);

    ImportArchiveReport report = new ImportArchiveReport();
    String expandedFolderPath = ZipUtils.extractZip(zipFile, folder.getRoot());
    IArchiveModel archive = archivalParser.parse(new File(expandedFolderPath), report);
    return archive;
  }

  private void assertLinkedRecordLinksAreReplaced(List<ArchivalDocumentParserRef> current) {
    for (ArchivalDocumentParserRef docs : current) {
      if (docs.getName().contains("complexDoc")) {
        ArchivalField field = docs.getArchivalDocument().getListFields().get(0);
        String data = field.getFieldData();

        Elements linkedRecordElements =
            getElementsFromHTML(data, FieldParserConstants.LINKEDRECORD_CLASS_NAME);
        for (Element el : linkedRecordElements) {
          String linkHref = el.attr("href");
          if (!linkHref.startsWith("../doc_BasicDocument")
              && !linkHref.startsWith("http://localhost:8080")) {
            fail("found an element without replaced link: " + el);
          }
        }
      }
    }
  }

  private void assertRawImageLinksAreReplaced(List<ArchivalDocumentParserRef> current) {
    for (ArchivalDocumentParserRef docs : current) {
      if (docs.getName().contains("complexDoc")) {
        ArchivalField field = docs.getArchivalDocument().getListFields().get(0);
        String data = field.getFieldData();
        System.err.println(data);
        Elements rawImageElements =
            getElementsFromHTML(data, FieldParserConstants.IMAGE_THMNAIL_DROPPED_CLASS_NAME);
        for (Element el : rawImageElements) {
          boolean found = false;
          for (File associateFile : docs.getFileList()) {
            if (associateFile.getName().equals(el.attr("src"))) {
              found = true;
            }
          }
          assertTrue("did not find replacement for element" + el, found);
        }
      }
    }
  }

  /** Gets JSoup elements with specified CSS class */
  private Elements getElementsFromHTML(String html, String cssClass) {
    Document d = Jsoup.parse(html);
    return d.getElementsByClass(cssClass);
  }

  private ImmutableExportRecordList createExportList(
      GlobalIdentifier id, IArchiveExportConfig config) {
    ExportRecordList list = new ExportRecordList();
    list.add(id);
    archivePlanner.updateExportListWithLinkedRecords(list, config);
    return list;
  }

  @Test
  public void testXMLExportImportRoundTrip() throws Exception {

    User exporter = createAndSaveUser(getRandomAlphabeticString("exporter"));
    logoutAndLoginAs(exporter);
    initUser(exporter);

    StructuredDocument doc = createBasicDocumentInRootFolderWithText(exporter, LINKED_FROM_CONTENT);
    doc.setDocTag("testTag");
    doc.setTagMetaData("testTag");

    // archive this document with revisions
    EcatComment comment = addNewCommentToField("comment1", doc.getFields().get(0), exporter);
    assertEquals(1, auditMgr.getRevisionsForEntity(EcatComment.class, comment.getComId()).size());

    // simulate saving of text field following comment addition.
    addNewCommentItemToExistingComment(
        "comment2", comment.getComId(), doc.getFields().get(0), exporter);
    List<AuditedEntity<EcatComment>> history =
        auditMgr.getRevisionsForEntity(EcatComment.class, comment.getComId());
    assertEquals(2, history.size());

    EcatImageAnnotation originalAnnotation =
        addImageAnnotationToField(doc.getFields().get(0), exporter);
    assertEquals(
        1,
        auditMgr
            .getRevisionsForEntity(EcatImageAnnotation.class, originalAnnotation.getId())
            .size());

    updateExistingImageAnnotation(
        originalAnnotation.getId(),
        doc.getFields().get(0),
        exporter,
        getTestZwibblerAnnotationString(getRandomName(5)));
    assertEquals(
        2,
        auditMgr
            .getRevisionsForEntity(EcatImageAnnotation.class, originalAnnotation.getId())
            .size());

    final int initialChemCount = rsChemElementManager.getAll().size();
    RSChemElement originalChemElement = addChemStructureToField(doc.getFields().get(0), exporter);
    assertEquals(
        1, auditMgr.getRevisionsForEntity(RSChemElement.class, originalChemElement.getId()).size());

    updateExistingChemElement(originalChemElement.getId(), doc.getFields().get(0), exporter);
    assertEquals(
        2, auditMgr.getRevisionsForEntity(RSChemElement.class, originalChemElement.getId()).size());
    recordMgr.save(doc, exporter);

    File afile = RSpaceTestUtils.getResource("testTxt.txt");
    addAttachmentDocumentToField(afile, doc.getFields().get(0), exporter);

    StructuredDocument toLinkTo = createBasicDocumentInRootFolderWithText(exporter, "toLinkTo");
    addLinkToOtherRecord(doc.getFields().get(0), toLinkTo);

    final int initalMathCount = mathMgr.getAll().size();
    RSMath originalMath = addMathToField(doc.getFields().get(0), exporter);

    ArchiveManifest manifest = new ArchiveManifest();
    ArchiveExportConfig expCfg = new ArchiveExportConfig();
    expCfg.setExporter(exporter);
    expCfg.setExportScope(ExportScope.SELECTION);
    expCfg.setTopLevelExportFolder(tempExportFolder.getRoot());
    ImmutableExportRecordList list = createExportList(doc.getOid(), expCfg);
    String zipName = archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
    assertTrue(zipName.indexOf("zip") > 0); // successfully created a zip file

    File zipFile = new File(tempExportFolder.getRoot(), zipName);
    assertTrue(zipFile.exists()); // TBD unzip to read contents.

    ArchivalImportConfig iconfig =
        createDefaultArchiveImportConfig(exporter, tempImportFolder.getRoot());
    ImportArchiveReport report =
        importer.importArchive(zipFile, iconfig, NULL_MONITOR, importStrategy::doImport);
    assertTrue(report.isSuccessful());
    String unzipStore = report.getExpandedZipFolderPath();
    File unzipF = new File(unzipStore);
    File[] files = unzipF.listFiles();
    int minimumNumberOfFiles = 4;
    assertTrue(files.length > minimumNumberOfFiles);

    // check if is in database
    Set<BaseRecord> insertDocs = report.getImportedRecords();
    assertTrue(insertDocs.size() > 0); // not empty
    for (BaseRecord rec : insertDocs) {
      Record rcd = recordMgr.get(rec.getId());
      assertNotNull(rcd);
    }
    List<RSMath> maths = mathMgr.getAll();
    List<RSChemElement> chems = rsChemElementManager.getAll();
    // original+imported = 2 additional
    assertEquals(initalMathCount + 2, maths.size());
    assertEquals(initialChemCount + 2, chems.size());

    StructuredDocument linkedFromDoc = findLinkedFromDoc(insertDocs);
    assertEquals("testTag", linkedFromDoc.getDocTag());
    Field imported = linkedFromDoc.getFields().get(0);
    openTransaction();
    FieldContents contents = fieldParser.findFieldElementsInContent(imported.getFieldData());
    commitTransaction();
    assertTrue(contents.hasElements(RSChemElement.class));
    assertTrue(contents.hasElements(RSMath.class));
    assertTrue(contents.hasImageAnnotations());
    assertTrue(contents.hasElements(EcatComment.class));
    assertFalse(
        "Math ID in text field was not updated",
        contents
            .getElements(RSMath.class)
            .getElements()
            .get(0)
            .getId()
            .equals(originalMath.getId()));
    assertFalse(
        "Chem ID in text field was not updated",
        contents
            .getElements(RSChemElement.class)
            .getElements()
            .get(0)
            .getId()
            .equals(originalChemElement.getId()));
    assertFalse(
        "Imageannotation ID in text field was not updated",
        contents
            .getImageAnnotations()
            .getElements()
            .get(0)
            .getId()
            .equals(originalAnnotation.getId()));
  }

  @Test
  public void testXMLExportImportWithChemistryFile() throws Exception {
    // Create user and login
    User exporter = createAndSaveUser(getRandomAlphabeticString("exporter"));
    logoutAndLoginAs(exporter);
    initUser(exporter);

    // Create new doc and add a couple chemistry elements, one with attached chemistry file
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(exporter, LINKED_FROM_CONTENT);

    final int initialChemCount = rsChemElementManager.getAll().size();
    RSChemElement basicChemElement = addChemStructureToField(doc.getFields().get(0), exporter);
    assertEquals(
        1, auditMgr.getRevisionsForEntity(RSChemElement.class, basicChemElement.getId()).size());

    final int initialChemFileCount = chemistryFileManager.getAll().size();
    EcatChemistryFile chemistryFile = addChemistryFileToGallery("Aminoglutethimide.mol", exporter);
    RSChemElement chemElementWithFile =
        addChemStructureToFieldWithLinkedChemFile(chemistryFile, doc.getFields().get(0), exporter);
    assertEquals(
        2, auditMgr.getRevisionsForEntity(RSChemElement.class, chemElementWithFile.getId()).size());
    // Revisions expected here are 2, chemistry file saved once when added to gallery and updated
    // when chem file added to field attachments
    assertEquals(
        2, auditMgr.getRevisionsForEntity(EcatChemistryFile.class, chemistryFile.getId()).size());

    ArchiveManifest manifest = new ArchiveManifest();
    ArchiveExportConfig expCfg = new ArchiveExportConfig();
    expCfg.setExporter(exporter);
    expCfg.setExportScope(ExportScope.SELECTION);
    expCfg.setTopLevelExportFolder(tempExportFolder.getRoot());
    ImmutableExportRecordList list = createExportList(doc.getOid(), expCfg);
    String zipName = archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
    assertTrue(zipName.indexOf("zip") > 0); // successfully created a zip file

    File zipFile = new File(tempExportFolder.getRoot(), zipName);
    assertTrue(zipFile.exists()); // TBD unzip to read contents.

    ArchivalImportConfig importConfig =
        createDefaultArchiveImportConfig(exporter, tempImportFolder.getRoot());
    ImportArchiveReport report =
        importer.importArchive(zipFile, importConfig, NULL_MONITOR, importStrategy::doImport);
    assertTrue(report.isSuccessful());
    String unzipStore = report.getExpandedZipFolderPath();
    File unzipF = new File(unzipStore);
    File[] files = unzipF.listFiles();
    int minimumNumberOfFiles = 4;
    assertTrue(files.length > minimumNumberOfFiles);

    // check if is in database
    Set<BaseRecord> insertDocs = report.getImportedRecords();
    assertTrue(insertDocs.size() > 0); // not empty
    for (BaseRecord rec : insertDocs) {
      Record rcd = recordMgr.get(rec.getId());
      assertNotNull(rcd);
    }
    List<RSChemElement> chemElements = rsChemElementManager.getAll();
    List<EcatChemistryFile> chemFiles = chemistryFileManager.getAll();
    // Initial 2 RsChemElements + 2 from import
    // Initial 1 EcatChemistryFile + 1 from import
    assertEquals(initialChemCount + 4, chemElements.size());
    assertEquals(initialChemFileCount + 2, chemFiles.size());
  }

  private StructuredDocument findLinkedFromDoc(Set<BaseRecord> insertDocs) {
    return insertDocs.stream()
        .filter(
            br -> br.asStrucDoc().getFields().get(0).getFieldData().contains(LINKED_FROM_CONTENT))
        .findFirst()
        .get()
        .asStrucDoc();
  }

  @Test
  public void testImportOfUserData() throws IOException, JAXBException, Exception {
    // the authenticated user performing the import.
    User sysAdminImporter = logoutAndLoginAsSysAdmin();

    ArchiveUsersTestData testData = ArchiveTestUtils.createArchiveUsersTestData();
    ArchiveUsers fromXml = ArchiveTestUtils.writeToXMLAndReadFromXML(testData.getArchiveInfo());
    userImporter.saveArchiveUsersToDatabase(sysAdminImporter, fromXml, new ImportArchiveReport());

    openTransaction();
    // now load back from database.
    User savedADmin = userMgr.getUserByUsername(testData.getAdmin().getUsername());
    Community comm = communityMgr.listCommunitiesForAdmin(savedADmin.getId()).get(0);
    Community reloadedCommunity = communityMgr.getCommunityWithAdminsAndGroups(comm.getId());
    Group grp = reloadedCommunity.getLabGroups().iterator().next();

    assertEquals(reloadedCommunity, grp.getCommunity());
    commitTransaction();
    assertEquals(2, grp.getMembers().size());

    User reloadedPI = userMgr.getUserByUsername(testData.getUser().getUniqueName());
    assertEquals(testData.getUser(), reloadedPI);
    assertTrue(reloadedPI.hasGroup(grp));
    assertTrue(reloadedPI.hasRole(Role.PI_ROLE));
  }

  @Test
  public void testExportWithLinksToDocsNotebooksAndFolders() throws Exception {
    User user = createInitAndLoginAnyUser();

    // create 3 docs with links going: firstDoc -> targetDocA -> targetDocB
    StructuredDocument firstDoc =
        createBasicDocumentInRootFolderWithText(user, "linked records doc");
    rename(user, firstDoc, "firstDoc");
    StructuredDocument targetDocA = createBasicDocumentInRootFolderWithText(user, "targetDocA");
    StructuredDocument targetDocB = createBasicDocumentInRootFolderWithText(user, "targetDocB");
    Field docField = firstDoc.getFields().get(0);
    addLinkToOtherRecord(docField, targetDocA);
    Field targetDocAField = targetDocA.getFields().get(0);
    addLinkToOtherRecord(targetDocAField, targetDocB);

    // create empty folder and a notebook, link them from the firstDoc
    Folder folder = createSubFolder(firstDoc.getParent(), "linkedFolder", user);
    Notebook notebook =
        createNotebookWithNEntries(firstDoc.getParent().getId(), "linkedNotebook", 1, user);
    addLinkToOtherRecord(docField, folder);
    addLinkToOtherRecord(docField, notebook);

    // create another empty folder (but this one will be included in export), add link to document
    Folder exportedFolder = createSubFolder(firstDoc.getParent(), "exportedFolder", user);
    addLinkToOtherRecord(docField, exportedFolder);

    /* add two rspace links pointing to other instance (i.e. as if imported internal link that now is re-exported) */
    // build the link based on targetDocA
    String externalRSpaceLinkStr = richTextUpdater.generateURLStringForInternalLink(targetDocA);
    String firstExternalLinkId = targetDocA.getId().toString();
    // but change the href URL to absolute, pointing to a different instance
    externalRSpaceLinkStr =
        externalRSpaceLinkStr.replace("/globalId/", "http://localhost:8081/globalId/");
    // in second link just change the id
    String secondExternalLinkId = targetDocA.getId() + "0";
    String externalRSpaceLinkStr2 =
        externalRSpaceLinkStr.replaceAll(firstExternalLinkId, secondExternalLinkId);
    docField.setFieldData(docField.getData() + externalRSpaceLinkStr + externalRSpaceLinkStr2);
    recordMgr.save(firstDoc, piUser);

    // now export document and empty folder, with default link depth 1
    ArchiveManifest manifest = new ArchiveManifest();
    ArchiveExportConfig expCfg = createDefaultArchiveConfig(user, tempExportFolder.getRoot());
    ImmutableExportRecordList list = createExportList(firstDoc.getOid(), expCfg);
    list.getFolderTree().add((new ArchiveModelFactory()).createArchiveFolder(exportedFolder));

    String zipFileName =
        archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
    assertTrue(zipFileName.indexOf("zip") > 0); // successfully created a zip file

    // and check archive contents
    IArchiveModel archive = parseArchiveFile(zipFileName, tempExportFolder);

    assertEquals(2, archive.getCurrentDocCount());
    assertEquals(
        "firstDoc should be part of archive",
        1,
        archive.findCurrentDocArchiveByName(firstDoc.getName()).size());
    assertEquals(
        "targetDocA should be part of archive",
        1,
        archive.findCurrentDocArchiveByName(targetDocA.getName()).size());
    assertEquals(
        "targetDocB should not be in archive",
        0,
        archive.findCurrentDocArchiveByName(targetDocB.getName()).size());

    // assert relative link from firstDoc to targetDocA, absolute links to folder and notebook
    ArchivalDocumentParserRef firstDocRef =
        archive.findCurrentDocArchiveByName(firstDoc.getName()).get(0);
    ArchivalField firstDocArchivalField = firstDocRef.getArchivalDocument().getListFields().get(0);
    String firstDocExportedData = firstDocArchivalField.getFieldData();
    assertTrue(firstDocExportedData, firstDocExportedData.contains("href=\"../doc_"));
    assertTrue(
        firstDocExportedData,
        firstDocExportedData.contains(
            "http://localhost:8080/globalId/" + folder.getGlobalIdentifier()));
    assertTrue(
        firstDocExportedData,
        firstDocExportedData.contains(
            "http://localhost:8080/globalId/" + notebook.getGlobalIdentifier()));

    // relative link to exported folder, matching the document name in html export
    assertTrue(
        firstDocExportedData,
        firstDocExportedData.contains(
            "href=\"../" + exportedFolder.getName() + "-" + exportedFolder.getId()));

    // absolute URLs pointing to another instance are left unchanged
    assertTrue(
        firstDocExportedData,
        firstDocExportedData.contains("http://localhost:8081/globalId/SD" + firstExternalLinkId));
    assertTrue(
        firstDocExportedData,
        firstDocExportedData.contains("http://localhost:8081/globalId/SD" + secondExternalLinkId));

    // exactly two links (to targetDocA and exportedFolder) in links list inside xml
    assertEquals(2, firstDocArchivalField.getLinkMeta().size());

    // absolute link created from targetDocA to targetDocB, as targetDocB is outside link depth
    ArchivalDocumentParserRef targetDocARef =
        archive.findCurrentDocArchiveByName(targetDocA.getName()).get(0);
    ArchivalField targetDocAArchivalField =
        targetDocARef.getArchivalDocument().getListFields().get(0);
    String targetDocAExportedFieldData = targetDocAArchivalField.getFieldData();
    assertFalse(
        targetDocAExportedFieldData, targetDocAExportedFieldData.contains("href=\"../doc_"));
    assertTrue(
        targetDocAExportedFieldData,
        targetDocAExportedFieldData.contains(
            "http://localhost:8080/globalId/" + targetDocB.getGlobalIdentifier()));
  }
}
