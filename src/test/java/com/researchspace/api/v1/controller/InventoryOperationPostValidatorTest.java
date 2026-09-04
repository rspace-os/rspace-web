package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.InventoryOperationPostValidator.MAX_EXTRA_FIELDS;
import static com.researchspace.api.v1.controller.InventoryOperationPostValidator.MAX_SUBSAMPLES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.researchspace.api.v1.model.ApiBarcode;
import com.researchspace.api.v1.model.ApiContainerLocation;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiGroupInfoWithSharedFlag;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventorySharingMode;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleNote;
import com.researchspace.api.v1.model.ApiTagInfo;
import com.researchspace.api.v1.model.ApiTargetLocation;
import com.researchspace.model.inventory.SampleSource;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.ApiExtraFieldsHelper;
import com.researchspace.service.inventory.InventoryOperationConfigRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;

/**
 * The operations endpoint is public API, so every rule the wizard enforces client-side must be
 * enforced here too (DevDocs/adr/0007). One fixture per configured operation, each the exact shape
 * the wizard's request builder produces; the tests then break them one rule at a time.
 */
class InventoryOperationPostValidatorTest {

  private final InventoryOperationPostValidator validator = newValidator();

  /** Fully-wired validator for unit tests; shared with the controller test in this package. */
  static InventoryOperationPostValidator newValidator() {
    ApiExtraFieldsHelper extraFieldsHelper = new ApiExtraFieldsHelper(new RecordFactory());
    SampleApiPostValidator sampleApiPostValidator = new SampleApiPostValidator();
    sampleApiPostValidator.extraFieldHelper = extraFieldsHelper;
    return new InventoryOperationPostValidator(
        new InventoryOperationConfigRegistry(), sampleApiPostValidator, extraFieldsHelper);
  }

  private Errors validate(ApiInventoryOperationPost request) {
    Errors errors = new BeanPropertyBindingResult(request, "request");
    validator.validate(request, errors);
    return errors;
  }

  private static void assertSingleErrorWithCode(Errors errors, String field, String code) {
    assertEquals(
        1,
        errors.getErrorCount(),
        () -> "expected exactly one error, got: " + errors.getAllErrors());
    FieldError error = errors.getFieldErrors(field).get(0);
    assertEquals(code, error.getCode());
  }

  // --- fixtures: one golden request per configured operation, as the wizard builds them ---

  static ApiQuantityInfo millilitres(String value) {
    return new ApiQuantityInfo(new BigDecimal(value), RSUnitDef.MILLI_LITRE.getId());
  }

  private static ApiQuantityInfo celsius(String value) {
    return new ApiQuantityInfo(new BigDecimal(value), RSUnitDef.CELSIUS.getId());
  }

  private static ApiInventoryOperationOriginUpdate origin(long id, String amountTaken) {
    ApiInventoryOperationOriginUpdate origin = new ApiInventoryOperationOriginUpdate();
    origin.setId(id);
    origin.setAmountTaken(millilitres(amountTaken));
    return origin;
  }

