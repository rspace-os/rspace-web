package com.researchspace.service.archive.export;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.core.util.progress.ProgressMonitor.NULL_MONITOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
import com.researchspace.linkedelements.FieldParser;
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
import com.researchspace.service.EcatChemistryFileManager;
import com.researchspace.service.RSMathManager;
import com.researchspace.service.archive.ArchiveExportServiceManager;
import com.researchspace.service.archive.ArchiveImporterManager;
import com.researchspace.service.archive.IArchiveParser;
import com.researchspace.service.archive.ImportArchiveReport;
import com.researchspace.service.archive.ImportStrategy;
import com.researchspace.service.archive.UserImporter;
import com.researchspace.testutils.ArchiveTestUtils;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import jakarta.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
  @Autowired private EcatChemistryFileManager chemistryFileManager;
  @Autowired private FieldParser fieldParser;
  @Autowired private RSMathManager mathMgr;
  @Autowired private UserImporter userImporter;
  @Autowired private ArchiveImporterManager importer;
  private @Autowired @Qualifier("importUsersAndRecords") ImportStrategy importStrategy;
  private @Autowired ArchiveExportPlanner archivePlanner;

  private IArchiveParser archivalParser;

  @Autowired
  @Qualifier("archiveManager")
  private ArchiveExportServiceManager archiveService;

  final int EXPECTED_FILE_COUNT_FOR_COMPLEX_DOC = 9;

  @TempDir public File tempExportFolder;
  @TempDir public File tempImportFolder;
  @TempDir public File spareFolder;
  @TempDir public File spareFolder2;
  @TempDir public File spareFolder3;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    archivalParser = applicationContext.getBean(IArchiveParser.class);
  }

  @AfterEach
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
    ArchiveExportConfig expCfg = createDefaultArchiveConfig(u1, tempExportFolder);
    ImmutableExportRecordList list = createExportList(doc1.getOid(), expCfg);
    String zipFileName =
        archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();

    // now check that only 2 records were exported, no duplicates
    IArchiveModel archive = parseArchiveFile(zipFileName, tempExportFolder);
    assertEquals(2, archive.getCurrentDocCount());

    File zipFile = new File(tempExportFolder, zipFileName);
    // make sure we only create 2 folders, 1 for each record. I.e., naming is consistent
    // for new files and for linked files
    String expandedFolderPath = ZipUtils.extractZip(zipFile, tempExportFolder);
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
    ArchiveExportConfig expCfg = createDefaultArchiveConfig(u1, tempExportFolder);
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
    ArchiveExportConfig expCfg = createDefaultArchiveConfig(user, tempExportFolder);
    ImmutableExportRecordList list = createExportList(doc.getOid(), expCfg);
    expCfg.setArchiveType(ArchiveExportConfig.HTML);
    archiveService =
        applicationContext.getBean("htmlexportWriter", ArchiveExportServiceManager.class);
    String zipFileName =
        archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
    assertTrue(zipFileName.indexOf("zip") > 0); // successfully created a zip file
    File zipFile = new File(tempExportFolder, zipFileName);
    ZipUtils.extractZip(zipFile, tempImportFolder);

    Collection<File> htmlFiles = FileUtils.listFiles(tempImportFolder, new String[] {"html"}, true);
    // created file and link file and 2 index files + nfs file if exists
    assertThat(htmlFiles).hasSize(5);
    Collection<File> mathSVGFiles =
        FileUtils.listFiles(tempImportFolder, new String[] {"svg"}, true);
    assertThat(mathSVGFiles).hasSize(1);
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
    ArchiveExportConfig expCfg = createDefaultArchiveConfig(user, tempExportFolder);
    expCfg.setArchiveType(ArchiveExportConfig.HTML);
    ImmutableExportRecordList list = createExportList(firstDoc.getOid(), expCfg);

    String zipFileName =
        archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
    assertTrue(zipFileName.indexOf("zip") > 0); // successfully created a zip file

    // and check archive contents
    File zipFile = new File(tempExportFolder, zipFileName);
    ZipUtils.extractZip(zipFile, tempImportFolder);

    Collection<File> htmlFiles = FileUtils.listFiles(tempImportFolder, new String[] {"html"}, true);
    // should be 3 files: exported doc and two versions of linked doc (latest and prev version)
    assertThat(htmlFiles).hasSize(3);
    // sort the names, first should be firstDoc, then secondDoc-rev and then secondDoc
    List<File> htmlFilesByName =
        htmlFiles.stream()
            .sorted((f1, f2) -> f1.getName().compareTo(f2.getName()))
            .collect(Collectors.toList());
    assertThat(htmlFilesByName.get(0).getName())
        .as("unexpected file: " + htmlFilesByName.get(0))
        .doesNotContain("rev");
    assertThat(htmlFilesByName.get(1).getName())
        .as("unexpected file: " + htmlFilesByName.get(1))
        .contains("rev");
    assertThat(htmlFilesByName.get(2).getName())
        .as("unexpected file: " + htmlFilesByName.get(2))
        .doesNotContain("rev");
    // assert exported version file contains correct version
    String revisionedFileContent =
        FileUtils.readFileToString(htmlFilesByName.get(1), StandardCharsets.UTF_8);
    assertThat(revisionedFileContent).as(revisionedFileContent).contains("version " + prevVersion);
  }

  @Test
  public void testExportComplexDocumentWithNoRevisions() throws Exception {
    User user = createInitAndLoginAnyUser();

    StructuredDocument doc = createComplexDocument(user);
    rename(user, doc, "complexDoc");

    // now export
    ArchiveManifest manifest = new ArchiveManifest();
    ArchiveExportConfig expCfg = createDefaultArchiveConfig(user, tempExportFolder);
    ImmutableExportRecordList list = createExportList(doc.getOid(), expCfg);

    String zipFileName =
        archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
    assertTrue(zipFileName.indexOf("zip") > 0); // successfully created a zip file

    // and check archive contents
    IArchiveModel archive = parseArchiveFile(zipFileName, tempExportFolder);

    List<ArchivalDocumentParserRef> current = archive.getCurrentVersions();
    List<ArchivalDocumentParserRef> all = archive.getAllVersions();
    assertEquals(2, archive.getCurrentDocCount());
    assertThat(all).hasSize(2);
    assertThat(getAssociatedFilesFromDoc("complexDoc", archive))
        .hasSize(EXPECTED_FILE_COUNT_FOR_COMPLEX_DOC);
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

    expCfg = createDefaultArchiveConfig(user, spareFolder);
    list = createExportList(copy.getOid(), expCfg);

    zipFileName = archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
    assertTrue(zipFileName.indexOf("zip") > 0); // successfully created a zip file
    archive = parseArchiveFile(zipFileName, spareFolder);

    current = archive.getCurrentVersions();
    assertRawImageLinksAreReplaced(current);

    // now share and check sharee can export OK too
    shareRecordWithGroup(user, group, copy);
    logoutAndLoginAs(u2);

    expCfg = createDefaultArchiveConfig(u2, spareFolder2);

    zipFileName = archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
    assertTrue(zipFileName.indexOf("zip") > 0); // successfully created a zip file
    archive = parseArchiveFile(zipFileName, spareFolder2);
    // check all atachments are exported fine too.
    current = archive.getCurrentVersions();
    assertRawImageLinksAreReplaced(current);
    assertThat(getAssociatedFilesFromDoc("complexDoc2", archive))
        .hasSize(EXPECTED_FILE_COUNT_FOR_COMPLEX_DOC);

    // now we'll create a new, alien user and paste content into their document.
    // exports shouldn't include these attachments
    // RSPAC-757
    User alien = createAndSaveUser(getRandomAlphabeticString("alien"));
    initUser(alien);
    logoutAndLoginAs(alien);
    StructuredDocument alienDoc =
        createBasicDocumentInRootFolderWithText(alien, doc.getFields().get(0).getFieldData());
    rename(alien, alienDoc, "alienDoc");
    expCfg = createDefaultArchiveConfig(alien, spareFolder3);
    list = createExportList(alienDoc.getOid(), expCfg);
    zipFileName = archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
    archive = parseArchiveFile(zipFileName, spareFolder3);
    assertThat(getAssociatedFilesFromDoc("alienDoc", archive)).isEmpty();
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

  private IArchiveModel parseArchiveFile(String zipFileName, File folder) throws IOException {
    File zipFile = new File(folder, zipFileName);

    ImportArchiveReport report = new ImportArchiveReport();
    String expandedFolderPath = ZipUtils.extractZip(zipFile, folder);
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
        Elements rawImageElements =
            getElementsFromHTML(data, FieldParserConstants.IMAGE_THMNAIL_DROPPED_CLASS_NAME);
        for (Element el : rawImageElements) {
          boolean found = false;
          for (File associateFile : docs.getFileList()) {
            if (associateFile.getName().equals(el.attr("src"))) {
              found = true;
            }
          }
          assertTrue(found, "did not find replacement for element" + el);
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
    assertThat(auditMgr.getRevisionsForEntity(EcatComment.class, comment.getComId())).hasSize(1);

    // simulate saving of text field following comment addition.
    addNewCommentItemToExistingComment(
        "comment2", comment.getComId(), doc.getFields().get(0), exporter);
    List<AuditedEntity<EcatComment>> history =
        auditMgr.getRevisionsForEntity(EcatComment.class, comment.getComId());
    assertThat(history).hasSize(2);

    EcatImageAnnotation originalAnnotation =
        addImageAnnotationToField(doc.getFields().get(0), exporter);
    assertThat(
            auditMgr.getRevisionsForEntity(EcatImageAnnotation.class, originalAnnotation.getId()))
        .hasSize(1);

    updateExistingImageAnnotation(
        originalAnnotation.getId(),
        doc.getFields().get(0),
        exporter,
        getTestZwibblerAnnotationString(getRandomName(5)));
    assertThat(
            auditMgr.getRevisionsForEntity(EcatImageAnnotation.class, originalAnnotation.getId()))
        .hasSize(2);

    final int initialChemCount = rsChemElementManager.getAll().size();
    RSChemElement originalChemElement = addChemStructureToField(doc.getFields().get(0), exporter);
    assertThat(auditMgr.getRevisionsForEntity(RSChemElement.class, originalChemElement.getId()))
        .hasSize(1);

    updateExistingChemElement(originalChemElement.getId(), doc.getFields().get(0), exporter);
    assertThat(auditMgr.getRevisionsForEntity(RSChemElement.class, originalChemElement.getId()))
        .hasSize(2);
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
    expCfg.setTopLevelExportFolder(tempExportFolder);
    ImmutableExportRecordList list = createExportList(doc.getOid(), expCfg);
    String zipName = archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
    assertTrue(zipName.indexOf("zip") > 0); // successfully created a zip file

    File zipFile = new File(tempExportFolder, zipName);
    assertThat(zipFile).exists(); // TBD unzip to read contents.

    ArchivalImportConfig iconfig = createDefaultArchiveImportConfig(exporter, tempImportFolder);
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
    assertThat(maths).hasSize(initalMathCount + 2);
    assertThat(chems).hasSize(initialChemCount + 2);

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
        contents
            .getElements(RSMath.class)
            .getElements()
            .get(0)
            .getId()
            .equals(originalMath.getId()),
        "Math ID in text field was not updated");
    assertFalse(
        contents
            .getElements(RSChemElement.class)
            .getElements()
            .get(0)
            .getId()
            .equals(originalChemElement.getId()),
        "Chem ID in text field was not updated");
    assertFalse(
        contents
            .getImageAnnotations()
            .getElements()
            .get(0)
            .getId()
            .equals(originalAnnotation.getId()),
        "Imageannotation ID in text field was not updated");
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
    assertThat(auditMgr.getRevisionsForEntity(RSChemElement.class, basicChemElement.getId()))
        .hasSize(1);

    final int initialChemFileCount = chemistryFileManager.getAll().size();
    EcatChemistryFile chemistryFile = addChemistryFileToGallery("Aminoglutethimide.mol", exporter);
    RSChemElement chemElementWithFile =
        addChemStructureToFieldWithLinkedChemFile(chemistryFile, doc.getFields().get(0), exporter);
    assertThat(auditMgr.getRevisionsForEntity(RSChemElement.class, chemElementWithFile.getId()))
        .hasSize(2);
    // Revisions expected here are 2, chemistry file saved once when added to gallery and updated
    // when chem file added to field attachments
    assertThat(auditMgr.getRevisionsForEntity(EcatChemistryFile.class, chemistryFile.getId()))
        .hasSize(2);

    ArchiveManifest manifest = new ArchiveManifest();
    ArchiveExportConfig expCfg = new ArchiveExportConfig();
    expCfg.setExporter(exporter);
    expCfg.setExportScope(ExportScope.SELECTION);
    expCfg.setTopLevelExportFolder(tempExportFolder);
    ImmutableExportRecordList list = createExportList(doc.getOid(), expCfg);
    String zipName = archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
    assertTrue(zipName.indexOf("zip") > 0); // successfully created a zip file

    File zipFile = new File(tempExportFolder, zipName);
    assertThat(zipFile).exists(); // TBD unzip to read contents.

    ArchivalImportConfig importConfig =
        createDefaultArchiveImportConfig(exporter, tempImportFolder);
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
    assertThat(chemElements).hasSize(initialChemCount + 4);
    assertThat(chemFiles).hasSize(initialChemFileCount + 2);
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
    assertThat(grp.getMembers()).hasSize(2);

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
    ArchiveExportConfig expCfg = createDefaultArchiveConfig(user, tempExportFolder);
    ImmutableExportRecordList list = createExportList(firstDoc.getOid(), expCfg);
    list.getFolderTree().add((new ArchiveModelFactory()).createArchiveFolder(exportedFolder));

    String zipFileName =
        archiveService.exportArchive(manifest, list, expCfg).getExportFile().getName();
    assertTrue(zipFileName.indexOf("zip") > 0); // successfully created a zip file

    // and check archive contents
    IArchiveModel archive = parseArchiveFile(zipFileName, tempExportFolder);

    assertEquals(2, archive.getCurrentDocCount());
    assertThat(archive.findCurrentDocArchiveByName(firstDoc.getName()))
        .as("firstDoc should be part of archive")
        .hasSize(1);
    assertThat(archive.findCurrentDocArchiveByName(targetDocA.getName()))
        .as("targetDocA should be part of archive")
        .hasSize(1);
    assertThat(archive.findCurrentDocArchiveByName(targetDocB.getName()))
        .as("targetDocB should not be in archive")
        .isEmpty();

    // assert relative link from firstDoc to targetDocA, absolute links to folder and notebook
    ArchivalDocumentParserRef firstDocRef =
        archive.findCurrentDocArchiveByName(firstDoc.getName()).get(0);
    ArchivalField firstDocArchivalField = firstDocRef.getArchivalDocument().getListFields().get(0);
    String firstDocExportedData = firstDocArchivalField.getFieldData();
    assertThat(firstDocExportedData).as(firstDocExportedData).contains("href=\"../doc_");
    assertThat(firstDocExportedData)
        .as(firstDocExportedData)
        .contains("http://localhost:8080/globalId/" + folder.getGlobalIdentifier());
    assertThat(firstDocExportedData)
        .as(firstDocExportedData)
        .contains("http://localhost:8080/globalId/" + notebook.getGlobalIdentifier());

    // relative link to exported folder, matching the document name in html export
    assertThat(firstDocExportedData)
        .as(firstDocExportedData)
        .contains("href=\"../" + exportedFolder.getName() + "-" + exportedFolder.getId());

    // absolute URLs pointing to another instance are left unchanged
    assertThat(firstDocExportedData)
        .as(firstDocExportedData)
        .contains("http://localhost:8081/globalId/SD" + firstExternalLinkId);
    assertThat(firstDocExportedData)
        .as(firstDocExportedData)
        .contains("http://localhost:8081/globalId/SD" + secondExternalLinkId);

    // exactly two links (to targetDocA and exportedFolder) in links list inside xml
    assertThat(firstDocArchivalField.getLinkMeta()).hasSize(2);

    // absolute link created from targetDocA to targetDocB, as targetDocB is outside link depth
    ArchivalDocumentParserRef targetDocARef =
        archive.findCurrentDocArchiveByName(targetDocA.getName()).get(0);
    ArchivalField targetDocAArchivalField =
        targetDocARef.getArchivalDocument().getListFields().get(0);
    String targetDocAExportedFieldData = targetDocAArchivalField.getFieldData();
    assertThat(targetDocAExportedFieldData)
        .as(targetDocAExportedFieldData)
        .doesNotContain("href=\"../doc_");
    assertThat(targetDocAExportedFieldData)
        .as(targetDocAExportedFieldData)
        .contains("http://localhost:8080/globalId/" + targetDocB.getGlobalIdentifier());
  }
}
