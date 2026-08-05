package com.researchspace.webapp.integrations.wopi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

public class OfficeOnlineControllerTest extends SpringTransactionalTest {

  @Autowired private OfficeOnlineController officeController;

  @Autowired private WopiDiscoveryServiceHandler discoveryServiceHandler;

  @Autowired private WopiDiscoveryProcessor discoveryProcessor;

  @Autowired private IPropertyHolder propertyHolder;

  private User testUser;

  @Before
  public void setUp() throws Exception {
    WopiTestUtilities.setWopiDiscoveryFromExampleFile(
        discoveryServiceHandler, discoveryProcessor, WopiTestUtilities.MSOFFICE_DISCOVERY_XML_FILE);
    testUser = doCreateAndInitUser(getRandomAlphabeticString("wopi"));
  }

  @Test
  public void testActionUrlCreation() throws IOException, URISyntaxException {
    EcatDocumentFile msDoc = addDocumentFromTestResourcesToGallery("MSattachment.doc", testUser);
    EcatDocumentFile msExcel = addDocumentFromTestResourcesToGallery("simpleExcel.xlsx", testUser);

    Map<String, String> requestParams = new LinkedHashMap<>();
    requestParams.put("test", "test");
    requestParams.put("wdA", "valueA");
    requestParams.put("wd", "cpe");
    requestParams.put("wdB", "valueB");

    WopiAction docViewAction = officeController.getWopiActionForMediaFile("view", msDoc);
    assertNotNull(docViewAction);
    assertEquals(
        "https://word-view.officeapps.live.com/wv/wordviewerframe.aspx?"
            + "wopisrc="
            + propertyHolder.getServerUrl()
            + "/wopi/files/"
            + msDoc.getGlobalIdentifier()
            + "&IsLicensedUser=1&wdA=valueA&wd=cpe&wdB=valueB",
        officeController.getWopiActionUrlForMediaFile(
            docViewAction, msDoc, testUser, requestParams));
    WopiAction docConvertAction = officeController.getWopiActionForMediaFile("convert", msDoc);
    assertNotNull(docConvertAction);
    assertEquals(
        "https://word-edit.officeapps.live.com/we/WordConvertAndEdit.aspx?"
            + "wopisrc="
            + propertyHolder.getServerUrl()
            + "/wopi/files/"
            + msDoc.getGlobalIdentifier()
            + "&wdA=valueA&wd=cpe&wdB=valueB",
        officeController.getWopiActionUrlForMediaFile(
            docConvertAction, msDoc, testUser, requestParams));

    WopiAction excelViewAction = officeController.getWopiActionForMediaFile("view", msExcel);
    assertNotNull(excelViewAction);
    assertEquals(
        "https://excel.officeapps.live.com/x/_layouts/xlviewerinternal.aspx?"
            + "wopisrc="
            + propertyHolder.getServerUrl()
            + "/wopi/files/"
            + msExcel.getGlobalIdentifier()
            + "&IsLicensedUser=1&wdA=valueA&wd=cpe&wdB=valueB",
        officeController.getWopiActionUrlForMediaFile(
            excelViewAction, msExcel, testUser, requestParams));
    WopiAction excelEditAction = officeController.getWopiActionForMediaFile("edit", msExcel);
    assertNotNull(excelEditAction);
    assertEquals(
        "https://excel.officeapps.live.com/x/_layouts/xlviewerinternal.aspx?"
            + "edit=1&wopisrc="
            + propertyHolder.getServerUrl()
            + "/wopi/files/"
            + msExcel.getGlobalIdentifier()
            + "&IsLicensedUser=1&wdA=valueA&wd=cpe&wdB=valueB",
        officeController.getWopiActionUrlForMediaFile(
            excelEditAction, msExcel, testUser, requestParams));
  }

  @Test
  public void checkSupportedConversionExtensions() {
    assertTrue(officeController.isConversionSupportedForExtension("doc"));
    assertFalse(officeController.isConversionSupportedForExtension("docx"));
  }

  @Test
  public void testSupportedExtensionsRetrieval() {
    Map<String, WopiApp> supportedExts = officeController.getSupportedExtensions();
    assertTrue(
        supportedExts.containsKey("docx")
            && supportedExts.containsKey("xlsx")
            && supportedExts.containsKey("pptx"),
        "unexpected exts: " + supportedExts);
  }
}
