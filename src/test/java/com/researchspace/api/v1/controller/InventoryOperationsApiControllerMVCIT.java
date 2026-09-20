package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryOperationResult;
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
import com.researchspace.service.inventory.impl.InventoryOperationInFlightOrigins;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@WebAppConfiguration
public class InventoryOperationsApiControllerMVCIT extends API_MVC_InventoryTestBase {

  private @Autowired SubSampleApiManager subSampleApiManager;
  private @Autowired InventoryEditLockTracker editLockTracker;
  private @Autowired InventoryOperationInFlightOrigins inFlightOrigins;

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

  private void enableOperations() {
    systemPropertyManager.save(
        SystemPropertyName.INVENTORY_OPERATIONS_AVAILABLE,
        HierarchicalPermission.ALLOWED,
        getSysAdminUser());
  }

  private ResultActions post(String operation, String operationJson) throws Exception {
    return mockMvc.perform(
        createBuilderForPostWithJSONBody(
            apiKey, "/operations/" + operation, anyUser, operationJson));
  }

  private static String quantityJson(String value, int unitId) {
    return "{\"numericValue\":" + value + ",\"unitId\":" + unitId + "}";
  }

  private static String originJson(ApiSubSample origin, String amountTakenJson) {
    return "{\"globalId\":\""
        + origin.getGlobalId()
        + "\""
        + (amountTakenJson == null ? "" : ",\"amountTaken\":" + amountTakenJson)
        + "}";
  }

  private static String creatingFields(String sampleName, int count, String eachAmountJson) {
    return "\"sampleName\":\""
        + sampleName
        + "\",\"count\":"
        + count
        + ",\"eachAmount\":"
        + eachAmountJson;
  }

  private static String singleOriginBody(
      String originJson, String fieldsJson, String topLevelExtras) {
    return "{\"origin\":" + originJson + "," + fieldsJson + topLevelExtras + "}";
  }

  private static String poolBody(String originsJson, String fieldsJson) {
    return "{\"origins\":[" + originsJson + "]," + fieldsJson + "}";
  }

  private static String deriveJson(
      ApiSubSample origin,
      String amountTakenJson,
      String sampleName,
      int count,
      String eachAmountJson,
      String topLevelExtras) {
    return singleOriginBody(
        originJson(origin, amountTakenJson),
        "\"processName\":\"PCR\"," + creatingFields(sampleName, count, eachAmountJson),
        topLevelExtras);
  }

  private static String aliquotJsonWith(
      ApiSubSample origin, String amountTakenJson, String eachAmountJson, String topLevelExtras) {
    return singleOriginBody(
        originJson(origin, amountTakenJson),
        creatingFields("Aliquots", 1, eachAmountJson),
        topLevelExtras);
  }

  private static String aliquotTakingJson(ApiSubSample origin, String amount) {
    int unitId = origin.getQuantity().getUnitId();
    return aliquotJsonWith(origin, quantityJson(amount, unitId), quantityJson(amount, unitId), "");
  }

  private ApiSampleWithFullSubSamples createdSample(MvcResult result) throws Exception {
    return getFromJsonResponseBody(result, ApiInventoryOperationResult.class).getSample();
  }

  /** A stand-in for another user's open edit session (the tracker only needs a username). */
  private User aColleague() {
    return com.researchspace.testutils.TestFactory.createAnyUser(
        com.researchspace.core.testutil.CoreTestUtils.getRandomName(10));
  }

  private ApiError performExpectingConflict(String operationJson) throws Exception {
    MvcResult result = post("aliquot", operationJson).andExpect(status().isConflict()).andReturn();
    return getErrorFromJsonResponseBody(result, ApiError.class);
  }

  @Test
  public void anOriginHeldByAnotherUserIsRefusedWithoutTouchingIt() throws Exception {
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
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
    assertQuantityUnchanged(origin);
    editLockTracker.attemptToUnlock(origin.getGlobalId(), colleague);
    post("aliquot", aliquotTakingJson(origin, "0.1")).andExpect(status().isCreated());
  }

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

