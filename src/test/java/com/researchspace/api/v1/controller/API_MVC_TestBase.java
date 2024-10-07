package com.researchspace.api.v1.controller;

import static java.util.stream.Collectors.joining;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Supplier;
import com.researchspace.api.v1.model.ApiFolder;
import com.researchspace.api.v1.model.ApiJob;
import com.researchspace.apiutils.ApiError;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.User;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.webapp.controller.MVCTestBase;
import java.io.ByteArrayInputStream;
import java.security.Principal;
import java.util.zip.ZipInputStream;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Provides utility methods for integration testing API controllers */
public class API_MVC_TestBase extends MVCTestBase {
  protected static final String testOAuthAppClientId = "testAppClientId1";
  protected static final String testOAuthAppClientSecret = "clientSecret1";

  protected API_ModelTestUtils apiModelTestUtils = new API_ModelTestUtils();

  protected @Autowired SystemPropertyManager sysPropMgr;

  static final int MIN_KEY_LENGTH = 16;

  static final String STATUS = "/status";

  static final String API_AVAILABLE = "api.available";

  protected void enableAPI(User apiUser) {
    sysPropMgr.save(API_AVAILABLE, "ALLOWED", apiUser);
  }

  protected void disableAPI(User apiUser) {
    sysPropMgr.save(API_AVAILABLE, "DENIED", apiUser);
  }

  /**
   * Initialises request creation and adds API key as header and principal to the request
   *
   * @param version
   * @param apiKey
   * @param user
   * @param suffixUrl
   * @return A MockHttpServletRequestBuilder for further configuration
   */
  protected MockHttpServletRequestBuilder createBuilderForGet(
      API_VERSION version, String apiKey, String suffixUrl, User user, Object... pathVars) {
    return MockMvcRequestBuilders.get(createUrl(version, suffixUrl), pathVars)
        .principal(createPrincipal(user))
        .header("apiKey", apiKey);
  }

  protected MockHttpServletRequestBuilder createBuilderForInventoryGet(
      API_VERSION version, String apiKey, String suffixUrl, User user, Object... pathVars) {
    return MockMvcRequestBuilders.get(createInventoryUrl(version, suffixUrl), pathVars)
        .principal(createPrincipal(user))
        .header("apiKey", apiKey);
  }

  protected MockHttpServletRequestBuilder createBuilderForAnonymousGet(
      API_VERSION version, String suffixUrl, Object... pathVars) {
    return MockMvcRequestBuilders.get(createUrl(version, suffixUrl), pathVars);
  }

  /**
   * Alternative GET creator using a Supplier<Principal> rather than a User
   *
   * @param version
   * @param apiKey
   * @param suffixUrl
   * @param principalSupplier
   * @param pathVars
   * @return
   */
  protected MockHttpServletRequestBuilder createBuilderForGet2(
      API_VERSION version,
      String apiKey,
      String suffixUrl,
      Supplier<Principal> principalSupplier,
      Object... pathVars) {
    return MockMvcRequestBuilders.get(createUrl(version, suffixUrl), pathVars)
        .principal(principalSupplier.get())
        .header("apiKey", apiKey);
  }

  protected MockHttpServletRequestBuilder createBuilderForDelete(
      String apiKey, String suffixUrl, User user, Object... pathVars) {
    return MockMvcRequestBuilders.delete(createUrl(API_VERSION.ONE, suffixUrl), pathVars)
        .principal(createPrincipal(user))
        .header("apiKey", apiKey);
  }

  protected MockHttpServletRequestBuilder createBuilderForPost(
      API_VERSION version, String apiKey, String suffixUrl, User user) {
    return MockMvcRequestBuilders.post(createUrl(version, suffixUrl))
        .principal(createPrincipal(user))
        .header("apiKey", apiKey);
  }

  protected MockHttpServletRequestBuilder createBuilderForInventoryPostWithJSONBody(
      String apiKey, String suffixUrl, User user, Object toPost) {
    String body = getStringBody(toPost);
    return preparePostOrPutRequestBody(
        post(createInventoryUrl(API_VERSION.ONE, suffixUrl)), body, user, apiKey);
  }

