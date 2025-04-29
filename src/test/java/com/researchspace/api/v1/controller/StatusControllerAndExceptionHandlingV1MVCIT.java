package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.API_VERSION.ONE;
import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.researchspace.api.v1.model.Status;
import com.researchspace.apiutils.ApiError;
import com.researchspace.model.User;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

public class StatusControllerAndExceptionHandlingV1MVCIT extends API_MVC_TestBase {

  private static final int MIN_KEY_LENGTH = 16;

  private static final String STATUS = "/status";
  private static final String NON_EXISTENT_DOCUMENT = "/documents/12345";

  User apiUser = null;

  String apiKey = "";

  @Before
  public void setUp() throws Exception {
    super.setUp();
    apiUser = createAndSaveUser(getRandomAlphabeticString("user"));
    apiKey = createNewApiKeyForUser(apiUser);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void statusHappyCase() throws Exception {
    MvcResult result =
        mockMvc.perform(createBuilderForGet(ONE, apiKey, STATUS, apiUser)).andReturn();
    Status status = getFromJsonResponseBody(result, Status.class);
    assertNotNull(status);
    assertEquals("OK", status.getMessage());
  }

  @Test
  public void missingHeaderGenerates401() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get(createUrl(ONE, STATUS)).principal(createPrincipal(apiUser)))
            .andReturn();
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertNotNull(error);
    log.info(error.toString());
    assertEquals(UNAUTHORIZED.value(), error.getHttpCode());
  }

  @Test
  public void disabledUserGenerates401() throws Exception {
    User apiUser = createAndSaveUser(getRandomAlphabeticString("user"));
    String apiKey = createNewApiKeyForUser(apiUser);
    apiUser.setEnabled(false);
    apiUser = userMgr.save(apiUser);
    MvcResult result =
        mockMvc.perform(createBuilderForGet(ONE, apiKey, STATUS, apiUser)).andReturn();
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertNotNull(error);
    log.info(error.toString());
    assertEquals(UNAUTHORIZED.value(), error.getHttpCode());
  }

  @Test
  public void invalidHeaderGenerates401() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(createUrl(ONE, STATUS))
                    .principal(createPrincipal(apiUser))
                    .header("apiKey", "????"))
            .andReturn();
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertNotNull(error);
    log.info(error.toString());
    assertEquals(UNAUTHORIZED.value(), error.getHttpCode());
  }

  @Test
  public void unavailableAPIGenerates401() throws Exception {
    try {
      disableAPI(apiUser);
      MvcResult result =
          mockMvc.perform(createBuilderForGet(ONE, apiKey, STATUS, apiUser)).andReturn();
      ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
      assertNotNull(error);
      log.warn(error.toString());
      assertEquals(UNAUTHORIZED.value(), error.getHttpCode());
    } finally {
      enableAPI(apiUser);
    }
  }

  @Test
  public void unknownHeaderGenerates401() throws Exception {
    MvcResult result =
        mockMvc
            .perform(createBuilderForGet(ONE, randomAlphabetic(MIN_KEY_LENGTH), STATUS, apiUser))
            .andReturn();
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertNotNull(error);
    log.info(error.toString());
    assertEquals(UNAUTHORIZED.value(), error.getHttpCode());
  }

  @Test
  public void notExistingDocumentExceptionCanBeConvertedToJSON() throws Exception {
    // Call with 'application/json' Accept header should respond with a JSON ApiError
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(ONE, apiKey, NON_EXISTENT_DOCUMENT, apiUser)
                    .accept(MediaType.APPLICATION_JSON))
            .andReturn();
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertNotNull(error);
    log.info(error.toString());
    assertEquals(NOT_FOUND.value(), error.getHttpCode());
  }

  @Test
  public void notExistingDocumentExceptionCanBeConvertedToCSV() throws Exception {
    // Call with 'text/csv' Accept header should respond with a CSV ApiError
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(ONE, apiKey, NON_EXISTENT_DOCUMENT, apiUser)
                    .accept(MediaType.valueOf("text/csv")))
            .andReturn();
    Assert.assertNotNull(result.getResolvedException());
    String csvResponse = result.getResponse().getContentAsString();
    log.info(csvResponse);

    String[] lines = API_ModelTestUtils.parseCSVResponseToLines(csvResponse);
    assertEquals(2, lines.length); // 1 header row and 1 error row
    final int ERROR_PROPERTY_COUNT = 5; // includes commas in error message;
    API_ModelTestUtils.assertRowAndColumnCountForApiError(csvResponse, 2, ERROR_PROPERTY_COUNT);

    assertEquals("NOT_FOUND", lines[1].split(",")[0]);
    assertEquals("404", lines[1].split(",")[1]);
  }
}
