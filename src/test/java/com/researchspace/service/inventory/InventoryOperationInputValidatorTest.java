package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.MapBindingResult;

/**
 * The inputs map is the values the user typed, keyed by the definition's input keys; nothing else.
 * One golden set per configured operation, then each rule broken one at a time.
 */
class InventoryOperationInputValidatorTest {

  private final InventoryOperationConfigRegistry registry = new InventoryOperationConfigRegistry();

  private Errors validate(String operationKey, Map<String, Object> inputs) {
    Errors errors = new MapBindingResult(inputs, "inputs");
    InventoryOperationInputValidator.validate(
        registry.get(operationKey).orElseThrow(), inputs, errors);
    return errors;
  }

  private static void assertSingleErrorOn(Errors errors, String inputKey, String code) {
    List<FieldError> fieldErrors = errors.getFieldErrors();
    assertEquals(1, fieldErrors.size(), () -> "expected one error, got " + errors.getAllErrors());
    assertEquals(inputKey, fieldErrors.get(0).getField());
    assertEquals(code, fieldErrors.get(0).getCode());
  }

  /**
   * No rejection may spell a raw config identifier into its localized sentence.
   *
   * <p>An input key ("sampleName") and a declared type ("integer") are wire-contract tokens, not
   * words in any language. Interpolating one into a translated sentence ships half a message the
   * user cannot read, and it is already redundant: the error carries the key as its FIELD.
   */
  @Test
  void noRejectionSpellsARawConfigIdentifierIntoItsMessage() {
    for (String operation :
        List.of("aliquot", "passage", "pool", "derive", "cryopreserve", "revive", "destroy")) {
      InventoryOperationConfig definition = registry.get(operation).orElseThrow();
      for (InventoryOperationConfig.Input input : definition.inputs()) {
        Map<String, Object> absent = goldenFor(operation);
        absent.remove(input.key());
        Map<String, Object> wrongType = goldenFor(operation);
        wrongType.put(input.key(), new Object());
        for (Errors errors : List.of(validate(operation, absent), validate(operation, wrongType))) {
          for (FieldError error : errors.getFieldErrors()) {
            assertNoRawIdentifiers(operation, input, error);
          }
        }
      }
    }
  }

  private static void assertNoRawIdentifiers(
      String operation, InventoryOperationConfig.Input input, FieldError error) {
    for (Object argument : error.getArguments() == null ? new Object[0] : error.getArguments()) {
      assertNotEquals(
          input.key(),
          String.valueOf(argument),
          () ->
              operation
                  + "."
                  + input.key()
                  + " rejected with "
                  + error.getCode()
                  + ", which interpolates the raw input KEY into a translated sentence. The key is"
                  + " already the error's field; the sentence must not repeat it untranslated.");
      assertNotEquals(
          input.type(),
          String.valueOf(argument),
          () ->
              operation
                  + "."
                  + input.key()
                  + " rejected with "
                  + error.getCode()
                  + ", which interpolates the raw declared TYPE into a translated sentence.");
    }
  }

  /**
   * The quantity each created subsample gets must be strictly positive: zero passes the plain
   * negative-number check, so an Aliquot with a positive {@code amountTaken} and {@code eachAmount}
   * of zero would deduct real stock from the origin while creating a subsample holding nothing.
   */
  @Test
  void everyOperationRejectsACreatedAmountOfZero() {
    for (String operation :
        List.of("aliquot", "passage", "pool", "derive", "cryopreserve", "revive")) {
      String createdAmountKey = registry.get(operation).orElseThrow().effect().eachAmountFrom();
      assertNotNull(createdAmountKey, operation + " is expected to declare eachAmountFrom");
      Map<String, Object> inputs = goldenFor(operation);
      inputs.put(createdAmountKey, millilitres("0"));

      assertSingleErrorOn(
          validate(operation, inputs),
          createdAmountKey,
          "errors.inventory.operation.createdAmountNotPositive");
    }
  }

  @Test
  void aZeroAmountTakenIsStillAccepted() {
    Map<String, Object> inputs = aliquot();
    inputs.put("amountTaken", millilitres("0"));

    assertEquals(List.of(), validate("aliquot", inputs).getAllErrors());
  }

