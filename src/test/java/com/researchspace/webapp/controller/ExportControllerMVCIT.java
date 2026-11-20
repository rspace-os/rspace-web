package com.researchspace.webapp.controller;

import static com.researchspace.core.util.progress.ProgressMonitor.NULL_MONITOR;
import static com.researchspace.webapp.controller.ExportController.IMPORT_FORM_ERROR_ATTR_NAME;
import static com.researchspace.webapp.controller.ExportControllerTest.createExportArchiveConfig;
import static com.researchspace.webapp.controller.ExportControllerTest.createExportArchiveConfigForUser;
import static com.researchspace.webapp.controller.ExportControllerTest.createExportConfig;
import static com.researchspace.webapp.controller.ExportControllerTest.createExportConfigForUser;
import static com.researchspace.webapp.controller.ExportControllerTest.createExportRaidAndElnArchiveConfigSelectionForProjectGroup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.Constants;
import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchiveManifest;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.export.pdf.ExportToFileConfig;
import com.researchspace.model.EcatImage;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.data.ArchiveExportNotificationData;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.model.dtos.export.ExportArchiveDialogConfigDTO;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.properties.IMutablePropertyHolder;
import com.researchspace.service.RaIDServiceManager;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.ImportArchiveReport;
import com.researchspace.service.archive.ImportStrategy;
import com.researchspace.service.archive.PostArchiveCompletion;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.MvcResult;

public class ExportControllerMVCIT extends MVCTestBase {

  @Autowired private RaIDServiceManager raIDServiceManager;
  @Autowired private IMutablePropertyHolder propertyHolder;
  @Autowired private ExportImport exportImportManager;

  @Autowired
  @Qualifier("importUsersAndRecords")
  private ImportStrategy importStrategy;

  @Autowired
  @Qualifier("standardPostExportCompletionImpl")
  private PostArchiveCompletion postExport;

  @BeforeEach
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void nonExistentArchiveHandledTest() throws Exception {
    logoutAndLoginAsSysAdmin();
    mockMvc
        .perform(
            get("/export/ajax/downloadArchive/{zipname}", "nonexistent.zip")
                .principal(() -> SYS_ADMIN_UNAME))
        .andExpect(status().isOk());
  }

  @Test
  void documentXmlRoundTripImportsNineTiffs(@TempDir Path workDir) throws Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument source = createBasicDocumentInRootFolderWithText(user, "Nine TIFFs");
    List<Long> sourceIds = new ArrayList<>();
    for (int i = 0; i < 9; i++) {
      sourceIds.add(addTiff(source, user, "image-" + i + ".tiff").getId());
    }

