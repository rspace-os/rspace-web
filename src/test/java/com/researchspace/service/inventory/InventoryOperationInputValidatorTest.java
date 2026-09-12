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
 * One golden set per configured operation, each the shape the M0 facade design sends, then each
 * rule broken one at a time.
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
   * user cannot read, and it is already redundant: the error carries the key as its FIELD, which is
   * what the wizard swaps for the input's localized label before showing it (operationsApi's
   * describeOperationError). The sentence only has to say what is wrong (parallel review, A3).
   */
  @Test
  void noRejectionSpellsARawConfigIdentifierIntoItsMessage() {
    for (String operation :
        List.of("aliquot", "passage", "pool", "derive", "cryopreserve", "revive", "destroy")) {
      InventoryOperationConfig definition = registry.get(operation).orElseThrow();
      for (InventoryOperationConfig.Input input : definition.inputs()) {
        // Every way this input can be rejected: absent, wrong type, and out of bounds.
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
   * The quantity each created subsample gets must be strictly positive.
   *
   * <p>Zero passed every check: {@code validateAmount} rejects only negatives, and the delegated
   * {@code SampleApiPostValidator} does the same. So an Aliquot with a positive {@code amountTaken}
   * and {@code eachAmount} of zero deducted real stock from the origin and created a subsample
   * holding nothing. The wizard forbids it client-side ({@code detailsValid}) and the operation
   * validator enforced it server-side before validation moved to the inputs map, so this restores a
   * rule the redesign dropped (Codex review, P2).
   *
   * <p>Scoped to the input the definition names in {@code effect.eachAmountFrom}, so an operation
   * that deliberately takes nothing from its origin (Passage: no {@code amountTakenFrom} at all) is
   * untouched.
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
    // Only the CREATED amount gained a positivity rule. Taking nothing from the origin is a real
    // case the definitions rely on, and nothing here should start rejecting it.
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
    // M0 D7: a typed facade client may omit count; the server supplies the definition's default,
    // typed as the integer the validator expects.
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
    // M0: storageTemp is required on cryopreserve (a frozen sample has a storage temperature); the
    // config used to leave it optional, which would have built a Frozen sample with no range.
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
    // 101 is the first count the request builder refuses (MAX_SUBSAMPLES); without this rule it
    // surfaced as a 500 where the client-assembled shape, since deleted, returned a 400 (M3a).
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
    // which has the stricter "greater than zero" rule of its own (Codex review, P2), so a negative
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
    // What Jackson hands a Map<String, Object> for a JSON object (probed); the caller must bind
    // quantities to a typed DTO before validating.
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
   * operation cannot succeed without it (M0 design, D7). The config must say so, or this validator
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
   * The M0 shapes carry amountTaken on the origin, not in the inputs map, so the golden sets omit
   * it. Five definitions still declare it as an input, so when a caller does put it in the map it
   * is checked like any other quantity.
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

  // --- golden input sets, one per operation, as the M0 facade shapes send them ---

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

  /** Pool's per-origin amounts travel on the origins, not in the inputs map (M0, D6). */
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
}