  @Test
  void missingRequiredInputIsAFieldErrorOnItsKey() {
    Map<String, Object> inputs = aliquot();
    inputs.remove("sampleName");
    assertSingleErrorOn(
        validate("aliquot", inputs), "sampleName", "errors.inventory.operation.inputRequired");
  }

  @Test
  void aliquotGoldenInputsPass() {
    Errors errors = validate("aliquot", aliquot());
    assertEquals(List.of(), errors.getAllErrors());
  }

  @Test
  void wrongTypeIsAFieldErrorOnItsKey() {
    Map<String, Object> inputs = aliquot();
    inputs.put("count", "2");
    assertSingleErrorOn(
        validate("aliquot", inputs), "count", "errors.inventory.operation.inputWrongType");
  }

  @Test
  void withDefaultsFillsAnAbsentCountWithTheDeclaredOne() {
    Map<String, Object> inputs = aliquot();
    inputs.remove("count");
    Map<String, Object> filled =
        InventoryOperationInputValidator.withDefaults(
            registry.get("aliquot").orElseThrow(), inputs);
    assertEquals(1, filled.get("count"));
    assertEquals(List.of(), validate("aliquot", filled).getAllErrors());
  }

  @Test
  void withDefaultsFillsRevivesStorageTempAsCelsius() {
    Map<String, Object> inputs = revive();
    inputs.remove("storageTemp");
    Map<String, Object> filled =
        InventoryOperationInputValidator.withDefaults(registry.get("revive").orElseThrow(), inputs);
    assertEquals(celsius("4"), filled.get("storageTemp"));
    assertEquals(List.of(), validate("revive", filled).getAllErrors());
  }

  @Test
  void withDefaultsLeavesASuppliedValueAndAnInputWithoutADefaultAlone() {
    Map<String, Object> inputs = aliquot();
    inputs.put("count", 7);
    Map<String, Object> filled =
        InventoryOperationInputValidator.withDefaults(
            registry.get("aliquot").orElseThrow(), inputs);
    assertEquals(7, filled.get("count"));
    assertEquals(inputs.keySet(), filled.keySet(), "no default is declared for anything else");
  }

  @Test
  void cryopreserveRequiresAStorageTemperature() {
    Map<String, Object> inputs = cryopreserve();
    inputs.remove("storageTemp");
    assertSingleErrorOn(
        validate("cryopreserve", inputs),
        "storageTemp",
        "errors.inventory.operation.inputRequired");
  }

  @Test
  void valueBelowTheDeclaredMinimumIsAFieldErrorOnItsKey() {
    Map<String, Object> inputs = aliquot();
    inputs.put("count", 0);
    assertSingleErrorOn(
        validate("aliquot", inputs), "count", "errors.inventory.operation.inputBelowMinimum");
  }

  @Test
  void valueAboveTheDeclaredMaximumIsAFieldErrorOnItsKey() {
    // 101 is the first count MAX_SUBSAMPLES refuses; without this check it surfaced as an
    // uncaught 500 instead of a rejected field.
    Map<String, Object> inputs = aliquot();
    inputs.put("count", 101);
    assertSingleErrorOn(
        validate("aliquot", inputs), "count", "errors.inventory.operation.inputAboveMaximum");
  }

  @Test
  void quantityWithANonAmountUnitIsAFieldErrorOnItsKey() {
    Map<String, Object> inputs = aliquot();
    inputs.put("eachAmount", celsius("3"));
    assertSingleErrorOn(
        validate("aliquot", inputs), "eachAmount", "errors.inventory.quantity.unitNotAmount");
  }

  @Test
  void quantityWithAnUnknownUnitIsAFieldErrorOnItsKey() {
    Map<String, Object> inputs = aliquot();
    inputs.put("eachAmount", new ApiQuantityInfo(new BigDecimal("3"), 999));
    assertSingleErrorOn(
        validate("aliquot", inputs), "eachAmount", "errors.inventory.quantity.unitInvalid");
  }

  @Test
  void quantityFinerThanThreeDecimalPlacesIsAFieldErrorOnItsKey() {
    Map<String, Object> inputs = aliquot();
    inputs.put("eachAmount", millilitres("0.0005"));
    assertSingleErrorOn(
        validate("aliquot", inputs), "eachAmount", "errors.inventory.operation.inputNotStorable");
  }