  @Test
  public void theCallersOwnClientLockNeitherBlocksNorIsReleasedByPerform() throws Exception {
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
    editLockTracker.attemptToLockForEdit(origin.getGlobalId(), anyUser);

    post("aliquot", aliquotTakingJson(origin, "0.1")).andExpect(status().isCreated());

    assertEquals(anyUser.getUsername(), editLockTracker.getLockOwnerForItem(origin.getGlobalId()));
    // the parent sample lock was this request's own and is given back
    assertNull(editLockTracker.getLockOwnerForItem(source.getGlobalId()));
    editLockTracker.attemptToUnlock(origin.getGlobalId(), anyUser);
  }

  @Test
  public void anOriginStillBeingOperatedOnByTheSameUserIsRefusedWithoutTouchingIt()
      throws Exception {
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
    try (InventoryOperationInFlightOrigins.Claim firstRequest =
        inFlightOrigins.claim(java.util.List.of(origin.getGlobalId()))) {

      ApiError error = performExpectingConflict(aliquotTakingJson(origin, "0.1"));

      assertEquals(ApiErrorCodes.EDIT_CONFLICT.getCode(), error.getInternalCode());
      assertTrue(
          error.getMessage().contains(origin.getGlobalId()),
          () -> "the conflict must name the origin, got " + error.getMessage());
      assertQuantityUnchanged(origin);
      assertNull(editLockTracker.getLockOwnerForItem(origin.getGlobalId()));
      assertNull(editLockTracker.getLockOwnerForItem(source.getGlobalId()));
    }

    post("aliquot", aliquotTakingJson(origin, "0.1")).andExpect(status().isCreated());
    assertFalse(inFlightOrigins.isInFlight(origin.getGlobalId()));
  }

  @Test
  public void aPoolWithOneOriginStillBeingOperatedOnIsRefusedWhole() throws Exception {
    ApiSubSample first = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    ApiSubSample second = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = first.getQuantity().getUnitId();
    String poolJson =
        poolBody(
            originJson(first, quantityJson("1", unitId))
                + ","
                + originJson(second, quantityJson("1", unitId)),
            creatingFields("Pooled", 1, quantityJson("2", unitId)));
    try (InventoryOperationInFlightOrigins.Claim otherRequest =
        inFlightOrigins.claim(java.util.List.of(second.getGlobalId()))) {

      post("pool", poolJson).andExpect(status().isConflict());

      assertFalse(inFlightOrigins.isInFlight(first.getGlobalId()));
    }
    post("pool", poolJson).andExpect(status().isCreated());
  }

  @Test
  public void deriveCreatesLinkedSampleAndReducesOriginByAmountTaken() throws Exception {
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
    Long originId = origin.getId();
    String originGlobalId = origin.getGlobalId();
    Integer unitId = origin.getQuantity().getUnitId();
    java.math.BigDecimal originalAmount = origin.getQuantity().getNumericValue();

    String operationJson =
        deriveJson(
            origin,
            quantityJson("0.6", unitId),
            "Derived material",
            2,
            quantityJson("0.5", unitId),
            "");

    MvcResult result = post("derive", operationJson).andExpect(status().isCreated()).andReturn();
    ApiSampleWithFullSubSamples created = createdSample(result);

    ApiExtraField sampleLink = findLinkField(created.getExtraFields());
    assertNotNull(sampleLink, "the derived sample must carry the provenance link");
    assertEquals("IsDerivedFrom", sampleLink.getLink().getRelationType());
    assertEquals(originGlobalId, sampleLink.getLink().getTargetGlobalId());

    assertEquals(2, created.getSubSamples().size());
    for (ApiSubSample ss : created.getSubSamples()) {
      assertTrue(
          ss.getExtraFields().isEmpty(), "the operation puts no extra fields on its subsamples");
    }

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
    ApiSampleTemplate template =
        createTemplate("operation target template", RSUnitDef.GRAM.getId());

    String operationJson =
        deriveJson(
            origin,
            quantityJson("0.6", unitId),
            "Derived from template",
            1,
            quantityJson("0.5", unitId),
            ",\"templateId\":" + template.getId());

    MvcResult result = post("derive", operationJson).andExpect(status().isCreated()).andReturn();

    assertEquals(
        template.getId(),
        createdSample(result).getTemplateId(),
        "the derived sample must be created from the chosen template");
  }

  @Test
  public void rejectsTakingMoreThanTheOriginHolds() throws Exception {
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
    Integer unitId = origin.getQuantity().getUnitId();
    java.math.BigDecimal tooMuch =
        origin.getQuantity().getNumericValue().add(java.math.BigDecimal.ONE);

    String operationJson =
        deriveJson(
            origin,
            quantityJson(tooMuch.toPlainString(), unitId),
            "Derived material",
            1,
            quantityJson("0.5", unitId),
            "");

    post("derive", operationJson).andExpect(status().isBadRequest());
    assertQuantityUnchanged(origin);
  }

