package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.SubSampleApiManager;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end coverage for the RSDEV-1231 operation wizard endpoint (POST /operations), exercised
 * via a "Derive" request: a single POST must atomically create one new Sample parenting N
 * subsamples, put an IsDerivedFrom link back to the origin on the new Sample AND on every created
 * subsample, and reduce the origin subsample by the amount taken from it (never increasing it). See
 * adr/0001, adr/0002.
 *
 * <p>Authored with the feature; not run automatically (extends a real-transaction MVC base).
 */
@WebAppConfiguration
public class InventoryOperationsApiControllerMVCIT extends API_MVC_InventoryTestBase {

  private @Autowired SubSampleApiManager subSampleApiManager;

  private User anyUser;
  private String apiKey;

  @Before
  public void setup() throws Exception {
    super.setUp();
    anyUser = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(anyUser);
  }

  @Test
  public void deriveCreatesLinkedSampleAndReducesOriginByAmountTaken() throws Exception {
    // an existing subsample to be the origin of the Derive operation
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
    Long originId = origin.getId();
    String originGlobalId = origin.getGlobalId();
    Integer unitId = origin.getQuantity().getUnitId();
    java.math.BigDecimal originalAmount = origin.getQuantity().getNumericValue();

    // The frontend assembles this from operations_config.json + the user's input: a fully-built new
    // sample (2 subsamples, each carrying the link) plus the amount to take from the origin.
    String linkJson =
        "{\"name\":\"Is Derived From using process: PCR\","
            + "\"type\":\"link\",\"newFieldRequest\":true,"
            + "\"link\":{\"relationType\":\"IsDerivedFrom\",\"targetGlobalId\":\""
            + originGlobalId
            + "\",\"versionPin\":null}}";
    String subSampleJson =
        "{\"quantity\":{\"numericValue\":0.5,\"unitId\":"
            + unitId
            + "},\"extraFields\":["
            + linkJson
            + "]}";
    String operationJson =
        "{\"operationType\":\"DERIVE\","
            + "\"origins\":[{\"id\":"
            + originId
            + ",\"amountTaken\":{\"numericValue\":0.6,\"unitId\":"
            + unitId
            + "}}],"
            + "\"newSample\":{\"name\":\"Derived material\",\"extraFields\":["
            + linkJson
            + "],\"subSamples\":["
            + subSampleJson
            + ","
            + subSampleJson
            + "]}}";

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/operations", anyUser, operationJson))
            .andExpect(status().isCreated())
            .andReturn();
    ApiSampleWithFullSubSamples created =
        getFromJsonResponseBody(result, ApiSampleWithFullSubSamples.class);

    // the new sample carries the IsDerivedFrom link back to the origin
    ApiExtraField sampleLink = findLinkField(created.getExtraFields());
    assertNotNull(sampleLink, "the derived sample must carry the provenance link");
    assertEquals("IsDerivedFrom", sampleLink.getLink().getRelationType());
    assertEquals(originGlobalId, sampleLink.getLink().getTargetGlobalId());

    // ... and so does every created subsample
    assertEquals(2, created.getSubSamples().size());
    for (ApiSubSample ss : created.getSubSamples()) {
      ApiExtraField ssLink = findLinkField(ss.getExtraFields());
      assertNotNull(ssLink, "every created subsample must carry the provenance link");
      assertEquals(originGlobalId, ssLink.getLink().getTargetGlobalId());
    }

    // the origin has been REDUCED by the amount taken (0.6), in one transaction with the creation.
    // registerApiSubSampleUsage subtracts and clamps at zero, so the origin can never increase.
    java.math.BigDecimal expectedAfter =
        originalAmount.subtract(new java.math.BigDecimal("0.6")).max(java.math.BigDecimal.ZERO);
    ApiSubSample reloadedOrigin = subSampleApiManager.getApiSubSampleById(originId, anyUser);
    assertTrue(
        expectedAfter.compareTo(reloadedOrigin.getQuantity().getNumericValue()) == 0,
        "origin quantity should be reduced by the amount taken");
  }