  @Test
  void quantityWithTrailingZerosIsStorable() {
    Map<String, Object> inputs = aliquot();
    inputs.put("eachAmount", millilitres("3.5000"));
    assertEquals(List.of(), validate("aliquot", inputs).getAllErrors());
  }

  @Test
  void negativeQuantityIsAFieldErrorOnItsKey() {
    // Asserted on amountTaken rather than eachAmount: eachAmount is the definition's created
    // amount,
    // which has the stricter "greater than zero" rule of its own, so a negative
    // there is reported under that code instead. This keeps the plain negative rule covered.
    Map<String, Object> inputs = aliquot();
    inputs.put("amountTaken", millilitres("-1"));
    assertSingleErrorOn(
        validate("aliquot", inputs), "amountTaken", "errors.inventory.quantity.negative");
  }

  @Test
  void aNegativeCreatedAmountIsReportedUnderThePositivityRule() {
    Map<String, Object> inputs = aliquot();
    inputs.put("eachAmount", millilitres("-1"));
    assertSingleErrorOn(
        validate("aliquot", inputs),
        "eachAmount",
        "errors.inventory.operation.createdAmountNotPositive");
  }

  @Test
  void quantityWithoutANumericValueIsTheWrongType() {
    Map<String, Object> inputs = aliquot();
    inputs.put("eachAmount", new ApiQuantityInfo(null, RSUnitDef.MILLI_LITRE));
    assertSingleErrorOn(
        validate("aliquot", inputs), "eachAmount", "errors.inventory.operation.inputWrongType");
  }

  @Test
  void rawMapIsNotAQuantity() {
    // A JSON object not bound to a typed DTO arrives as a Map<String, Object>, not an
    // ApiQuantityInfo.
    Map<String, Object> inputs = aliquot();
    inputs.put("eachAmount", Map.of("numericValue", 3, "unitId", 3));
    assertSingleErrorOn(
        validate("aliquot", inputs), "eachAmount", "errors.inventory.operation.inputWrongType");
  }

  @Test
  void temperatureAboveTheDeclaredCelsiusMaximumIsAFieldErrorOnItsKey() {
    Map<String, Object> inputs = cryopreserve();
    inputs.put("storageTemp", celsius("-10"));
    assertSingleErrorOn(
        validate("cryopreserve", inputs),
        "storageTemp",
        "errors.inventory.operation.storageTempAboveMax");
  }

  @Test
  void temperatureBelowTheDeclaredCelsiusMinimumIsAFieldErrorOnItsKey() {
    Map<String, Object> inputs = revive();
    inputs.put("storageTemp", celsius("2"));
    assertSingleErrorOn(
        validate("revive", inputs),
        "storageTemp",
        "errors.inventory.operation.storageTempBelowMin");
  }

  @Test
  void temperatureBoundsAreComparedOnTheTemperatureDenotedNotTheNumber() {
    Map<String, Object> inputs = revive();
    inputs.put("storageTemp", new ApiQuantityInfo(new BigDecimal("277.15"), RSUnitDef.KELVIN));
    assertEquals(List.of(), validate("revive", inputs).getAllErrors(), "277.15 K is 4 degC");
  }

  @Test
  void temperatureWithANonTemperatureUnitIsAFieldErrorOnItsKey() {
    Map<String, Object> inputs = revive();
    inputs.put("storageTemp", millilitres("4"));
    assertSingleErrorOn(
        validate("revive", inputs), "storageTemp", "errors.inventory.temperature.invalidUnit");
  }

  @Test
  void temperatureFinerThanThreeDecimalPlacesIsAFieldErrorOnItsKey() {
    Map<String, Object> inputs = revive();
    inputs.put("storageTemp", celsius("4.0005"));
    assertSingleErrorOn(
        validate("revive", inputs), "storageTemp", "errors.inventory.temperature.notStorable");
  }

  @Test
  void everyConfiguredOperationsGoldenInputsPass() {
    Map<String, Map<String, Object>> golden = new LinkedHashMap<>();
    golden.put("aliquot", aliquot());
    golden.put("passage", passage());
    golden.put("pool", pool());
    golden.put("derive", derive());
    golden.put("cryopreserve", cryopreserve());
    golden.put("revive", revive());
    golden.put("destroy", destroy());
    assertEquals(registry.keys(), golden.keySet(), "one golden set per configured operation");
    golden.forEach(
        (operation, inputs) ->
            assertEquals(
                List.of(), validate(operation, inputs).getAllErrors(), operation + " golden"));
  }

