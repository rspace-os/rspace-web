package com.researchspace.webapp.controller;

import static com.researchspace.webapp.controller.ExportControllerTest.createExportArchiveConfigForUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.model.User;
import com.researchspace.model.dtos.export.ExportArchiveDialogConfigDTO;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsExportPlan;
import com.researchspace.testutils.SpringTransactionalTest;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
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
    assertThat(nfsExportPlansFromSession).isEmpty();

    ExportArchiveDialogConfigDTO config = createExportArchiveConfigForUser(user.getUsername());
    NfsExportPlan exportPlan = controller.createQuickExportPlan(config, request, principalStub);
    assertNotNull(exportPlan);
    assertThat(exportPlan.getFoundNfsLinks()).hasSize(1);
    assertThat(exportPlan.getFoundFileSystems()).hasSize(1);
    assertEquals(1, exportPlan.countFileSystemsRequiringLogin());
    assertThat(nfsExportPlansFromSession).hasSize(1);
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
    assertThat(exportPlan2.getFoundNfsLinks()).hasSize(1);
    assertThat(exportPlan2.getFoundFileSystems()).hasSize(1);
    assertEquals(
        0, exportPlan2.countFileSystemsRequiringLogin()); // nfs client reports being logged in
    assertThat(nfsExportPlansFromSession).hasSize(2);
  }
}
