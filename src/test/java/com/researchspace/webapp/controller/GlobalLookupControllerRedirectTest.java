package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Fast unit tests for global ID redirects, in particular version-suffixed inventory global IDs
 * (e.g. SA42v2) resolving to the versioned inventory viewer URL.
 */
public class GlobalLookupControllerRedirectTest {

  private final GlobalLookupController controller = new GlobalLookupController();

  @Test
  public void versionedSampleRedirectsToVersionedViewer() {
    assertEquals("redirect:/inventory/sample/42?version=2", controller.globalIdLookUp("SA42v2"));
  }

  @Test
  public void versionedSubSampleRedirectsToVersionedViewer() {
    assertEquals("redirect:/inventory/subsample/9?version=3", controller.globalIdLookUp("SS9v3"));
  }

  @Test
  public void versionedContainerRedirectsToVersionedViewer() {
    assertEquals("redirect:/inventory/container/10?version=1", controller.globalIdLookUp("IC10v1"));
  }

  @Test
  public void versionedTemplateRedirectsToVersionedViewer() {
    assertEquals(
        "redirect:/inventory/sampletemplate/7?version=2", controller.globalIdLookUp("IT7v2"));
  }

  @Test
  public void unversionedInventoryRedirectsUnchanged() {
    assertEquals("redirect:/inventory/sample/42", controller.globalIdLookUp("SA42"));
    assertEquals("redirect:/inventory/container/10", controller.globalIdLookUp("IC10"));
  }

  @Test
  public void versionedDocumentStillRedirectsToAuditView() {
    assertEquals(
        "redirect:"
            + StructuredDocumentController.STRUCTURED_DOCUMENT_AUDIT_VIEW_URL
            + "?globalId=SD1v2",
        controller.globalIdLookUp("SD1v2"));
  }

  @Test
  public void versionedInstrumentRedirectsToVersionedViewer() {
    assertEquals("redirect:/inventory/instrument/7?version=1", controller.globalIdLookUp("IN7v1"));
  }

  @Test
  public void versionedInstrumentTemplateRedirectsToVersionedViewer() {
    assertEquals(
        "redirect:/inventory/instrumenttemplate/7?version=2", controller.globalIdLookUp("NT7v2"));
  }

  @Test
  public void benchRedirectUnchanged() {
    assertEquals("redirect:/inventory/search?parentGlobalId=BE5", controller.globalIdLookUp("BE5"));
  }
}
