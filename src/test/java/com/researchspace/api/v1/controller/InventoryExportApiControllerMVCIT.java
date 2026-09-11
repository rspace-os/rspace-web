package com.researchspace.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class InventoryExportApiControllerMVCIT extends API_MVC_InventoryTestBase {

  User anyUser;
  String apiKey;

  private static final int EXPECTED_CONTENT_DISPOSITION_LENGTH =
      "attachment; filename=\"RSpace-2022-06-08-15-39-CSV-INVENTORY-pKGDzoo2GGXTkg.csv\"".length();

  @BeforeEach
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
    assertThat(job.getLinks()).hasSize(2);
    Map exportJobResultProps = (Map) job.getResult();
    assertThat(exportJobResultProps).hasSize(4);
    assertThat((Integer) exportJobResultProps.get("size")).isGreaterThan(500);

    result = downloadExportedFileLinkInExportJobResult(job);

    String contentHeader = result.getResponse().getHeader("Content-Disposition");
    assertThat(contentHeader).as(contentHeader).startsWith("attachment; filename=\"RSpace-");
    assertThat(contentHeader).as(contentHeader).hasSize(EXPECTED_CONTENT_DISPOSITION_LENGTH);

    // confirm subsamples fragment content
    String singleSubSampleCsvOutput = result.getResponse().getContentAsString();
    assertThat(singleSubSampleCsvOutput)
        .as(singleSubSampleCsvOutput)
        .startsWith("# RSpace Inventory Export\n# Exported content: SUBSAMPLES");
    assertNotNull(singleSubSampleCsvOutput);
    String expectedNoteContent =
        "\"Note created by \"\""
            + anyUser.getUsername()
            + "\"\" at "
            + Instant.ofEpochMilli(complexSubSample.getNotes().get(0).getCreationDateMillis())
                .toString()
            + ": \"\"test note\"\"\"";
    assertThat(singleSubSampleCsvOutput)
        .as(singleSubSampleCsvOutput)
        .contains(
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
                + ",any content\n");

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
    assertThat(contentHeader).as(contentHeader).startsWith("attachment; filename=\"RSpace-");
    assertThat(contentHeader).as(contentHeader).hasSize(EXPECTED_CONTENT_DISPOSITION_LENGTH);

    // confirm full content
    String fullExportContent = result.getResponse().getContentAsString();
    assertNotNull(fullExportContent);
    assertThat(fullExportContent)
        .as(fullExportContent)
        .startsWith("# RSpace Inventory Export\n# Exported content: CONTAINERS");
    assertThat(fullExportContent).as(fullExportContent).contains("# Export mode: FULL");
    // confirm container lines
    assertThat(fullExportContent)
        .as(fullExportContent)
        .contains(
            "Global ID,Name,Tags,Owner,Description,Parent Container (Global ID),Container Type,Can"
                + " Store Containers (Y/N),Can Store Subsamples (Y/N),Number of Stored"
                + " Containers,Number of Stored Subsamples\n");
    assertThat(fullExportContent).as(fullExportContent).contains(",my container,");
    assertTrue(fullExportContent.contains(",my container,"), fullExportContent);
    // confirm samples lines
    assertThat(fullExportContent)
        .as(fullExportContent)
        .contains(
            "Global ID,Name,Tags,Owner,Description,Parent Template (Global ID),Parent Template"
                + " (name),Total Quantity,Expiry Date,Sample Source,Storage Temperature"
                + " (min),Storage Temperature (max),\"MyNumber (NUMBER, IT");
    assertThat(fullExportContent).as(fullExportContent).contains(",mySample,");
    assertTrue(fullExportContent.contains(",mySample,"), fullExportContent);
    assertTrue(fullExportContent.contains(",myComplexSample,"), fullExportContent);
    // confirm subsamples lines
    assertThat(fullExportContent)
        .as(fullExportContent)
        .contains("\n# RSpace Inventory Export\n# Exported content: SUBSAMPLES");
    assertThat(fullExportContent)
        .as(fullExportContent)
        .contains(
            "Global ID,Name,Tags,Owner,Description,Parent Sample (Global ID),"
                + "Parent Container (Global ID),Quantity,Notes,\"Data (TEXT, SS");
    assertThat(fullExportContent).as(fullExportContent).contains(",mySubSample,");
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
    assertThat(compactExportContent)
        .as(fullExportContent)
        .startsWith("# RSpace Inventory Export\n# Exported content: CONTAINERS");
    assertThat(compactExportContent).as(fullExportContent).contains("# Export mode: COMPACT");
    // confirm container lines
    assertThat(compactExportContent)
        .as(compactExportContent)
        .contains(
            "Global ID,Name,Tags,Owner,Description,Parent Container (Global ID),Container Type,Can"
                + " Store Containers (Y/N),Can Store Subsamples (Y/N),Number of Stored"
                + " Containers,Number of Stored Subsamples\n");
    assertThat(compactExportContent).as(compactExportContent).contains(",my container,");
    assertTrue(compactExportContent.contains(",my container,"), compactExportContent);
    // confirm samples lines
    assertThat(compactExportContent)
        .as(compactExportContent)
        .contains(
            "Global ID,Name,Tags,Owner,Description,Parent Template (Global ID),"
                + "Parent Template (name),Total Quantity,Expiry Date,Sample Source,"
                + "Storage Temperature (min),Storage Temperature (max)\n");
    assertThat(compactExportContent).as(compactExportContent).contains(",mySample,");
    assertTrue(compactExportContent.contains(",mySample,"), compactExportContent);
    assertTrue(compactExportContent.contains(",myComplexSample,"), compactExportContent);
    // confirm subsamples lines
    assertThat(compactExportContent)
        .as(compactExportContent)
        .contains(
            "Global ID,Name,Tags,Owner,Description,Parent Sample (Global ID),"
                + "Parent Container (Global ID),Quantity,Notes\n");
    assertThat(compactExportContent).as(compactExportContent).contains(",mySubSample,");
    assertTrue(compactExportContent.contains(",mySubSample,"), compactExportContent);
    assertFalse(
        compactExportContent.contains(extraFieldContentFromComplexSubSample), compactExportContent);

    assertThat(compactExportContent.length()).isLessThan(fullExportContent.length());
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

  @Test
  public void exportInstrumentTemplateAsCsv() throws Exception {
    com.researchspace.api.v1.model.ApiInstrumentTemplate template =
        createBasicInstrumentTemplateForUser(anyUser, "export-target-template");

    String settingsJson =
        "{ \"globalIds\": [ \""
            + template.getGlobalId()
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
    assertNotNull(job);
    result = downloadExportedFileLinkInExportJobResult(job);

    String exportContent = result.getResponse().getContentAsString();
    assertNotNull(exportContent);
    // the exporter emits its own comment header for instrument templates
    assertThat(exportContent)
        .as("expected INSTRUMENT_TEMPLATES marker, got:\n" + exportContent)
        .contains("# Exported content: INSTRUMENT_TEMPLATES");
    // and the template's data row appears with its global id
    assertThat(exportContent)
        .as("expected the template global id " + template.getGlobalId() + " in:\n" + exportContent)
        .contains(template.getGlobalId());
    assertThat(exportContent)
        .as("expected the template name in:\n" + exportContent)
        .contains("export-target-template");
  }
}
