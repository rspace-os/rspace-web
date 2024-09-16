package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiJob;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.apiutils.ApiError;
import com.researchspace.model.User;
import java.time.Instant;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class InventoryExportApiControllerMVCIT extends API_MVC_InventoryTestBase {

  User anyUser;
  String apiKey;

  private static final int EXPECTED_CONTENT_DISPOSITION_LENGTH =
      "attachment; filename=\"RSpace-2022-06-08-15-39-CSV-INVENTORY-pKGDzoo2GGXTkg.csv\"".length();

  @Before
  public void setup() throws Exception {
    super.setUp();
    anyUser = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(anyUser);
  }

  @Test
  public void exportComplexSubSampleAsCsv() throws Exception {

    ApiSampleWithFullSubSamples complexSample = createComplexSampleForUser(anyUser);
    ApiSubSample complexSubSample = complexSample.getSubSamples().get(0);

    // let's export a complex subsample
    String settingsJson =
        "{ \"globalIds\": [ \""
            + complexSubSample.getGlobalId()
            + "\"], \"resultFileType\": \"SINGLE_CSV\" }";
    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/export"))
                    .param("exportSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());
    ApiJob job = getFromJsonResponseBody(result, ApiJob.class);
    assertEquals(2, job.getLinks().size());
    Map exportJobResultProps = (Map) job.getResult();
    assertEquals(4, exportJobResultProps.size());
    assertTrue(((Integer) exportJobResultProps.get("size")) > 500);

    result = downloadExportedFileLinkInExportJobResult(job);

    String contentHeader = result.getResponse().getHeader("Content-Disposition");
    assertTrue(contentHeader.startsWith("attachment; filename=\"RSpace-"), contentHeader);
    assertEquals(EXPECTED_CONTENT_DISPOSITION_LENGTH, contentHeader.length(), contentHeader);

    // confirm subsamples fragment content
    String singleSubSampleCsvOutput = result.getResponse().getContentAsString();
    assertTrue(
        singleSubSampleCsvOutput.startsWith(
            "# RSpace Inventory Export\n# Exported content: SUBSAMPLES"),
        singleSubSampleCsvOutput);
    assertNotNull(singleSubSampleCsvOutput);
    String expectedNoteContent =
        "\"Note created by \"\""
            + anyUser.getUsername()
            + "\"\" at "
            + Instant.ofEpochMilli(complexSubSample.getNotes().get(0).getCreationDateMillis())
                .toString()
            + ": \"\"test note\"\"\"";
    assertTrue(
        singleSubSampleCsvOutput.contains(
            "Global ID,Name,Tags,Owner,Description,Parent Sample (Global ID),Parent Container"
                + " (Global ID),Quantity,Notes,\"Data (TEXT, "
                + complexSubSample.getGlobalId()
                + ")\"\n"
                + complexSubSample.getGlobalId()
                + ",mySubSample,,"
                + anyUser.getUsername()
                + ",,"
                + complexSample.getGlobalId()
                + ","
                + complexSubSample.getParentContainer().getGlobalId()
                + ",1 ml,"
                + expectedNoteContent
                + ",any content\n"),
        singleSubSampleCsvOutput);

    // export in compact mode
    settingsJson =
        "{ \"globalIds\": [ \""
            + complexSubSample.getGlobalId()
            + "\"], \"exportMode\": \"COMPACT\", \"resultFileType\": \"SINGLE_CSV\" }";
    result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/export"))
                    .param("exportSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());
    job = getFromJsonResponseBody(result, ApiJob.class);
    result = downloadExportedFileLinkInExportJobResult(job);

    singleSubSampleCsvOutput = result.getResponse().getContentAsString();
    assertNotNull(singleSubSampleCsvOutput);
    assertTrue(
        singleSubSampleCsvOutput.startsWith(
            "# RSpace Inventory Export\n# Exported content: SUBSAMPLES"),
        singleSubSampleCsvOutput);
    assertTrue(
        singleSubSampleCsvOutput.contains(
            "Global ID,Name,Tags,Owner,Description,Parent Sample (Global ID),Parent Container"
                + " (Global ID),Quantity,Notes\n"
                + complexSubSample.getGlobalId()
                + ",mySubSample,,"
                + anyUser.getUsername()
                + ",,"
                + complexSample.getGlobalId()
                + ","
                + complexSubSample.getParentContainer().getGlobalId()
                + ",1 ml,"
                + expectedNoteContent
                + "\n"),
        singleSubSampleCsvOutput);
  }

  private MvcResult downloadExportedFileLinkInExportJobResult(ApiJob job) throws Exception {
    MvcResult result;
    String apiExportDownloadLink = job.getLinkOfType(ApiLinkItem.ENCLOSURE_REL).get().getLink();
    String relativeApiExportDownloadLink =
        apiExportDownloadLink.substring(apiExportDownloadLink.indexOf("/api/"));
    result =
        mockMvc.perform(get(relativeApiExportDownloadLink).header("apiKey", apiKey)).andReturn();
    assertNull(result.getResolvedException());
    return result;
  }

  @Test
  public void exportAllUserItemsAsCsv() throws Exception {

    createBasicContainerForUser(anyUser, "my container");
    createBasicSampleForUser(anyUser);
    ApiSampleWithFullSubSamples complexSample = createComplexSampleForUser(anyUser);
    String extraFieldContentFromComplexSubSample =
        complexSample.getSubSamples().get(0).getExtraFields().get(0).getContent();

    // let's export all items belonging to anyUser
    String settingsJson =
        "{ \"users\": [ \"" + anyUser.getUsername() + "\"], \"resultFileType\": \"SINGLE_CSV\" }";
    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/export"))
                    .param("exportSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());
    ApiJob job = getFromJsonResponseBody(result, ApiJob.class);
    assertNotNull(job);

    // download connected file
    result = downloadExportedFileLinkInExportJobResult(job);

    // confirm filename
    String contentHeader = result.getResponse().getHeader("Content-Disposition");
    assertTrue(contentHeader.startsWith("attachment; filename=\"RSpace-"), contentHeader);
    assertEquals(EXPECTED_CONTENT_DISPOSITION_LENGTH, contentHeader.length(), contentHeader);

    // confirm full content
    String fullExportContent = result.getResponse().getContentAsString();
    assertNotNull(fullExportContent);
    assertTrue(
        fullExportContent.startsWith("# RSpace Inventory Export\n# Exported content: CONTAINERS"),
        fullExportContent);
    assertTrue(fullExportContent.contains("# Export mode: FULL"), fullExportContent);
    // confirm container lines
    assertTrue(
        fullExportContent.contains(
            "Global ID,Name,Tags,Owner,Description,Parent Container (Global ID),Container Type,Can"
                + " Store Containers (Y/N),Can Store Subsamples (Y/N),Number of Stored"
                + " Containers,Number of Stored Subsamples\n"),
        fullExportContent);
    assertTrue(fullExportContent.contains(",my container,"), fullExportContent);
    // confirm samples lines
    assertTrue(
        fullExportContent.contains(
            "Global ID,Name,Tags,Owner,Description,Parent Template (Global ID),Parent Template"
                + " (name),Total Quantity,Expiry Date,Sample Source,Storage Temperature"
                + " (min),Storage Temperature (max),\"MyNumber (NUMBER, IT"),
        fullExportContent);
    assertTrue(fullExportContent.contains(",mySample,"), fullExportContent);
    assertTrue(fullExportContent.contains(",myComplexSample,"), fullExportContent);
    // confirm subsamples lines
    assertTrue(
        fullExportContent.contains("\n# RSpace Inventory Export\n# Exported content: SUBSAMPLES"),
        fullExportContent);
    assertTrue(
        fullExportContent.contains(
            "Global ID,Name,Tags,Owner,Description,Parent Sample (Global ID),"
                + "Parent Container (Global ID),Quantity,Notes,\"Data (TEXT, SS"),
        fullExportContent);
    assertTrue(fullExportContent.contains(",mySubSample,"), fullExportContent);
    assertTrue(
        fullExportContent.contains(extraFieldContentFromComplexSubSample), fullExportContent);

    // export in compact mode
    settingsJson =
        "{ \"users\": [ \""
            + anyUser.getUsername()
            + "\"], \"exportMode\": \"COMPACT\", \"resultFileType\": \"SINGLE_CSV\" }";
    result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/export"))
                    .param("exportSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());
    job = getFromJsonResponseBody(result, ApiJob.class);
    result = downloadExportedFileLinkInExportJobResult(job);

    // confirm compact content
    String compactExportContent = result.getResponse().getContentAsString();
    assertNotNull(compactExportContent);
    assertTrue(
        compactExportContent.startsWith(
            "# RSpace Inventory Export\n# Exported content: CONTAINERS"),
        fullExportContent);
    assertTrue(compactExportContent.contains("# Export mode: COMPACT"), fullExportContent);
    // confirm container lines
    assertTrue(
        compactExportContent.contains(
            "Global ID,Name,Tags,Owner,Description,Parent Container (Global ID),Container Type,Can"
                + " Store Containers (Y/N),Can Store Subsamples (Y/N),Number of Stored"
                + " Containers,Number of Stored Subsamples\n"),
        compactExportContent);
    assertTrue(compactExportContent.contains(",my container,"), compactExportContent);
    // confirm samples lines
    assertTrue(
        compactExportContent.contains(
            "Global ID,Name,Tags,Owner,Description,Parent Template (Global ID),"
                + "Parent Template (name),Total Quantity,Expiry Date,Sample Source,"
                + "Storage Temperature (min),Storage Temperature (max)\n"),
        compactExportContent);
    assertTrue(compactExportContent.contains(",mySample,"), compactExportContent);
    assertTrue(compactExportContent.contains(",myComplexSample,"), compactExportContent);
    // confirm subsamples lines
    assertTrue(
        compactExportContent.contains(
            "Global ID,Name,Tags,Owner,Description,Parent Sample (Global ID),"
                + "Parent Container (Global ID),Quantity,Notes\n"),
        compactExportContent);
    assertTrue(compactExportContent.contains(",mySubSample,"), compactExportContent);
    assertFalse(
        compactExportContent.contains(extraFieldContentFromComplexSubSample), compactExportContent);

    assertTrue(compactExportContent.length() < fullExportContent.length());
  }

  @Test
  public void checkExportSettingsPostValidation() throws Exception {

    String settingsJson = "{ malformed";
    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/export"))
                    .param("exportSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andExpect(status().is4xxClientError())
            .andReturn();
    assertNotNull(result.getResolvedException());
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(error, "exportSettings should be of type");

    settingsJson = "{ \"globalIds\": [\"1\"] }";
    result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/export"))
                    .param("exportSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andExpect(status().is4xxClientError())
            .andReturn();
    assertNotNull(result.getResolvedException());
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(
        error, "Some of requested globalIds are not valid global identifiers: [1]");

    settingsJson = "{ \"exportMode\": \"UNKNOWN\", \"globalIds\": [\"SS1\"] }";
    result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/export"))
                    .param("exportSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andExpect(status().is4xxClientError())
            .andReturn();
    assertNotNull(result.getResolvedException());
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(error, "exportMode should be either 'FULL' or 'COMPACT'");

    settingsJson =
        "{ \"exportMode\": \"COMPACT\", \"globalIds\": [\"SS1\"], \"resultFileType\": \"CSV\" }";
    result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/export"))
                    .param("exportSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andExpect(status().is4xxClientError())
            .andReturn();
    assertNotNull(result.getResolvedException());
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertApiErrorContainsMessage(error, "resultFileType should be either 'ZIP' or 'SINGLE_CSV'");
  }
}