  /**
   * A created subsample takes its quantity from eachAmount and nothing else, so a creating
   * operation cannot succeed without it. The config must declare it required, or this validator
   * would pass an inputs map the builder cannot turn into a sample.
   */
  @Test
  void everyCreatingOperationRequiresEachAmount() {
    for (String operation : registry.keys()) {
      InventoryOperationConfig definition = registry.get(operation).orElseThrow();
      if (definition.noOutput()) {
        continue;
      }
      Map<String, Object> inputs = new LinkedHashMap<>(goldenFor(operation));
      inputs.remove("eachAmount");
      Errors errors = validate(operation, inputs);
      assertEquals(
          1, errors.getFieldErrorCount(), () -> operation + " without eachAmount: " + errors);
      assertEquals("eachAmount", errors.getFieldErrors().get(0).getField(), operation);
      assertEquals(
          "errors.inventory.operation.inputRequired",
          errors.getFieldErrors().get(0).getCode(),
          operation);
    }
  }

  /**
   * The golden sets omit amountTaken because it normally travels on the origin, not the inputs map.
   * Five definitions still declare it as an input, so a caller that does put it in the map gets it
   * checked like any other quantity.
   */
  @Test
  void amountTakenInTheMapIsValidatedAsAQuantity() {
    Map<String, Object> inputs = aliquot();
    inputs.put("amountTaken", millilitres("6"));
    assertEquals(List.of(), validate("aliquot", inputs).getAllErrors());
    inputs.put("amountTaken", celsius("6"));
    assertSingleErrorOn(
        validate("aliquot", inputs), "amountTaken", "errors.inventory.quantity.unitNotAmount");
  }

  /**
   * The declared-input loop reads the DEFINITION's keys out of the caller's map and never walks the
   * map itself, so an undeclared key used to be unreachable rather than wrong: its value was
   * dropped and the request answered 201.
   */
  @Test
  void anInputKeyTheDefinitionDoesNotDeclareIsRejected() {
    Map<String, Object> inputs = aliquot();
    inputs.put("cryomedium", "DMSO");
    assertSingleErrorOn(
        validate("aliquot", inputs), "cryomedium", "errors.inventory.operation.inputUnknown");
  }

  /**
   * An undeclared key is arbitrary client text, so it may look like a property path. The rejection
   * still has to be a plain field error naming that key: Errors treats "a.b" and "a[0]" as
   * navigation, and a caller must not be able to turn a 400 into a 500 by choosing a key.
   */
  @Test
  void anUndeclaredKeyThatLooksLikeAPropertyPathIsStillRejectedOnThatKey() {
    for (String key : List.of("a.b", "a[0]", "  ", "Cryomedium", "sampleName ")) {
      Map<String, Object> inputs = aliquot();
      inputs.put(key, "x");
      Errors errors = validate("aliquot", inputs);
      assertEquals(
          1, errors.getFieldErrorCount(), () -> "key [" + key + "] gave " + errors.getAllErrors());
      assertEquals(key, errors.getFieldErrors().get(0).getField());
      assertEquals(
          "errors.inventory.operation.inputUnknown", errors.getFieldErrors().get(0).getCode());
    }
  }

  /**
   * The empty key is the one Errors cannot attach to a field: rejectValue("") is promoted to a
   * global error rather than a field error, so this checks the global error list instead.
   */
  @Test
  void theEmptyInputKeyIsRejectedGloballyRatherThanIgnored() {
    Map<String, Object> inputs = aliquot();
    inputs.put("", "x");
    Errors errors = validate("aliquot", inputs);
    assertEquals(0, errors.getFieldErrorCount());
    assertEquals(1, errors.getGlobalErrorCount(), () -> "" + errors.getAllErrors());
    assertEquals(
        "errors.inventory.operation.inputUnknown", errors.getGlobalErrors().get(0).getCode());
  }

  /** Destroy declares no inputs at all, so every key is undeclared except the empty map itself. */
  @Test
  void anOperationThatDeclaresNoInputsAcceptsOnlyAnEmptyMap() {
    assertEquals(List.of(), validate("destroy", inputs()).getAllErrors());
    assertSingleErrorOn(
        validate("destroy", inputs("sampleName", "anything")),
        "sampleName",
        "errors.inventory.operation.inputUnknown");
  }

