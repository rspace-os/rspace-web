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
import com.researchspace.apiutils.ApiError;
import com.researchspace.model.User;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.SubSampleApiManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

  @BeforeEach
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

  /** Posts the body, expects a 400, asserts the origin was left untouched, returns the response. */
  private MvcResult assertRejectedLeavingOriginUnchanged(ApiSubSample origin, String operationJson)
      throws Exception {
    java.math.BigDecimal before = origin.getQuantity().getNumericValue();
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/operations", anyUser, operationJson))
            .andExpect(status().isBadRequest())
            .andReturn();
    ApiSubSample reloaded = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
    assertTrue(
        before.compareTo(reloaded.getQuantity().getNumericValue()) == 0,
        "origin must be unchanged when the request does not match its operation definition");
    return result;
  }

  /**
   * The field paths named by a rejection's error messages ("path: message"). Unchecked so it can be
   * used inside an assertion's message supplier.
   */
  private List<String> rejectedFields(MvcResult result) {
    try {
      return getErrorFromJsonResponseBody(result, ApiError.class).getErrors().stream()
          .map(message -> message.substring(0, Math.max(message.indexOf(':'), 0)))
          .toList();
    } catch (Exception unreadableBody) {
      throw new IllegalStateException("could not read the error response body", unreadableBody);
    }
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

  /**
   * RSDEV-1231: identical requests racing the same origin used to have every losing request surface
   * an uncaught commit-time conflict (a deadlock, or "record has changed since last read") as a
   * 500, even though the origin itself always ended up in the correct final state. Fires {@code
   * count} copies of {@code operationJson} concurrently and returns each response status.
   */
  private List<Integer> fireConcurrentOperationRequests(String operationJson, int count)
      throws Exception {
    return fireConcurrentOperationRequests(Collections.nCopies(count, operationJson));
  }

  /**
   * As above, but each request has its own body, so requests that overlap only partly (two Pools
   * sharing one of their origins) can race each other.
   */
  private List<Integer> fireConcurrentOperationRequests(List<String> operationJsons)
      throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(operationJsons.size());
    try {
      List<Callable<Integer>> requests = new ArrayList<>();
      for (String operationJson : operationJsons) {
        requests.add(
            () ->
                mockMvc
                    .perform(
                        createBuilderForPostWithJSONBody(
                            apiKey, "/operations", anyUser, operationJson))
                    .andReturn()
                    .getResponse()
                    .getStatus());
      }
      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> result : pool.invokeAll(requests)) {
        statuses.add(result.get());
      }
      return statuses;
    } finally {
      pool.shutdown();
    }
  }

  @Test
  public void parallelDestroyRequestsAgainstTheSameOriginNeverReturn5xx() throws Exception {
    // Destroy must take the origin's entire quantity, so only the first of these to commit can
    // still match it; every other request should lose cleanly (400/409), never 500.
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    String disposedField =
        "{\"name\":\"Disposed\",\"type\":\"text\",\"newFieldRequest\":true,"
            + "\"operationFieldKey\":\"operations.destroy.disposedField\",\"content\":\"2026-09-03\"}";
    String operationJson =
        "{\"operationType\":\"destroy\",\"origins\":[{\"id\":"
            + origin.getId()
            + ",\"amountTaken\":{\"numericValue\":"
            + origin.getQuantity().getNumericValue().toPlainString()
            + ",\"unitId\":"
            + origin.getQuantity().getUnitId()
            + "},\"extraFields\":["
            + disposedField
            + "]}]}";

    int samplesBefore = sampleCount();
    List<Integer> statuses = fireConcurrentOperationRequests(operationJson, 5);

    assertTrue(statuses.stream().noneMatch(status -> status >= 500), () -> "5xx in " + statuses);
    assertEquals(
        1,
        statuses.stream().filter(status -> status == 201).count(),
        () -> "exactly one request should win the race, got " + statuses);
    ApiSubSample reloaded = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
    assertTrue(
        java.math.BigDecimal.ZERO.compareTo(reloaded.getQuantity().getNumericValue()) == 0,
        "origin should be fully consumed by the one request that won");
    // A losing request must roll back completely, not just fail to decrement: Destroy creates no
    // sample, so a partially-applied loser would show up as a stray one here.
    assertEquals(samplesBefore, sampleCount(), "a terminal operation creates no sample");
  }

  @Test
  public void parallelAliquotRequestsAgainstTheSameOriginNeverReturn5xx() throws Exception {
    // Aliquot only decrements, so several concurrent requests can legitimately all succeed before
    // the origin runs out; the race is only over which of them commits first, so none may 500.
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    java.math.BigDecimal originalAmount = origin.getQuantity().getNumericValue();
    String operationJson = aliquotJson(origin, isPartOfLinkJson(origin.getGlobalId()));

    int samplesBefore = sampleCount();
    List<Integer> statuses = fireConcurrentOperationRequests(operationJson, 5);

    assertTrue(statuses.stream().noneMatch(status -> status >= 500), () -> "5xx in " + statuses);
    long successes = statuses.stream().filter(status -> status == 201).count();
    ApiSubSample reloaded = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
    java.math.BigDecimal expected =
        originalAmount.subtract(java.math.BigDecimal.valueOf(successes));
    assertTrue(
        expected.compareTo(reloaded.getQuantity().getNumericValue()) == 0,
        () -> "origin should be reduced by exactly " + successes + " g, got statuses " + statuses);
    // The origin ending up right is only half the invariant: each 201 must have produced exactly
    // one sample, and each loser none, or a rolled-back decrement would still leave its output.
    assertEquals(
        samplesBefore + successes,
        sampleCount(),
        () -> "one created sample per 201, got statuses " + statuses);
  }

  // --- code review (2026-09-03) reproductions: each is a field-scoped 400 leaving the origin
  // untouched, where it used to be a 422 or a 201 with wrong data ---

  /** An Aliquot request body with explicit amount-taken and single-child JSON. */
  private String aliquotJsonWith(
      ApiSubSample origin, String amountTakenJson, String subSampleJson, String newSampleExtras) {
    return "{\"operationType\":\"aliquot\",\"origins\":[{\"id\":"
        + origin.getId()
        + ",\"amountTaken\":"
        + amountTakenJson
        + "}],\"newSample\":{\"name\":\"Aliquots\","
        + newSampleExtras
        + "\"extraFields\":["
        + isPartOfLinkJson(origin.getGlobalId())
        + "],\"subSamples\":["
        + subSampleJson
        + "]}}";
  }

  private static String quantityJson(String value, int unitId) {
    return "{\"numericValue\":" + value + ",\"unitId\":" + unitId + "}";
  }

  @Test
  public void rejectsAmountTakenInAUnitThatDoesNotExist() throws Exception {
    // review repro f4-unknown: used to reach QuantityUtils.sum and surface as a 422
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = origin.getQuantity().getUnitId();
    assertRejectedLeavingOriginUnchanged(
        origin,
        aliquotJsonWith(
            origin,
            quantityJson("1", 999999),
            "{\"quantity\":" + quantityJson("0.5", unitId) + "}",
            ""));
  }

  @Test
  public void rejectsAmountTakenInADifferentCategoryThanTheOrigin() throws Exception {
    // review repro f4-category: millilitres taken from a gram origin used to be a 422
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = origin.getQuantity().getUnitId();
    assertRejectedLeavingOriginUnchanged(
        origin,
        aliquotJsonWith(
            origin,
            quantityJson("1", RSUnitDef.MILLI_LITRE.getId()),
            "{\"quantity\":" + quantityJson("0.5", unitId) + "}",
            ""));
  }

  @Test
  public void rejectsANewSubSampleInADifferentCategoryThanTheOrigin() throws Exception {
    // review repro f5: a millilitre child from a gram origin used to be created
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = origin.getQuantity().getUnitId();
    assertRejectedLeavingOriginUnchanged(
        origin,
        aliquotJsonWith(
            origin,
            quantityJson("1", unitId),
            "{\"quantity\":" + quantityJson("0.5", RSUnitDef.MILLI_LITRE.getId()) + "}",
            ""));
  }

  @Test
  public void rejectsANewSubSampleOutsideTheChosenTemplatesCategory() throws Exception {
    // review repro f5-template: gram children under a volume template used to be created when a
    // comparable top-level quantity was sent as a decoy
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = origin.getQuantity().getUnitId();
    ApiSampleTemplatePost templatePost = new ApiSampleTemplatePost();
    templatePost.setName("RSDEV-1231 volume template");
    templatePost.setDefaultUnitId(RSUnitDef.MILLI_LITRE.getId());
    MvcResult templateResult =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/sampleTemplates", anyUser, templatePost))
            .andExpect(status().isCreated())
            .andReturn();
    ApiSampleTemplate template = getFromJsonResponseBody(templateResult, ApiSampleTemplate.class);
    assertRejectedLeavingOriginUnchanged(
        origin,
        aliquotJsonWith(
            origin,
            quantityJson("1", unitId),
            "{\"quantity\":" + quantityJson("0.5", unitId) + "}",
            "\"templateId\":"
                + template.getId()
                + ",\"quantity\":"
                + quantityJson("0.5", RSUnitDef.MILLI_LITRE.getId())
                + ","));
  }

  @Test
  public void rejectsADocumentationLinkToAnInventoryRecord() throws Exception {
    // review repro f6: IsDocumentedBy pointing at the origin subsample itself used to be stored
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    String documentation =
        "{\"name\":\"Documented by\",\"type\":\"link\",\"newFieldRequest\":true,"
            + "\"operationFieldKey\":\"operations.documentationLink\","
            + "\"link\":{\"relationType\":\"IsDocumentedBy\",\"targetGlobalId\":\""
            + origin.getGlobalId()
            + "\",\"versionPin\":null}}";
    assertRejectedLeavingOriginUnchanged(
        origin, aliquotJson(origin, isPartOfLinkJson(origin.getGlobalId()) + "," + documentation));
  }

  @Test
  public void rejectsANewSubSampleQuantityFinerThanTheStored3dp() throws Exception {
    // review repro f7: 0.0004 used to persist as a subsample holding 0
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = origin.getQuantity().getUnitId();
    assertRejectedLeavingOriginUnchanged(
        origin,
        aliquotJsonWith(
            origin,
            quantityJson("1", unitId),
            "{\"quantity\":" + quantityJson("0.0004", unitId) + "}",
            ""));
  }

  @Test
  public void rejectsANameAndIconSmuggledOntoANewSubSample() throws Exception {
    // review repro f9: both used to be persisted on the created subsample
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = origin.getQuantity().getUnitId();
    MvcResult rejection =
        assertRejectedLeavingOriginUnchanged(
            origin,
            aliquotJsonWith(
                origin,
                quantityJson("1", unitId),
                "{\"quantity\":"
                    + quantityJson("0.5", unitId)
                    + ",\"name\":\"Injected child name\",\"iconId\":424242}",
                ""));
    // both properties are reported, each against the child that carried it, so a client can see
    // which field to drop rather than a single opaque "bad request"
    assertTrue(
        rejectedFields(rejection)
            .containsAll(List.of("newSample.subSamples[0].name", "newSample.subSamples[0].iconId")),
        () -> "expected both field paths, got " + rejectedFields(rejection));
  }

  @Test
  public void rejectsAnIconSmuggledOntoTheNewSample() throws Exception {
    // The child's icon is covered above; the sample's own icon is a separate property and no
    // operation declares it either (code review, finding 9).
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = origin.getQuantity().getUnitId();
    MvcResult rejection =
        assertRejectedLeavingOriginUnchanged(
            origin,
            aliquotJsonWith(
                origin,
                quantityJson("1", unitId),
                "{\"quantity\":" + quantityJson("0.5", unitId) + "}",
                "\"iconId\":424242,"));
    assertTrue(
        rejectedFields(rejection).contains("newSample.iconId"),
        () -> "expected newSample.iconId, got " + rejectedFields(rejection));
  }

  @Test
  public void rejectsNullListElementsAsFieldScoped400() throws Exception {
    // review repro f3: a JSON "[null]" element reached the delegated samples validator and NPEd,
    // surfacing as a 500. Each must now be a field-scoped 400 naming the list it came from.
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = origin.getQuantity().getUnitId();
    String child = "{\"quantity\":" + quantityJson("0.5", unitId) + "}";
    List<String> bodiesWithANullElement =
        List.of(
            aliquotJsonWith(origin, quantityJson("1", unitId), child, "\"tags\":[null],"),
            aliquotJson(origin, isPartOfLinkJson(origin.getGlobalId()) + ",null"),
            aliquotJsonWith(origin, quantityJson("1", unitId), "null", ""));
    for (String body : bodiesWithANullElement) {
      MvcResult rejection = assertRejectedLeavingOriginUnchanged(origin, body);
      assertTrue(
          rejectedFields(rejection).stream().allMatch(field -> field.startsWith("newSample.")),
          () ->
              "expected newSample-scoped errors for "
                  + body
                  + ", got "
                  + rejectedFields(rejection));
    }
  }

  @Test
  public void acceptsAmountTakenInAnotherUnitOfTheOriginsCategory() throws Exception {
    // The origin's category is fixed, not its unit: the rejection tests above must not have
    // tightened into unit equality. 1000 mg taken from a 5 g origin leaves 4 g.
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = origin.getQuantity().getUnitId();
    mockMvc
        .perform(
            createBuilderForPostWithJSONBody(
                apiKey,
                "/operations",
                anyUser,
                aliquotJsonWith(
                    origin,
                    quantityJson("1000", RSUnitDef.MILLI_GRAM.getId()),
                    "{\"quantity\":" + quantityJson("0.5", unitId) + "}",
                    "")))
        .andExpect(status().isCreated());

    ApiSubSample reloaded = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
    assertTrue(
        origin
                .getQuantity()
                .getNumericValue()
                .subtract(java.math.BigDecimal.ONE)
                .compareTo(reloaded.getQuantity().getNumericValue())
            == 0,
        () -> "origin should be reduced by 1 g, got " + reloaded.getQuantity().getNumericValue());
  }

  @Test
  public void derivesTheCreatedSampleTotalFromItsChildrenNotTheTopLevelQuantity() throws Exception {
    // The server ignores newSample.quantity when children are posted and sums them instead, which
    // is why the template check had to reach the children rather than trusting a comparable
    // top-level value (code review, finding 5). Pinning it here keeps that reasoning testable.
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = origin.getQuantity().getUnitId();
    String child = "{\"quantity\":" + quantityJson("0.5", unitId) + "}";
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey,
                    "/operations",
                    anyUser,
                    aliquotJsonWith(
                        origin,
                        quantityJson("1", unitId),
                        child + "," + child,
                        "\"quantity\":" + quantityJson("99", unitId) + ",")))
            .andExpect(status().isCreated())
            .andReturn();

    ApiSampleWithFullSubSamples created =
        getFromJsonResponseBody(result, ApiSampleWithFullSubSamples.class);
    assertEquals(2, created.getSubSamples().size());
    assertTrue(
        new java.math.BigDecimal("1").compareTo(created.getQuantity().getNumericValue()) == 0,
        () -> "expected the two 0.5 children to total 1, got " + created.getQuantity());
    assertEquals(unitId, created.getQuantity().getUnitId());
  }

  @Test
  public void parallelPoolRequestsOverOverlappingOriginsNeverDeadlock() throws Exception {
    // Two Pools sharing one origin lock two rows each. Locked in request order they would deadlock
    // (each holding what the other wants), which is why the manager sorts origins by id before
    // locking (code review, finding 1). The bodies below list their origins in opposite orders, so
    // only that sort keeps them from crossing. Repeated over fresh origins: a deadlock is a race,
    // and one round can miss it.
    for (int round = 0; round < 5; round++) {
      ApiSubSample a =
          createSampleHolding("pool A " + round, "5", RSUnitDef.GRAM.getId())
              .getSubSamples()
              .get(0);
      ApiSubSample b =
          createSampleHolding("pool B " + round, "5", RSUnitDef.GRAM.getId())
              .getSubSamples()
              .get(0);
      ApiSubSample c =
          createSampleHolding("pool C " + round, "5", RSUnitDef.GRAM.getId())
              .getSubSamples()
              .get(0);

      List<Integer> statuses =
          fireConcurrentOperationRequests(
              List.of(poolJson("pool BA " + round, b, a), poolJson("pool BC " + round, b, c)));

      assertTrue(
          statuses.stream().noneMatch(status -> status >= 500),
          () -> "5xx from overlapping pools: " + statuses);
      // Each 201 took 1 g from each of its two origins; A and C are named once, B by both.
      int baWon = statuses.get(0) == 201 ? 1 : 0;
      int bcWon = statuses.get(1) == 201 ? 1 : 0;
      assertQuantityIs(a, 5 - baWon, statuses);
      assertQuantityIs(c, 5 - bcWon, statuses);
      assertQuantityIs(b, 5 - baWon - bcWon, statuses);
    }
  }

  /** A Pool request over exactly two origins, taking 1 g from each. */
  private String poolJson(String name, ApiSubSample first, ApiSubSample second) {
    return "{\"operationType\":\"pool\",\"origins\":[{\"id\":"
        + first.getId()
        + ",\"amountTaken\":"
        + quantityJson("1", RSUnitDef.GRAM.getId())
        + "},{\"id\":"
        + second.getId()
        + ",\"amountTaken\":"
        + quantityJson("1", RSUnitDef.GRAM.getId())
        + "}],\"newSample\":{\"name\":\""
        + name
        + "\",\"extraFields\":["
        + hasPartLinkJson(first.getGlobalId())
        + ","
        + hasPartLinkJson(second.getGlobalId())
        + "],\"subSamples\":[{\"quantity\":"
        + quantityJson("2", RSUnitDef.GRAM.getId())
        + "}]}}";
  }

  private void assertQuantityIs(ApiSubSample origin, int expected, List<Integer> statuses)
      throws Exception {
    ApiSubSample reloaded = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
    assertTrue(
        java.math.BigDecimal.valueOf(expected).compareTo(reloaded.getQuantity().getNumericValue())
            == 0,
        () ->
            "expected "
                + origin.getGlobalId()
                + " to hold "
                + expected
                + ", got "
                + reloaded.getQuantity().getNumericValue()
                + " after "
                + statuses);
  }

  /** How many samples the test user can see; the outputs a race actually created. */
  private int sampleCount() {
    return sampleApiMgr.getSamplesForUser(null, null, null, anyUser).getTotalHits().intValue();
  }
}
