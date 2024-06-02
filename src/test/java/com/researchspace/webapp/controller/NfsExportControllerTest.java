package com.researchspace.webapp.controller;

import static com.researchspace.webapp.controller.ExportControllerTest.createExportArchiveConfigForUser;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.researchspace.model.User;
import com.researchspace.model.dtos.export.ExportArchiveDialogConfigDTO;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsExportPlan;
import com.researchspace.testutils.SpringTransactionalTest;
import java.security.Principal;
import java.util.Collections;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindingResult;

public class NfsExportControllerTest extends SpringTransactionalTest {

  @Autowired private NfsExportController controller;

  @Mock private BindingResult errors;

  private HttpServletRequest request = new MockHttpServletRequest();

  @Test
  public void generatePlanForUserExport() throws Exception {
    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("nfs"));
    initialiseContentWithEmptyContent(user);
    logoutAndLoginAs(user);
    Principal principalStub = new MockPrincipal(user.getUsername());

    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "any text");
    NfsElement nfsElem = addNfsFileStoreAndLink(doc.getFields().get(0), user, "/dummyPath");
    NfsFileStore fileStore = nfsMgr.getNfsFileStore(nfsElem.getFileStoreId());

    Map<String, NfsExportPlan> nfsExportPlansFromSession =
        controller.getNfsExportPlansFromSession(request);
    assertEquals(0, nfsExportPlansFromSession.size());

    ExportArchiveDialogConfigDTO config = createExportArchiveConfigForUser(user.getUsername());
    NfsExportPlan exportPlan = controller.createQuickExportPlan(config, request, principalStub);
    assertNotNull(exportPlan);
    assertEquals(1, exportPlan.getFoundNfsLinks().size());
    assertEquals(1, exportPlan.getFoundFileSystems().size());
    assertEquals(1, exportPlan.countFileSystemsRequiringLogin());
    assertEquals(1, nfsExportPlansFromSession.size());
    assertEquals(
        exportPlan.getPlanId(), nfsExportPlansFromSession.values().iterator().next().getPlanId());

    // let's check behaviour with nfs client present and reporting user logged in
    NfsController mockNfsController = Mockito.mock(NfsController.class);
    NfsClient mockNfsClient = Mockito.mock(NfsClient.class);
    Mockito.when(mockNfsClient.isUserLoggedIn()).thenReturn(true);
    Map<Long, NfsClient> clientMap =
        Collections.singletonMap(fileStore.getFileSystem().getId(), mockNfsClient);
    Mockito.when(mockNfsController.retrieveNfsClientsMapFromSession(request)).thenReturn(clientMap);
    controller.setNfsController(mockNfsController);

    NfsExportPlan exportPlan2 = controller.createQuickExportPlan(config, request, principalStub);
    assertNotNull(exportPlan2);
    assertEquals(1, exportPlan2.getFoundNfsLinks().size());
    assertEquals(1, exportPlan2.getFoundFileSystems().size());
    assertEquals(
        0, exportPlan2.countFileSystemsRequiringLogin()); // nfs client reports being logged in
    assertEquals(2, nfsExportPlansFromSession.size());
  }
}
