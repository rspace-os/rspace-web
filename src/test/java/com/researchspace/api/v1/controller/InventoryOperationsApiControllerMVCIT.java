package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.apiutils.ApiError;
import com.researchspace.apiutils.ApiErrorCodes;
import com.researchspace.model.User;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.inventory.SubSampleApiManager;
import com.researchspace.service.inventory.impl.InventoryEditLockTracker;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end coverage for the RSDEV-1231 operation endpoint (POST /operations). A single POST
 * carries the origins with their amounts and the values the user typed; the server builds one new
 * Sample parenting N subsamples from them and the operation definition, puts a provenance link back
 * to each origin on the new Sample, and reduces each origin subsample by the amount taken from it
 * (never increasing it), all in one transaction. The live-state and concurrency rules of that
 * transaction are exercised here against a real database. See DevDocs/adr/0007.
 *
 * <p>Authored with the feature; not run automatically (extends a real-transaction MVC base).
 */
@WebAppConfiguration
public class InventoryOperationsApiControllerMVCIT extends API_MVC_InventoryTestBase {

  private @Autowired SubSampleApiManager subSampleApiManager;
  private @Autowired InventoryEditLockTracker editLockTracker;

  private @Autowired SystemPropertyManager systemPropertyManager;

  private User anyUser;
  private String apiKey;

  @BeforeEach
  public void setup() throws Exception {
    super.setUp();
    anyUser = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(anyUser);
    enableOperations();
  }

  /**
   * RSDEV-1231 seeds {@code inventory.operations.available} DENIED, so each test turns it on first:
   * what is under test in this class is the operation, not the toggle.
   */
  private void enableOperations() {
    systemPropertyManager.save(
        SystemPropertyName.INVENTORY_OPERATIONS_AVAILABLE,
        HierarchicalPermission.ALLOWED,
        getSysAdminUser());
  }

  // --- request bodies, in the shape the wizard sends ---

  private static String quantityJson(String value, int unitId) {
    return "{\"numericValue\":" + value + ",\"unitId\":" + unitId + "}";
  }

  /** One origin element; a null amountMode leaves the property absent. */
  private static String originJson(ApiSubSample origin, String amountMode, String amountTakenJson) {
    return "{\"id\":"
        + origin.getId()
        + (amountMode == null ? "" : ",\"amountMode\":\"" + amountMode + "\"")
        + ",\"amountTaken\":"
        + amountTakenJson
        + "}";
  }

  /** The inputs every creating operation declares. */
  private static String creatingInputs(String sampleName, int count, String eachAmountJson) {
    return "\"sampleName\":\""
        + sampleName
        + "\",\"count\":"
        + count
        + ",\"eachAmount\":"
        + eachAmountJson;
  }

  private static String body(String operationType, String originsJson, String inputsJson) {
    return body(operationType, originsJson, inputsJson, "");
  }

  /** topLevelExtras is appended verbatim, e.g. {@code ,"templateId":5}. */
  private static String body(
      String operationType, String originsJson, String inputsJson, String topLevelExtras) {
    return "{\"operationType\":\""
        + operationType
        + "\",\"origins\":["
        + originsJson
        + "],\"inputs\":{"
        + inputsJson
        + "}"
        + topLevelExtras
        + "}";
  }

  /** A Derive taking the given amount from the origin into {@code count} children of eachAmount. */
  private static String deriveJson(
      ApiSubSample origin,
      String amountTakenJson,
      String sampleName,
      int count,
      String eachAmountJson,
      String topLevelExtras) {
    return body(
        "derive",
        originJson(origin, null, amountTakenJson),
        "\"processName\":\"PCR\"," + creatingInputs(sampleName, count, eachAmountJson),
        topLevelExtras);
  }

  private static String aliquotJsonWith(
      ApiSubSample origin, String amountTakenJson, String eachAmountJson, String topLevelExtras) {
    return body(
        "aliquot",
        originJson(origin, null, amountTakenJson),
        creatingInputs("Aliquots", 1, eachAmountJson),
        topLevelExtras);
  }

  /** An Aliquot taking the given amount, in the origin's own unit, into one child of the same. */
  private static String aliquotTakingJson(ApiSubSample origin, String amount) {
    int unitId = origin.getQuantity().getUnitId();
    return body(
        "aliquot",
        originJson(origin, null, quantityJson(amount, unitId)),
        creatingInputs("Aliquot of " + origin.getGlobalId(), 1, quantityJson(amount, unitId)));
  }

