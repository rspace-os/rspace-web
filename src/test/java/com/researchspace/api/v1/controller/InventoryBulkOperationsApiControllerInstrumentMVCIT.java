package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationPost.BulkApiOperationType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import com.researchspace.model.User;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

/** MVC integration tests for bulk operations on Instrument and InstrumentTemplate records. */
@WebAppConfiguration
public class InventoryBulkOperationsApiControllerInstrumentMVCIT extends API_MVC_InventoryTestBase {

  private User anyUser;
  private String apiKey;

  @Before
  public void setup() throws Exception {
    super.setUp();
    anyUser = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(anyUser);
  }

  @Test
  public void bulkDuplicateInstrumentAndInstrumentTemplate_succeeds() throws Exception {
    // Create a standalone instrument (no template)
    String createInstrumentJson = "{ \"name\": \"bulk-dup-instrument\" }";
    MvcResult instrumentResult =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/instruments", anyUser, createInstrumentJson))
            .andReturn();
    ApiInstrument instrument = getFromJsonResponseBody(instrumentResult, ApiInstrument.class);
    assertNotNull(instrument);
    assertNotNull(instrument.getId());

    // Create a minimal instrument template
    ApiInstrumentTemplatePost templatePost = new ApiInstrumentTemplatePost();
    templatePost.setName("bulk-dup-template");
    MvcResult templateResult =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/instrumentTemplates", anyUser, templatePost))
            .andReturn();
    ApiInstrumentTemplate template =
        getFromJsonResponseBody(templateResult, ApiInstrumentTemplate.class);
    assertNotNull(template);
    assertNotNull(template.getId());

    // Bulk DUPLICATE: one INSTRUMENT + one INSTRUMENT_TEMPLATE
    String bulkJson =
        String.format(
            "{ \"operationType\": \"%s\", \"records\": ["
                + " { \"type\": \"INSTRUMENT\",          \"id\": %d },"
                + " { \"type\": \"INSTRUMENT_TEMPLATE\", \"id\": %d }"
                + "] }",
            BulkApiOperationType.DUPLICATE, instrument.getId(), template.getId());

    MvcResult result = postBulkOperation(bulkJson);
    assertNull(result.getResolvedException());

    ApiInventoryBulkOperationResult bulkOpResult =
        getFromJsonResponseBody(result, ApiInventoryBulkOperationResult.class);
    assertNotNull(bulkOpResult);
    assertEquals(2, bulkOpResult.getSuccessCount());
    assertEquals(0, bulkOpResult.getErrorCount());
    assertEquals(
        ApiInventoryRecordType.INSTRUMENT, bulkOpResult.getResults().get(0).getRecord().getType());
    assertEquals(
        ApiInventoryRecordType.INSTRUMENT_TEMPLATE,
        bulkOpResult.getResults().get(1).getRecord().getType());
  }

  private MvcResult postBulkOperation(String detailsJSON) throws Exception {
    return mockMvc
        .perform(
            createBuilderForPost(API_VERSION.ONE, apiKey, "/bulk", anyUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content(detailsJSON))
        .andReturn();
  }
}
