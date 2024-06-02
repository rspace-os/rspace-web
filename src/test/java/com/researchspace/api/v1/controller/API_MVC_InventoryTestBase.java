package com.researchspace.api.v1.controller;

import static org.mockito.Mockito.verify;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Provides utility methods for integration testing API inventory controllers */
public class API_MVC_InventoryTestBase extends API_MVC_TestBase {

  protected static final String BASE_64 =
      "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAQIAAAESAQMAAAAsV"
          + " 0mIAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAGUExURf///wAAAFXC034AAAAJcEhZcwAADsMAAA7"
          + " DAcdvqGQAAABWSURBVGje7dUhDsAgEETR5VYcv8fCgUEg1rXdhOR9/ZKRE5LO2kwbH4snHe8EQRAEQWxR88g+m"
          + " yAIgiDeCo9MEARBEP+Lmr/1yARBEARxhyj5fSktYgFPS1k85Tqe JQAAAABJRU5ErkJggg==";

  protected @Autowired AuditTrailService auditer;

  @Override
  protected String createUrl(API_VERSION version, String suffixUrl) {
    return "/api/inventory/v" + version.getVersion() + "/" + suffixUrl;
  }

  protected MockHttpServletRequestBuilder getSampleById(User user, String apiKey, Long sampleId) {
    return createBuilderForGet(API_VERSION.ONE, apiKey, "/samples/{id}", user, sampleId);
  }

  protected MockHttpServletRequestBuilder getSubSampleById(
      User user, String apiKey, Long subSampleId) {
    return createBuilderForGet(API_VERSION.ONE, apiKey, "/subSamples/{id}", user, subSampleId);
  }

  protected MockHttpServletRequestBuilder getContainerById(
      User user, String apiKey, Long containerId, boolean includeContent) {
    MockHttpServletRequestBuilder result =
        createBuilderForGet(API_VERSION.ONE, apiKey, "/containers/{id}", user, containerId);
    if (includeContent) {
      result.param("includeContent", "true");
    }
    return result;
  }

  protected MockHttpServletRequestBuilder getWorkbenchById(
      User user, String apiKey, Long workbenchId, boolean includeContent) {
    MockHttpServletRequestBuilder result =
        createBuilderForGet(API_VERSION.ONE, apiKey, "/workbenches/{id}", user, workbenchId);
    if (includeContent) {
      result.param("includeContent", "true");
    }
    return result;
  }

  protected MockHttpServletRequestBuilder getVisibleWorkbenches(User user, String apiKey) {
    return createBuilderForGet(API_VERSION.ONE, apiKey, "/workbenches", user);
  }

  protected void verifyAuditAction(AuditAction wantedAction, int wantedNumberOfInvocations) {
    verify(auditer, Mockito.times(wantedNumberOfInvocations))
        .notify(
            Mockito.argThat((GenericEvent event) -> event.getAuditAction().equals(wantedAction)));
  }
}
