package com.researchspace.service.inventory.operations;

import static com.researchspace.service.inventory.operations.OperationTestFixtures.celsius;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.millilitres;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.validation.MapBindingResult;

class OperationQuantityRulesTest {

  // A map-backed result, so a rule can be exercised on the field name it would use in production
  // without standing up the request bean that carries it.
  private final MapBindingResult errors = new MapBindingResult(new HashMap<>(), "request");

  private String codeOn(String field) {
    return errors.getFieldError(field) == null ? null : errors.getFieldError(field).getCode();
  }

  @Test
  void aCreatedAmountMustBePositive() {
    OperationQuantityRules.createdAmount(millilitres("0"), "eachAmount", errors);

    assertEquals("errors.inventory.operation.createdAmountNotPositive", codeOn("eachAmount"));
  }

  @Test
  void anAmountTakenMayBeZeroButNotNegative() {
    OperationQuantityRules.amountTaken(millilitres("0"), "a", errors);
    assertNull(codeOn("a"));

    OperationQuantityRules.amountTaken(millilitres("-1"), "b", errors);
    assertEquals("errors.inventory.operation.amountTakenInvalid", codeOn("b"));
  }

  @Test
  void anAmountMustCarryAKnownUnitThatMeasuresAnAmount() {
    OperationQuantityRules.amountTaken(new ApiQuantityInfo(BigDecimal.ONE, 9999), "a", errors);
    assertEquals("errors.inventory.quantity.unitInvalid", codeOn("a"));

    OperationQuantityRules.amountTaken(
        new ApiQuantityInfo(BigDecimal.ONE, RSUnitDef.CELSIUS.getId()), "b", errors);
    assertEquals("errors.inventory.quantity.unitNotAmount", codeOn("b"));
  }

  @Test
  void anAmountFinerThanTheColumnStoresIsRejectedRatherThanRounded() {
    // Quantities persist at 3dp, so 0.0004 ml would silently take nothing at all.
    OperationQuantityRules.amountTaken(millilitres("0.0004"), "a", errors);
    assertEquals("errors.inventory.operation.amountTakenTooPrecise", codeOn("a"));

    OperationQuantityRules.createdAmount(millilitres("0.0004"), "b", errors);
    assertEquals("errors.inventory.operation.inputNotStorable", codeOn("b"));
  }

  @Test
  void aCreatedAmountObjectSentWithoutANumberIsRejected() {
    // @NotNull asserts the object is present, not that it carries a number, so {unitId: 3} alone
    // reaches here; unchecked it becomes subsamples holding null while the origin is decremented.
    OperationQuantityRules.createdAmount(
        new ApiQuantityInfo(null, RSUnitDef.MILLI_LITRE.getId()), "eachAmount", errors);

    assertEquals("errors.inventory.operation.createdAmountNotPositive", codeOn("eachAmount"));
  }

  @Test
  void aTemperatureMustCarryATemperatureUnit() {
    OperationQuantityRules.temperature(millilitres("4"), "storageTemp", null, null, errors);

    assertEquals("errors.inventory.temperature.invalidUnit", codeOn("storageTemp"));
  }

  @Test
  void aTemperatureObjectSentWithoutANumberIsRejected() {
    // Cryopreserve's @NotNull only asserts storageTemp is present. A number-less one passed every
    // rule here and every rule on the sample, then failed while the entity was persisted.
    OperationQuantityRules.temperature(
        new ApiQuantityInfo(null, RSUnitDef.CELSIUS.getId()),
        "storageTemp",
        null,
        new BigDecimal("-18"),
        errors);

    assertEquals("errors.inventory.operation.inputRequired", codeOn("storageTemp"));
  }

  @Test
  void aTemperatureBelowAbsoluteZeroIsRejectedEvenInsideTheOperationsBounds() {
    // Cryopreserve sets no lower bound, so -300 C sits under its -18 C ceiling and passed; the
    // sample's own @ValidTemperature would have caught it, but only once the entity was persisted.
    OperationQuantityRules.temperature(
        celsius("-300"), "storageTemp", null, new BigDecimal("-18"), errors);

    assertEquals("errors.inventory.temperature.belowAbsoluteZero", codeOn("storageTemp"));
  }

  @Test
  void celsiusBoundsAreJudgedOnTheTemperatureDenoted() {
    // 300 K is about 27 C, so it is above a 4 C ceiling although 300 as a number is not below it.
    OperationQuantityRules.temperature(
        new ApiQuantityInfo(new BigDecimal("300"), RSUnitDef.KELVIN.getId()),
        "storageTemp",
        null,
        new BigDecimal("4"),
        errors);

    assertEquals("errors.inventory.operation.storageTempAboveMax", codeOn("storageTemp"));
  }

  @Test
  void aBoundedTemperatureInsideItsRangeIsAccepted() {
    for (String celsiusValue : List.of("4", "-80", "120")) {
      MapBindingResult own = new MapBindingResult(new HashMap<>(), "request");
      OperationQuantityRules.temperature(
          celsius(celsiusValue), "storageTemp", new BigDecimal("-200"), new BigDecimal("120"), own);
      assertNull(own.getFieldError("storageTemp"), celsiusValue);
    }
  }

  @Test
  void aTotalTooLargeForTheQuantityColumnIsReportedOnTheAmount() {
    OperationQuantityRules.totalStorable(
        new BigDecimal("100"), millilitres("9999999999999999"), "eachAmount", errors);

    assertEquals("errors.inventory.operation.totalNotStorable", codeOn("eachAmount"));
  }

  @Test
  void aTotalIsNotCheckedWhenTheAmountItselfWasAlreadyRejected() {
    // One problem per field: the caller fixes the amount, then sees the total if it still fails.
    OperationQuantityRules.createdAmount(millilitres("0"), "eachAmount", errors);
    OperationQuantityRules.totalStorable(
        new BigDecimal("100"), millilitres("9999999999999999"), "eachAmount", errors);

    assertEquals(1, errors.getFieldErrorCount("eachAmount"));
  }
}
