package com.researchspace.api.v1.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.researchspace.api.v1.model.ApiInventorySystemSettings;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.validation.BindingResult;

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

  /**
   * Captures a provider's identifier settings so a test can put them back.
   *
   * <p>These settings are system properties in the SHARED database, not per-test state. A test that
   * points a provider at a dummy server writes over whatever the developer had configured, and
   * MVCITs run against the same dev database people use by hand. Restoring is not tidiness: leaving
   * a dummy server URL and token behind sends the next person to re-enter their credentials, and
   * re-entering half of a matched pair (a token without its community id, say) breaks the
   * integration in a way that looks like a code regression. That is exactly what happened once.
   *
   * <p>Pair with {@link #restoreIdentifierSettings}, and restore in an {@code @After} that runs
   * even when the test fails.
   */
  protected ApiInventorySystemSettings.IdentifierSettings captureIdentifierSettings(
      SystemSettingsApiController settingsController, IdentifierType provider) throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    return settingsController
        .getInventorySettings(new MockHttpServletRequest(), sysadmin)
        .findByProvider(provider)
        .orElse(null);
  }

  /**
   * Puts back what {@link #captureIdentifierSettings} read. A null argument means the provider had
   * no settings at all, which cannot be expressed through the update endpoint (it skips null
   * fields), so the enabled flag is cleared and the rest is left as the test set it - the honest
   * best effort, and enough to stop a dummy provider being live.
   */
  protected void restoreIdentifierSettings(
      SystemSettingsApiController settingsController,
      IdentifierType provider,
      ApiInventorySystemSettings.IdentifierSettings original)
      throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    ApiInventorySystemSettings.IdentifierSettings restore =
        original != null ? original : new ApiInventorySystemSettings.IdentifierSettings();
    restore.setProvider(provider);
    if (original == null) {
      restore.setEnabled("false");
    }
    settingsController.updateInventorySettings(
        new MockHttpServletRequest(), restore, mock(BindingResult.class), sysadmin);
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
