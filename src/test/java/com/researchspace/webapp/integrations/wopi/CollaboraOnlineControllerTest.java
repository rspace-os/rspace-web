package com.researchspace.webapp.integrations.wopi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.integrations.wopi.WopiDiscoveryServiceHandler.WopiAction;
import com.researchspace.webapp.integrations.wopi.WopiDiscoveryServiceHandler.WopiApp;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class CollaboraOnlineControllerTest extends SpringTransactionalTest {

  @Autowired private CollaboraOnlineController collaboraOnlineController;

  @Autowired private WopiDiscoveryServiceHandler discoveryServiceHandler;

  @Autowired private WopiDiscoveryProcessor discoveryProcessor;

  @Autowired private IPropertyHolder propertyHolder;

  private User testUser;

  @Before
  public void setUp() throws Exception {
    WopiTestUtilities.setWopiDiscoveryFromExampleFile(
        discoveryServiceHandler,
        discoveryProcessor,
        WopiTestUtilities.COLLABORA_DISCOVERY_XML_FILE);
    testUser = doCreateAndInitUser(getRandomAlphabeticString("wopi"));
  }

  @Test
  public void testActionUrlCreation() throws IOException, URISyntaxException {
    EcatDocumentFile msDoc = addDocumentFromTestResourcesToGallery("MSattachment.doc", testUser);
    EcatDocumentFile msExcel = addDocumentFromTestResourcesToGallery("simpleExcel.xlsx", testUser);

    Map<String, String> requestParams = new LinkedHashMap<>();

    WopiAction docViewAction = collaboraOnlineController.getWopiActionForMediaFile("view", msDoc);
    assertNotNull(docViewAction);
    assertEquals(
        "https://demo.eu.collaboraonline.com/browser/739da71/cool.html?"
            + "WOPISrc="
            + propertyHolder.getServerUrl()
            + "/wopi/files/"
            + msDoc.getGlobalIdentifier(),
        collaboraOnlineController.getWopiActionUrlForMediaFile(
            docViewAction, msDoc, requestParams));

    WopiAction excelViewAction =
        collaboraOnlineController.getWopiActionForMediaFile("view", msExcel);
    assertNotNull(excelViewAction);
    assertEquals(
        "https://demo.eu.collaboraonline.com/browser/739da71/cool.html?"
            + "WOPISrc="
            + propertyHolder.getServerUrl()
            + "/wopi/files/"
            + msExcel.getGlobalIdentifier(),
        collaboraOnlineController.getWopiActionUrlForMediaFile(
            excelViewAction, msExcel, requestParams));
    WopiAction excelEditAction =
        collaboraOnlineController.getWopiActionForMediaFile("edit", msExcel);
    assertNotNull(excelEditAction);
    assertEquals(
        "https://demo.eu.collaboraonline.com/browser/739da71/cool.html?"
            + "WOPISrc="
            + propertyHolder.getServerUrl()
            + "/wopi/files/"
            + msExcel.getGlobalIdentifier(),
        collaboraOnlineController.getWopiActionUrlForMediaFile(
            excelEditAction, msExcel, requestParams));
  }

  @Test
  public void testSupportedExtensionsRetrieval() {
    Map<String, WopiApp> supportedExts = collaboraOnlineController.getSupportedExtensions();
    assertTrue(
        supportedExts.containsKey("docx")
            && supportedExts.containsKey("xlsx")
            && supportedExts.containsKey("pptx"),
        "unexpected exts: " + supportedExts);
    assertTrue(
        supportedExts.containsKey("odt")
            && supportedExts.containsKey("ods")
            && supportedExts.containsKey("odp"),
        "unexpected exts: " + supportedExts);
  }
}
