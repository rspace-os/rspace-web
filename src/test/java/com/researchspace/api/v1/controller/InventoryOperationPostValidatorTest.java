package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryOperationAmountMode;
import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.InventoryOperationConfigRegistry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    return new InventoryOperationPostValidator(new InventoryOperationConfigRegistry());
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

  static ApiInventoryOperationPost passageRequest() {
    return request(
        "passage",
        newSample(
            "Passaged",
            linkTo("operations.passage.linkFieldName", "IsDerivedFrom", 100),
            textField("operations.passage.numberField", "4")),
        origin(100, "0"));
  }

  static ApiInventoryOperationPost poolRequest() {
    return request(
        "pool",
        newSample(
            "Pooled",
            linkTo("operations.pool.linkFieldName", "HasPart", 100),
            linkTo("operations.pool.linkFieldName", "HasPart", 101)),
        origin(100, "0.6"),
        origin(101, "0.7"));
  }

  static ApiInventoryOperationPost deriveRequest() {
    return request(
        "derive",
        newSample("Derived", linkTo("operations.derive.linkFieldName", "IsDerivedFrom", 100)),
        origin(100, "0.6"));
  }

  static ApiInventoryOperationPost cryopreserveRequest() {
    ApiSampleWithFullSubSamples sample =
        newSample(
            "Frozen",
            linkTo("operations.cryopreserve.linkFieldName", "IsDerivedFrom", 100),
            textField("operations.cryopreserve.cryomediumField", "DMSO 10%"));
    sample.setStorageTempMin(celsius("-20"));
    sample.setStorageTempMax(celsius("-20"));
    return request("cryopreserve", sample, origin(100, "0.6"));
  }

  static ApiInventoryOperationPost reviveRequest() {
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
  void anAbsentAmountIsAcceptedWhereTheDefinitionDecidesWhatIsTaken() {
    // A typed facade sends no amount for Passage (takes nothing) or Destroy (takes everything);
    // the manager's builder supplies it (M6). The wizard still sends one, checked as before.
    ApiInventoryOperationPost passage = passageRequest();
    passage.getOrigins().get(0).setAmountTaken(null);
    assertFalse(validate(passage).hasErrors(), () -> validate(passage).getAllErrors().toString());
    ApiInventoryOperationPost destroy = destroyRequest();
    destroy.getOrigins().get(0).setAmountTaken(null);
    assertFalse(validate(destroy).hasErrors(), () -> validate(destroy).getAllErrors().toString());
  }

  @Test
  void anAbsentAmountIsStillRejectedWhereTheOperationTakesFromTheOrigin() {
    ApiInventoryOperationPost aliquot = aliquotRequest();
    aliquot.getOrigins().get(0).setAmountTaken(null);
    assertSingleErrorWithCode(
        validate(aliquot),
        "origins[0].amountTaken",
        "errors.inventory.operation.amountTakenInvalid");
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
        // An omitted operationType is the likeliest first mistake against a new endpoint,
        // and "Unknown operation type [null]." named nothing useful (parallel review).
        validate(request), "operationType", "errors.inventory.operation.operationTypeRequired");
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

  // --- the new sample gets the full samples-endpoint validation (delegated) ---

  // --- the created subsamples must actually hold something ---

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
  void rejectsAnExplicitAmountModeOnAnOriginEmptyingOperation() {
    // Destroy's whole promise is to empty the origin, so its amount is a compare-and-swap claim the
    // manager checks live. A client declaring "explicit" is claiming the opposite, which this
    // operation cannot honour: malformed, so 400 here rather than the manager's 409 (RSDEV-1231).
    ApiInventoryOperationPost request = destroyRequest();
    request.getOrigins().get(0).setAmountMode(ApiInventoryOperationAmountMode.EXPLICIT);
    assertSingleErrorWithCode(
        validate(request),
        "origins[0].amountMode",
        "errors.inventory.operation.amountModeMustBeAll");
  }

  @Test
  void acceptsAnAllAmountModeOnAnOriginEmptyingOperation() {
    ApiInventoryOperationPost request = destroyRequest();
    request.getOrigins().get(0).setAmountMode(ApiInventoryOperationAmountMode.ALL);
    assertFalse(validate(request).hasErrors());
  }

  @Test
  void acceptsAnAbsentAmountModeOnEveryOperation() {
    // Backward compatibility: amountMode is optional on the wire, so a request predating it must
    // still validate, on an origin-emptying operation as much as any other. The golden fixtures
    // carry no mode, so this pins the default explicitly rather than by omission.
    for (ApiInventoryOperationPost request : List.of(aliquotRequest(), destroyRequest())) {
      assertNull(request.getOrigins().get(0).getAmountMode());
      Errors errors = validate(request);
      assertFalse(
          errors.hasErrors(),
          () -> request.getOperationType() + " without amountMode: " + errors.getAllErrors());
    }
  }

  @Test
  void acceptsAnExplicitAmountModeOnAnOperationThatDoesNotEmptyItsOrigin() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.getOrigins().get(0).setAmountMode(ApiInventoryOperationAmountMode.EXPLICIT);
    assertFalse(validate(request).hasErrors());
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

  // --- configured temperature bounds (unit-aware) ---

  // --- provenance links back to every origin ---

  // --- the new sample's extra fields are matched to the definition by key ---

  // --- the new sample is a whitelist: only what the definition declares may be sent ---

  // --- origin extra fields: strictly new-field requests, contents fully validated ---

  private static ApiExtraField disposedField() {
    ApiExtraField field = new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.TEXT);
    field.setName("Disposed");
    field.setContent("2026-08-20");
    field.setNewFieldRequest(true);
    field.setOperationFieldKey("operations.destroy.disposedField");
    return field;
  }

  // --- malformed list elements must be clean 400s, not 500s ---

  @Test
  void rejectsNullOriginListEntry() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.setOrigins(new ArrayList<>(Arrays.asList((ApiInventoryOperationOriginUpdate) null)));
    assertSingleErrorWithCode(
        validate(request), "origins", "errors.inventory.operation.originIdRequired");
  }

  // --- storage temperature shape and magnitude (Copilot review, PR #1090) ---

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
  void rejectsAmountTakenInAUnitThatIsNotAnAmount() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.getOrigins().get(0).setAmountTaken(celsius("1"));
    assertSingleErrorWithCode(
        validate(request), "origins[0].amountTaken", "errors.inventory.quantity.unitNotAmount");
  }

  // --- documentation link target (code review, finding 6) ---

  @Test
  void rejectsAnAllAmountModeOnAnOperationThatTakesNothingFromItsOrigins() {
    // Passage links to its origin and takes nothing, so its amount must be exactly zero. Declaring
    // "all" claims the zero equals the origin's whole quantity, which the manager compare-and-swaps
    // and rejects as a 409 telling the client to reload; reloading changes nothing, so the client
    // resubmits the only payload the validator accepts and 409s forever. A whole-origin claim is
    // meaningless here, so it is malformed: a 400, like the mirror rule for Destroy (parallel
    // review).
    ApiInventoryOperationPost request = passageRequest();
    request.getOrigins().get(0).setAmountMode(ApiInventoryOperationAmountMode.ALL);
    assertSingleErrorWithCode(
        validate(request),
        "origins[0].amountMode",
        "errors.inventory.operation.amountModeNotApplicable");
  }

  @Test
  void rejectsAnUnrecognisedAmountModeWithAKeyedMessage() {
    // An unrecognised wire value binds to UNKNOWN rather than throwing out of the @JsonCreator.
    // Throwing there produced an HttpMessageNotReadableException whose raw English message the
    // shared advice copied into the 400 body, so an untranslated developer string (echoing the
    // client's own input) reached the user. Rejecting it here keeps the 400 and makes the message
    // a catalog key like every other rule on this endpoint (parallel review).
    ApiInventoryOperationPost request = aliquotRequest();
    request.getOrigins().get(0).setAmountMode(ApiInventoryOperationAmountMode.UNKNOWN);
    assertSingleErrorWithCode(
        validate(request), "origins[0].amountMode", "errors.inventory.operation.amountModeUnknown");
  }

  @Test
  void stillAcceptsAnAllAmountModeOnAnOperationThatDoesTakeFromItsOrigins() {
    // Aliquot decrements its origin, so "take all of it" is a real request and stays a
    // compare-and-swap rather than a 400.
    ApiInventoryOperationPost request = aliquotRequest();
    request.getOrigins().get(0).setAmountMode(ApiInventoryOperationAmountMode.ALL);
    assertFalse(validate(request).hasErrors());
  }

  // --- the request's top-level fields: inputs, template, documentation target ---

  @Test
  void inputsShapeIsValidWithoutANewSample() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.setNewSample(null);
    request.setInputs(java.util.Map.of());
    Errors errors = validate(request);
    assertFalse(errors.hasErrors(), () -> "unexpected: " + errors.getAllErrors());
  }

  @Test
  void inputsShapeAcceptsATemplateAndADocumentationTarget() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.setNewSample(null);
    request.setInputs(java.util.Map.of());
    request.setTemplateId(42L);
    request.setDocumentedByGlobalId("SD99");
    Errors errors = validate(request);
    assertFalse(errors.hasErrors(), () -> "unexpected: " + errors.getAllErrors());
  }

  @Test
  void inputsShapeRejectsADocumentationTargetThatIsNotAnElnRecord() {
    for (String target : List.of("SS100", "garbage", "")) {
      ApiInventoryOperationPost request = aliquotRequest();
      request.setNewSample(null);
      request.setInputs(java.util.Map.of());
      request.setDocumentedByGlobalId(target);
      assertSingleErrorWithCode(
          validate(request),
          "documentedByGlobalId",
          "errors.inventory.operation.documentationLinkTargetInvalid");
    }
  }
}
