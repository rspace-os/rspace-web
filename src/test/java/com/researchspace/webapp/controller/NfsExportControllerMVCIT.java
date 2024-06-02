package com.researchspace.webapp.controller;

import static com.researchspace.webapp.controller.ExportControllerTest.createExportArchiveConfigForUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

public class NfsExportControllerMVCIT extends MVCTestBase {

  @Autowired private NfsExportController controller;

  @Before
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
    assertEquals(1, quickPlan.getFoundFileSystems().size());
    assertEquals(1, quickPlan.countFileSystemsRequiringLogin());
    assertEquals(2, quickPlan.getFoundNfsLinks().size());
    assertEquals(2, quickPlan.getFoundFileSystems().get(0).getFoundNfsLinks().size());
    assertEquals(0, quickPlan.getCheckedNfsLinks().size());
    assertEquals(0, quickPlan.getFoundFileSystems().get(0).getCheckedNfsLinks().size());

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
    assertEquals(1, fullPlan.getFoundFileSystems().size());
    assertEquals(0, fullPlan.countFileSystemsRequiringLogin());
    assertEquals(2, fullPlan.getFoundNfsLinks().size()); // file link + folder links
    assertEquals(2, fullPlan.getFoundFileSystems().get(0).getFoundNfsLinks().size());
    assertEquals(2, fullPlan.getCheckedNfsLinks().size()); // file elem + folder elem
    assertEquals(2, fullPlan.getFoundFileSystems().get(0).getCheckedNfsLinks().size());
    assertTrue(fullPlan.getCheckedNfsLinks().containsValue(testFile));
    assertTrue(fullPlan.getCheckedNfsLinks().containsValue(testFolder));
    assertEquals(1, fullPlan.getCheckedNfsLinkMessages().size()); // from skipped subfolder
    assertEquals(
        1,
        fullPlan
            .getFoundFileSystems()
            .get(0)
            .getCheckedNfsLinkMessages()
            .size()); // from skipped subfolder
    assertEquals(
        NfsExportManagerImpl.SUBFOLDER_NOT_INCLUDED_MSG,
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
    assertEquals(
        fullPlan.getFoundFileSystems().size(), fullPlanRegenerated.getFoundFileSystems().size());
    assertEquals(fullPlan.getFoundNfsLinks().size(), fullPlanRegenerated.getFoundNfsLinks().size());
    assertEquals(
        fullPlan.getFoundFileSystems().get(0).getFoundNfsLinks().size(),
        fullPlanRegenerated.getFoundFileSystems().get(0).getFoundNfsLinks().size());
    assertEquals(
        fullPlan.getCheckedNfsLinks().size(), fullPlanRegenerated.getCheckedNfsLinks().size());
    assertEquals(
        fullPlan.getFoundFileSystems().get(0).getCheckedNfsLinks().size(),
        fullPlanRegenerated.getFoundFileSystems().get(0).getCheckedNfsLinks().size());
  }
}
