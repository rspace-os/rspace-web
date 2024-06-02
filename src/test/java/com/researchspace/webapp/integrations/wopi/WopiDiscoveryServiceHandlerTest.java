package com.researchspace.webapp.integrations.wopi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.integrations.wopi.WopiDiscoveryServiceHandler.WopiAction;
import com.researchspace.webapp.integrations.wopi.WopiDiscoveryServiceHandler.WopiApp;
import java.io.IOException;
import java.util.Map;
import javax.xml.bind.JAXBException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@RunWith(ConditionalTestRunner.class)
// Need to be sure office online integration is enabled
@TestPropertySource(
    properties = {
      "msoffice.wopi.enabled=true",
      "msoffice.wopi.discovery.url=https://ffc-onenote.officeapps.live.com/hosting/discovery"
    })
public class WopiDiscoveryServiceHandlerTest extends SpringTransactionalTest {

  @Autowired private WopiDiscoveryServiceHandler discoveryServiceHandler;

  @Autowired private WopiDiscoveryProcessor discoveryProcessor;

  @Test
  public void checkExampleDiscoveryXmlProcessing() throws IOException, JAXBException {
    WopiTestUtilities.setWopiDiscoveryFromExampleFile(
        discoveryServiceHandler, discoveryProcessor, WopiTestUtilities.MSOFFICE_DISCOVERY_XML_FILE);
    Map<String, WopiAction> docxActions = discoveryServiceHandler.getActionsForFileType("docx");
    assertEquals(15, docxActions.size());
    assertEquals(
        "https://word-view.officeapps.live.com/wv/wordviewerframe.aspx?"
            + "<ui=UI_LLCC&><rs=DC_LLCC&><dchat=DISABLE_CHAT&><hid=HOST_SESSION_ID&>"
            + "<sc=SESSION_CONTEXT&><wopisrc=WOPI_SOURCE&><showpagestats=PERFSTATS&>"
            + "<IsLicensedUser=BUSINESS_USER&><actnavid=ACTIVITY_NAVIGATION_ID&>",
        docxActions.get("view").getUrlsrc());
    assertEquals("Word", docxActions.get("view").getApp().getName());
    assertEquals("Word", docxActions.get("edit").getApp().getName());

    Map<String, WopiAction> csvActions = discoveryServiceHandler.getActionsForFileType("csv");
    assertEquals(4, csvActions.size());
    assertEquals("xlsx", csvActions.get("convert").getTargetext());

    Map<String, WopiApp> supportedExts = discoveryServiceHandler.getSupportedExtensions();
    assertTrue(supportedExts.containsKey("docx"));
    // extension "wopitest" does not have a default app, which makes it special
    assertTrue(supportedExts.containsKey("wopitest"));
    // RSPAC-2066: WordPdf app should be ignored
    assertFalse(supportedExts.containsKey("pdf"));
  }

  /**
   * If this test suddenly fails, it means that Microsoft have updated their discovery XML with some
   * sort of changes that break our WOPI integration :(
   */
  @RunIfSystemPropertyDefined(value = "nightly")
  @Test
  public void checkActualDiscoveryXmlProcessing() throws InterruptedException {
    discoveryProcessor.updateData();
    long waitTime = 0;
    while (discoveryServiceHandler.getSupportedExtensions().isEmpty()) {
      if (waitTime > 1000L * 20)
        throw new RuntimeException("Discovery XMl was not processed in time");
      Thread.sleep(100L);
      waitTime += 100L;
    }
    // Test basic properties that should hold true for the foreseeable future :P
    assertTrue(discoveryServiceHandler.getSupportedExtensions().containsKey("docx"));
    assertTrue(discoveryServiceHandler.getSupportedExtensions().containsKey("csv"));
    assertEquals(
        "Word",
        discoveryServiceHandler.getActionsForFileType("docx").get("view").getApp().getName());
    assertEquals(
        "Excel",
        discoveryServiceHandler.getActionsForFileType("csv").get("view").getApp().getName());
  }
}