  @Test
  public void operationCreatesDerivedSampleFromChosenTemplate() throws Exception {
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
    Long originId = origin.getId();
    String originGlobalId = origin.getGlobalId();
    Integer unitId = origin.getQuantity().getUnitId();

    // the user chooses an existing template (option "any") for the derived sample
    ApiSampleTemplatePost templatePost = new ApiSampleTemplatePost();
    templatePost.setName("operation target template");
    templatePost.setDefaultUnitId(RSUnitDef.GRAM.getId());
    MvcResult templateResult =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/sampleTemplates", anyUser, templatePost))
            .andExpect(status().isCreated())
            .andReturn();
    ApiSampleTemplate template = getFromJsonResponseBody(templateResult, ApiSampleTemplate.class);

    String linkJson =
        "{\"name\":\"Is Derived From\",\"type\":\"link\",\"newFieldRequest\":true,"
            + "\"link\":{\"relationType\":\"IsDerivedFrom\",\"targetGlobalId\":\""
            + originGlobalId
            + "\",\"versionPin\":null}}";
    String operationJson =
        "{\"operationType\":\"DERIVE\","
            + "\"origins\":[{\"id\":"
            + originId
            + ",\"amountTaken\":{\"numericValue\":0.6,\"unitId\":"
            + unitId
            + "}}],"
            + "\"newSample\":{\"name\":\"Derived from template\",\"templateId\":"
            + template.getId()
            + ",\"extraFields\":["
            + linkJson
            + "],\"subSamples\":[{\"quantity\":{\"numericValue\":0.5,\"unitId\":"
            + unitId
            + "},\"extraFields\":["
            + linkJson
            + "]}]}}";

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/operations", anyUser, operationJson))
            .andExpect(status().isCreated())
            .andReturn();
    ApiSampleWithFullSubSamples created =
        getFromJsonResponseBody(result, ApiSampleWithFullSubSamples.class);

    assertEquals(
        template.getId(),
        created.getTemplateId(),
        "the derived sample must be created from the chosen template");
  }

  @Test
  public void rejectsTakingMoreThanTheOriginHolds() throws Exception {
    // adr/0005: taking more than the origin currently holds must be rejected (400), not clamped,
    // and
    // must leave the origin untouched.
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
    Long originId = origin.getId();
    String originGlobalId = origin.getGlobalId();
    Integer unitId = origin.getQuantity().getUnitId();
    java.math.BigDecimal originalAmount = origin.getQuantity().getNumericValue();
    java.math.BigDecimal tooMuch = originalAmount.add(java.math.BigDecimal.ONE);

    String linkJson =
        "{\"name\":\"Is Derived From using process:"
            + " PCR\",\"type\":\"link\",\"newFieldRequest\":true,"
            + "\"link\":{\"relationType\":\"IsDerivedFrom\",\"targetGlobalId\":\""
            + originGlobalId
            + "\",\"versionPin\":null}}";
    String operationJson =
        "{\"operationType\":\"DERIVE\","
            + "\"origins\":[{\"id\":"
            + originId
            + ",\"amountTaken\":{\"numericValue\":"
            + tooMuch.toPlainString()
            + ",\"unitId\":"
            + unitId
            + "}}],"
            + "\"newSample\":{\"name\":\"Derived material\",\"extraFields\":["
            + linkJson
            + "],\"subSamples\":[{\"quantity\":{\"numericValue\":0.5,\"unitId\":"
            + unitId
            + "},\"extraFields\":["
            + linkJson
            + "]}]}}";

    mockMvc
        .perform(createBuilderForPostWithJSONBody(apiKey, "/operations", anyUser, operationJson))
        .andExpect(status().isBadRequest());

    // the origin is unchanged (the operation was rejected before any mutation)
    ApiSubSample reloadedOrigin = subSampleApiManager.getApiSubSampleById(originId, anyUser);
    assertTrue(
        originalAmount.compareTo(reloadedOrigin.getQuantity().getNumericValue()) == 0,
        "origin quantity must be unchanged when over-removal is rejected");
  }

  private ApiExtraField findLinkField(List<ApiExtraField> extraFields) {
    return extraFields.stream().filter(ef -> ef.getLink() != null).findFirst().orElse(null);
  }
}
