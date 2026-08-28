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
 * subsamples, put an IsDerivedFrom link back to the origin on the new Sample, and reduce the origin
 * subsample by the amount taken from it (never increasing it). Every extra field carries the
 * definition key that produced it ({@code operationFieldKey}); the request is whitelisted against
 * that definition, so the created subsamples carry a quantity and nothing else. See
 * DevDocs/adr/0007.
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
    // sample (2 subsamples, each holding a quantity and nothing else) plus the amount to take from
    // the origin. Every extra field carries the definition key that produced it.
    String linkJson =
        "{\"name\":\"Is Derived From using process: PCR\","
            + "\"type\":\"link\",\"newFieldRequest\":true,"
            + "\"operationFieldKey\":\"operations.derive.linkFieldName\","
            + "\"link\":{\"relationType\":\"IsDerivedFrom\",\"targetGlobalId\":\""
            + originGlobalId
            + "\",\"versionPin\":null}}";
    String subSampleJson = "{\"quantity\":{\"numericValue\":0.5,\"unitId\":" + unitId + "}}";
    String operationJson =
        "{\"operationType\":\"derive\","
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

    // ... while the created subsamples carry only their quantity: the operation's links and text
    // fields live on the sample, and a subsample extra field would now be rejected as undeclared.
    assertEquals(2, created.getSubSamples().size());
    for (ApiSubSample ss : created.getSubSamples()) {
      assertTrue(
          ss.getExtraFields().isEmpty(), "the operation puts no extra fields on its subsamples");
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

    String linkJson = deriveLinkJson(originGlobalId);
    String operationJson =
        "{\"operationType\":\"derive\","
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
            + "}}]}}";

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
    // DevDocs/adr/0007: taking more than the origin currently holds must be rejected (400), not
    // clamped,
    // and
    // must leave the origin untouched.
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
    Long originId = origin.getId();
    String originGlobalId = origin.getGlobalId();
    Integer unitId = origin.getQuantity().getUnitId();
    java.math.BigDecimal originalAmount = origin.getQuantity().getNumericValue();
    java.math.BigDecimal tooMuch = originalAmount.add(java.math.BigDecimal.ONE);

    String operationJson =
        "{\"operationType\":\"derive\","
            + "\"origins\":[{\"id\":"
            + originId
            + ",\"amountTaken\":{\"numericValue\":"
            + tooMuch.toPlainString()
            + ",\"unitId\":"
            + unitId
            + "}}],"
            + "\"newSample\":{\"name\":\"Derived material\",\"extraFields\":["
            + deriveLinkJson(originGlobalId)
            + "],\"subSamples\":[{\"quantity\":{\"numericValue\":0.5,\"unitId\":"
            + unitId
            + "}}]}}";

    mockMvc
        .perform(createBuilderForPostWithJSONBody(apiKey, "/operations", anyUser, operationJson))
        .andExpect(status().isBadRequest());

    // the origin is unchanged (the operation was rejected before any mutation)
    ApiSubSample reloadedOrigin = subSampleApiManager.getApiSubSampleById(originId, anyUser);
    assertTrue(
        originalAmount.compareTo(reloadedOrigin.getQuantity().getNumericValue()) == 0,
        "origin quantity must be unchanged when over-removal is rejected");
  }

  @Test
  public void rollsBackOriginDecrementWhenSampleCreationFailsInsideTheTransaction()
      throws Exception {
    // The atomicity claim (DevDocs/adr/0007) rests on InventoryOperationManager matching the
    // service.inventory.*Manager AOP pointcut; only a real transaction can prove it. Trigger an
    // in-transaction failure AFTER the origin decrement: the documentation link targets a document
    // that does not exist, so link creation (assertTargetExistsAndReadable) throws while the new
    // sample is being assembled, after registerApiSubSampleUsage already ran. Without a working
    // transaction the origin would silently lose quantity with no sample created.
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
    Long originId = origin.getId();
    String originGlobalId = origin.getGlobalId();
    Integer unitId = origin.getQuantity().getUnitId();
    java.math.BigDecimal originalAmount = origin.getQuantity().getNumericValue();

    String provenanceLink = deriveLinkJson(originGlobalId);
    String brokenDocumentationLink =
        "{\"name\":\"SOP\",\"type\":\"link\",\"newFieldRequest\":true,"
            + "\"operationFieldKey\":\"operations.documentationLink\","
            + "\"link\":{\"relationType\":\"IsDocumentedBy\",\"targetGlobalId\":\"SD999999999\","
            + "\"versionPin\":null}}";
    String operationJson =
        "{\"operationType\":\"derive\","
            + "\"origins\":[{\"id\":"
            + originId
            + ",\"amountTaken\":{\"numericValue\":0.6,\"unitId\":"
            + unitId
            + "}}],"
            + "\"newSample\":{\"name\":\"Rollback probe\",\"extraFields\":["
            + provenanceLink
            + ","
            + brokenDocumentationLink
            + "],\"subSamples\":[{\"quantity\":{\"numericValue\":0.5,\"unitId\":"
            + unitId
            + "}}]}}";

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/operations", anyUser, operationJson))
            .andReturn();
    assertTrue(
        result.getResponse().getStatus() >= 400,
        "a failing in-transaction link creation must not report success, was: "
            + result.getResponse().getStatus());

    // the origin's quantity is unchanged: the decrement was rolled back with the failed creation
    ApiSubSample reloadedOrigin = subSampleApiManager.getApiSubSampleById(originId, anyUser);
    assertTrue(
        originalAmount.compareTo(reloadedOrigin.getQuantity().getNumericValue()) == 0,
        "origin quantity must be restored when the operation fails mid-transaction");
  }

  // --- security-review hardening (2026-08-28 reviews): one end-to-end probe per fix ---

  /** POST /samples with one subsample holding exactly the given quantity; returns the sample. */
  private ApiSampleWithFullSubSamples createSampleHolding(String name, String value, int unitId)
      throws Exception {
    String sampleJson =
        "{\"name\":\""
            + name
            + "\",\"subSamples\":[{\"quantity\":{\"numericValue\":"
            + value
            + ",\"unitId\":"
            + unitId
            + "}}]}";
    MvcResult result =
        mockMvc
            .perform(createBuilderForPostWithJSONBody(apiKey, "/samples", anyUser, sampleJson))
            .andExpect(status().isCreated())
            .andReturn();
    return getFromJsonResponseBody(result, ApiSampleWithFullSubSamples.class);
  }

  private String hasPartLinkJson(String targetGlobalId) {
    return "{\"name\":\"Has Part "
        + targetGlobalId
        + "\",\"type\":\"link\",\"newFieldRequest\":true,"
        + "\"operationFieldKey\":\"operations.pool.linkFieldName\","
        + "\"link\":{\"relationType\":\"HasPart\",\"targetGlobalId\":\""
        + targetGlobalId
        + "\",\"versionPin\":null}}";
  }

  /** Derive's declared provenance link back to one origin, as the wizard builds it. */
  private String deriveLinkJson(String targetGlobalId) {
    return "{\"name\":\"Is Derived From using process: PCR\","
        + "\"type\":\"link\",\"newFieldRequest\":true,"
        + "\"operationFieldKey\":\"operations.derive.linkFieldName\","
        + "\"link\":{\"relationType\":\"IsDerivedFrom\",\"targetGlobalId\":\""
        + targetGlobalId
        + "\",\"versionPin\":null}}";
  }

  /** Aliquot's declared provenance link back to its origin. */
  private String isPartOfLinkJson(String targetGlobalId) {
    return "{\"name\":\"Derived from\",\"type\":\"link\",\"newFieldRequest\":true,"
        + "\"operationFieldKey\":\"operations.aliquot.linkFieldName\","
        + "\"link\":{\"relationType\":\"IsPartOf\",\"targetGlobalId\":\""
        + targetGlobalId
        + "\",\"versionPin\":null}}";
  }

  @Test
  public void rejectsPoolingAVolumeOriginWithAMassOrigin() throws Exception {
    // security review finding 4: the wizard blocks mixed-category pooling; the endpoint must too
    ApiSubSample volumeOrigin =
        createSampleHolding("F4a volume", "5", RSUnitDef.MILLI_LITRE.getId())
            .getSubSamples()
            .get(0);
    ApiSubSample massOrigin =
        createSampleHolding("F4a mass", "5", RSUnitDef.GRAM.getId()).getSubSamples().get(0);

    String operationJson =
        "{\"operationType\":\"pool\",\"origins\":["
            + "{\"id\":"
            + volumeOrigin.getId()
            + ",\"amountTaken\":{\"numericValue\":1,\"unitId\":"
            + RSUnitDef.MILLI_LITRE.getId()
            + "}},{\"id\":"
            + massOrigin.getId()
            + ",\"amountTaken\":{\"numericValue\":1,\"unitId\":"
            + RSUnitDef.GRAM.getId()
            + "}}],\"newSample\":{\"name\":\"Mixed pool\",\"extraFields\":["
            + hasPartLinkJson(volumeOrigin.getGlobalId())
            + ","
            + hasPartLinkJson(massOrigin.getGlobalId())
            + "],\"subSamples\":[{\"quantity\":{\"numericValue\":2,\"unitId\":"
            + RSUnitDef.MILLI_LITRE.getId()
            + "}}]}}";

    mockMvc
        .perform(createBuilderForPostWithJSONBody(apiKey, "/operations", anyUser, operationJson))
        .andExpect(status().isBadRequest());

    for (ApiSubSample origin : List.of(volumeOrigin, massOrigin)) {
      ApiSubSample reloaded = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
      assertTrue(
          origin.getQuantity().getNumericValue().compareTo(reloaded.getQuantity().getNumericValue())
              == 0,
          "origins must be unchanged when the category mismatch is rejected");
    }
  }

  @Test
  public void provenanceLinkInsideATextTypedFieldIsRejectedNotSilentlyDropped() throws Exception {
    // security review finding 7: persistence only creates a link for a link-typed field, so a link
    // payload in a text-typed field must fail validation (400) rather than 201 with the link gone
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
    Integer unitId = origin.getQuantity().getUnitId();
    java.math.BigDecimal originalAmount = origin.getQuantity().getNumericValue();

    String textCarriedLink =
        "{\"name\":\"Is Derived From\",\"type\":\"text\",\"newFieldRequest\":true,"
            + "\"operationFieldKey\":\"operations.derive.linkFieldName\","
            + "\"link\":{\"relationType\":\"IsDerivedFrom\",\"targetGlobalId\":\""
            + origin.getGlobalId()
            + "\",\"versionPin\":null}}";
    String operationJson =
        "{\"operationType\":\"derive\",\"origins\":[{\"id\":"
            + origin.getId()
            + ",\"amountTaken\":{\"numericValue\":0.6,\"unitId\":"
            + unitId
            + "}}],\"newSample\":{\"name\":\"Derived material\",\"extraFields\":["
            + textCarriedLink
            + "],\"subSamples\":[{\"quantity\":{\"numericValue\":0.5,\"unitId\":"
            + unitId
            + "}}]}}";

    mockMvc
        .perform(createBuilderForPostWithJSONBody(apiKey, "/operations", anyUser, operationJson))
        .andExpect(status().isBadRequest());

    ApiSubSample reloaded = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
    assertTrue(
        originalAmount.compareTo(reloaded.getQuantity().getNumericValue()) == 0,
        "origin must be unchanged when the provenance link is not an effective link field");
  }

  @Test
  public void rejectsUnequalChildQuantitiesForAnEachAmountOperation() throws Exception {
    // valid-payload review finding 2: the API documents N equal subsamples (one each-amount input
    // copied to all children); 0.25 + 0.75 must be rejected, not silently persisted
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
    Integer unitId = origin.getQuantity().getUnitId();
    java.math.BigDecimal originalAmount = origin.getQuantity().getNumericValue();

    String linkJson = isPartOfLinkJson(origin.getGlobalId());
    String operationJson =
        "{\"operationType\":\"aliquot\",\"origins\":[{\"id\":"
            + origin.getId()
            + ",\"amountTaken\":{\"numericValue\":1,\"unitId\":"
            + unitId
            + "}}],\"newSample\":{\"name\":\"Uneven aliquots\",\"extraFields\":["
            + linkJson
            + "],\"subSamples\":["
            + "{\"quantity\":{\"numericValue\":0.25,\"unitId\":"
            + unitId
            + "}},"
            + "{\"quantity\":{\"numericValue\":0.75,\"unitId\":"
            + unitId
            + "}}]}}";

    mockMvc
        .perform(createBuilderForPostWithJSONBody(apiKey, "/operations", anyUser, operationJson))
        .andExpect(status().isBadRequest());

    ApiSubSample reloaded = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
    assertTrue(
        originalAmount.compareTo(reloaded.getQuantity().getNumericValue()) == 0,
        "origin must be unchanged when unequal children are rejected");
  }

  @Test
  public void originExtraFieldLinkingTheOriginToItselfIsRejected() throws Exception {
    // valid-payload review finding 1: the create-field path must enforce the self-link rule against
    // the authoritative parent (the payload's parentGlobalId is client-supplied and was forgeable).
    // The strict whitelist closes this a second way: Destroy declares only its disposed-date origin
    // field, so a "Self reference" link field is undeclared content (DevDocs/adr/0007).
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
    java.math.BigDecimal originalAmount = origin.getQuantity().getNumericValue();
    Integer unitId = origin.getQuantity().getUnitId();

    String selfLinkField =
        "{\"name\":\"Self reference\",\"type\":\"link\",\"newFieldRequest\":true,"
            + "\"link\":{\"relationType\":\"References\",\"targetGlobalId\":\""
            + origin.getGlobalId()
            + "\",\"versionPin\":null}}";
    String operationJson =
        "{\"operationType\":\"destroy\",\"origins\":[{\"id\":"
            + origin.getId()
            + ",\"amountTaken\":{\"numericValue\":"
            + originalAmount.toPlainString()
            + ",\"unitId\":"
            + unitId
            + "},\"extraFields\":["
            + selfLinkField
            + "]}]}";

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/operations", anyUser, operationJson))
            .andReturn();
    assertTrue(
        result.getResponse().getStatus() >= 400,
        "a self-link origin field must not report success, was: "
            + result.getResponse().getStatus());

    // the rejection rolled the whole operation back: quantity untouched, no self-link persisted
    ApiSubSample reloaded = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
    assertTrue(
        originalAmount.compareTo(reloaded.getQuantity().getNumericValue()) == 0,
        "origin quantity must be unchanged when the self-link is rejected");
    assertTrue(
        reloaded.getExtraFields().stream().allMatch(ef -> ef.getLink() == null),
        "no self-link field may be persisted on the origin");
  }

  // --- strict full-definition validation (DevDocs/adr/0007): one probe per review repro ---

  /** Posts the body, expects a 400, and asserts the origin was left untouched. */
  private void assertRejectedLeavingOriginUnchanged(ApiSubSample origin, String operationJson)
      throws Exception {
    java.math.BigDecimal before = origin.getQuantity().getNumericValue();
    mockMvc
        .perform(createBuilderForPostWithJSONBody(apiKey, "/operations", anyUser, operationJson))
        .andExpect(status().isBadRequest());
    ApiSubSample reloaded = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
    assertTrue(
        before.compareTo(reloaded.getQuantity().getNumericValue()) == 0,
        "origin must be unchanged when the request does not match its operation definition");
  }

  /** An Aliquot request body with the given extra fields on its new sample. */
  private String aliquotJson(ApiSubSample origin, String sampleExtraFieldsJson) {
    return "{\"operationType\":\"aliquot\",\"origins\":[{\"id\":"
        + origin.getId()
        + ",\"amountTaken\":{\"numericValue\":1,\"unitId\":"
        + origin.getQuantity().getUnitId()
        + "}}],\"newSample\":{\"name\":\"Aliquots\",\"extraFields\":["
        + sampleExtraFieldsJson
        + "],\"subSamples\":[{\"quantity\":{\"numericValue\":0.5,\"unitId\":"
        + origin.getQuantity().getUnitId()
        + "}}]}}";
  }

  @Test
  public void rejectsOriginFieldOnAnOperationThatDeclaresNone() throws Exception {
    // review repro F5a: only Destroy declares an origin field, so writing one to an Aliquot origin
    // is content no definition describes
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    String undeclaredOriginField =
        "{\"name\":\"Disposed\",\"type\":\"text\",\"newFieldRequest\":true,"
            + "\"operationFieldKey\":\"operations.destroy.disposedField\","
            + "\"content\":\"2026-08-28\"}";
    String operationJson =
        "{\"operationType\":\"aliquot\",\"origins\":[{\"id\":"
            + origin.getId()
            + ",\"amountTaken\":{\"numericValue\":1,\"unitId\":"
            + origin.getQuantity().getUnitId()
            + "},\"extraFields\":["
            + undeclaredOriginField
            + "]}],\"newSample\":{\"name\":\"Aliquots\",\"extraFields\":["
            + isPartOfLinkJson(origin.getGlobalId())
            + "],\"subSamples\":[{\"quantity\":{\"numericValue\":0.5,\"unitId\":"
            + origin.getQuantity().getUnitId()
            + "}}]}}";
    assertRejectedLeavingOriginUnchanged(origin, operationJson);
  }

  @Test
  public void rejectsDestroyWithoutItsDeclaredDisposedField() throws Exception {
    // review repro F5b: Destroy declares a disposed-date field on each origin; omitting it would
    // empty the subsample with no record of the disposal
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    String operationJson =
        "{\"operationType\":\"destroy\",\"origins\":[{\"id\":"
            + origin.getId()
            + ",\"amountTaken\":{\"numericValue\":"
            + origin.getQuantity().getNumericValue().toPlainString()
            + ",\"unitId\":"
            + origin.getQuantity().getUnitId()
            + "}}]}";
    assertRejectedLeavingOriginUnchanged(origin, operationJson);
  }

  @Test
  public void rejectsPassageWithoutItsDeclaredPassageNumberField() throws Exception {
    // review repro F5c: Passage declares a passage-number text field on the new sample
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    String linkJson =
        "{\"name\":\"Passaged from\",\"type\":\"link\",\"newFieldRequest\":true,"
            + "\"operationFieldKey\":\"operations.passage.linkFieldName\","
            + "\"link\":{\"relationType\":\"IsDerivedFrom\",\"targetGlobalId\":\""
            + origin.getGlobalId()
            + "\",\"versionPin\":null}}";
    String operationJson =
        "{\"operationType\":\"passage\",\"origins\":[{\"id\":"
            + origin.getId()
            + ",\"amountTaken\":{\"numericValue\":0,\"unitId\":"
            + origin.getQuantity().getUnitId()
            + "}}],\"newSample\":{\"name\":\"HeLa p3\",\"extraFields\":["
            + linkJson
            + "],\"subSamples\":[{\"quantity\":{\"numericValue\":0.5,\"unitId\":"
            + origin.getQuantity().getUnitId()
            + "}}]}}";
    assertRejectedLeavingOriginUnchanged(origin, operationJson);
  }

  @Test
  public void rejectsCryopreserveWithAStorageTemperatureRangeRatherThanOneValue() throws Exception {
    // review repro F5d: one temperature input feeds both bounds, so a range describes a sample the
    // operation cannot produce
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    String linkJson =
        "{\"name\":\"Frozen from\",\"type\":\"link\",\"newFieldRequest\":true,"
            + "\"operationFieldKey\":\"operations.cryopreserve.linkFieldName\","
            + "\"link\":{\"relationType\":\"IsDerivedFrom\",\"targetGlobalId\":\""
            + origin.getGlobalId()
            + "\",\"versionPin\":null}}";
    String cryomediumJson =
        "{\"name\":\"Cryomedium\",\"type\":\"text\",\"newFieldRequest\":true,"
            + "\"operationFieldKey\":\"operations.cryopreserve.cryomediumField\","
            + "\"content\":\"10% DMSO\"}";
    String operationJson =
        "{\"operationType\":\"cryopreserve\",\"origins\":[{\"id\":"
            + origin.getId()
            + ",\"amountTaken\":{\"numericValue\":1,\"unitId\":"
            + origin.getQuantity().getUnitId()
            + "}}],\"newSample\":{\"name\":\"Frozen cells\","
            + "\"storageTempMin\":{\"numericValue\":-80,\"unitId\":"
            + RSUnitDef.CELSIUS.getId()
            + "},\"storageTempMax\":{\"numericValue\":-20,\"unitId\":"
            + RSUnitDef.CELSIUS.getId()
            + "},\"extraFields\":["
            + linkJson
            + ","
            + cryomediumJson
            + "],\"subSamples\":[{\"quantity\":{\"numericValue\":1,\"unitId\":"
            + origin.getQuantity().getUnitId()
            + "}}]}}";
    assertRejectedLeavingOriginUnchanged(origin, operationJson);
  }

  @Test
  public void rejectsSharingSmuggledOntoTheNewSample() throws Exception {
    // review repro F5e: no operation definition declares sharing, so the endpoint must not be a
    // back door into it
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    String operationJson =
        aliquotJson(origin, isPartOfLinkJson(origin.getGlobalId()))
            .replace(
                "\"newSample\":{\"name\":\"Aliquots\"",
                "\"newSample\":{\"sharingMode\":\"WHITELIST\",\"name\":\"Aliquots\"");
    assertRejectedLeavingOriginUnchanged(origin, operationJson);
  }

  @Test
  public void rejectsSubSamplePlacementSmuggledOntoTheNewSample() throws Exception {
    // review repro F5f: the created subsamples go to the workbench; placement is not part of any
    // operation definition
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    String operationJson =
        "{\"operationType\":\"aliquot\",\"origins\":[{\"id\":"
            + origin.getId()
            + ",\"amountTaken\":{\"numericValue\":1,\"unitId\":"
            + origin.getQuantity().getUnitId()
            + "}}],\"newSample\":{\"name\":\"Aliquots\",\"extraFields\":["
            + isPartOfLinkJson(origin.getGlobalId())
            + "],\"subSamples\":[{\"quantity\":{\"numericValue\":0.5,\"unitId\":"
            + origin.getQuantity().getUnitId()
            + "},\"parentLocation\":{\"id\":1}}]}}";
    assertRejectedLeavingOriginUnchanged(origin, operationJson);
  }

  @Test
  public void rejectsAnExtraFieldWithNoDefinitionKey() throws Exception {
    // Fields are matched by key, not by name: a field the wizard never built carries no key the
    // definition declares, so it cannot be smuggled in under a plausible display name.
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    String unkeyedField =
        "{\"name\":\"Derived from\",\"type\":\"text\",\"newFieldRequest\":true,"
            + "\"content\":\"anything\"}";
    assertRejectedLeavingOriginUnchanged(
        origin, aliquotJson(origin, isPartOfLinkJson(origin.getGlobalId()) + "," + unkeyedField));
  }

  private ApiExtraField findLinkField(List<ApiExtraField> extraFields) {
    return extraFields.stream().filter(ef -> ef.getLink() != null).findFirst().orElse(null);
  }
}