  // --- the edit-session lock the controller holds around Perform (RSDEV-1231 S2/S3) ---

  /**
   * A stand-in for another user's open edit session. The tracker holds locks by username and never
   * touches the database, so the lock holder needs no account here, only a name.
   */
  private User aColleague() {
    return com.researchspace.testutils.TestFactory.createAnyUser(
        com.researchspace.core.testutil.CoreTestUtils.getRandomName(10));
  }

  private ApiError performExpectingConflict(String operationJson) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/operations", anyUser, operationJson))
            .andExpect(status().isConflict())
            .andReturn();
    return getErrorFromJsonResponseBody(result, ApiError.class);
  }

  @Test
  public void anOriginHeldByAnotherUserIsRefusedWithoutTouchingIt() throws Exception {
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
    java.math.BigDecimal originalAmount = origin.getQuantity().getNumericValue();
    User colleague = aColleague();
    editLockTracker.attemptToLockForEdit(origin.getGlobalId(), colleague);

    ApiError error = performExpectingConflict(aliquotTakingJson(origin, "0.1"));

    assertTrue(
        error.getMessage().contains(origin.getGlobalId()),
        () -> "the conflict must name the origin, got " + error.getMessage());
    assertTrue(
        error.getMessage().contains(colleague.getUsername())
            || error.getMessage().contains(colleague.getFirstName()),
        () -> "the conflict must name the holder, got " + error.getMessage());
    ApiSubSample reloaded = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
    assertEquals(0, originalAmount.compareTo(reloaded.getQuantity().getNumericValue()));

    // and the same request succeeds once the colleague is done
    editLockTracker.attemptToUnlock(origin.getGlobalId(), colleague);
    mockMvc
        .perform(
            createBuilderForPostWithJSONBody(
                apiKey, "/operations", anyUser, aliquotTakingJson(origin, "0.1")))
        .andExpect(status().isCreated());
  }

  /**
   * The sibling case: the colleague holds the PARENT sample, not the origin itself. Two operations
   * on children of one sample write the same sample row, so the parent is in the lock set too.
   */
  @Test
  public void aParentSampleHeldByAnotherUserIsRefused() throws Exception {
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
    User colleague = aColleague();
    editLockTracker.attemptToLockForEdit(source.getGlobalId(), colleague);

    ApiError error = performExpectingConflict(aliquotTakingJson(origin, "0.1"));

    assertTrue(
        error.getMessage().contains(source.getGlobalId()),
        () -> "the conflict must name the parent sample, got " + error.getMessage());
    editLockTracker.attemptToUnlock(source.getGlobalId(), colleague);
  }

  /**
   * The wizard holds the client lock on its origins for its whole lifetime, so the caller's own
   * lock must not refuse the caller's own Perform, and must survive it: the wizard releases it on
   * close.
   */
  @Test
  public void theCallersOwnClientLockNeitherBlocksNorIsReleasedByPerform() throws Exception {
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
    editLockTracker.attemptToLockForEdit(origin.getGlobalId(), anyUser);

    mockMvc
        .perform(
            createBuilderForPostWithJSONBody(
                apiKey, "/operations", anyUser, aliquotTakingJson(origin, "0.1")))
        .andExpect(status().isCreated());

    assertEquals(anyUser.getUsername(), editLockTracker.getLockOwnerForItem(origin.getGlobalId()));
    // the parent sample lock was this request's own and is given back
    assertNull(editLockTracker.getLockOwnerForItem(source.getGlobalId()));
    editLockTracker.attemptToUnlock(origin.getGlobalId(), anyUser);
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

    // The wizard sends the typed inputs and the amount to take from the origin; the server builds
    // the sample (2 subsamples, each holding a quantity and nothing else) and its provenance link.
    String operationJson =
        deriveJson(
            origin,
            quantityJson("0.6", unitId),
            "Derived material",
            2,
            quantityJson("0.5", unitId),
            "");

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
    // fields live on the sample.
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

    String operationJson =
        deriveJson(
            origin,
            quantityJson("0.6", unitId),
            "Derived from template",
            1,
            quantityJson("0.5", unitId),
            ",\"templateId\":" + template.getId());

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
    // clamped, and must leave the origin untouched.
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
    Long originId = origin.getId();
    Integer unitId = origin.getQuantity().getUnitId();
    java.math.BigDecimal originalAmount = origin.getQuantity().getNumericValue();
    java.math.BigDecimal tooMuch = originalAmount.add(java.math.BigDecimal.ONE);

    String operationJson =
        deriveJson(
            origin,
            quantityJson(tooMuch.toPlainString(), unitId),
            "Derived material",
            1,
            quantityJson("0.5", unitId),
            "");

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
  public void aDocumentationTargetThatCannotBeResolvedIsRejectedBeforeAnyMutation()
      throws Exception {
    // The generic endpoint does not rename paths, so the error names the field as sent (F4).
    //
    // This replaces rollsBackOriginDecrementWhenSampleCreationFailsInsideTheTransaction, which
    // used this same unresolvable target to force a failure AFTER the origin decrement and so
    // prove the AOP transaction rolls back. The target is now rejected before the transaction
    // opens, which leaves that test asserting nothing, and no other failure reachable through
    // this endpoint writes before it throws: checkOriginLiveState runs every origin's checks
    // before any decrement, and the parent-total recompute it does first writes the sum of the
    // children, which a rejected request leaves unchanged either way. Real-transaction rollback
    // therefore needs an injected failure rather than a request-shaped one, and is not covered
    // here any more.
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
    Integer unitId = origin.getQuantity().getUnitId();
    java.math.BigDecimal originalAmount = origin.getQuantity().getNumericValue();

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey,
                    "/operations",
                    anyUser,
                    deriveJson(
                        origin,
                        quantityJson("0.6", unitId),
                        "Documented output",
                        1,
                        quantityJson("0.5", unitId),
                        ",\"documentedByGlobalId\":\"SD999999999\"")))
            .andExpect(status().isBadRequest())
            .andReturn();
    List<String> errors = getErrorFromJsonResponseBody(result, ApiError.class).getErrors();
    assertTrue(
        errors.stream().anyMatch(message -> message.startsWith("documentedByGlobalId:")),
        () -> "expected documentedByGlobalId, got " + errors);

    ApiSubSample reloadedOrigin = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
    assertTrue(
        originalAmount.compareTo(reloadedOrigin.getQuantity().getNumericValue()) == 0,
        "origin quantity must be untouched");
  }

  /**
   * The whole flow end to end, with no concurrency in it at all: an operation commits, and only
   * then does the user save an edit to the origin.
   *
   * <p>Nothing overlaps here. What decides it is whether the edit carries a quantity. A PUT that
   * sends one replaces the stored value, deliberately: that is how a user corrects a quantity, and
   * the server cannot tell a correction apart from a client echoing back the number the page was
   * loaded with. So the client does not send one it was not given, and that is pinned where the
   * payload is built (SubSampleModel paramsForBackend tests); this is the other half, that a
   * payload without a quantity leaves the deduction standing (Codex review, PR #1090).
   */
  @Test
  public void aRenameSavedAfterAnOperationLeavesTheDeductionStanding() throws Exception {
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    java.math.BigDecimal beforeOperation = origin.getQuantity().getNumericValue();

    mockMvc
        .perform(
            createBuilderForPostWithJSONBody(
                apiKey, "/operations", anyUser, aliquotTakingJson(origin, "1")))
        .andExpect(status().isCreated());
    java.math.BigDecimal afterOperation =
        subSampleApiManager
            .getApiSubSampleById(origin.getId(), anyUser)
            .getQuantity()
            .getNumericValue();
    assertEquals(
        0,
        beforeOperation.subtract(java.math.BigDecimal.ONE).compareTo(afterOperation),
        "precondition: the operation took 1 from the origin");

    mockMvc
        .perform(
            createBuilderForPutWithJSONBody(
                apiKey,
                "/subSamples/" + origin.getId(),
                anyUser,
                "{\"id\":" + origin.getId() + ",\"name\":\"Renamed after the aliquot\"}"))
        .andExpect(status().is2xxSuccessful());

    ApiSubSample reloaded = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
    assertEquals("Renamed after the aliquot", reloaded.getName(), "the rename must land");
    assertEquals(
        0,
        afterOperation.compareTo(reloaded.getQuantity().getNumericValue()),
        "the edit must not put the pre-operation quantity back: that is stock reappearing with"
            + " material already made from it");
  }

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

  @Test
  public void passageIntoATemplateThatAlreadyDeclaresTheCounterFieldMergesInsteadOfDuplicating()
      throws Exception {
    // A Passage template may legitimately declare its own "Passage number" field. The operation
    // generates a field of that name too, so the created sample used to end up with two and
    // assertNoDuplicateFieldNames rejected the whole request: every Passage onto such a template
    // failed, and the wizard could not rename the generated field (Codex review, PR #1090).
    // Renaming it would clear the rejection but strand the counter, which finds the previous number
    // by name, so the value has to land in the inherited field instead.
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = origin.getQuantity().getUnitId();
    ApiSampleTemplatePost templatePost = new ApiSampleTemplatePost();
    templatePost.setName("passage template with its own counter");
    templatePost.setDefaultUnitId(unitId);
    templatePost
        .getFields()
        .add(createBasicApiSampleField("Passage number", ApiFieldType.TEXT, ""));
    MvcResult templateResult =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/sampleTemplates", anyUser, templatePost))
            .andExpect(status().isCreated())
            .andReturn();
    ApiSampleTemplate template = getFromJsonResponseBody(templateResult, ApiSampleTemplate.class);

    String operationJson =
        body(
            "passage",
            originJson(origin, null, quantityJson("0", unitId)),
            creatingInputs("Passaged", 1, quantityJson("1", unitId)),
            ",\"templateId\":" + template.getId());

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/operations", anyUser, operationJson))
            .andExpect(status().isCreated())
            .andReturn();

    ApiSampleWithFullSubSamples created =
        getFromJsonResponseBody(result, ApiSampleWithFullSubSamples.class);
    ApiSample reloaded = sampleApiMgr.getApiSampleById(created.getId(), anyUser);
    long counterFields =
        Stream.concat(
                reloaded.getFields().stream().map(ApiInventoryEntityField::getName),
                reloaded.getExtraFields().stream().map(ApiExtraField::getName))
            .filter(name -> "Passage number".equalsIgnoreCase(name == null ? "" : name.trim()))
            .count();
    assertEquals(1, counterFields, "the created sample must hold exactly one Passage number field");
    // and it is the inherited one, carrying the operation's value (the server starts the counter
    // at 1 when the origin's parent holds none), so the next Passage's counter lookup finds it.
    assertEquals(
        "1",
        reloaded.getFields().stream()
            .filter(f -> "Passage number".equals(f.getName()))
            .map(ApiInventoryEntityField::getContent)
            .findFirst()
            .orElse(null),
        "the operation's value must land in the template's own field");
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
        body(
            "pool",
            originJson(volumeOrigin, null, quantityJson("1", RSUnitDef.MILLI_LITRE.getId()))
                + ","
                + originJson(massOrigin, null, quantityJson("1", RSUnitDef.GRAM.getId())),
            creatingInputs("Mixed pool", 1, quantityJson("2", RSUnitDef.MILLI_LITRE.getId())));

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
  public void rejectsAnOriginTheCallerCannotEditThroughTheFullStack() throws Exception {
    // security review D1: the unit tests pin authz-before-read with mocks; this pins it through the
    // real controller, Shiro and permission utils. An unrelated user can neither read nor edit the
    // origin, so the Inventory API answers its 404 (a foreign id is indistinguishable from a
    // missing one) and nothing is written.
    User otherUser = createInitAndLoginAnyUser();
    ApiSubSample foreignOrigin = createBasicSampleForUser(otherUser).getSubSamples().get(0);
    ApiSubSample ownOrigin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = foreignOrigin.getQuantity().getUnitId();

    // single origin
    mockMvc
        .perform(
            createBuilderForPostWithJSONBody(
                apiKey, "/operations", anyUser, aliquotTakingJson(foreignOrigin, "1")))
        .andExpect(status().isNotFound());

    // Pool over one origin the caller owns and one it does not: the whole request is refused
    // before either origin is decremented
    String poolJson =
        body(
            "pool",
            originJson(ownOrigin, null, quantityJson("1", unitId))
                + ","
                + originJson(foreignOrigin, null, quantityJson("1", unitId)),
            creatingInputs("Pooled with a foreign origin", 1, quantityJson("2", unitId)));
    mockMvc
        .perform(createBuilderForPostWithJSONBody(apiKey, "/operations", anyUser, poolJson))
        .andExpect(status().isNotFound());

    ApiSubSample reloadedForeign =
        subSampleApiManager.getApiSubSampleById(foreignOrigin.getId(), otherUser);
    assertEquals(
        0,
        foreignOrigin
            .getQuantity()
            .getNumericValue()
            .compareTo(reloadedForeign.getQuantity().getNumericValue()),
        "the foreign origin must be untouched");
    ApiSubSample reloadedOwn = subSampleApiManager.getApiSubSampleById(ownOrigin.getId(), anyUser);
    assertEquals(
        0,
        ownOrigin
            .getQuantity()
            .getNumericValue()
            .compareTo(reloadedOwn.getQuantity().getNumericValue()),
        "the caller's own origin must be untouched when a sibling origin is refused");
  }

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
        "origin must be unchanged when the request is rejected");
    return result;
  }

  private ApiExtraField findLinkField(List<ApiExtraField> extraFields) {
    return extraFields.stream().filter(ef -> ef.getLink() != null).findFirst().orElse(null);
  }

  // --- code review (2026-09-03) reproductions: each is a field-scoped 400 leaving the origin
  // untouched, where it used to be a 422 or a 201 with wrong data ---

  @Test
  public void rejectsAmountTakenInAUnitThatDoesNotExist() throws Exception {
    // review repro f4-unknown: used to reach QuantityUtils.sum and surface as a 422
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = origin.getQuantity().getUnitId();
    assertRejectedLeavingOriginUnchanged(
        origin,
        aliquotJsonWith(origin, quantityJson("1", 999999), quantityJson("0.5", unitId), ""));
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
            quantityJson("0.5", unitId),
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
            quantityJson("0.5", RSUnitDef.MILLI_LITRE.getId()),
            ""));
  }

  @Test
  public void rejectsANewSubSampleOutsideTheChosenTemplatesCategory() throws Exception {
    // review repro f5-template: gram children under a volume template used to be created
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
            quantityJson("0.5", unitId),
            ",\"templateId\":" + template.getId()));
  }

  @Test
  public void rejectsADocumentationLinkToAnInventoryRecord() throws Exception {
    // review repro f6: IsDocumentedBy pointing at the origin subsample itself used to be stored
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = origin.getQuantity().getUnitId();
    assertRejectedLeavingOriginUnchanged(
        origin,
        aliquotJsonWith(
            origin,
            quantityJson("1", unitId),
            quantityJson("0.5", unitId),
            ",\"documentedByGlobalId\":\"" + origin.getGlobalId() + "\""));
  }

  @Test
  public void rejectsANewSubSampleQuantityFinerThanTheStored3dp() throws Exception {
    // review repro f7: 0.0004 used to persist as a subsample holding 0
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = origin.getQuantity().getUnitId();
    assertRejectedLeavingOriginUnchanged(
        origin,
        aliquotJsonWith(origin, quantityJson("1", unitId), quantityJson("0.0004", unitId), ""));
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
                    quantityJson("0.5", unitId),
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

  // --- the inventory.operations.available toggle (RSDEV-1231) ---

  @Test
  public void everyRouteIsRefusedWhileOperationsAreDenied() throws Exception {
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
    java.math.BigDecimal originalAmount = origin.getQuantity().getNumericValue();
    systemPropertyManager.save(
        SystemPropertyName.INVENTORY_OPERATIONS_AVAILABLE,
        HierarchicalPermission.DENIED,
        getSysAdminUser());

    MvcResult post =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey, "/operations", anyUser, aliquotTakingJson(origin, "0.1")))
            .andExpect(status().isNotFound())
            .andReturn();
    ApiError postError = getErrorFromJsonResponseBody(post, ApiError.class);
    assertEquals(ApiErrorCodes.CONFIGURED_UNAVAILABLE.getCode(), postError.getInternalCode());
    assertTrue(
        postError.getMessage().contains("inventory.operations.available"),
        () -> "the refusal must name the property, got " + postError.getMessage());

    MvcResult config =
        mockMvc
            .perform(
                createBuilderForInventoryGet(
                    API_VERSION.ONE, apiKey, "/operations/config", anyUser))
            .andExpect(status().isNotFound())
            .andReturn();
    assertEquals(
        ApiErrorCodes.CONFIGURED_UNAVAILABLE.getCode(),
        getErrorFromJsonResponseBody(config, ApiError.class).getInternalCode());

    // refused before anything is read or written: the origin is untouched
    ApiSubSample reloaded = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
    assertEquals(0, originalAmount.compareTo(reloaded.getQuantity().getNumericValue()));
  }
}