  /**
   * An undeclared key is reported ALONGSIDE the declared inputs' own problems, not instead of them:
   * a caller fixing a typo should not then discover a second, previously hidden error.
   */
  @Test
  void anUndeclaredKeyDoesNotHideTheDeclaredInputsOwnErrors() {
    Map<String, Object> inputs = aliquot();
    inputs.remove("sampleName");
    inputs.put("cryomedium", "DMSO");
    Errors errors = validate("aliquot", inputs);
    assertEquals(2, errors.getFieldErrorCount(), () -> "" + errors.getAllErrors());
    assertEquals(
        List.of("sampleName", "cryomedium"),
        errors.getFieldErrors().stream().map(FieldError::getField).toList());
  }

  private static Map<String, Object> goldenFor(String operation) {
    return switch (operation) {
      case "aliquot" -> aliquot();
      case "passage" -> passage();
      case "pool" -> pool();
      case "derive" -> derive();
      case "cryopreserve" -> cryopreserve();
      case "revive" -> revive();
      case "destroy" -> destroy();
      default -> throw new IllegalArgumentException("no golden inputs for " + operation);
    };
  }

  // --- golden input sets, one per operation ---

  private static Map<String, Object> inputs(Object... keysAndValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return map;
  }

  private static ApiQuantityInfo millilitres(String value) {
    return new ApiQuantityInfo(new BigDecimal(value), RSUnitDef.MILLI_LITRE);
  }

  private static ApiQuantityInfo celsius(String value) {
    return new ApiQuantityInfo(new BigDecimal(value), RSUnitDef.CELSIUS);
  }

  private static Map<String, Object> aliquot() {
    return inputs("sampleName", "Aliquots", "count", 2, "eachAmount", millilitres("3"));
  }

  private static Map<String, Object> passage() {
    return inputs("sampleName", "HeLa p3", "count", 1, "eachAmount", millilitres("5"));
  }

  /** Pool's per-origin amounts travel on the origins, not in the inputs map. */
  private static Map<String, Object> pool() {
    return inputs("sampleName", "Pooled lysate", "count", 1, "eachAmount", millilitres("13"));
  }

  private static Map<String, Object> derive() {
    return inputs(
        "processName",
        "PCR",
        "sampleName",
        "Derived DNA",
        "count",
        1,
        "eachAmount",
        millilitres("1"));
  }

  private static Map<String, Object> cryopreserve() {
    return inputs(
        "sampleName",
        "Frozen",
        "count",
        1,
        "eachAmount",
        millilitres("1"),
        "cryomedium",
        "DMSO 10%",
        "storageTemp",
        celsius("-80"));
  }

  private static Map<String, Object> revive() {
    return inputs(
        "sampleName",
        "Revived",
        "count",
        1,
        "eachAmount",
        millilitres("1"),
        "storageTemp",
        celsius("4"));
  }

  private static Map<String, Object> destroy() {
    return inputs();
  }

  // --- lengths: text inputs land in varchar columns ---

  /**
   * A name input is bound to EditInfo.name, varchar(255); a text-field-content input is bound to
   * EditInfo.description, varchar(250). Without this check, an overlong value reached the INSERT as
   * a 500 instead of being rejected on its own key.
   */
  @Test
  void aNameInputLongerThanTheRecordNameLimitIsAFieldErrorOnItsKey() {
    for (String operation :
        List.of("aliquot", "passage", "pool", "derive", "cryopreserve", "revive")) {
      String nameKey = registry.get(operation).orElseThrow().effect().nameFrom();
      Map<String, Object> tooLong = goldenFor(operation);
      tooLong.put(nameKey, "x".repeat(256));
      assertSingleErrorOn(
          validate(operation, tooLong), nameKey, "errors.inventory.operation.inputTooLong");

      Map<String, Object> atTheLimit = goldenFor(operation);
      atTheLimit.put(nameKey, "x".repeat(255));
      assertEquals(
          List.of(), validate(operation, atTheLimit).getAllErrors(), operation + " at 255");
    }
  }

