package com.researchspace.webapp.controller;

import static com.researchspace.webapp.controller.ExportController.IMPORT_FORM_ERROR_ATTR_NAME;
import static com.researchspace.webapp.controller.ExportControllerTest.createExportArchiveConfig;
import static com.researchspace.webapp.controller.ExportControllerTest.createExportArchiveConfigForUser;
import static com.researchspace.webapp.controller.ExportControllerTest.createExportConfig;
import static com.researchspace.webapp.controller.ExportControllerTest.createExportConfigForUser;
import static com.researchspace.webapp.controller.ExportControllerTest.createExportRaidAndElnArchiveConfigSelectionForProjectGroup;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
import com.researchspace.core.util.ISearchResults;
import com.researchspace.export.pdf.ExportToFileConfig;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.data.ArchiveExportNotificationData;
import com.researchspace.model.dtos.export.ExportArchiveDialogConfigDTO;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.RaIDServiceManager;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

public class ExportControllerMVCIT extends MVCTestBase {

  @Autowired private RaIDServiceManager raIDServiceManager;

  @Before
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
            doc.getName());
    String requestContent = mapper.writeValueAsString(exportRequestDTO);
    log.info(requestContent);

    // WHEN / THEN
    exportAndExpectFailure(pi1, exportRequestDTO, requestContent);
  }

  @Test
  public void exportElnByPiTest() throws Exception {
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
            doc.getName());
    String requestContent = mapper.writeValueAsString(exportRequestDTO);
    log.info(requestContent);

    raIDServiceManager.bindRaidToGroupAndSave(pi1, exportRequestDTO.getRaidAssociated());

    // WHEN
    exportAndExpectSuccess(pi1, requestContent);

    // THEN
    Map<String, byte[]> mapFileByName = extractElnExportMapByFilename(pi1);

    // assert entry into manifest.txt
    String actualExportedManifest =
        new String(mapFileByName.get("manifest.txt"), StandardCharsets.UTF_8);
    String raidIdentifierUrl = exportRequestDTO.getRaidAssociated().getRaid().getRaidIdentifier();
    assertTrue(actualExportedManifest.contains("ProjectID:" + raidIdentifierUrl));

    // assert entries into RO-crate
    String actualRoCrate = new String(mapFileByName.get("ro-crate-metadata.json"));
    String projectId = "#project-" + projectGroup.getDisplayName() + "-" + projectGroup.getId();
    actualRoCrate = flattenJson(actualRoCrate);
    assertTrue(actualRoCrate.contains("\"isPartOf\":{\"@id\":\"" + projectId + "\"}"));
    assertTrue(actualRoCrate.contains("\"name\":\"" + projectGroup.getDisplayName() + "\""));
    assertTrue(actualRoCrate.contains("\"url\":\"" + raidIdentifierUrl + "\""));
    assertTrue(actualRoCrate.contains("\"@id\":\"" + projectId + "\"}"));
    assertTrue(actualRoCrate.contains("\"@type\":\"ResearchProject\"}"));
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
    // invalid syntax
    final String SERVER_PATH = "serverFilePath";
    final String SERVER_IMPORT_URL = "/export/importServerArchive";
    final String ARCHIVE_IMPORT_VIEW = "/import/archiveImport";
    mockMvc
        .perform(
            post(SERVER_IMPORT_URL).principal(anyUser::getUsername).param(SERVER_PATH, "invalid*"))
        .andExpect(flash().attributeExists(IMPORT_FORM_ERROR_ATTR_NAME))
        .andExpect(redirectedUrl(ARCHIVE_IMPORT_VIEW));
    // missing file
    mockMvc
        .perform(
            post(SERVER_IMPORT_URL)
                .principal(anyUser::getUsername)
                .param(SERVER_PATH, "/a/b/c/nonexistentZip.zip"))
        .andExpect(flash().attributeExists(IMPORT_FORM_ERROR_ATTR_NAME))
        .andExpect(redirectedUrl(ARCHIVE_IMPORT_VIEW));

    // actually imports
    File realFile = RSpaceTestUtils.getResource("archives/v1-35.zip");
    // copy file to temp dir to avoid problem with parsing release dir path on jenkins
    File realFileTempLocation = Files.createTempFile("archiveTest", "v1-35.zip").toFile();
    FileUtils.copyFile(realFile, realFileTempLocation);
    final String ARCHIVE_REPORT_VIEW = "/import/archiveImportReport";
    mockMvc
        .perform(
            post(SERVER_IMPORT_URL)
                .principal(anyUser::getUsername)
                .param(SERVER_PATH, realFileTempLocation.getAbsolutePath()))
        .andExpect(flash().attributeCount(0))
        .andExpect(redirectedUrl(ARCHIVE_REPORT_VIEW));
  }

  private User updatePreferenceToLetter(User u) {
    return userMgr.setPreference(Preference.UI_PDF_PAGE_SIZE, "LETTER", u.getUsername()).getUser();
  }

  private MvcResult submitAndAssertGetPageSize(String size, User u) throws Exception {
    return mockMvc
        .perform(get("/export/ajax/defaultPDFConfig").principal(u::getUsername))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.pageSize", is(size)))
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
        .andExpect(content().string(messages.getMessage("pdfArchiving.submission.successMsg")));
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
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    messages.getMessage(
                        "workspace.export.msgFailure",
                        new String[] {
                          "Export",
                          "The submitted RaID \""
                              + exportRequestDTO.getRaidAssociated().getRaid().getRaidIdentifier()
                              + "\" "
                              + "is not currently associated to the projectId \""
                              + exportRequestDTO.getRaidAssociated().getProjectGroupId()
                              + "\""
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