  @Test
  public void aDocumentationTargetThatCannotBeResolvedIsRejectedBeforeAnyMutation()
      throws Exception {
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
    Integer unitId = origin.getQuantity().getUnitId();

    MvcResult result =
        post(
                "derive",
                deriveJson(
                    origin,
                    quantityJson("0.6", unitId),
                    "Documented output",
                    1,
                    quantityJson("0.5", unitId),
                    ",\"documentedByGlobalId\":\"SD999999999\""))
            .andExpect(status().isBadRequest())
            .andReturn();
    List<String> errors = getErrorFromJsonResponseBody(result, ApiError.class).getErrors();
    assertTrue(
        errors.stream().anyMatch(message -> message.startsWith("documentedByGlobalId:")),
        () -> "expected documentedByGlobalId, got " + errors);

    assertQuantityUnchanged(origin);
  }

  @Test
  public void aRenameSavedAfterAnOperationLeavesTheDeductionStanding() throws Exception {
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    java.math.BigDecimal beforeOperation = origin.getQuantity().getNumericValue();

    post("aliquot", aliquotTakingJson(origin, "1")).andExpect(status().isCreated());
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

  private ApiSampleTemplate createTemplate(
      String name, int defaultUnitId, ApiInventoryEntityField... fields) throws Exception {
    ApiSampleTemplatePost templatePost = new ApiSampleTemplatePost();
    templatePost.setName(name);
    templatePost.setDefaultUnitId(defaultUnitId);
    templatePost.getFields().addAll(List.of(fields));
    MvcResult templateResult =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/sampleTemplates", anyUser, templatePost))
            .andExpect(status().isCreated())
            .andReturn();
    return getFromJsonResponseBody(templateResult, ApiSampleTemplate.class);
  }

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
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = origin.getQuantity().getUnitId();
    ApiSampleTemplate template =
        createTemplate(
            "passage template with its own counter",
            unitId,
            createBasicApiSampleField("Passage number", ApiFieldType.TEXT, ""));

    String operationJson =
        singleOriginBody(
            originJson(origin, null),
            creatingFields("Passaged", 1, quantityJson("1", unitId)),
            ",\"templateId\":" + template.getId());

    MvcResult result = post("passage", operationJson).andExpect(status().isCreated()).andReturn();

    ApiSample reloaded = sampleApiMgr.getApiSampleById(createdSample(result).getId(), anyUser);
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
    ApiSubSample volumeOrigin =
        createSampleHolding("F4a volume", "5", RSUnitDef.MILLI_LITRE.getId())
            .getSubSamples()
            .get(0);
    ApiSubSample massOrigin =
        createSampleHolding("F4a mass", "5", RSUnitDef.GRAM.getId()).getSubSamples().get(0);

    String operationJson =
        poolBody(
            originJson(volumeOrigin, quantityJson("1", RSUnitDef.MILLI_LITRE.getId()))
                + ","
                + originJson(massOrigin, quantityJson("1", RSUnitDef.GRAM.getId())),
            creatingFields("Mixed pool", 1, quantityJson("2", RSUnitDef.MILLI_LITRE.getId())));

    post("pool", operationJson).andExpect(status().isBadRequest());

    for (ApiSubSample origin : List.of(volumeOrigin, massOrigin)) {
      assertQuantityUnchanged(origin);
    }
  }

  @Test
  public void rejectsAnOriginTheCallerCannotEditThroughTheFullStack() throws Exception {
    // An unrelated user can neither read nor edit the origin, so the Inventory API answers its 404
    // (a foreign id is indistinguishable from a missing one) and nothing is written.
    User otherUser = createInitAndLoginAnyUser();
    ApiSubSample foreignOrigin = createBasicSampleForUser(otherUser).getSubSamples().get(0);
    ApiSubSample ownOrigin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = foreignOrigin.getQuantity().getUnitId();
    post("aliquot", aliquotTakingJson(foreignOrigin, "1")).andExpect(status().isNotFound());

    String poolJson =
        poolBody(
            originJson(ownOrigin, quantityJson("1", unitId))
                + ","
                + originJson(foreignOrigin, quantityJson("1", unitId)),
            creatingFields("Pooled with a foreign origin", 1, quantityJson("2", unitId)));
    post("pool", poolJson).andExpect(status().isNotFound());

    ApiSubSample reloadedForeign =
        subSampleApiManager.getApiSubSampleById(foreignOrigin.getId(), otherUser);
    assertEquals(
        0,
        foreignOrigin
            .getQuantity()
            .getNumericValue()
            .compareTo(reloadedForeign.getQuantity().getNumericValue()),
        "the foreign origin must be untouched");
    assertQuantityUnchanged(ownOrigin);
  }