    Elements images = documentImages(importedDocument(roundTrip(user, workDir, source)));
    assertThat(images).hasSize(9);
    assertThat(images.stream().map(this::imageId))
        .doesNotHaveDuplicates()
        .doesNotContainAnyElementsOf(sourceIds);
    for (int i = 0; i < images.size(); i++) {
      assertTiffImage(images.get(i), "image-" + i + ".tiff", user);
    }
  }

  @Test
  void documentXmlRoundTripReusesRepeatedTiff(@TempDir Path workDir) throws Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument source = createBasicDocumentInRootFolderWithText(user, "Repeated TIFF");
    EcatImage tiff = addTiff(source, user, "repeated.tiff");
    addImageToField(source.getFields().get(0), user, tiff);
    long imageCount = getImageCount();

    Elements images = documentImages(importedDocument(roundTrip(user, workDir, source)));
    assertThat(images).hasSize(2);
    assertEquals(imageId(images.get(0)), imageId(images.get(1)));
    assertEquals(imageCount + 1, getImageCount());
    assertTiffImage(images.get(0), tiff.getName(), user);
  }

  @Test
  void documentXmlRoundTripPreservesPngAlongsideTiff(@TempDir Path workDir) throws Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument source = createBasicDocumentInRootFolderWithText(user, "PNG and TIFF");
    EcatImage png = addImageToField(source.getFields().get(0), user);
    EcatImage tiff = addTiff(source, user, "original.tiff");

    Elements images = documentImages(importedDocument(roundTrip(user, workDir, source)));
    assertThat(images).hasSize(2);
    EcatImage importedPng = mediaMgr.getImage(imageId(images.get(0)), user, false);
    assertEquals(png.getName(), importedPng.getName());
    assertEquals("png", importedPng.getExtension());
    assertArrayEquals(downloadGalleryFile(png.getId()), downloadGalleryFile(importedPng.getId()));
    assertTiffImage(images.get(1), tiff.getName(), user);
  }

  @Test
  void documentXmlImportsPngWithExtensionlessLinkFile(@TempDir Path workDir) throws Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument source = createBasicDocumentInRootFolderWithText(user, "Extensionless link");
    EcatImage png = addImageToField(source.getFields().get(0), user);
    ExportSelection selection =
        ExportSelection.createRecordsExportSelection(
            new Long[] {source.getId()}, new String[] {RecordType.NORMAL.toString()});
    File archive = exportSelection(user, workDir, selection);

    String archivedName = FilenameUtils.getName(png.getFileUri());
    try (var zip = FileSystems.newFileSystem(archive.toPath())) {
      Path archivedPng;
      try (var entries = Files.walk(zip.getPath("/"))) {
        archivedPng = entries.filter(path -> path.endsWith(archivedName)).findFirst().orElseThrow();
      }
      Files.move(archivedPng, archivedPng.resolveSibling("payload"));
      Path recordFolder = archivedPng.getParent();
      Path documentXml = recordFolder.resolve(recordFolder.getFileName() + ".xml");
      String xml = Files.readString(documentXml);
      assertThat(xml).contains("<linkFile>" + archivedName + "</linkFile>");
      Files.writeString(documentXml, xml.replace(archivedName, "payload"));

      // The edited fixture needs its own archive ID for checksum validation.
      Path manifest = recordFolder.resolveSibling(ExportImport.EXPORT_MANIFEST);
      String idKey = ArchiveManifest.ARCHIVE_ID + ":";
      Files.writeString(
          manifest,
          Files.readString(manifest)
              .replaceFirst("(?m)^" + idKey + ".*", idKey + UUID.randomUUID()));
    }

    Elements images = documentImages(importedDocument(importSelection(user, workDir, archive)));
    assertThat(images).hasSize(1);
    Element image = images.get(0);
    EcatImage imported = mediaMgr.getImage(imageId(image), user, false);
    assertEquals(png.getName(), imported.getName());
    assertThat(imported.getFileName()).matches("Picture1_\\d+\\.png");
    assertPngRenders(image, user);
  }

  @Test
  void documentXmlRoundTripPreservesTiffOnSecondImport(@TempDir Path workDir) throws Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument source = createBasicDocumentInRootFolderWithText(user, "Re-exported TIFF");
    EcatImage tiff = addTiff(source, user, "re-exported.tiff");

    StructuredDocument firstImport = importedDocument(roundTrip(user, workDir, source));
    Elements images = documentImages(importedDocument(roundTrip(user, workDir, firstImport)));
    assertThat(images).hasSize(1);
    assertNotEquals(imageId(documentImages(firstImport).get(0)), imageId(images.get(0)));
    assertTiffImage(images.get(0), tiff.getName(), user);
  }

  @ParameterizedTest
  @ValueSource(strings = {"scan.png", "Microscope slide"})
  void documentXmlRoundTripPreservesRenamedTiff(String displayName, @TempDir Path workDir)
      throws Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument source = createBasicDocumentInRootFolderWithText(user, "Renamed TIFF");
    EcatImage tiff = addTiff(source, user, "original.tiff");
    assertTrue(recordMgr.renameRecord(displayName, tiff.getId(), user));

    Elements images = documentImages(importedDocument(roundTrip(user, workDir, source)));
    assertThat(images).hasSize(1);
    Element image = images.get(0);
    assertTiffImage(image, displayName, user);
    assertArrayEquals(downloadGalleryFile(tiff.getId()), downloadGalleryFile(imageId(image)));
  }

  @Test
  void mixedXmlSelectionReusesSelectedTiff(@TempDir Path workDir) throws Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument source = createBasicDocumentInRootFolderWithText(user, "Selected TIFF");
    EcatImage tiff = addTiff(source, user, "selected.tiff");
    long imageCount = getImageCount();

    ExportSelection selection =
        ExportSelection.createRecordsExportSelection(
            new Long[] {source.getId(), tiff.getId()},
            new String[] {RecordType.NORMAL.toString(), RecordType.MEDIA_FILE.toString()});
    ImportArchiveReport report = roundTrip(user, workDir, selection);
    Elements images = documentImages(importedDocument(report));
    assertThat(images).hasSize(1);
    assertEquals(imageCount + 1, getImageCount());
    assertNotEquals(tiff.getId().longValue(), imageId(images.get(0)));
    assertTiffImage(images.get(0), tiff.getName(), user);
  }

  private EcatImage addTiff(StructuredDocument document, User user, String name)
      throws IOException {
    try (var input = getTestResourceFileStream("Picture1.tiff")) {
      EcatImage image = mediaMgr.saveNewImage(name, input, user, null);
      return addImageToField(document.getFields().get(0), user, image);
    }
  }

  private ImportArchiveReport roundTrip(User user, Path workDir, StructuredDocument document)
      throws Exception {
    return roundTrip(
        user,
        workDir,
        ExportSelection.createRecordsExportSelection(
            new Long[] {document.getId()}, new String[] {RecordType.NORMAL.toString()}));
  }

  private ImportArchiveReport roundTrip(User user, Path workDir, ExportSelection selection)
      throws Exception {
    return importSelection(user, workDir, exportSelection(user, workDir, selection));
  }

  private File exportSelection(User user, Path workDir, ExportSelection selection)
      throws Exception {
    ArchiveExportConfig config =
        createDefaultArchiveConfig(user, Files.createTempDirectory(workDir, "export").toFile());
    config.setArchiveType(ArchiveExportConfig.XML);
    return exportImportManager
        .asyncExportSelectionToArchive(
            selection, config, user, URI.create("http://localhost"), postExport)
        .get()
        .getExportFile();
  }

  private ImportArchiveReport importSelection(User user, Path workDir, File archive)
      throws Exception {
    ArchivalImportConfig importConfig =
        createDefaultArchiveImportConfig(
            user, Files.createTempDirectory(workDir, "import").toFile());
    ImportArchiveReport report =
        exportImportManager.importArchive(
            fileToMultipartfile(archive.getName(), archive),
            user.getUsername(),
            importConfig,
            NULL_MONITOR,
            importStrategy::doImport);
    assertTrue(report.isSuccessful(), () -> report.getErrorList().getErrorMessages().toString());
    return report;
  }

  private StructuredDocument importedDocument(ImportArchiveReport report) {
    assertThat(report.getImportedRecords()).hasSize(1);
    return report.getImportedRecords().iterator().next().asStrucDoc();
  }

  private Elements documentImages(StructuredDocument document) {
    return Jsoup.parse(document.getFields().get(0).getFieldData()).select("img");
  }

  private long imageId(Element image) {
    return Long.parseLong(image.id().split("-")[1]);
  }

  private byte[] downloadGalleryFile(long id) throws Exception {
    return mockMvc
        .perform(get("/Streamfile/{id}", id))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsByteArray();
  }

  private void assertTiffImage(Element image, String filename, User user) throws Exception {
    EcatImage imported = mediaMgr.getImage(imageId(image), user, false);
    assertEquals(filename, imported.getName());
    assertEquals("tiff", imported.getExtension());
    assertPngRenders(image, user);
  }

  private void assertPngRenders(Element image, User user) throws Exception {
    byte[] bytes =
        mockMvc
            .perform(get(image.attr("src")).principal(() -> user.getUsername()))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertNotNull(ImageIO.read(new ByteArrayInputStream(bytes)));
  }

  @Test
  public void exportMyStuffTest() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    createBasicDocumentInRootFolderWithText(anyUser, "any text");

    ObjectMapper mapper = new ObjectMapper();
    String requestContent =
        mapper.writeValueAsString(createExportArchiveConfigForUser(anyUser.getUsername()));
    log.info(requestContent);
    exportAndExpectSuccess(anyUser, requestContent);
  }

  @Test
  public void getExportAllTagsForArchiveUserDocsTest() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    RSForm aUserForm = createAnyForm(anyUser);
    StructuredDocument exporteeDoc = createBasicDocumentInRootFolderWithText(anyUser, "any text");
    exporteeDoc.setTagMetaData("DOCTAGMETA");
    Folder targetFolder =
        doInTransaction(() -> createFolder(getRootFolderForUser(anyUser), anyUser, "target"));
    targetFolder.setTagMetaData("FOLDERTAGMETA");
    folderMgr.save(targetFolder, anyUser);
    StructuredDocument exporteeSubFolderDoc =
        createDocumentInFolder(targetFolder, aUserForm, anyUser);
    exporteeSubFolderDoc.setTagMetaData("SUBFOLDER_DOCTAGMETA");
    recordMgr.save(exporteeDoc, anyUser);
    recordMgr.save(exporteeSubFolderDoc, anyUser);
    ObjectMapper mapper = new ObjectMapper();
    String requestContent =
        mapper.writeValueAsString(createExportArchiveConfigForUser(anyUser.getUsername()));
    log.info(requestContent);
    String exportTags = retrieveExportTagsForArchiveAndExpectSuccess(anyUser, requestContent);
    assertEquals("DOCTAGMETA,FOLDERTAGMETA,SUBFOLDER_DOCTAGMETA", exportTags);
  }

  @Test
  public void getExportAllTagsForPDFUserDocsTest() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    RSForm aUserForm = createAnyForm(anyUser);
    StructuredDocument exporteeDoc = createBasicDocumentInRootFolderWithText(anyUser, "any text");
    exporteeDoc.setTagMetaData("DOCTAGMETA");
    Folder targetFolder =
        doInTransaction(() -> createFolder(getRootFolderForUser(anyUser), anyUser, "target"));
    targetFolder.setTagMetaData("FOLDERTAGMETA");
    folderMgr.save(targetFolder, anyUser);
    StructuredDocument exporteeSubFolderDoc =
        createDocumentInFolder(targetFolder, aUserForm, anyUser);
    exporteeSubFolderDoc.setTagMetaData("SUBFOLDER_DOCTAGMETA");
    recordMgr.save(exporteeDoc, anyUser);
    recordMgr.save(exporteeSubFolderDoc, anyUser);
    ObjectMapper mapper = new ObjectMapper();
    String requestContent =
        mapper.writeValueAsString(createExportConfigForUser(anyUser.getUsername()));
    log.info(requestContent);
    String exportTags = retrieveExportTagsForPDFAndExpectSuccess(anyUser, requestContent);
    assertEquals("DOCTAGMETA,FOLDERTAGMETA,SUBFOLDER_DOCTAGMETA", exportTags);
  }

  @Test
  public void getExportSelectedDocsTagsForArchiveTest() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    StructuredDocument exporteeDoc = createBasicDocumentInRootFolderWithText(anyUser, "any text");
    exporteeDoc.setTagMetaData("DOCTAGMETA");
    RSForm aUserForm = createAnyForm(anyUser);
    recordMgr.save(exporteeDoc, anyUser);
    Folder targetFolder =
        doInTransaction(() -> createFolder(getRootFolderForUser(anyUser), anyUser, "target"));
    targetFolder.setTagMetaData("FOLDERTAGMETA");
    folderMgr.save(targetFolder, anyUser);
    StructuredDocument exporteeSubFolderDoc =
        createDocumentInFolder(targetFolder, aUserForm, anyUser);
    exporteeSubFolderDoc.setTagMetaData("SUBFOLDER_DOCTAGMETA");
    recordMgr.save(exporteeSubFolderDoc, anyUser);
    final Long[] exportIds = new Long[] {exporteeDoc.getId(), targetFolder.getId()};
    final String[] exportNames = {exporteeDoc.getName(), targetFolder.getName()};
    final String[] exportTypes = {"NORMAL", "FOLDER"};
    ObjectMapper mapper = new ObjectMapper();
    String requestContent =
        mapper.writeValueAsString(
            createExportArchiveConfig(exportIds, exportNames, exportTypes, false));
    log.info(requestContent);
    String exportTags = retrieveExportTagsForArchiveAndExpectSuccess(anyUser, requestContent);
    assertEquals("DOCTAGMETA,FOLDERTAGMETA,SUBFOLDER_DOCTAGMETA", exportTags);
  }

  @Test
  public void getExportSelectedDocsTagsForPDFTest() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    StructuredDocument exporteeDoc = createBasicDocumentInRootFolderWithText(anyUser, "any text");
    exporteeDoc.setTagMetaData("DOCTAGMETA");
    RSForm aUserForm = createAnyForm(anyUser);
    recordMgr.save(exporteeDoc, anyUser);
    Folder targetFolder =
        doInTransaction(() -> createFolder(getRootFolderForUser(anyUser), anyUser, "target"));
    targetFolder.setTagMetaData("FOLDERTAGMETA");
    folderMgr.save(targetFolder, anyUser);
    StructuredDocument exporteeSubFolderDoc =
        createDocumentInFolder(targetFolder, aUserForm, anyUser);
    exporteeSubFolderDoc.setTagMetaData("SUBFOLDER_DOCTAGMETA");
    recordMgr.save(exporteeSubFolderDoc, anyUser);
    final Long[] exportIds = new Long[] {exporteeDoc.getId(), targetFolder.getId()};
    final String[] exportNames = {exporteeDoc.getName(), targetFolder.getName()};
    final String[] exportTypes = {"NORMAL", "FOLDER"};
    ObjectMapper mapper = new ObjectMapper();
    String requestContent =
        mapper.writeValueAsString(
            createExportConfig(exportIds, exportNames, exportTypes, new ExportToFileConfig()));
    log.info(requestContent);
    String exportTags = retrieveExportTagsForPDFAndExpectSuccess(anyUser, requestContent);
    assertEquals("DOCTAGMETA,FOLDERTAGMETA,SUBFOLDER_DOCTAGMETA", exportTags);
  }

  @Test
  public void getExportTagsSelectedAndLinkedDocsForArchiveTest() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    StructuredDocument exporteeDoc = createBasicDocumentInRootFolderWithText(anyUser, "any text");
    exporteeDoc.setTagMetaData("DOCTAGMETA");
    recordMgr.save(exporteeDoc, anyUser);
    StructuredDocument linkedTo = createBasicDocumentInRootFolderWithText(anyUser, "linked-doc");
    linkedTo.setTagMetaData("LINKEDDOCTAGMETA");
    recordMgr.save(linkedTo, anyUser);
    addLinkToOtherRecord(exporteeDoc.getFields().get(0), linkedTo, false);
    final Long[] exportIds = new Long[] {exporteeDoc.getId()};
    final String[] exportNames = {exporteeDoc.getName()};
    final String[] exportTypes = {"NORMAL"};
    ObjectMapper mapper = new ObjectMapper();
    String requestContent =
        mapper.writeValueAsString(
            createExportArchiveConfig(exportIds, exportNames, exportTypes, false));
    log.info(requestContent);
    String exportTags = retrieveExportTagsForArchiveAndExpectSuccess(anyUser, requestContent);
    assertEquals("DOCTAGMETA,LINKEDDOCTAGMETA", exportTags);
  }

  @Test
  public void exportHtmlBySysadminTest() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    createBasicDocumentInRootFolderWithText(anyUser, "any text");
    User syadmin = logoutAndLoginAsSysAdmin();
    ObjectMapper mapper = new ObjectMapper();
    String requestContent =
        mapper.writeValueAsString(createExportArchiveConfigForUser(anyUser.getUsername()));
    log.info(requestContent);
    exportAndExpectSuccess(syadmin, requestContent);
  }

  @Test
  public void exportFailureCauseNoRaidAssociationElnByPiTest() throws Exception {
    // GIVEN
    User pi1 = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initUser(pi1);
    Group projectGroup = createGroupForUsers(pi1, pi1.getUsername(), "", pi1);

    logoutAndLoginAs(pi1);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(pi1, "any text");
    User toAdd = createAndSaveUser(getRandomAlphabeticString("other"));
    initUser(toAdd);
    grpMgr.addUserToGroup(pi1.getUsername(), projectGroup.getId(), RoleInGroup.DEFAULT);

    ObjectMapper mapper = new ObjectMapper();
    ExportArchiveDialogConfigDTO exportRequestDTO =
        createExportRaidAndElnArchiveConfigSelectionForProjectGroup(
            projectGroup.getId(),
            projectGroup.getDisplayName(),
            pi1.getUsername(),
            doc.getId(),
            doc.getName(),
            "NORMAL");
    exportRequestDTO.getRepositoryConfig().setExportToRaid(true);
    String requestContent = mapper.writeValueAsString(exportRequestDTO);
    log.info(requestContent);

    // WHEN / THEN
    exportAndExpectFailure(pi1, exportRequestDTO, requestContent);
  }

  @Test
  public void exportElnWithoutRaidOnManifestByPiTest() throws Exception {
    // GIVEN
    User pi1 = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initUser(pi1);
    Group projectGroup = createGroupForUsers(pi1, pi1.getUsername(), "", pi1);
    Folder rootSharedFolder = folderMgr.getFolder(projectGroup.getCommunalGroupFolderId(), pi1);
    logoutAndLoginAs(pi1);
    StructuredDocument docPartOfProject =
        createBasicDocumentInRootFolderWithText(pi1, "any text in project");
    sharingHandler.shareIntoSharedFolderOrNotebook(
        pi1, rootSharedFolder, docPartOfProject.getId(), null);
    StructuredDocument docNotPartOfProject =
        createBasicDocumentInRootFolderWithText(pi1, "any text in root");
    User toAdd = createAndSaveUser(getRandomAlphabeticString("other"));
    initUser(toAdd);
    grpMgr.addUserToGroup(pi1.getUsername(), projectGroup.getId(), RoleInGroup.DEFAULT);

    ObjectMapper mapper = new ObjectMapper();
    ExportArchiveDialogConfigDTO exportRequestDTO =
        createExportRaidAndElnArchiveConfigSelectionForProjectGroup(
            projectGroup.getId(),
            projectGroup.getDisplayName(),
            pi1.getUsername(),
            new Long[] {docPartOfProject.getId(), docNotPartOfProject.getId()},
            new String[] {docPartOfProject.getName(), docNotPartOfProject.getName()},
            new String[] {"NORMAL", "NORMAL"});
    String requestContent = mapper.writeValueAsString(exportRequestDTO);
    log.info(requestContent);

    try {
      raIDServiceManager.bindRaidToGroupAndSave(pi1, exportRequestDTO.getRaidAssociated());

      // WHEN
      exportAndExpectSuccess(pi1, requestContent);

      // THEN
      Map<String, byte[]> mapFileByName = extractElnExportMapByFilename(pi1);

      // assert entry into manifest.txt
      String actualExportedManifest =
          new String(mapFileByName.get("manifest.txt"), StandardCharsets.UTF_8);
      String raidIdentifierUrl = exportRequestDTO.getRaidAssociated().getRaid().getRaidIdentifier();
      assertThat(actualExportedManifest)
          .as("RAiD has been found inside the manifest.txt, but it should not have")
          .doesNotContain("ProjectID:" + raidIdentifierUrl);

      // assert entries into RO-crate
      String actualRoCrate = new String(mapFileByName.get("ro-crate-metadata.json"));
      String projectId = "#project-" + projectGroup.getDisplayName() + "-" + projectGroup.getId();
      actualRoCrate = flattenJson(actualRoCrate);
      assertThat(actualRoCrate).doesNotContain("\"isPartOf\":{\"@id\":\"" + projectId + "\"}");
      assertThat(actualRoCrate)
          .doesNotContain("\"name\":\"" + projectGroup.getDisplayName() + "\"");
      assertThat(actualRoCrate).doesNotContain("\"url\":\"" + raidIdentifierUrl + "\"");
      assertThat(actualRoCrate).doesNotContain("\"@id\":\"" + projectId + "\"}");
      assertThat(actualRoCrate).doesNotContain("\"@type\":\"ResearchProject\"}");
    } finally {
      raIDServiceManager.unbindRaidFromGroupAndSave(pi1, projectGroup.getId());
    }
  }

  @Test
  public void exportElnWithRaidOnManifestByPiTest() throws Exception {
    // GIVEN
    User pi1 = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initUser(pi1);
    logoutAndLoginAs(pi1);
    Group projectGroup = createGroupForUsers(pi1, pi1.getUsername(), "", pi1);
    Folder rootSharedFolder = folderMgr.getFolder(projectGroup.getCommunalGroupFolderId(), pi1);
    Folder sharedSubFolder = createSubFolder(rootSharedFolder, "SharedSubFolder", pi1);

    StructuredDocument doc = createBasicDocumentInRootFolderWithText(pi1, "any text in project");
    sharingHandler.shareIntoSharedFolderOrNotebook(pi1, sharedSubFolder, doc.getId(), null);

    User toAdd = createAndSaveUser(getRandomAlphabeticString("other"));
    initUser(toAdd);
    grpMgr.addUserToGroup(pi1.getUsername(), projectGroup.getId(), RoleInGroup.DEFAULT);

    ObjectMapper mapper = new ObjectMapper();
    ExportArchiveDialogConfigDTO exportRequestDTO =
        createExportRaidAndElnArchiveConfigSelectionForProjectGroup(
            projectGroup.getId(),
            projectGroup.getDisplayName(),
            pi1.getUsername(),
            sharedSubFolder.getId(),
            sharedSubFolder.getName(),
            "FOLDER:SHARED_FOLDER");
    String requestContent = mapper.writeValueAsString(exportRequestDTO);
    log.info(requestContent);
    try {
      raIDServiceManager.bindRaidToGroupAndSave(pi1, exportRequestDTO.getRaidAssociated());

      // WHEN
      exportAndExpectSuccess(pi1, requestContent);

      // THEN
      Map<String, byte[]> mapFileByName = extractElnExportMapByFilename(pi1);

      // assert entry into manifest.txt
      String actualExportedManifest =
          new String(mapFileByName.get("manifest.txt"), StandardCharsets.UTF_8);
      String raidIdentifierUrl = exportRequestDTO.getRaidAssociated().getRaid().getRaidIdentifier();
      assertThat(actualExportedManifest)
          .as("RAiD not fount inside the manifest.txt")
          .contains("ProjectID:" + raidIdentifierUrl);

      // assert entries into RO-crate
      String actualRoCrate = new String(mapFileByName.get("ro-crate-metadata.json"));
      String projectId = "#project-" + projectGroup.getDisplayName() + "-" + projectGroup.getId();
      actualRoCrate = flattenJson(actualRoCrate);
      assertThat(actualRoCrate).contains("\"isPartOf\":[{\"@id\":\"" + projectId + "\"}");
      assertThat(actualRoCrate).contains("\"name\":\"" + projectGroup.getDisplayName() + "\"");
      assertThat(actualRoCrate).contains("\"url\":\"" + raidIdentifierUrl + "\"");
      assertThat(actualRoCrate).contains("\"@id\":\"" + projectId + "\"");
      assertThat(actualRoCrate).contains("\"@type\":\"ResearchProject\"");
    } finally {
      raIDServiceManager.unbindRaidFromGroupAndSave(pi1, projectGroup.getId());
    }
  }

  @Test
  public void getPDFConfig() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    MvcResult result = submitAndAssertGetPageSize("A4", anyUser);
    assertNull(result.getResolvedException());

    anyUser = updatePreferenceToLetter(anyUser);

    result = submitAndAssertGetPageSize("LETTER", anyUser);
    assertNull(result.getResolvedException());
  }

  @Test
  public void importPathValid() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    final String SERVER_PATH = "serverFilePath";
    final String SERVER_IMPORT_URL = "/export/importServerArchive";
    File realFile = RSpaceTestUtils.getResource("archives/v1-35.zip");
    boolean wasEnabled = propertyHolder.isImportArchiveFromServerEnabled();
    String previousFolder = propertyHolder.getImportArchiveFromServerFolder();
    Path importFolder = Files.createTempDirectory("serverImport");
    try {
      propertyHolder.setImportArchiveFromServerEnabled("true");
      propertyHolder.setImportArchiveFromServerFolder(importFolder.toString());
      // a real archive at a path the ServerPath rules refuse, so this fails if @Valid stops
      // being applied to the request
      File refused = importFolder.resolve("bad;name.zip").toFile();
      FileUtils.copyFile(realFile, refused);
      mockMvc
          .perform(
              post(SERVER_IMPORT_URL)
                  .principal(anyUser::getUsername)
                  .param(SERVER_PATH, refused.getAbsolutePath()))
          .andExpect(flash().attributeExists(IMPORT_FORM_ERROR_ATTR_NAME))
          .andExpect(redirectedUrl("/import/archiveImport"));

      File inside = importFolder.resolve("v1-35.zip").toFile();
      FileUtils.copyFile(realFile, inside);
      mockMvc
          .perform(
              post(SERVER_IMPORT_URL)
                  .principal(anyUser::getUsername)
                  .param(SERVER_PATH, inside.getAbsolutePath()))
          .andExpect(flash().attributeCount(0))
          .andExpect(redirectedUrl("/import/archiveImportReport"));
    } finally {
      propertyHolder.setImportArchiveFromServerEnabled(Boolean.toString(wasEnabled));
      propertyHolder.setImportArchiveFromServerFolder(previousFolder);
      FileUtils.deleteQuietly(importFolder.toFile());
    }
  }

  private User updatePreferenceToLetter(User u) {
    return userMgr.setPreference(Preference.UI_PDF_PAGE_SIZE, "LETTER", u.getUsername()).getUser();
  }

  private MvcResult submitAndAssertGetPageSize(String size, User u) throws Exception {
    return mockMvc
        .perform(get("/export/ajax/defaultPDFConfig").principal(u::getUsername))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.pageSize").value(size))
        .andReturn();
  }

  private Map<String, byte[]> extractElnExportMapByFilename(User user) throws Exception {
    PaginationCriteria<CommunicationTarget> paginationCriteria =
        PaginationCriteria.createDefaultForClass(CommunicationTarget.class);
    ISearchResults<Notification> newNotifications =
        communicationMgr.getNewNotificationsForUser(user.getUsername(), paginationCriteria);
    Notification lastNotification = newNotifications.getFirstResult();
    String downloadLink =
        ((ArchiveExportNotificationData) lastNotification.getNotificationDataObject())
            .getDownloadLink();
    String filename = downloadLink.split("/")[downloadLink.split("/").length - 1];

    byte[] downloadedElnBytes =
        retrieveExportedFile(user, downloadLink).getResponse().getContentAsByteArray();

    return createFilesMapFromZip(filename, downloadedElnBytes);
  }

  private MvcResult retrieveExportedFile(User u, String downloadLink) throws Exception {
    return mockMvc
        .perform(get(downloadLink.split("8080")[1]).principal(u::getUsername))
        .andExpect(status().isOk())
        .andReturn();
  }

  private void exportAndExpectSuccess(User u, String requestContent) throws Exception {
    mockMvc
        .perform(
            post("/export/ajax/exportArchive")
                .principal(u::getUsername)
                .content(requestContent)
                .contentType(APPLICATION_JSON_UTF8))
        .andExpect(status().isOk())
        .andExpect(
            content().string(messages.getMessage("importExport.pdfArchiving.submission.success")));
  }

  private void exportAndExpectFailure(
      User u, ExportArchiveDialogConfigDTO exportRequestDTO, String requestContent)
      throws Exception {
    mockMvc
        .perform(
            post("/export/ajax/exportArchive")
                .principal(u::getUsername)
                .content(requestContent)
                .contentType(APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest())
        .andExpect(
            content()
                .string(
                    messages.getMessage(
                        "workspace.export.failureMessage",
                        new Object[] {
                          messages.getMessage("common:actions.export"),
                          messages.getMessage(
                              "workspace.export.raidAssociationMissing",
                              new Object[] {exportRequestDTO.getProjectGroupId()})
                        })));
  }

  private String retrieveExportTagsForPDFAndExpectSuccess(User u, String requestContent)
      throws Exception {
    return retrieveExportTagsAndExpectSuccess(
        u, requestContent, "/export/ajax/exportRecordTagsPdfsAndDocs");
  }

  private String retrieveExportTagsForArchiveAndExpectSuccess(User u, String requestContent)
      throws Exception {
    return retrieveExportTagsAndExpectSuccess(
        u, requestContent, "/export/ajax/exportRecordTagsArchive");
  }

  private String retrieveExportTagsAndExpectSuccess(User u, String requestContent, String url)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post(url)
                    .principal(u::getUsername)
                    .content(requestContent)
                    .contentType(APPLICATION_JSON_UTF8))
            .andExpect(status().isOk())
            .andReturn();
    String[] results = getFromJsonAjaxReturnObject(result, String[].class);
    return String.join(",", results);
  }

  private static String flattenJson(String actualRoCrate) {
    return actualRoCrate.replaceAll(" ", "").replaceAll("\n", "").replaceAll("\t", "");
  }

  private static Map<String, byte[]> createFilesMapFromZip(String filename, byte[] zipFileBytes)
      throws IOException {
    Map<String, byte[]> result = new LinkedHashMap<>();
    File fileFromBytes = null;
    try {
      Path zipDirectory = Files.createTempDirectory(filename + "_temp_");
      fileFromBytes = new File(zipDirectory + "/" + filename);
      FileUtils.writeByteArrayToFile(fileFromBytes, zipFileBytes);

      try (ZipFile zipFile = new ZipFile(fileFromBytes)) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          if (!entry.isDirectory()) {
            result.put(
                entry.getName().split("/")[entry.getName().split("/").length - 1],
                zipFile.getInputStream(entry).readAllBytes());
          }
        }
      }
    } finally {
      if (fileFromBytes != null) {
        FileUtils.delete(fileFromBytes);
      }
    }
    return result;
  }
}