  protected MockHttpServletRequestBuilder createBuilderForPostWithJSONBody(
      String apiKey, String suffixUrl, User user, Object toPost) {
    String body = getStringBody(toPost);
    return preparePostOrPutRequestBody(
        post(createUrl(API_VERSION.ONE, suffixUrl)), body, user, apiKey);
  }

  protected MockHttpServletRequestBuilder createBuilderForPutWithJSONBody(
      String apiKey, String suffixUrl, User user, Object toPut) {
    String body = getStringBody(toPut);
    return preparePostOrPutRequestBody(
        put(createUrl(API_VERSION.ONE, suffixUrl)), body, user, apiKey);
  }

  MockHttpServletRequestBuilder preparePostOrPutRequestBody(
      MockHttpServletRequestBuilder builder, String body, User user, String apiKey) {
    return builder
        .content(body)
        .contentType(MediaType.APPLICATION_JSON)
        .principal(createPrincipal(user))
        .header("apiKey", apiKey);
  }

  private String getStringBody(Object toPost) {
    String body = "";
    if (toPost instanceof String) {
      body = toPost.toString();
    } else {
      body = JacksonUtil.toJson(toPost);
    }
    return body;
  }

  protected MockHttpServletRequestBuilder createBuilderForPut(
      API_VERSION version, String apiKey, String suffixUrl, User user, Object... pathVars) {
    return MockMvcRequestBuilders.put(createUrl(version, suffixUrl), pathVars)
        .principal(createPrincipal(user))
        .header("apiKey", apiKey);
  }

  protected String createUrl(API_VERSION version, String suffixUrl) {
    return "/api/v" + version.getVersion() + "/" + suffixUrl;
  }

  protected String createInventoryUrl(API_VERSION version, String suffixUrl) {
    return "/api/inventory/v" + version.getVersion() + "/" + suffixUrl;
  }

  protected Principal createPrincipal(User user) {
    return new MockPrincipal(user.getUsername());
  }

  /**
   * Gets API Error body
   *
   * @param result
   * @param clazz
   * @return
   * @throws Exception
   */
  protected <T> T getErrorFromJsonResponseBody(MvcResult result, Class<T> clazz) throws Exception {
    Assert.assertNotNull(result.getResolvedException());
    String content = result.getResponse().getContentAsString();
    return (new ObjectMapper()).readValue(content, clazz);
  }

  /**
   * Create a folder via API
   *
   * @param anyUser
   * @param apiKey
   * @param folderPost
   * @return
   */
  protected MockHttpServletRequestBuilder folderCreate(
      User anyUser, String apiKey, ApiFolder folderPost) {
    return createBuilderForPostWithJSONBody(apiKey, "/folders", anyUser, folderPost);
  }

  /** Asserts that the supplied msgFragement exists in any error message in getErrors() */
  protected void assertApiErrorContainsMessage(ApiError error, String msgFragment) {

    boolean errorMatch =
        error.getErrors().stream()
            .anyMatch(e -> e.toLowerCase().contains(msgFragment.toLowerCase()));
    String concatenatedMsges = error.getErrors().stream().collect(joining(","));
    assertTrue(
        String.format(
            "Expected '%s' to be present in error messages - %s", msgFragment, concatenatedMsges),
        errorMatch);
  }

  /**
   * Given a completed job, downloads the export and returns a ZipInputStream to inspect the zip
   * contents <br>
   * Client should close the stream
   */
  protected ZipInputStream getZipInputStreamToArchive(User any, String apiKey, ApiJob completed)
      throws Exception {
    String downloadUrl =
        completed.getLinks().get(0).getLink().replace("http://localhost:8080/api/v1", "");
    MvcResult result2 =
        mockMvc.perform(createBuilderForGet(API_VERSION.ONE, apiKey, downloadUrl, any)).andReturn();

    byte[] responseBytes = result2.getResponse().getContentAsByteArray();
    assertNotNull(responseBytes);
    ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(responseBytes));
    return zis;
  }
}