  /**
   * A provenance link as the wizard builds it: the resolved (localized, interpolated) display name
   * plus the definition key that identifies which link spec produced it.
   */
  private static ApiExtraField linkTo(String fieldKey, String relationType, long originId) {
    ApiExtraField field = new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.LINK);
    field.setName(relationType + " SS" + originId);
    field.setNewFieldRequest(true);
    field.setOperationFieldKey(fieldKey);
    ApiInventoryLink link = new ApiInventoryLink();
    link.setRelationType(relationType);
    link.setTargetGlobalId("SS" + originId);
    field.setLink(link);
    return field;
  }

  private static ApiExtraField textField(String fieldKey, String content) {
    ApiExtraField field = new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.TEXT);
    field.setName(fieldKey);
    field.setNewFieldRequest(true);
    field.setOperationFieldKey(fieldKey);
    field.setContent(content);
    return field;
  }

  private static ApiExtraField documentationLink() {
    ApiExtraField field = new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.LINK);
    field.setName("Standard operating procedure");
    field.setNewFieldRequest(true);
    field.setOperationFieldKey("operations.documentationLink");
    ApiInventoryLink link = new ApiInventoryLink();
    link.setRelationType("IsDocumentedBy");
    link.setTargetGlobalId("SD1");
    field.setLink(link);
    return field;
  }

  private static ApiSampleWithFullSubSamples newSample(String name, ApiExtraField... links) {
    ApiSampleWithFullSubSamples sample = new ApiSampleWithFullSubSamples(name);
    ApiSubSample subSample = new ApiSubSample();
    subSample.setQuantity(millilitres("0.5"));
    sample.getSubSamples().add(subSample);
    sample.getExtraFields().addAll(List.of(links));
    return sample;
  }

  private static ApiInventoryOperationPost request(
      String operationType,
      ApiSampleWithFullSubSamples newSample,
      ApiInventoryOperationOriginUpdate... origins) {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType(operationType);
    request.setNewSample(newSample);
    request.setOrigins(new ArrayList<>(List.of(origins)));
    return request;
  }

  static ApiInventoryOperationPost aliquotRequest() {
    return request(
        "aliquot",
        newSample("Aliquots", linkTo("operations.aliquot.linkFieldName", "IsPartOf", 100)),
        origin(100, "0.6"));
  }

  private static ApiInventoryOperationPost passageRequest() {
    return request(
        "passage",
        newSample(
            "Passaged",
            linkTo("operations.passage.linkFieldName", "IsDerivedFrom", 100),
            textField("operations.passage.numberField", "4")),
        origin(100, "0"));
  }

  private static ApiInventoryOperationPost poolRequest() {
    return request(
        "pool",
        newSample(
            "Pooled",
            linkTo("operations.pool.linkFieldName", "HasPart", 100),
            linkTo("operations.pool.linkFieldName", "HasPart", 101)),
        origin(100, "0.6"),
        origin(101, "0.7"));
  }

  private static ApiInventoryOperationPost deriveRequest() {
    return request(
        "derive",
        newSample("Derived", linkTo("operations.derive.linkFieldName", "IsDerivedFrom", 100)),
        origin(100, "0.6"));
  }

  private static ApiInventoryOperationPost cryopreserveRequest() {
    ApiSampleWithFullSubSamples sample =
        newSample(
            "Frozen",
            linkTo("operations.cryopreserve.linkFieldName", "IsDerivedFrom", 100),
            textField("operations.cryopreserve.cryomediumField", "DMSO 10%"));
    sample.setStorageTempMin(celsius("-20"));
    sample.setStorageTempMax(celsius("-20"));
    return request("cryopreserve", sample, origin(100, "0.6"));
  }

  private static ApiInventoryOperationPost reviveRequest() {
    ApiSampleWithFullSubSamples sample =
        newSample("Revived", linkTo("operations.revive.linkFieldName", "IsDerivedFrom", 100));
    sample.setStorageTempMin(celsius("4"));
    sample.setStorageTempMax(celsius("4"));
    return request("revive", sample, origin(100, "0.6"));
  }

  static ApiInventoryOperationPost destroyRequest() {
    ApiInventoryOperationPost request = request("destroy", null, origin(100, "5"));
    request.getOrigins().get(0).setExtraFields(new ArrayList<>(List.of(disposedField())));
    return request;
  }

  @Test
  void everyConfiguredOperationsGoldenRequestPasses() {
    for (ApiInventoryOperationPost request :
        List.of(
            aliquotRequest(),
            passageRequest(),
            poolRequest(),
            deriveRequest(),
            cryopreserveRequest(),
            reviveRequest(),
            destroyRequest())) {
      Errors errors = validate(request);
      assertFalse(
          errors.hasErrors(),
          () -> request.getOperationType() + " golden request: " + errors.getAllErrors());
    }
  }

  // --- operation type allowlist ---

  @Test
  void rejectsUnknownOperationType() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.setOperationType("teleport");
    assertSingleErrorWithCode(
        validate(request), "operationType", "errors.inventory.operation.unknownType");
  }

  @Test
  void rejectsMissingOperationType() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.setOperationType(null);
    assertSingleErrorWithCode(
        validate(request), "operationType", "errors.inventory.operation.unknownType");
  }

  @Test
  void operationTypeIsCaseSensitiveLikeTheConfigKeys() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.setOperationType("Aliquot");
    assertTrue(validate(request).hasFieldErrors("operationType"));
  }

  // --- origin cardinality ---

  @Test
  void rejectsEmptyOrigins() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.setOrigins(new ArrayList<>());
    assertTrue(validate(request).hasFieldErrors("origins"));
  }

  @Test
  void rejectsSecondOriginForSingleOriginOperation() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.getOrigins().add(origin(101, "0.6"));
    request
        .getNewSample()
        .getExtraFields()
        .add(linkTo("operations.aliquot.linkFieldName", "IsPartOf", 101));
    assertSingleErrorWithCode(
        validate(request), "origins", "errors.inventory.operation.originCountExact");
  }

  @Test
  void rejectsSingleOriginForMultiOriginOperation() {
    ApiInventoryOperationPost request =
        request(
            "pool",
            newSample("Pooled", linkTo("operations.pool.linkFieldName", "HasPart", 100)),
            origin(100, "0.6"));
    assertSingleErrorWithCode(
        validate(request), "origins", "errors.inventory.operation.originCountMinimum");
  }

  // --- new-sample presence follows the operation's noOutput flag ---

  @Test
  void rejectsNewSampleForTerminalOperation() {
    ApiInventoryOperationPost request = destroyRequest();
    request.setNewSample(newSample("Should not exist"));
    assertSingleErrorWithCode(
        validate(request), "newSample", "errors.inventory.operation.newSampleForbidden");
  }

  @Test
  void rejectsMissingNewSampleForCreatingOperation() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.setNewSample(null);
    assertSingleErrorWithCode(
        validate(request), "newSample", "errors.inventory.operation.newSampleRequired");
  }

  // --- the new sample gets the full samples-endpoint validation (delegated) ---

  @Test
  void rejectsBlankSampleNameViaSamplesEndpointRules() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.getNewSample().setName("   ");
    assertTrue(validate(request).hasFieldErrors("newSample.name"));
  }

  @Test
  void rejectsStorageTempMinAboveMaxViaSamplesEndpointRules() {
    ApiInventoryOperationPost request = cryopreserveRequest();
    request.getNewSample().setStorageTempMin(celsius("-18"));
    request.getNewSample().setStorageTempMax(celsius("-20"));
    assertTrue(validate(request).hasFieldErrors("newSample.storageTempMin"));
  }

  @Test
  void rejectsNonTemperatureStorageTempUnitViaSamplesEndpointRules() {
    ApiInventoryOperationPost request = cryopreserveRequest();
    request.getNewSample().setStorageTempMax(millilitres("-20"));
    assertTrue(validate(request).hasFieldErrors("newSample.storageTempMax"));
  }

  @Test
  void rejectsLinkFieldWithUnknownRelationTypeViaSamplesEndpointRules() {
    ApiInventoryOperationPost request = aliquotRequest();
    ApiExtraField badLink = linkTo("operations.aliquot.linkFieldName", "MadeFriendsWith", 100);
    request.getNewSample().getExtraFields().add(badLink);
    assertTrue(validate(request).hasFieldErrors("newSample.extraFields[1].link.relationType"));
  }

  // --- the created subsamples must actually hold something ---

  @Test
  void rejectsNewSampleWithoutSubSamples() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.getNewSample().getSubSamples().clear();
    assertSingleErrorWithCode(
        validate(request), "newSample.subSamples", "errors.inventory.operation.subSamplesRequired");
  }

  @Test
  void rejectsSubSampleWithZeroQuantity() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.getNewSample().getSubSamples().get(0).setQuantity(millilitres("0"));
    assertSingleErrorWithCode(
        validate(request),
        "newSample.subSamples[0].quantity",
        "errors.inventory.operation.subSampleQuantityInvalid");
  }

  @Test
  void rejectsSubSampleWithoutQuantity() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.getNewSample().getSubSamples().get(0).setQuantity(null);
    assertSingleErrorWithCode(
        validate(request),
        "newSample.subSamples[0].quantity",
        "errors.inventory.operation.subSampleQuantityInvalid");
  }

  @Test
  void rejectsSubSampleQuantityWithUnsetUnit() {
    // The samples-endpoint rules also reject unit id 0 as an invalid unit, so assert on our code
    // being among the errors rather than on the error count.
    ApiInventoryOperationPost request = aliquotRequest();
    request
        .getNewSample()
        .getSubSamples()
        .get(0)
        .setQuantity(new ApiQuantityInfo(new BigDecimal("0.5"), 0));
    assertTrue(
        validate(request).getFieldErrors("newSample.subSamples[0].quantity").stream()
            .anyMatch(
                error ->
                    "errors.inventory.operation.subSampleQuantityInvalid".equals(error.getCode())));
  }

  // --- per-operation amount-taken semantics ---

  @Test
  void rejectsZeroAmountTakenWhenTheOperationDecrementsItsOrigin() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.getOrigins().get(0).setAmountTaken(millilitres("0"));
    assertSingleErrorWithCode(
        validate(request),
        "origins[0].amountTaken",
        "errors.inventory.operation.amountTakenPositive");
  }

  @Test
  void rejectsPositiveAmountTakenWhenTheOperationLeavesItsOriginUntouched() {
    ApiInventoryOperationPost request = passageRequest();
    request.getOrigins().get(0).setAmountTaken(millilitres("0.5"));
    assertSingleErrorWithCode(
        validate(request), "origins[0].amountTaken", "errors.inventory.operation.amountTakenZero");
  }

  @Test
  void rejectsAmountTakenFinerThanTheStored3dp() {
    // QuantityInfo persists at 3dp (HALF_UP), so a finer amount would silently decrement the origin
    // by a different quantity than the one that was validated (0.0004 ml would take nothing).
    ApiInventoryOperationPost request = aliquotRequest();
    request.getOrigins().get(0).setAmountTaken(millilitres("0.0004"));
    assertSingleErrorWithCode(
        validate(request),
        "origins[0].amountTaken",
        "errors.inventory.operation.amountTakenTooPrecise");
  }

  @Test
  void acceptsAmountTakenWithTrailingZerosBeyond3dp() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.getOrigins().get(0).setAmountTaken(millilitres("0.5000"));
    assertFalse(validate(request).hasErrors());
  }

  // --- equal child quantities: one each-amount input is copied to every new subsample ---

  private static ApiSubSample subSampleOf(ApiQuantityInfo quantity) {
    ApiSubSample subSample = new ApiSubSample();
    subSample.setQuantity(quantity);
    return subSample;
  }

  @Test
  void rejectsUnequalSubSampleQuantitiesWhenTheOperationConfiguresOneEachAmount() {
    // The wizard's single each-amount input is copied to every child, and the OpenAPI description
    // promises N equal subsamples; 0.25 + 0.75 would silently break that contract (valid-payload
    // review, finding 2).
    ApiInventoryOperationPost request = aliquotRequest();
    request.getNewSample().getSubSamples().get(0).setQuantity(millilitres("0.25"));
    request.getNewSample().getSubSamples().add(subSampleOf(millilitres("0.75")));
    assertSingleErrorWithCode(
        validate(request),
        "newSample.subSamples",
        "errors.inventory.operation.subSampleQuantitiesUnequal");
  }

  @Test
  void acceptsEqualSubSampleQuantitiesIncludingAcrossUnitsOfTheSameCategory() {
    // 0.5 ml and 500 µl denote the same amount; equality is unit-aware, not textual
    ApiInventoryOperationPost request = aliquotRequest();
    request
        .getNewSample()
        .getSubSamples()
        .add(
            subSampleOf(new ApiQuantityInfo(new BigDecimal("500"), RSUnitDef.MICRO_LITRE.getId())));
    Errors errors = validate(request);
    assertFalse(errors.hasErrors(), () -> "cross-unit equal children: " + errors.getAllErrors());
  }

  @Test
  void rejectsSubSampleQuantitiesFromDifferentCategoriesAsUnequal() {
    // 0.5 ml vs 0.5 g cannot be equal amounts of the same material
    ApiInventoryOperationPost request = aliquotRequest();
    request
        .getNewSample()
        .getSubSamples()
        .add(subSampleOf(new ApiQuantityInfo(new BigDecimal("0.5"), RSUnitDef.GRAM.getId())));
    assertSingleErrorWithCode(
        validate(request),
        "newSample.subSamples",
        "errors.inventory.operation.subSampleQuantitiesUnequal");
  }

  // --- configured temperature bounds (unit-aware) ---

  @Test
  void rejectsMissingStorageTemperatureWhenTheOperationConfiguresOne() {
    ApiInventoryOperationPost request = cryopreserveRequest();
    request.getNewSample().setStorageTempMin(null);
    request.getNewSample().setStorageTempMax(null);
    Errors errors = validate(request);
    for (String field : List.of("newSample.storageTempMin", "newSample.storageTempMax")) {
      assertEquals(
          "errors.inventory.operation.storageTempRequired",
          errors.getFieldErrors(field).get(0).getCode());
    }
  }

  @Test
  void rejectsCryopreserveTemperatureAboveTheConfiguredMax() {
    ApiInventoryOperationPost request = cryopreserveRequest();
    request.getNewSample().setStorageTempMin(celsius("-10"));
    request.getNewSample().setStorageTempMax(celsius("-10"));
    Errors errors = validate(request);
    for (String field : List.of("newSample.storageTempMin", "newSample.storageTempMax")) {
      assertEquals(
          "errors.inventory.operation.storageTempAboveMax",
          errors.getFieldErrors(field).get(0).getCode());
    }
  }

  @Test
  void comparesConfiguredCelsiusBoundsAcrossTemperatureScales() {
    // 250 K is -23.15 C: cold enough for cryopreserve's max of -18 C ...
    ApiInventoryOperationPost cryopreserve = cryopreserveRequest();
    ApiQuantityInfo kelvin250 =
        new ApiQuantityInfo(new BigDecimal("250"), RSUnitDef.KELVIN.getId());
    cryopreserve.getNewSample().setStorageTempMin(kelvin250);
    cryopreserve.getNewSample().setStorageTempMax(kelvin250);
    Errors cryopreserveErrors = validate(cryopreserve);
    assertFalse(
        cryopreserveErrors.hasErrors(),
        () -> "250 K cryopreserve: " + cryopreserveErrors.getAllErrors());

    // ... but far below revive's minimum of 4 C.
    ApiInventoryOperationPost revive = reviveRequest();
    revive.getNewSample().setStorageTempMin(kelvin250);
    revive.getNewSample().setStorageTempMax(kelvin250);
    assertEquals(
        "errors.inventory.operation.storageTempBelowMin",
        validate(revive).getFieldErrors("newSample.storageTempMin").get(0).getCode());
  }

  @Test
  void rejectsReviveTemperatureAboveTheConfiguredMax() {
    ApiInventoryOperationPost request = reviveRequest();
    request.getNewSample().setStorageTempMin(celsius("130"));
    request.getNewSample().setStorageTempMax(celsius("130"));
    assertEquals(
        "errors.inventory.operation.storageTempAboveMax",
        validate(request).getFieldErrors("newSample.storageTempMax").get(0).getCode());
  }

  // --- provenance links back to every origin ---

  @Test
  void rejectsNewSampleMissingTheLinkToItsOrigin() {
    ApiInventoryOperationPost request =
        request("aliquot", newSample("Aliquots"), origin(100, "0.6"));
    assertSingleErrorWithCode(
        validate(request),
        "newSample.extraFields",
        "errors.inventory.operation.linkToOriginRequired");
  }

  @Test
  void rejectsLinkWithADifferentRelationTypeThanConfigured() {
    // aliquot must link IsPartOf; a valid but different relation type does not satisfy it
    ApiInventoryOperationPost request =
        request(
            "aliquot",
            newSample("Aliquots", linkTo("operations.aliquot.linkFieldName", "IsDerivedFrom", 100)),
            origin(100, "0.6"));
    assertSingleErrorWithCode(
        validate(request),
        "newSample.extraFields",
        "errors.inventory.operation.linkToOriginRequired");
  }

  @Test
  void rejectsMultiOriginOperationMissingTheLinkToOneOrigin() {
    ApiInventoryOperationPost request =
        request(
            "pool",
            newSample("Pooled", linkTo("operations.pool.linkFieldName", "HasPart", 100)),
            origin(100, "0.6"),
            origin(101, "0.7"));
    assertSingleErrorWithCode(
        validate(request),
        "newSample.extraFields",
        "errors.inventory.operation.linkToOriginRequired");
  }

  @Test
  void provenanceLinkOnANonLinkFieldDoesNotSatisfyTheRequirement() {
    // Persistence only creates a link when the field's effective type is LINK
    // (ApiExtraFieldsHelper.addRecordExtraFieldForIncomingApiField); a link payload carried by a
    // text-typed or type-omitted field would validate here yet silently vanish on save (security
    // review, finding 7), so it must not count as the required provenance link.
    for (ApiExtraField.ExtraFieldTypeEnum type :
        Arrays.asList(ApiExtraField.ExtraFieldTypeEnum.TEXT, null)) {
      ApiInventoryOperationPost request = aliquotRequest();
      request.getNewSample().getExtraFields().get(0).setType(type);
      assertTrue(
          validate(request).getFieldErrors("newSample.extraFields").stream()
              .anyMatch(
                  error ->
                      "errors.inventory.operation.linkToOriginRequired".equals(error.getCode())),
          "a " + type + "-typed field carrying a link payload must not satisfy the link rule");
    }
  }

  @Test
  void allowsTheOptionalDocumentationLink() {
    // The documentation step is a wizard-level feature available to every output-producing
    // operation, so its IsDocumentedBy link is accepted without the definition declaring it
    // (DevDocs/adr/0007).
    ApiInventoryOperationPost request = aliquotRequest();
    request.getNewSample().getExtraFields().add(documentationLink());
    Errors errors = validate(request);
    assertFalse(errors.hasErrors(), () -> "documentation link: " + errors.getAllErrors());
  }

  // --- the new sample's extra fields are matched to the definition by key ---

  @Test
  void rejectsNewSampleExtraFieldWhoseKeyTheDefinitionDoesNotDeclare() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.getNewSample().getExtraFields().add(textField("operations.smuggled.field", "x"));
    assertSingleErrorWithCode(
        validate(request),
        "newSample.extraFields[1].operationFieldKey",
        "errors.inventory.operation.fieldKeyUnknown");
  }

  @Test
  void rejectsNewSampleExtraFieldCarryingNoKeyAtAll() {
    // Resolved field names interpolate user input and are localized, so a field with no key cannot
    // be matched to the definition at all.
    ApiInventoryOperationPost request = aliquotRequest();
    ApiExtraField unkeyed = textField("operations.passage.numberField", "3");
    unkeyed.setOperationFieldKey(null);
    request.getNewSample().getExtraFields().add(unkeyed);
    assertSingleErrorWithCode(
        validate(request),
        "newSample.extraFields[1].operationFieldKey",
        "errors.inventory.operation.fieldKeyUnknown");
  }

  @Test
  void rejectsDuplicateOfADeclaredLinkField() {
    // Aliquot declares one link and has one origin, so a second field with the same key is one
    // more than the definition describes.
    ApiInventoryOperationPost request = aliquotRequest();
    request
        .getNewSample()
        .getExtraFields()
        .add(linkTo("operations.aliquot.linkFieldName", "IsPartOf", 100));
    assertTrue(
        validate(request).getFieldErrors("newSample.extraFields").stream()
            .anyMatch(
                error ->
                    "errors.inventory.operation.declaredFieldMissing".equals(error.getCode())));
  }

  @Test
  void rejectsMissingDeclaredTextField() {
    // Passage declares a passage-number text field; omitting it drops part of the operation's
    // record (review repro F5c).
    ApiInventoryOperationPost request = passageRequest();
    request.getNewSample().getExtraFields().removeIf(field -> field.getLink() == null);
    assertSingleErrorWithCode(
        validate(request),
        "newSample.extraFields",
        "errors.inventory.operation.declaredFieldMissing");
  }

  @Test
  void rejectsDeclaredTextFieldSentAsALink() {
    ApiInventoryOperationPost request = cryopreserveRequest();
    request.getNewSample().getExtraFields().stream()
        .filter(
            field -> "operations.cryopreserve.cryomediumField".equals(field.getOperationFieldKey()))
        .forEach(field -> field.setType(ApiExtraField.ExtraFieldTypeEnum.LINK));
    assertTrue(
        validate(request).getFieldErrors("newSample.extraFields[1]").stream()
            .anyMatch(
                error ->
                    "errors.inventory.operation.declaredFieldMissing".equals(error.getCode())));
  }

  @Test
  void rejectsDocumentationLinkWithTheWrongRelationType() {
    ApiInventoryOperationPost request = aliquotRequest();
    ApiExtraField documentation = documentationLink();
    documentation.getLink().setRelationType("IsDerivedFrom");
    request.getNewSample().getExtraFields().add(documentation);
    assertSingleErrorWithCode(
        validate(request),
        "newSample.extraFields[1].link",
        "errors.inventory.operation.documentationLinkInvalid");
  }

  @Test
  void rejectsASecondDocumentationLink() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.getNewSample().getExtraFields().add(documentationLink());
    request.getNewSample().getExtraFields().add(documentationLink());
    assertTrue(
        validate(request).getFieldErrors("newSample.extraFields").stream()
            .anyMatch(
                error ->
                    "errors.inventory.operation.declaredFieldMissing".equals(error.getCode())));
  }

  // --- the new sample is a whitelist: only what the definition declares may be sent ---

  @Test
  void rejectsNewSamplePropertiesNoOperationDefinitionDeclares() {
    // An operation request is not a general sample POST: the wizard sends exactly the properties
    // the definition declares, so anything else is smuggled state (sharing, placement, tags,
    // images) and is rejected naming the property (DevDocs/adr/0007).
    record Undeclared(String property, Consumer<ApiSampleWithFullSubSamples> smuggle) {}
    List<Undeclared> undeclared =
        List.of(
            new Undeclared("description", sample -> sample.setDescription("smuggled")),
            new Undeclared(
                "tags", sample -> sample.setTags(new ArrayList<>(List.of(new ApiTagInfo())))),
            new Undeclared(
                "barcodes",
                sample -> sample.setBarcodes(new ArrayList<>(List.of(new ApiBarcode())))),
            new Undeclared(
                "sharingMode", sample -> sample.setSharingMode(ApiInventorySharingMode.WHITELIST)),
            new Undeclared(
                "sharedWith",
                sample ->
                    sample.setSharedWith(
                        new ArrayList<>(List.of(new ApiGroupInfoWithSharedFlag())))),
            new Undeclared("newBase64Image", sample -> sample.setNewBase64Image("data:image/png")),
            new Undeclared("sampleSource", sample -> sample.setSampleSource(SampleSource.OTHER)),
            new Undeclared("expiryDate", sample -> sample.setExpiryDate(LocalDate.of(2030, 1, 1))),
            new Undeclared(
                "newSampleSubSamplesCount", sample -> sample.setNewSampleSubSamplesCount(3)),
            new Undeclared(
                "newSampleSubSampleTargetLocations",
                sample ->
                    sample.setNewSampleSubSampleTargetLocations(
                        new ArrayList<>(List.of(new ApiTargetLocation())))));
    for (Undeclared smuggled : undeclared) {
      ApiInventoryOperationPost request = aliquotRequest();
      smuggled.smuggle().accept(request.getNewSample());
      assertSingleErrorWithCode(
          validate(request),
          "newSample." + smuggled.property(),
          "errors.inventory.operation.undeclaredProperty");
    }
  }

  @Test
  void rejectsStorageTemperatureOnAnOperationThatDeclaresNone() {
    // Only an operation with a temperature input (Cryopreserve, Revive) may set the new sample's
    // storage temperature; Aliquot declares none, so sending one is undeclared content.
    ApiInventoryOperationPost request = aliquotRequest();
    request.getNewSample().setStorageTempMin(celsius("-80"));
    request.getNewSample().setStorageTempMax(celsius("-80"));
    Errors errors = validate(request);
    assertTrue(
        errors.getFieldErrors("newSample.storageTempMin").stream()
            .anyMatch(
                error -> "errors.inventory.operation.undeclaredProperty".equals(error.getCode())));
    assertTrue(
        errors.getFieldErrors("newSample.storageTempMax").stream()
            .anyMatch(
                error -> "errors.inventory.operation.undeclaredProperty".equals(error.getCode())));
  }

  @Test
  void rejectsSubSamplePropertiesNoOperationDefinitionDeclares() {
    // The created subsamples carry a quantity and nothing else: the operation's own fields go on
    // the sample, so notes, fields and placement on a subsample are undeclared content.
    record Undeclared(String property, Consumer<ApiSubSample> smuggle) {}
    List<Undeclared> undeclared =
        List.of(
            new Undeclared(
                "notes",
                subSample -> subSample.setNotes(new ArrayList<>(List.of(new ApiSubSampleNote())))),
            new Undeclared(
                "extraFields",
                subSample ->
                    subSample.setExtraFields(
                        new ArrayList<>(
                            List.of(new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.TEXT))))),
            new Undeclared("description", subSample -> subSample.setDescription("smuggled")),
            new Undeclared("name", subSample -> subSample.setName("Injected child name")),
            new Undeclared("iconId", subSample -> subSample.setIconId(424242L)),
            new Undeclared(
                "parentLocation",
                subSample -> subSample.setParentLocation(new ApiContainerLocation())));
    for (Undeclared smuggled : undeclared) {
      ApiInventoryOperationPost request = aliquotRequest();
      smuggled.smuggle().accept(request.getNewSample().getSubSamples().get(0));
      String field = "newSample.subSamples[0]." + smuggled.property();
      assertTrue(
          validate(request).getFieldErrors(field).stream()
              .anyMatch(
                  error -> "errors.inventory.operation.undeclaredProperty".equals(error.getCode())),
          () -> field + " must be rejected as undeclared");
    }
  }

  // --- origin extra fields: strictly new-field requests, contents fully validated ---

  private static ApiExtraField disposedField() {
    ApiExtraField field = new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.TEXT);
    field.setName("Disposed");
    field.setContent("2026-08-20");
    field.setNewFieldRequest(true);
    field.setOperationFieldKey("operations.destroy.disposedField");
    return field;
  }

  private static ApiExtraField newSampleFieldWithKey(
      ApiInventoryOperationPost request, String key) {
    return request.getNewSample().getExtraFields().stream()
        .filter(field -> key.equals(field.getOperationFieldKey()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void rejectsOriginExtraFieldOnAnOperationThatDeclaresNone() {
    // Only Destroy declares an origin field; adding one to an Aliquot origin writes to a record the
    // operation is only supposed to decrement (review repro F5a).
    ApiInventoryOperationPost request = aliquotRequest();
    request.getOrigins().get(0).setExtraFields(new ArrayList<>(List.of(disposedField())));
    assertSingleErrorWithCode(
        validate(request),
        "origins[0].extraFields[0].operationFieldKey",
        "errors.inventory.operation.fieldKeyUnknown");
  }

  @Test
  void rejectsMissingDeclaredOriginField() {
    // Destroy declares a disposed-date field on each origin; omitting it loses the disposal record
    // (review repro F5b).
    ApiInventoryOperationPost request = destroyRequest();
    request.getOrigins().get(0).setExtraFields(new ArrayList<>());
    assertSingleErrorWithCode(
        validate(request),
        "origins[0].extraFields",
        "errors.inventory.operation.declaredFieldMissing");
  }

  @Test
  void rejectsOriginFieldContentTheComputedFunctionCouldNotHaveProduced() {
    // Destroy's disposed date is computed by "today", so its content must be an ISO date; the
    // backend checks the shape rather than recomputing it (DevDocs/adr/0007).
    for (String content : List.of("last Tuesday", "20/08/2026", "")) {
      ApiInventoryOperationPost request = destroyRequest();
      request.getOrigins().get(0).getExtraFields().get(0).setContent(content);
      assertSingleErrorWithCode(
          validate(request),
          "origins[0].extraFields[0].content",
          "errors.inventory.operation.computedContentInvalid");
    }
  }

  @Test
  void rejectsPassageNumberThatIsNotAPositiveWholeNumber() {
    // Passage's number is computed by "increment", so it can only ever be a positive integer.
    for (String content : List.of("0", "-1", "2.5", "two", "")) {
      ApiInventoryOperationPost request = passageRequest();
      newSampleFieldWithKey(request, "operations.passage.numberField").setContent(content);
      assertSingleErrorWithCode(
          validate(request),
          "newSample.extraFields[1].content",
          "errors.inventory.operation.computedContentInvalid");
    }
  }

  @Test
  void allowsBlankContentInADeclaredFieldFedByAnOptionalInput() {
    // Cryopreserve's cryomedium input is optional, so its field may legitimately be empty; only a
    // field fed by a required input must carry content.
    ApiInventoryOperationPost request = cryopreserveRequest();
    newSampleFieldWithKey(request, "operations.cryopreserve.cryomediumField").setContent("");
    Errors errors = validate(request);
    assertFalse(errors.hasErrors(), () -> "optional content: " + errors.getAllErrors());
  }

  @Test
  void rejectsDifferentStorageTemperatureMinimumAndMaximum() {
    // One temperature input feeds both storage temperatures, so a request that spreads them into a
    // range describes something the operation cannot produce (review repro F5d).
    ApiInventoryOperationPost request = cryopreserveRequest();
    request.getNewSample().setStorageTempMin(celsius("-80"));
    request.getNewSample().setStorageTempMax(celsius("-20"));
    assertSingleErrorWithCode(
        validate(request),
        "newSample.storageTempMax",
        "errors.inventory.operation.storageTempSingleValue");
  }

  @Test
  void allowsNewFieldRequestExtraFieldsOnAnOrigin() {
    ApiInventoryOperationPost request = destroyRequest();
    request.getOrigins().get(0).setExtraFields(new ArrayList<>(List.of(disposedField())));
    Errors errors = validate(request);
    assertFalse(errors.hasErrors(), () -> "disposed field: " + errors.getAllErrors());
  }

  @Test
  void rejectsOriginExtraFieldThatIsNotANewFieldRequest() {
    ApiInventoryOperationPost request = destroyRequest();
    ApiExtraField notNew = disposedField();
    notNew.setNewFieldRequest(false);
    request.getOrigins().get(0).setExtraFields(new ArrayList<>(List.of(notNew)));
    assertSingleErrorWithCode(
        validate(request),
        "origins[0].extraFields[0].newFieldRequest",
        "errors.inventory.operation.originFieldNewOnly");
  }

  @Test
  void rejectsOriginExtraFieldThatDeletesOrEditsAnExistingField() {
    // a delete request, or an id-bearing edit of an existing field, is a mutation no operation
    // definition describes; only adding new fields is allowed (DevDocs/adr/0007)
    ApiInventoryOperationPost request = destroyRequest();
    ApiExtraField delete = disposedField();
    delete.setDeleteFieldRequest(true);
    request.getOrigins().get(0).setExtraFields(new ArrayList<>(List.of(delete)));
    assertTrue(
        validate(request).hasFieldErrors("origins[0].extraFields[0].newFieldRequest"),
        "delete request must be rejected");

    ApiInventoryOperationPost edit = destroyRequest();
    ApiExtraField existing = disposedField();
    existing.setId(42L);
    edit.getOrigins().get(0).setExtraFields(new ArrayList<>(List.of(existing)));
    assertTrue(
        validate(edit).hasFieldErrors("origins[0].extraFields[0].newFieldRequest"),
        "id-bearing edit must be rejected");
  }

  @Test
  void validatesOriginExtraFieldContentsLikeTheSubsampleEndpoint() {
    // a link-typed origin field without a link payload is rejected by the same shared field
    // validator the subsample PUT endpoint uses
    ApiInventoryOperationPost request = destroyRequest();
    ApiExtraField linkWithoutPayload = new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.LINK);
    linkWithoutPayload.setName("Broken link");
    linkWithoutPayload.setNewFieldRequest(true);
    request.getOrigins().get(0).setExtraFields(new ArrayList<>(List.of(linkWithoutPayload)));
    assertTrue(validate(request).hasFieldErrors("origins[0].extraFields[0].link"));
  }

  // --- malformed list elements must be clean 400s, not 500s ---

  @Test
  void rejectsNullOriginListEntry() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.setOrigins(new ArrayList<>(Arrays.asList((ApiInventoryOperationOriginUpdate) null)));
    assertSingleErrorWithCode(
        validate(request), "origins", "errors.inventory.operation.originIdRequired");
  }

  @Test
  void rejectsNullSubSampleListEntry() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.getNewSample().setSubSamples(new ArrayList<>(Arrays.asList((ApiSubSample) null)));
    assertTrue(
        validate(request).getFieldErrors("newSample.subSamples").stream()
            .anyMatch(
                error ->
                    "errors.inventory.operation.subSampleQuantityInvalid".equals(error.getCode())));
  }

  @Test
  void rejectsNullTagsListEntry() {
    // Would otherwise reach the delegated samples validator's tag-length check and NPE.
    ApiInventoryOperationPost request = aliquotRequest();
    request.getNewSample().setTags(new ArrayList<>(Arrays.asList((ApiTagInfo) null)));
    assertSingleErrorWithCode(
        validate(request), "newSample.tags", "errors.inventory.operation.undeclaredProperty");
  }

  @Test
  void rejectsNullExtraFieldListEntry() {
    // Would otherwise reach the delegated samples validator's key-lookup loop and NPE.
    ApiInventoryOperationPost request = aliquotRequest();
    request.getNewSample().getExtraFields().add(null);
    assertSingleErrorWithCode(
        validate(request), "newSample.extraFields", "errors.inventory.operation.fieldKeyUnknown");
  }

  // --- storage temperature shape and magnitude (Copilot review, PR #1090) ---

  @Test
  void malformedStorageTemperatureIsAFieldScopedErrorNotAnException() {
    // The delegated samples validator resolves the unit before the operation's own bounds check
    // runs, so a temperature object with no unit (JSON "{}") or an unknown one used to escape as an
    // unchecked IllegalArgumentException, i.e. a 500 rather than a 400.
    for (ApiQuantityInfo malformed :
        List.of(
            new ApiQuantityInfo(new BigDecimal("-20"), (Integer) null),
            new ApiQuantityInfo(new BigDecimal("-20"), 999999),
            new ApiQuantityInfo(null, (Integer) null))) {
      ApiInventoryOperationPost request = cryopreserveRequest();
      request.getNewSample().setStorageTempMax(malformed);
      Errors errors = validate(request);
      assertTrue(
          errors.hasErrors(),
          () -> "a malformed storage temperature must be reported, not thrown: " + malformed);
    }
  }

  @Test
  void rejectsAStorageTemperatureTooLargeForTheStoredColumn() {
    // -1E30 is below Cryopreserve's -18 maximum, so the configured bound accepts it, but the
    // storage-temperature column is DECIMAL(19,3): persisting it would overflow or silently store a
    // different temperature. Same rule as amountTaken (code review, finding 7).
    ApiInventoryOperationPost request = cryopreserveRequest();
    request
        .getNewSample()
        .setStorageTempMin(
            new ApiQuantityInfo(new BigDecimal("-1E+30"), RSUnitDef.CELSIUS.getId()));
    request
        .getNewSample()
        .setStorageTempMax(
            new ApiQuantityInfo(new BigDecimal("-1E+30"), RSUnitDef.CELSIUS.getId()));
    assertTrue(
        validate(request).getFieldErrors().stream()
            .anyMatch(error -> "errors.inventory.temperature.notStorable".equals(error.getCode())),
        () -> "expected a not-storable rejection, got " + validate(request).getAllErrors());
  }

  // --- origin count ceiling (resource-exhaustion guard) ---

  @Test
  void rejectsMoreOriginsThanTheMaximum() {
    ApiSampleWithFullSubSamples sample = newSample("Pooled");
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("pool");
    request.setNewSample(sample);
    List<ApiInventoryOperationOriginUpdate> origins = new ArrayList<>();
    for (long id = 1; id <= 101; id++) {
      origins.add(origin(id, "0.6"));
      sample.getExtraFields().add(linkTo("operations.pool.linkFieldName", "HasPart", id));
    }
    request.setOrigins(origins);
    assertTrue(
        validate(request).getFieldErrors("origins").stream()
            .anyMatch(
                error -> "errors.inventory.operation.originCountMaximum".equals(error.getCode())));
  }

  @Test
  void reportsTheMaximumBeforeTheCardinalityForAnOversizedSingleOriginOperation() {
    // A single-origin operation with 500 origins used to report only "exactly one", hiding the
    // actual reason and still walking every origin afterwards (Copilot review, PR #1090).
    ApiInventoryOperationPost request = aliquotRequest();
    List<ApiInventoryOperationOriginUpdate> origins = new ArrayList<>();
    for (long id = 1; id <= 101; id++) {
      origins.add(origin(id, "0.6"));
    }
    request.setOrigins(origins);
    Errors errors = validate(request);
    assertEquals(
        List.of("errors.inventory.operation.originCountMaximum"),
        errors.getFieldErrors("origins").stream().map(FieldError::getCode).toList(),
        () -> "expected only the ceiling error, got " + errors.getAllErrors());
    // and validation stops there rather than reporting a per-origin error for each of the 101
    assertEquals(
        1, errors.getErrorCount(), () -> "expected one error, got " + errors.getAllErrors());
  }

  @Test
  void rejectsMoreSubSamplesThanTheMaximumBeforeWalkingThem() {
    // The DTO's @Size records a violation, but this validator still ran every per-subsample pass
    // over the whole list. Bounded like the origins ceiling (Copilot review, PR #1090).
    ApiInventoryOperationPost request = aliquotRequest();
    List<ApiSubSample> children = new ArrayList<>();
    for (int i = 0; i <= MAX_SUBSAMPLES; i++) {
      ApiSubSample child = new ApiSubSample();
      child.setQuantity(millilitres("0.5"));
      children.add(child);
    }
    request.getNewSample().setSubSamples(children);
    Errors errors = validate(request);
    assertSingleErrorWithCode(
        errors, "newSample.subSamples", "errors.inventory.operation.subSampleCountMaximum");
  }

  @Test
  void rejectsMoreExtraFieldsThanTheMaximumBeforeMatchingThemPerOrigin() {
    // Worse than the subsample list: extraFields has no @Size at all, and validateDeclaredLinks
    // scans every field once per origin, so an oversized list is O(origins x fields) of work on a
    // public endpoint (Copilot review, PR #1090).
    ApiInventoryOperationPost request = aliquotRequest();
    for (int i = 0; i <= MAX_EXTRA_FIELDS; i++) {
      request
          .getNewSample()
          .getExtraFields()
          .add(linkTo("operations.aliquot.linkFieldName", "IsPartOf", 100));
    }
    Errors errors = validate(request);
    assertSingleErrorWithCode(
        errors, "newSample.extraFields", "errors.inventory.operation.extraFieldCountMaximum");
  }

  // --- origin shape rules (operation-independent) ---

  @Test
  void rejectsOriginWithoutId() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.getOrigins().get(0).setId(null);
    assertTrue(validate(request).hasFieldErrors("origins[0].id"));
  }

  @Test
  void rejectsNegativeAmountTaken() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.getOrigins().get(0).setAmountTaken(millilitres("-1"));
    assertTrue(validate(request).hasFieldErrors("origins[0].amountTaken"));
  }

  @Test
  void rejectsMissingAmountTaken() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.getOrigins().get(0).setAmountTaken(null);
    assertTrue(validate(request).hasFieldErrors("origins[0].amountTaken"));
  }

  @Test
  void rejectsAmountTakenWithoutNumericValue() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.getOrigins().get(0).setAmountTaken(new ApiQuantityInfo(null, 3));
    assertTrue(validate(request).hasFieldErrors("origins[0].amountTaken"));
  }

  @Test
  void rejectsAmountTakenWithoutUnit() {
    // A null unitId passes the numeric check but fails later in the manager (toQuantityInfo needs a
    // unit for the unit-aware subtraction); reject it here with a clean 400 rather than a 500.
    ApiInventoryOperationPost request = aliquotRequest();
    request
        .getOrigins()
        .get(0)
        .setAmountTaken(new ApiQuantityInfo(new BigDecimal("1"), (Integer) null));
    assertTrue(validate(request).hasFieldErrors("origins[0].amountTaken"));
  }

  @Test
  void rejectsAmountTakenWithNonPositiveUnit() {
    // The frontend uses unitId <= 0 (UNSET_UNIT = 0) as an "unset" marker; a non-positive unit id
    // is not a real unit and would fail the unit-aware subtraction, so reject it here with a clean
    // 400.
    ApiInventoryOperationPost request = aliquotRequest();
    request.getOrigins().get(0).setAmountTaken(new ApiQuantityInfo(new BigDecimal("1"), 0));
    assertTrue(validate(request).hasFieldErrors("origins[0].amountTaken"));
  }

  @Test
  void rejectsDuplicateOriginIds() {
    // The same subsample listed twice would be decremented twice while each entry is validated
    // against the same original quantity, so it could be drained past the over-removal limit.
    ApiInventoryOperationPost request = poolRequest();
    request.getOrigins().get(1).setId(100L);
    assertTrue(validate(request).hasFieldErrors("origins[1].id"));
  }

  // --- amount-taken unit: must be a real amount unit (code review F4) ---

  @Test
  void rejectsAmountTakenWithAUnitThatDoesNotExist() {
    // unitId > 0 is not enough: an unknown id reached QuantityUtils.sum in the manager and surfaced
    // as a 422, not a field-scoped 400.
    ApiInventoryOperationPost request = aliquotRequest();
    request.getOrigins().get(0).setAmountTaken(new ApiQuantityInfo(new BigDecimal("1"), 999999));
    assertSingleErrorWithCode(
        validate(request), "origins[0].amountTaken", "errors.inventory.quantity.unitInvalid");
  }

  @Test
  void rejectsCreatedSubSamplesWhoseTotalCannotBeStored() {
    // Each child is storable on its own, but creation derives and persists the parent sample total
    // from them, into the same DECIMAL(19,3) column. Two children at the per-value ceiling sum past
    // it, so a request that passes every individual check would still 500 inside the manager
    // (Copilot review, PR #1090).
    ApiInventoryOperationPost request = aliquotRequest();
    ApiSubSample first = request.getNewSample().getSubSamples().get(0);
    first.setQuantity(millilitres("9999999999999999.999"));
    ApiSubSample second = new ApiSubSample();
    second.setQuantity(millilitres("9999999999999999.999"));
    request.getNewSample().getSubSamples().add(second);

    assertSingleErrorWithCode(
        validate(request),
        "newSample.subSamples",
        "errors.inventory.operation.subSampleTotalNotStorable");
  }

  @Test
  void rejectsAmountTakenInAUnitThatIsNotAnAmount() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.getOrigins().get(0).setAmountTaken(celsius("1"));
    assertSingleErrorWithCode(
        validate(request), "origins[0].amountTaken", "errors.inventory.quantity.unitNotAmount");
  }

  @Test
  void rejectsNewSubSampleQuantityFinerThanTheStored3dp() {
    // Same rule as amountTaken: 0.0004 ml would persist as 0 once QuantityInfo rounds to 3dp,
    // creating a subsample that holds nothing (code review, finding 7).
    ApiInventoryOperationPost request = aliquotRequest();
    request.getNewSample().getSubSamples().get(0).setQuantity(millilitres("0.0004"));
    assertSingleErrorWithCode(
        validate(request),
        "newSample.subSamples[0].quantity",
        "errors.inventory.operation.subSampleQuantityTooPrecise");
  }

  @Test
  void acceptsNewSubSampleQuantityAtTheStoredPrecision() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.getNewSample().getSubSamples().get(0).setQuantity(millilitres("0.001"));
    assertFalse(validate(request).hasErrors());
  }

  @Test
  void rejectsAnIconSmuggledOntoTheNewSample() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.getNewSample().setIconId(424242L);
    assertSingleErrorWithCode(
        validate(request), "newSample.iconId", "errors.inventory.operation.undeclaredProperty");
  }

  /**
   * Tripwire (code review, finding 9): the subsample whitelist is a denylist in code, so a property
   * added to the subsample DTO later would be accepted silently. This pins the DTO's writable JSON
   * properties; a new one fails here until the operation contract either declares it or the
   * validator rejects it.
   */
  @Test
  void everyWritableSubSampleJsonPropertyIsEitherDeclaredOrRejected() {
    ObjectMapper mapper = new ObjectMapper();
    Set<String> writable =
        mapper
            .getDeserializationConfig()
            .introspect(mapper.constructType(ApiSubSample.class))
            .findProperties()
            .stream()
            .filter(BeanPropertyDefinition::couldDeserialize)
            .map(BeanPropertyDefinition::getName)
            .collect(Collectors.toCollection(TreeSet::new));
    Set<String> declared = Set.of("quantity");
    Set<String> rejectedAsUndeclared =
        Set.of(
            "barcodes",
            "description",
            "extraFields",
            "iconId",
            "name",
            "newBase64Image",
            "notes",
            "parentContainers",
            "parentLocation",
            "sharedWith",
            "sharingMode",
            "tags");
    // apiTagInfo is the DTO's alternative tag input; it populates tags, which is rejected above.
    Set<String> feedsARejectedProperty = Set.of("apiTagInfo");
    Set<String> assignedByTheServerOnCreation =
        Set.of(
            "_links",
            "attachments",
            "created",
            "createdBy",
            "deleted",
            "deletedDate",
            "deletedOnSampleDeletion",
            "globalId",
            "historicalVersion",
            "id",
            "identifiers",
            "lastModified",
            "lastMoveDate",
            "lastNonWorkbenchParent",
            "modifiedBy",
            "modifiedByFullName",
            "owner",
            "permittedActions",
            "revisionId",
            "sample",
            "storedInContainer",
            "type",
            "version");
    Set<String> ignoredByTheServer = new TreeSet<>(feedsARejectedProperty);
    ignoredByTheServer.addAll(assignedByTheServerOnCreation);
    Set<String> unaccounted = new TreeSet<>(writable);
    unaccounted.removeAll(declared);
    unaccounted.removeAll(rejectedAsUndeclared);
    unaccounted.removeAll(ignoredByTheServer);
    assertTrue(
        unaccounted.isEmpty(),
        () ->
            "ApiSubSample gained writable JSON properties the operation contract does not account"
                + " for: "
                + unaccounted
                + " (all writable: "
                + writable
                + ")");
  }

  // --- documentation link target (code review, finding 6) ---

  @Test
  void rejectsADocumentationLinkToAnInventoryRecord() {
    ApiInventoryOperationPost request = aliquotRequest();
    ApiExtraField documentation = documentationLink();
    documentation.getLink().setTargetGlobalId("SS100");
    request.getNewSample().getExtraFields().add(documentation);
    assertSingleErrorWithCode(
        validate(request),
        "newSample.extraFields[1].link",
        "errors.inventory.operation.documentationLinkTargetInvalid");
  }

  @Test
  void acceptsADocumentationLinkToAnyElnRecordThePickerOffers() {
    for (String target : List.of("SD1", "NB1", "GL1")) {
      ApiInventoryOperationPost request = aliquotRequest();
      ApiExtraField documentation = documentationLink();
      documentation.getLink().setTargetGlobalId(target);
      request.getNewSample().getExtraFields().add(documentation);
      assertFalse(validate(request).hasErrors(), () -> target + " should be accepted");
    }
  }
}