  private void assertRejectedLeavingOriginUnchanged(ApiSubSample origin, String operationJson)
      throws Exception {
    post("aliquot", operationJson).andExpect(status().isBadRequest());
    assertQuantityUnchanged(origin);
  }

  /** Reloads the origin and asserts the request left its quantity alone. */
  private void assertQuantityUnchanged(ApiSubSample origin) {
    ApiSubSample reloaded = subSampleApiManager.getApiSubSampleById(origin.getId(), anyUser);
    assertEquals(
        0,
        origin.getQuantity().getNumericValue().compareTo(reloaded.getQuantity().getNumericValue()),
        "origin must be unchanged when the request is rejected");
  }

  private ApiExtraField findLinkField(List<ApiExtraField> extraFields) {
    return extraFields.stream().filter(ef -> ef.getLink() != null).findFirst().orElse(null);
  }

  @Test
  public void rejectsAmountTakenInAUnitThatDoesNotExist() throws Exception {
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = origin.getQuantity().getUnitId();
    assertRejectedLeavingOriginUnchanged(
        origin,
        aliquotJsonWith(origin, quantityJson("1", 999999), quantityJson("0.5", unitId), ""));
  }

  @Test
  public void rejectsAmountTakenInADifferentCategoryThanTheOrigin() throws Exception {
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
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = origin.getQuantity().getUnitId();
    ApiSampleTemplate template =
        createTemplate("RSDEV-1231 volume template", RSUnitDef.MILLI_LITRE.getId());
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
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = origin.getQuantity().getUnitId();
    assertRejectedLeavingOriginUnchanged(
        origin,
        aliquotJsonWith(origin, quantityJson("1", unitId), quantityJson("0.0004", unitId), ""));
  }

  @Test
  public void acceptsAmountTakenInAnotherUnitOfTheOriginsCategory() throws Exception {
    ApiSubSample origin = createBasicSampleForUser(anyUser).getSubSamples().get(0);
    int unitId = origin.getQuantity().getUnitId();
    post(
            "aliquot",
            aliquotJsonWith(
                origin,
                quantityJson("1000", RSUnitDef.MILLI_GRAM.getId()),
                quantityJson("0.5", unitId),
                ""))
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
  public void everyRouteIsRefusedWhileOperationsAreDenied() throws Exception {
    ApiSampleWithFullSubSamples source = createBasicSampleForUser(anyUser);
    ApiSubSample origin = source.getSubSamples().get(0);
    systemPropertyManager.save(
        SystemPropertyName.INVENTORY_OPERATIONS_AVAILABLE,
        HierarchicalPermission.DENIED,
        getSysAdminUser());

    MvcResult refused =
        post("aliquot", aliquotTakingJson(origin, "0.1"))
            .andExpect(status().isNotFound())
            .andReturn();
    ApiError refusal = getErrorFromJsonResponseBody(refused, ApiError.class);
    assertEquals(ApiErrorCodes.CONFIGURED_UNAVAILABLE.getCode(), refusal.getInternalCode());
    assertTrue(
        refusal.getMessage().contains("inventory.operations.available"),
        () -> "the refusal must name the property, got " + refusal.getMessage());

    // The availability check runs before the request's own rules, so the other six routes are
    // refused identically without each needing a valid body.
    for (String operation :
        List.of("passage", "pool", "derive", "cryopreserve", "revive", "destroy")) {
      MvcResult other = post(operation, "{}").andExpect(status().isNotFound()).andReturn();
      assertEquals(
          ApiErrorCodes.CONFIGURED_UNAVAILABLE.getCode(),
          getErrorFromJsonResponseBody(other, ApiError.class).getInternalCode(),
          operation);
    }

    assertQuantityUnchanged(origin);
  }
}
