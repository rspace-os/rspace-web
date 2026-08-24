package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.ApiExtraFieldsHelper;
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

  private static ApiExtraField linkTo(String relationType, long originId) {
    ApiExtraField field = new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.LINK);
    field.setName(relationType + " SS" + originId);
    field.setNewFieldRequest(true);
    ApiInventoryLink link = new ApiInventoryLink();
    link.setRelationType(relationType);
    link.setTargetGlobalId("SS" + originId);
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
    return request("aliquot", newSample("Aliquots", linkTo("IsPartOf", 100)), origin(100, "0.6"));
  }

  private static ApiInventoryOperationPost passageRequest() {
    return request(
        "passage", newSample("Passaged", linkTo("IsDerivedFrom", 100)), origin(100, "0"));
  }

  private static ApiInventoryOperationPost poolRequest() {
    return request(
        "pool",
        newSample("Pooled", linkTo("HasPart", 100), linkTo("HasPart", 101)),
        origin(100, "0.6"),
        origin(101, "0.7"));
  }

  private static ApiInventoryOperationPost deriveRequest() {
    return request(
        "derive", newSample("Derived", linkTo("IsDerivedFrom", 100)), origin(100, "0.6"));
  }

  private static ApiInventoryOperationPost cryopreserveRequest() {
    ApiSampleWithFullSubSamples sample = newSample("Frozen", linkTo("IsDerivedFrom", 100));
    sample.setStorageTempMin(celsius("-20"));
    sample.setStorageTempMax(celsius("-20"));
    return request("cryopreserve", sample, origin(100, "0.6"));
  }

  private static ApiInventoryOperationPost reviveRequest() {
    ApiSampleWithFullSubSamples sample = newSample("Revived", linkTo("IsDerivedFrom", 100));
    sample.setStorageTempMin(celsius("4"));
    sample.setStorageTempMax(celsius("4"));
    return request("revive", sample, origin(100, "0.6"));
  }

  static ApiInventoryOperationPost destroyRequest() {
    return request("destroy", null, origin(100, "5"));
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
    request.getNewSample().getExtraFields().add(linkTo("IsPartOf", 101));
    assertSingleErrorWithCode(
        validate(request), "origins", "errors.inventory.operation.originCountExact");
  }

  @Test
  void rejectsSingleOriginForMultiOriginOperation() {
    ApiInventoryOperationPost request =
        request("pool", newSample("Pooled", linkTo("HasPart", 100)), origin(100, "0.6"));
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
    ApiExtraField badLink = linkTo("MadeFriendsWith", 100);
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
        request("aliquot", newSample("Aliquots", linkTo("IsDerivedFrom", 100)), origin(100, "0.6"));
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
            newSample("Pooled", linkTo("HasPart", 100)),
            origin(100, "0.6"),
            origin(101, "0.7"));
    assertSingleErrorWithCode(
        validate(request),
        "newSample.extraFields",
        "errors.inventory.operation.linkToOriginRequired");
  }

  @Test
  void allowsExtraFieldsBeyondTheRequiredLinks() {
    // The wizard adds an optional IsDocumentedBy link and text fields (e.g. Cryomedium); the
    // backend must accept fields it does not require (DevDocs/adr/0007).
    ApiInventoryOperationPost request = aliquotRequest();
    ApiExtraField documentation = linkTo("IsDocumentedBy", 0);
    documentation.getLink().setTargetGlobalId("SD1");
    documentation.setName("Standard operating procedure");
    request.getNewSample().getExtraFields().add(documentation);
    ApiExtraField cryomedium = new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.TEXT);
    cryomedium.setName("Cryomedium");
    cryomedium.setNewFieldRequest(true);
    cryomedium.setContent("DMSO 10%");
    request.getNewSample().getExtraFields().add(cryomedium);
    Errors errors = validate(request);
    assertFalse(errors.hasErrors(), () -> "extras must be allowed: " + errors.getAllErrors());
  }

  // --- origin extra fields: strictly new-field requests, contents fully validated ---

  private static ApiExtraField disposedField() {
    ApiExtraField field = new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.TEXT);
    field.setName("Disposed");
    field.setContent("2026-08-20");
    field.setNewFieldRequest(true);
    return field;
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
      sample.getExtraFields().add(linkTo("HasPart", id));
    }
    request.setOrigins(origins);
    assertTrue(
        validate(request).getFieldErrors("origins").stream()
            .anyMatch(
                error -> "errors.inventory.operation.originCountMaximum".equals(error.getCode())));
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

  // --- static live-state helpers (used by the controller, DevDocs/adr/0007 + 0015) ---

  private static ApiQuantityInfo grams(String value) {
    return new ApiQuantityInfo(new BigDecimal(value), RSUnitDef.GRAM.getId());
  }

  @Test
  void detectsOverRemovalInTheSameUnit() {
    assertTrue(InventoryOperationPostValidator.amountTakenExceedsOrigin(grams("6"), grams("5")));
  }

  @Test
  void allowsTakingUpToAndWithinTheOriginQuantity() {
    assertFalse(InventoryOperationPostValidator.amountTakenExceedsOrigin(grams("5"), grams("5")));
    assertFalse(InventoryOperationPostValidator.amountTakenExceedsOrigin(grams("4"), grams("5")));
  }

  @Test
  void comparesUnitAwareAcrossUnitsInTheSameCategory() {
    // 0.006 kg = 6 g, which exceeds a 5 g origin.
    ApiQuantityInfo sixGramsAsKilos =
        new ApiQuantityInfo(new BigDecimal("0.006"), RSUnitDef.KILO.getId());
    assertTrue(
        InventoryOperationPostValidator.amountTakenExceedsOrigin(sixGramsAsKilos, grams("5")));
    ApiQuantityInfo fourGramsAsKilos =
        new ApiQuantityInfo(new BigDecimal("0.004"), RSUnitDef.KILO.getId());
    assertFalse(
        InventoryOperationPostValidator.amountTakenExceedsOrigin(fourGramsAsKilos, grams("5")));
  }

  @Test
  void doesNotFlagNullAmountTakenOrDifferentCategories() {
    assertFalse(InventoryOperationPostValidator.amountTakenExceedsOrigin(null, grams("5")));
    // a volume amount against a mass origin is not commensurate, so it is not treated as
    // over-removal
    ApiQuantityInfo sixMillilitres = millilitres("6");
    assertFalse(
        InventoryOperationPostValidator.amountTakenExceedsOrigin(sixMillilitres, grams("5")));
  }

  @Test
  void flagsPositiveAmountTakenFromOriginWithNoQuantity() {
    // A subsample whose quantity was never set holds nothing, so taking any positive amount from it
    // is over-removal (DevDocs/adr/0007). A null origin quantity, or one with a null numeric value,
    // is treated as zero available rather than as "no limit".
    assertTrue(InventoryOperationPostValidator.amountTakenExceedsOrigin(grams("6"), null));
    assertTrue(
        InventoryOperationPostValidator.amountTakenExceedsOrigin(
            grams("6"), new ApiQuantityInfo(null, RSUnitDef.GRAM.getId())));
  }

  @Test
  void originHoldsNothingTreatsMissingOrNonPositiveQuantityAsEmpty() {
    assertTrue(InventoryOperationPostValidator.originHoldsNothing(null));
    assertTrue(
        InventoryOperationPostValidator.originHoldsNothing(
            new ApiQuantityInfo(null, RSUnitDef.GRAM.getId())));
    assertTrue(InventoryOperationPostValidator.originHoldsNothing(grams("0")));
    assertTrue(InventoryOperationPostValidator.originHoldsNothing(grams("-1")));
    assertFalse(InventoryOperationPostValidator.originHoldsNothing(grams("0.001")));
  }

  @Test
  void amountTakenEmptiesOriginIsUnitAwareEquality() {
    assertTrue(InventoryOperationPostValidator.amountTakenEmptiesOrigin(grams("5"), grams("5")));
    // 0.005 kg denotes the same amount as 5 g
    assertTrue(
        InventoryOperationPostValidator.amountTakenEmptiesOrigin(
            new ApiQuantityInfo(new BigDecimal("0.005"), RSUnitDef.KILO.getId()), grams("5")));
    assertFalse(InventoryOperationPostValidator.amountTakenEmptiesOrigin(grams("4"), grams("5")));
    assertFalse(InventoryOperationPostValidator.amountTakenEmptiesOrigin(grams("6"), grams("5")));
    // incomparable categories and missing values never count as emptying
    assertFalse(
        InventoryOperationPostValidator.amountTakenEmptiesOrigin(millilitres("5"), grams("5")));
    assertFalse(InventoryOperationPostValidator.amountTakenEmptiesOrigin(null, grams("5")));
    assertFalse(InventoryOperationPostValidator.amountTakenEmptiesOrigin(grams("5"), null));
  }
}
