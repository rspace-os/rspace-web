package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.InventoryBulkOperationStatus;
import com.researchspace.api.v1.model.ApiInventoryImportInstrumentImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportInstrumentParseResult;
import com.researchspace.api.v1.model.ApiInventoryImportResult;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.User;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
@TestPropertySource(properties = {"inventory.instrument.enabled=true"})
public class InventoryImportApiControllerInstrumentMVCIT extends API_MVC_InventoryTestBase {

  private User anyUser;
  private String apiKey;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    anyUser = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(anyUser);
  }

  private MockMultipartFile csvFile(String partName, String filename, String content) {
    return new MockMultipartFile(
        partName, filename, "text/csv", content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @Test
  public void parseInstrumentsCsvSuggestsTemplate() throws Exception {
    String csv = "Serial Number,Calibration\nSN-1,0.5\nSN-2,0.7\n";

    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/parseFile"))
                    .file(csvFile("file", "microscopes.csv", csv))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("recordType", "INSTRUMENTS")
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiInventoryImportInstrumentParseResult parseResult =
        getFromJsonResponseBody(result, ApiInventoryImportInstrumentParseResult.class);
    assertNotNull(parseResult);
    assertEquals(2, parseResult.getRowsCount());
    ApiInstrumentTemplatePost suggestedTemplate = parseResult.getTemplateInfo();
    assertNotNull(suggestedTemplate);
    assertEquals("microscopes", suggestedTemplate.getName());
    assertEquals(2, suggestedTemplate.getFields().size());
  }

  @Test
  public void importInstrumentsWithSuggestedTemplate() throws Exception {
    // step 1: parse → get suggested template
    String csv = "Name,Serial Number\nMicroscope-A,SN-1\nMicroscope-B,SN-2\n";

    MvcResult parseRes =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/parseFile"))
                    .file(csvFile("file", "microscopes.csv", csv))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("recordType", "INSTRUMENTS")
                    .header("apiKey", apiKey))
            .andReturn();
    ApiInventoryImportInstrumentParseResult parseResult =
        getFromJsonResponseBody(parseRes, ApiInventoryImportInstrumentParseResult.class);
    ApiInstrumentTemplatePost templateInfo = parseResult.getTemplateInfo();
    // first column is mapped to `name` via field-mappings; remove it from template fields so the
    // template has exactly the right number of remaining fields.
    templateInfo.getFields().remove(0);

    // step 2: import — with the parsed template
    String settingsJson =
        "{ \"instrumentSettings\": { \"fieldMappings\": { \"Name\": \"name\" },"
            + " \"templateInfo\": "
            + JacksonUtil.toJson(templateInfo)
            + " } }";

    MvcResult importRes =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(csvFile("instrumentsFile", "microscopes.csv", csv))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(importRes.getResolvedException());

    ApiInventoryImportResult importResult =
        getFromJsonResponseBody(importRes, ApiInventoryImportResult.class);
    assertEquals(InventoryBulkOperationStatus.COMPLETED, importResult.getStatus());

    ApiInventoryImportInstrumentImportResult instrumentResult = importResult.getInstrumentResult();
    assertNotNull(instrumentResult);
    assertTrue(instrumentResult.isTemplateCreated());
    ApiInventoryRecordInfo createdTemplate = instrumentResult.getTemplate().getRecord();
    assertNotNull(createdTemplate);
    assertEquals(2, instrumentResult.getSuccessCount());

    ApiInstrument firstInstrument =
        (ApiInstrument) instrumentResult.getResults().get(0).getRecord();
    assertEquals("Microscope-A", firstInstrument.getName());
    assertEquals(createdTemplate.getId(), firstInstrument.getTemplateId());
  }

  @Test
  public void importInstrumentsReusingExistingTemplate() throws Exception {
    // create a template via the manager first
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(anyUser, "existing-tmpl");

    // CSV with name + one column matching the template's 1 field (named "text")
    String csv = "Name,text\nMicroscope-X,val-x\nMicroscope-Y,val-y\n";

    String settingsJson =
        "{ \"instrumentSettings\": { \"fieldMappings\": { \"Name\": \"name\" },"
            + " \"templateId\": "
            + template.getId()
            + " } }";

    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(csvFile("instrumentsFile", "instruments.csv", csv))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiInventoryImportResult importResult =
        getFromJsonResponseBody(result, ApiInventoryImportResult.class);
    assertEquals(InventoryBulkOperationStatus.COMPLETED, importResult.getStatus());

    ApiInventoryImportInstrumentImportResult instrumentResult = importResult.getInstrumentResult();
    assertNotNull(instrumentResult);
    // template was not created — it was retrieved by id
    assertEquals(false, instrumentResult.isTemplateCreated());
    assertEquals(template.getId(), instrumentResult.getTemplate().getRecord().getId());
    assertEquals(2, instrumentResult.getSuccessCount());
  }

  @Test
  public void importInstrumentsWithoutTemplateInfoIsRejected() throws Exception {
    String csv = "Name,F1\nM-A,v\n";

    // settings have no templateInfo and no templateId — validator should reject
    String settingsJson =
        "{ \"instrumentSettings\": { \"fieldMappings\": { \"Name\": \"name\" } } }";

    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(csvFile("instrumentsFile", "x.csv", csv))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                    .is4xxClientError())
            .andReturn();
    assertTrue(
        result.getResolvedException() != null
            || result.getResponse().getStatus() == 400
            || result.getResponse().getStatus() == 422);
  }

  @Test
  public void importInstrumentsWithoutNameMappingIsRejected() throws Exception {
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(anyUser, "no-name-tmpl");
    String csv = "Whatever,text\nM-A,v\n";

    // fieldMappings missing `name` — validator should reject
    String settingsJson =
        "{ \"instrumentSettings\": { \"fieldMappings\": { \"Whatever\": \"description\" },"
            + " \"templateId\": "
            + template.getId()
            + " } }";

    mockMvc
        .perform(
            multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                .file(csvFile("instrumentsFile", "x.csv", csv))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .param("importSettings", settingsJson)
                .header("apiKey", apiKey))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .is4xxClientError())
        .andReturn();
  }
}
