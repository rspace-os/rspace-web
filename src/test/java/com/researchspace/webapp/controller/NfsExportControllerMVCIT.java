package com.researchspace.webapp.controller;

import static com.researchspace.webapp.controller.ExportControllerTest.createExportArchiveConfigForUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsExportPlan;
import com.researchspace.netfiles.NfsFileDetails;
import com.researchspace.netfiles.NfsFolderDetails;
import com.researchspace.netfiles.NfsTarget;
import com.researchspace.service.impl.NfsExportManagerImpl;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

public class NfsExportControllerMVCIT extends MVCTestBase {

  @Autowired private NfsExportController controller;

  @Autowired private com.researchspace.service.MessageSourceUtils messageSource;

  @BeforeEach
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void getQuickAndFullPlanForUserNfsExport() throws Exception {
    User u = createAndSaveUser(getRandomAlphabeticString("nfsTest"));
    initUser(u);
    logoutAndLoginAs(u);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(u, "any text");
    NfsElement nfsFileElem = addNfsFileStoreAndLink(doc.getFields().get(0), u, "/test.txt");
    NfsFileStore fileStore = nfsMgr.getNfsFileStore(nfsFileElem.getFileStoreId());
    NfsElement nfsFolderElem =
        addNfsFileStoreLink(doc.getFields().get(0), u, fileStore.getId(), "/otherDocs", true);

    ObjectMapper mapper = new ObjectMapper();
    String requestContent =
        mapper.writeValueAsString(createExportArchiveConfigForUser(u.getUsername()));
    log.info(requestContent);

    MockHttpSession mockSession = new MockHttpSession();
    MvcResult quickPlanResult =
        mockMvc
            .perform(
                post("/nfsExport/ajax/createQuickExportPlan")
                    .principal(new MockPrincipal(u.getUsername()))
                    .session(mockSession)
                    .content(requestContent)
                    .contentType(MediaType.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk())
            .andReturn();

    NfsExportPlan quickPlan = getFromJsonResponseBody(quickPlanResult, NfsExportPlan.class);
    assertNotNull(quickPlan);
    assertThat(quickPlan.getFoundFileSystems()).hasSize(1);
    assertEquals(1, quickPlan.countFileSystemsRequiringLogin());
    assertThat(quickPlan.getFoundNfsLinks()).hasSize(2);
    assertThat(quickPlan.getFoundFileSystems().get(0).getFoundNfsLinks()).hasSize(2);
    assertThat(quickPlan.getCheckedNfsLinks()).isEmpty();
    assertThat(quickPlan.getFoundFileSystems().get(0).getCheckedNfsLinks()).isEmpty();

    // now let's mock the nfs client as if it was logged in, and ask for full scan
    NfsController mockNfsController = Mockito.mock(NfsController.class);
    NfsClient mockNfsClient = Mockito.mock(NfsClient.class);
    Mockito.when(mockNfsClient.isUserLoggedIn()).thenReturn(true);
    Map<Long, NfsClient> clientMap =
        Collections.singletonMap(fileStore.getFileSystem().getId(), mockNfsClient);
    Mockito.when(mockNfsController.retrieveNfsClientsMapFromSession(any())).thenReturn(clientMap);
    controller.setNfsController(mockNfsController);

    // let's mock the responses for file and folder links
    NfsFileDetails testFile = new NfsFileDetails("test.txt");
    testFile.setFileSystemFullPath(fileStore.getAbsolutePath(nfsFileElem.getPath()));
    Mockito.when(mockNfsClient.queryForNfsFile(new NfsTarget(testFile.getFileSystemFullPath())))
        .thenReturn(testFile);

    // let's have a folder with another file and subfolder
    NfsFolderDetails testFolder = new NfsFolderDetails("otherDocs");
    NfsFileDetails testFileInFolder = new NfsFileDetails("subfolderDoc.txt");
    testFolder.getContent().add(testFileInFolder);
    testFolder.setFileSystemFullPath(fileStore.getAbsolutePath(nfsFolderElem.getPath()));
    NfsFolderDetails testSubfolder = new NfsFolderDetails("subfolderWithMoreDocs");
    testSubfolder.setFileSystemFullPath(
        testFolder.getFileSystemFullPath() + "/subfolderWithMoreDocs");
    testFolder.getContent().add(testSubfolder);
    Mockito.when(mockNfsClient.queryForNfsFolder(new NfsTarget(testFolder.getFileSystemFullPath())))
        .thenReturn(testFolder);

    MvcResult fullPlanResult =
        mockMvc
            .perform(
                post("/nfsExport/ajax/createFullExportPlan?planId=" + quickPlan.getPlanId())
                    .principal(new MockPrincipal(u.getUsername()))
                    .session(mockSession)
                    .content(requestContent)
                    .contentType(MediaType.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk())
            .andReturn();

    NfsExportPlan fullPlan = getFromJsonResponseBody(fullPlanResult, NfsExportPlan.class);
    assertNotNull(fullPlan);
    assertThat(fullPlan.getFoundFileSystems()).hasSize(1);
    assertEquals(0, fullPlan.countFileSystemsRequiringLogin());
    assertThat(fullPlan.getFoundNfsLinks()).hasSize(2); // file link + folder links
    assertThat(fullPlan.getFoundFileSystems().get(0).getFoundNfsLinks()).hasSize(2);
    assertThat(fullPlan.getCheckedNfsLinks()).hasSize(2); // file elem + folder elem
    assertThat(fullPlan.getFoundFileSystems().get(0).getCheckedNfsLinks()).hasSize(2);
    assertThat(fullPlan.getCheckedNfsLinks()).containsValue(testFile);
    assertThat(fullPlan.getCheckedNfsLinks()).containsValue(testFolder);
    assertThat(fullPlan.getCheckedNfsLinkMessages()).hasSize(1); // from skipped subfolder
    assertThat(fullPlan.getFoundFileSystems().get(0).getCheckedNfsLinkMessages())
        .hasSize(1); // from skipped subfolder
    assertEquals(
        messageSource.getMessage(NfsExportManagerImpl.SUBFOLDER_NOT_INCLUDED_MSG_KEY),
        fullPlan.getCheckedNfsLinkMessages().values().iterator().next());

    // let's re-generate full export plan and ensure the results are the same
    MvcResult fullPlanRegeneratedResult =
        mockMvc
            .perform(
                post("/nfsExport/ajax/createFullExportPlan?planId=" + quickPlan.getPlanId())
                    .principal(new MockPrincipal(u.getUsername()))
                    .session(mockSession)
                    .content(requestContent)
                    .contentType(MediaType.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk())
            .andReturn();
    NfsExportPlan fullPlanRegenerated =
        getFromJsonResponseBody(fullPlanRegeneratedResult, NfsExportPlan.class);
    assertNotNull(fullPlanRegenerated);
    assertThat(fullPlanRegenerated.getFoundFileSystems())
        .hasSameSizeAs(fullPlan.getFoundFileSystems());
    assertThat(fullPlanRegenerated.getFoundNfsLinks()).hasSameSizeAs(fullPlan.getFoundNfsLinks());
    assertThat(fullPlanRegenerated.getFoundFileSystems().get(0).getFoundNfsLinks())
        .hasSameSizeAs(fullPlan.getFoundFileSystems().get(0).getFoundNfsLinks());
    assertThat(fullPlanRegenerated.getCheckedNfsLinks())
        .hasSameSizeAs(fullPlan.getCheckedNfsLinks());
    assertThat(fullPlanRegenerated.getFoundFileSystems().get(0).getCheckedNfsLinks())
        .hasSameSizeAs(fullPlan.getFoundFileSystems().get(0).getCheckedNfsLinks());
  }
}
