package com.researchspace.webapp.integrations.protocolsio;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.protocolsio.Protocol;
import com.researchspace.service.FolderManager;
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
  @Mock ProtocolsIOToDocumentConverter converter;
  @Mock UserManager userMgr;
  @Mock FolderManager folderMgr;
  @InjectMocks ProtocolsIOController ctrller;
  User subject = TestFactory.createAnyUser("any");
  Folder folder = TestFactory.createAFolder("imports", subject);

  @Before
  public void setUp() throws Exception {
    folder.setId(3L);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void importExternalDataOK() {
    Protocol protocol = getAProtocol();
    StructuredDocument anyDoc = getADocument();
    when(converter.generateFromProtocol(protocol, subject)).thenReturn(anyDoc);
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(subject);
    when(folderMgr.getImportsFolder(subject)).thenReturn(folder);
    AjaxReturnObject<ProtocolsIOController.PIOResponse> rc =
        ctrller.importExternalData(TransformerUtils.toList(protocol));
    assertEquals(2L, rc.getData().getResults().get(0).getId().longValue());
    assertEquals(3L, rc.getData().getImportFolderId().longValue());
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