  @Test
  void aTextInputStoredAsFieldContentLongerThanTheContentLimitIsAFieldErrorOnItsKey() {
    Map<String, Object> tooLong = cryopreserve();
    tooLong.put("cryomedium", "m".repeat(251));
    assertSingleErrorOn(
        validate("cryopreserve", tooLong), "cryomedium", "errors.inventory.operation.inputTooLong");

    Map<String, Object> atTheLimit = cryopreserve();
    atTheLimit.put("cryomedium", "m".repeat(250));
    assertEquals(List.of(), validate("cryopreserve", atTheLimit).getAllErrors());
  }

  @Test
  void aCountAsALongAboveTheMaximumIsAboveMaximumNotWrongType() {
    // Jackson binds a large JSON integer to Long; it is still a whole number, judged by the bound.
    Map<String, Object> inputs = aliquot();
    inputs.put("count", 10_000_000_000L);
    assertSingleErrorOn(
        validate("aliquot", inputs), "count", "errors.inventory.operation.inputAboveMaximum");
  }

  @Test
  void aCountAsABigIntegerIsAWholeNumber() {
    Map<String, Object> inputs = aliquot();
    inputs.put("count", java.math.BigInteger.valueOf(3));
    assertEquals(List.of(), validate("aliquot", inputs).getAllErrors());
  }

  @Test
  void aNegativeCountIsBelowTheMinimum() {
    Map<String, Object> inputs = aliquot();
    inputs.put("count", -1);
    assertSingleErrorOn(
        validate("aliquot", inputs), "count", "errors.inventory.operation.inputBelowMinimum");
  }

  @Test
  void aQuantityWithTheWizardsUnsetUnitIsAFieldErrorOnItsKey() {
    // unit id 0 is a "no unit chosen" marker, not a valid unit.
    Map<String, Object> inputs = aliquot();
    inputs.put("eachAmount", new ApiQuantityInfo(new BigDecimal("3"), 0));
    assertSingleErrorOn(
        validate("aliquot", inputs), "eachAmount", "errors.inventory.quantity.unitInvalid");
  }

  @Test
  void aQuantityWithMoreIntegerDigitsThanTheColumnHoldsIsNotStorable() {
    // DECIMAL(19,3): 1E+17 passes a scale-only check and fails at the INSERT.
    Map<String, Object> inputs = aliquot();
    inputs.put("eachAmount", millilitres("1E+17"));
    assertSingleErrorOn(
        validate("aliquot", inputs), "eachAmount", "errors.inventory.operation.inputNotStorable");
  }

  @Test
  void anEachAmountThatFitsButWhoseTotalDoesNotIsAFieldErrorOnItsKey() {
    // DECIMAL(19,3) holds 16 integer digits. Each of the two children fits it; the parent total the
    // created sample recalculates from them does not. That recompute happens during persistence, so
    // without this the overflow is a 500 inside the transaction, after the origins were
    // decremented, instead of
    // a rejected field.
    Map<String, Object> inputs = passage();
    inputs.put("count", 2);
    inputs.put("eachAmount", millilitres("6E+15"));
    assertSingleErrorOn(
        validate("passage", inputs), "eachAmount", "errors.inventory.operation.totalNotStorable");
  }

  @Test
  void aTotalThatStillFitsTheColumnIsAccepted() {
    // The boundary the test above is one step past: 2 x 4E+15 is 8E+15, sixteen integer digits.
    Map<String, Object> inputs = passage();
    inputs.put("count", 2);
    inputs.put("eachAmount", millilitres("4E+15"));
    assertEquals(List.of(), validate("passage", inputs).getAllErrors());
  }

  @Test
  void whitespaceOnlyRequiredTextIsAbsent() {
    Map<String, Object> inputs = aliquot();
    inputs.put("sampleName", "   ");
    assertSingleErrorOn(
        validate("aliquot", inputs), "sampleName", "errors.inventory.operation.inputRequired");
  }

  @Test
  void aTemperatureWithTheWizardsUnsetUnitIsAFieldErrorOnItsKey() {
    Map<String, Object> inputs = cryopreserve();
    inputs.put("storageTemp", new ApiQuantityInfo(new BigDecimal("-80"), 0));
    assertSingleErrorOn(
        validate("cryopreserve", inputs),
        "storageTemp",
        "errors.inventory.temperature.invalidUnit");
  }
}
