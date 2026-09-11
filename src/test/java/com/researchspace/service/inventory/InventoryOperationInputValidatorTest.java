package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    Map<String, Object> inputs = aliquot();
    inputs.put("eachAmount", millilitres("-1"));
    assertSingleErrorOn(
        validate("aliquot", inputs), "eachAmount", "errors.inventory.quantity.negative");
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
