package com.researchspace.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.researchspace.api.v1.model.ApiInventoryEditLock;
import com.researchspace.api.v1.model.ApiInventoryEditLock.ApiInventoryEditLockStatus;
import com.researchspace.api.v1.model.ApiJob;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.api.v1.model.ApiSampleSearchResult;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.apiutils.ApiError;
import com.researchspace.license.InactiveLicenseTestService;
import com.researchspace.licensews.LicenseExpiredException;
import com.researchspace.model.User;
import com.researchspace.service.LicenseService;
import com.researchspace.webapp.controller.ServiceLoggerAspct;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;

/** Inventory is read-only while the license has expired. */
public class InventoryExpiredLicenseMVCIT extends API_MVC_InventoryTestBase {

  private @Autowired InventoryLicenseInterceptor licenseInterceptor;
  private @Autowired LicenseService licenseService;
  private @Autowired ServiceLoggerAspct serviceLoggerAspct;

  private User user;
  private String apiKey;
  private ApiSampleWithFullSubSamples sample;

  @BeforeEach
  public void setup() throws Exception {
    super.setUp();
    user = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(user);
    sample = createBasicSampleForUser(user, "licenseTestSample");
  }

  @AfterEach
  public void restoreLicense() {
    licenseInterceptor.setLicenseService(licenseService);
  }

  private void expireLicense() {
    licenseInterceptor.setLicenseService(new InactiveLicenseTestService());
  }

  @Test
  public void writesAreRejectedAndDataIsUnchanged() throws Exception {
    long samplesBefore = countSamples();
    expireLicense();

    MvcResult created =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/samples", user, "{ \"name\": \"createdWhileExpired\" }"))
            .andReturn();
    assertLicenseExpired(created);
    assertEquals(samplesBefore, countSamples());

    MvcResult updated =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(
                    apiKey, "/samples/" + sample.getId(), user, "{ \"name\": \"renamed\" }"))
            .andReturn();
    assertLicenseExpired(updated);

    MvcResult deleted =
        mockMvc
            .perform(createBuilderForDelete(apiKey, "/samples/{id}", user, sample.getId()))
            .andReturn();
    assertLicenseExpired(deleted);

    MvcResult read = mockMvc.perform(getSampleById(user, apiKey, sample.getId())).andReturn();
    assertNull(read.getResolvedException());
    ApiSampleWithFullSubSamples reloaded =
        getFromJsonResponseBody(read, ApiSampleWithFullSubSamples.class);
    assertEquals("licenseTestSample", reloaded.getName());
    assertFalse(reloaded.isDeleted());
  }

  @Test
  public void systemSettingsUpdateIsRejected() throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    String sysadminApiKey = createNewApiKeyForUser(sysadmin);
    expireLicense();

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPutWithJSONBody(sysadminApiKey, "/system/settings", sysadmin, "{}"))
            .andReturn();
    assertLicenseExpired(result);
  }

  @Test
  public void readsSearchBarcodesAndCsvExportStillWork() throws Exception {
    expireLicense();

    MvcResult search =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/search", user)
                    .param("query", "licenseTestSample"))
            .andReturn();
    assertEquals(HttpStatus.OK.value(), search.getResponse().getStatus());

    MvcResult barcode =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/barcodes", user)
                    .param("content", sample.getGlobalId()))
            .andReturn();
    assertEquals(HttpStatus.OK.value(), barcode.getResponse().getStatus());

    String settingsJson =
        "{ \"globalIds\": [ \""
            + sample.getGlobalId()
            + "\"], \"exportMode\": \"COMPACT\", \"resultFileType\": \"SINGLE_CSV\" }";
    MvcResult export =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/export"))
                    .param("exportSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(export.getResolvedException());
    ApiJob job = getFromJsonResponseBody(export, ApiJob.class);
    String downloadLink = job.getLinkOfType(ApiLinkItem.ENCLOSURE_REL).get().getLink();
    MvcResult download =
        mockMvc
            .perform(
                get(downloadLink.substring(downloadLink.indexOf("/api/"))).header("apiKey", apiKey))
            .andReturn();
    assertNull(download.getResolvedException());
    assertThat(download.getResponse().getContentAsString())
        .startsWith("# RSpace Inventory Export")
        .contains(sample.getGlobalId());
  }

  @Test
  public void existingEditLockCanBeReleasedButNotTaken() throws Exception {
    String lockUrl = "/editLocks/" + sample.getGlobalId();
    assertEquals(ApiInventoryEditLockStatus.LOCKED_OK, lock(lockUrl).getStatus());
    expireLicense();

    MvcResult release = mockMvc.perform(createBuilderForDelete(apiKey, lockUrl, user)).andReturn();
    assertNull(release.getResolvedException());
    assertEquals(HttpStatus.OK.value(), release.getResponse().getStatus());

    assertLicenseExpired(
        mockMvc.perform(createBuilderForPost(API_VERSION.ONE, apiKey, lockUrl, user)).andReturn());

    licenseInterceptor.setLicenseService(licenseService);
    // a lock still held after the release would report WAS_ALREADY_LOCKED
    assertEquals(ApiInventoryEditLockStatus.LOCKED_OK, lock(lockUrl).getStatus());
    mockMvc.perform(createBuilderForDelete(apiKey, lockUrl, user)).andReturn();
  }

  @Test
  public void expiredElnApiWriteKeepsItsExistingResponse() throws Exception {
    serviceLoggerAspct.setLicenseService(new InactiveLicenseTestService());
    try {
      MvcResult result =
          mockMvc
              .perform(preparePostOrPutRequestBody(post("/api/v1/documents"), "{}", user, apiKey))
              .andReturn();
      assertInstanceOf(LicenseExpiredException.class, result.getResolvedException());
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), result.getResponse().getStatus());
      assertThat(result.getResponse().getContentAsString()).doesNotContain("Inventory");
    } finally {
      serviceLoggerAspct.setLicenseService(licenseService);
    }
  }

  private ApiInventoryEditLock lock(String lockUrl) throws Exception {
    MvcResult result =
        mockMvc.perform(createBuilderForPost(API_VERSION.ONE, apiKey, lockUrl, user)).andReturn();
    assertNull(result.getResolvedException());
    return getFromJsonResponseBody(result, ApiInventoryEditLock.class);
  }

  private long countSamples() throws Exception {
    MvcResult result =
        mockMvc.perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/samples", user)).andReturn();
    return getFromJsonResponseBody(result, ApiSampleSearchResult.class).getTotalHits();
  }

  private void assertLicenseExpired(MvcResult result) throws Exception {
    assertInstanceOf(InventoryReadOnlyException.class, result.getResolvedException());
    assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertThat(error.getMessage()).contains("license has expired");
  }
}
