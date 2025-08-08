package com.researchspace.webapp.integrations.protocolsio;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.auth.PermissionUtils;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.User;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.protocolsio.Protocol;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.SharingHandler;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.AjaxReturnObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ProtocolsIOControllerTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  private @Mock ProtocolsIOToDocumentConverter converter;
  private @Mock UserManager userMgr;
  private @Mock FolderManager folderMgr;
  private @Mock RecordManager recordManager;
  private @Mock SharingHandler recordShareHandler;
  private @Mock PermissionUtils permissionUtils;
  private @InjectMocks ProtocolsIOController ctrller;
  private User subject = TestFactory.createAnyUser("any");
  private Folder workspaceRootFolder = TestFactory.createAFolder("workspaceRoot", subject);
  private Folder importsFolder = TestFactory.createAFolder("imports", subject);
  private Folder sharedFolder = TestFactory.createAFolder("shared", subject);
  private Folder sharedNotebook = TestFactory.createANotebook("notebook", subject);
  private Protocol protocol = getAProtocol();
  private StructuredDocument anyDoc = getADocument();

  @Before
  public void setUp() throws Exception {
    importsFolder.setId(3L);
    sharedFolder.setId(1L);
    sharedNotebook.setId(4L);
    workspaceRootFolder.setId(5L);
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(subject);
    when(converter.generateFromProtocol(protocol, subject, sharedFolder.getId())).thenReturn(null);
    when(converter.generateFromProtocol(protocol, subject, workspaceRootFolder.getId()))
        .thenReturn(null);
    when(converter.generateFromProtocol(protocol, subject, importsFolder.getId()))
        .thenReturn(anyDoc);
    when(converter.generateFromProtocol(protocol, subject, sharedNotebook.getId()))
        .thenReturn(anyDoc);
    when(folderMgr.getImportsFolder(subject)).thenReturn(importsFolder);
    when(folderMgr.getFolder(sharedFolder.getId(), subject)).thenReturn(sharedFolder);
    when(folderMgr.getFolder(importsFolder.getId(), subject)).thenReturn(importsFolder);
    when(folderMgr.getFolder(sharedNotebook.getId(), subject)).thenReturn(sharedNotebook);
    when(folderMgr.getFolder(workspaceRootFolder.getId(), subject)).thenReturn(workspaceRootFolder);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void importExternalDataOK() {
    when(recordManager.isSharedFolderOrSharedNotebookWithoutCreatePermission(
            subject, workspaceRootFolder))
        .thenReturn(false);
    when(recordManager.isSharedFolderOrSharedNotebookWithoutCreatePermission(
            subject, importsFolder))
        .thenReturn(false);
    AjaxReturnObject<ProtocolsIOController.PIOResponse> rc =
        ctrller.importExternalData(workspaceRootFolder.getId(), TransformerUtils.toList(protocol));
    assertEquals(anyDoc.getId(), rc.getData().getResults().get(0).getId());
    assertEquals(importsFolder.getId(), rc.getData().getImportFolderId());
    verifyNoInteractions(recordShareHandler);
  }

  @Test
  public void importExternalDataIntoASharedFolder() {
    when(recordManager.isSharedFolderOrSharedNotebookWithoutCreatePermission(subject, sharedFolder))
        .thenReturn(true);
    AjaxReturnObject<ProtocolsIOController.PIOResponse> rc =
        ctrller.importExternalData(sharedFolder.getId(), TransformerUtils.toList(protocol));
    assertEquals(anyDoc.getId(), rc.getData().getResults().get(0).getId());
    assertEquals(importsFolder.getId(), rc.getData().getImportFolderId());
    verify(recordShareHandler)
        .shareIntoSharedFolderOrNotebook(subject, sharedFolder, anyDoc.getId());
  }

  @Test
  public void importExternalData_INTO_OWNED_SharedNotebook() {
    when(recordManager.isSharedFolderOrSharedNotebookWithoutCreatePermission(
            subject, sharedNotebook))
        .thenReturn(false);
    when(permissionUtils.isPermitted(sharedNotebook, PermissionType.CREATE, subject))
        .thenReturn(true);

    AjaxReturnObject<ProtocolsIOController.PIOResponse> rc =
        ctrller.importExternalData(sharedNotebook.getId(), TransformerUtils.toList(protocol));
    assertEquals(anyDoc.getId(), rc.getData().getResults().get(0).getId());
    assertEquals(sharedNotebook.getId(), rc.getData().getImportFolderId());
    verifyNoInteractions(recordShareHandler);
  }

  @Test
  public void importExternalData_INTO_NOT_OWNED_SharedNotebook() {
    when(recordManager.isSharedFolderOrSharedNotebookWithoutCreatePermission(
            subject, sharedNotebook))
        .thenReturn(true);
    when(permissionUtils.isPermitted(sharedNotebook, PermissionType.CREATE, subject))
        .thenReturn(false);

    AjaxReturnObject<ProtocolsIOController.PIOResponse> rc =
        ctrller.importExternalData(sharedNotebook.getId(), TransformerUtils.toList(protocol));
    assertEquals(anyDoc.getId(), rc.getData().getResults().get(0).getId());
    assertEquals(importsFolder.getId(), rc.getData().getImportFolderId());
    verify(recordShareHandler)
        .shareIntoSharedFolderOrNotebook(subject, sharedNotebook, anyDoc.getId());
  }

  private Protocol getAProtocol() {
    Protocol protocol = new Protocol();
    protocol.setNumberOfSteps(2);
    protocol.setTitle("anyTitle");
    return protocol;
  }

  private StructuredDocument getADocument() {
    StructuredDocument rc = TestFactory.createAnySD();
    rc.setId(2L);
    rc.setOwner(subject);
    return rc;
  }
}
