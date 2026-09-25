package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.QuantityUtils;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.model.units.TemperatureValidator;
import java.math.BigDecimal;
import org.springframework.validation.Errors;

/**
 * The quantity and temperature rules the request bodies' annotations cannot express, because each
 * needs {@link RSUnitDef} to say what a unit id means: whether it is a unit at all, whether it
 * measures an amount or a temperature, and what a value in it comes to in Celsius.
 */
public final class OperationQuantityRules {

  private static final QuantityUtils QUANTITY_UTILS = new QuantityUtils();

  private OperationQuantityRules() {}

  /**
   * Zero is rejected here: it would create a subsample holding nothing while still deducting real
   * stock from the origin.
   */
  public static void createdAmount(ApiQuantityInfo amount, String field, Errors errors) {
    if (amount == null) {
      return; // absence is the body's own @NotNull
    }
    // @NotNull asserts the object is present, not that it carries a number, so a body sending only
    // a unit arrives here.
    BigDecimal value = amount.getNumericValue();
    if (value == null || value.signum() <= 0) {
      errors.rejectValue(
          field,
          "errors.inventory.operation.createdAmountNotPositive",
          "Each created subsample must be given a quantity greater than zero.");
      if (value == null) {
        return; // the remaining rules read the number
      }
    }
    storableAmountUnit(amount, field, "errors.inventory.operation.inputNotStorable", errors);
  }

  public static void amountTaken(ApiQuantityInfo amount, String field, Errors errors) {
    if (amount == null
        || amount.getNumericValue() == null
        || amount.getUnitId() == null
        || amount.getUnitId() <= 0
        || amount.getNumericValue().compareTo(BigDecimal.ZERO) < 0) {
      // The frontend uses a non-positive unit id (UNSET_UNIT = 0) as an "unset" marker.
      errors.rejectValue(
          field,
          "errors.inventory.operation.amountTakenInvalid",
          "Each origin must specify a non-negative amount, with a unit, to take from it.");
      return;
    }
    storableAmountUnit(amount, field, "errors.inventory.operation.amountTakenTooPrecise", errors);
  }

  private static void storableAmountUnit(
      ApiQuantityInfo amount, String field, String notStorableCode, Errors errors) {
    if (!QuantityInfo.canStoreWithoutRounding(amount.getNumericValue())) {
      // Quantities persist at 3dp (HALF_UP), so a finer value would be stored as a rounded
      // surrogate the caller never sent.
      errors.rejectValue(field, notStorableCode, "This amount supports at most 3 decimal places.");
    }
    Integer unitId = amount.getUnitId();
    if (unitId == null || !RSUnitDef.exists(unitId)) {
      errors.rejectValue(
          field,
          "errors.inventory.quantity.unitInvalid",
          new Object[] {unitId},
          "The quantity's unit id is not a unit.");
    } else if (!RSUnitDef.getUnitById(unitId).isAmount()) {
      errors.rejectValue(
          field,
          "errors.inventory.quantity.unitNotAmount",
          new Object[] {unitId},
          "The quantity's unit is not a mass, volume or dimensionless unit.");
    }
  }

  /**
   * Bounds are compared on the temperature the value denotes, not its raw number, so a value sent
   * in Kelvin or Fahrenheit is judged correctly.
   */
  public static void temperature(
      ApiQuantityInfo temperature,
      String field,
      BigDecimal minCelsius,
      BigDecimal maxCelsius,
      Errors errors) {
    if (temperature == null) {
      return; // absence is the body's own @NotNull
    }
    if (temperature.getNumericValue() == null) {
      // @NotNull asserts the object is present, not that it carries a number, and the sample's own
      // @ValidTemperature treats a number-less temperature as unresolved and passes it too.
      errors.rejectValue(
          field, "errors.inventory.operation.inputRequired", "A temperature value is required.");
      return;
    }
    Integer unitId = temperature.getUnitId();
    if (unitId == null
        || !RSUnitDef.exists(unitId)
        || !RSUnitDef.getUnitById(unitId).isTemperature()) {
      errors.rejectValue(
          field, "errors.inventory.temperature.invalidUnit", "Not a temperature unit.");
      return;
    }
    if (!QuantityInfo.canStoreWithoutRounding(temperature.getNumericValue())) {
      errors.rejectValue(
          field,
          "errors.inventory.temperature.notStorable",
          "The temperature supports at most 3 decimal places.");
      return;
    }
    // Whole degrees in the unit sent: the sample form can only show and save whole degrees, so an
    // operation must not create a temperature it would silently truncate. Stricter than
    // POST /samples, deliberately (ADR 0011 D5).
    if (temperature.getNumericValue().stripTrailingZeros().scale() > 0) {
      errors.rejectValue(
          field,
          "errors.inventory.operation.storageTempNotWhole",
          "The storage temperature must be whole degrees.");
      return;
    }
    if (!TemperatureValidator.validate(temperature)) {
      // The one thing this validator judges that the checks above do not: below absolute zero. An
      // operation's own bounds need not exclude it (Cryopreserve sets no lower bound at all).
      errors.rejectValue(
          field,
          "errors.inventory.temperature.belowAbsoluteZero",
          "The temperature is below absolute zero.");
      return;
    }
    QuantityInfo value = QuantityInfo.of(temperature);
    if (maxCelsius != null && compareCelsius(value, maxCelsius) > 0) {
      errors.rejectValue(
          field,
          "errors.inventory.operation.storageTempAboveMax",
          new Object[] {maxCelsius},
          "The temperature is above this operation's maximum.");
    }
    if (minCelsius != null && compareCelsius(value, minCelsius) < 0) {
      errors.rejectValue(
          field,
          "errors.inventory.operation.storageTempBelowMin",
          new Object[] {minCelsius},
          "The temperature is below this operation's minimum.");
    }
  }

  private static int compareCelsius(QuantityInfo value, BigDecimal celsius) {
    QuantityInfo bound = QuantityInfo.of(celsius, RSUnitDef.CELSIUS);
    return QUANTITY_UTILS.getComparatorFor(bound).compare(value, bound);
  }

  /**
   * The created sample's total quantity is the sum of its subsamples, recalculated while the sample
   * is persisted. Each child fits the DECIMAL(19,3) quantity column while their product may not,
   * and nothing between here and the INSERT checks the sum.
   *
   * <p>Skipped when the amount was already rejected on its own terms, so the caller sees one
   * problem per field.
   */
  public static void totalStorable(
      BigDecimal count, ApiQuantityInfo eachAmount, String field, Errors errors) {
    if (count == null
        || eachAmount == null
        || eachAmount.getNumericValue() == null
        || errors.getFieldErrorCount(field) > 0) {
      return;
    }
    BigDecimal total = eachAmount.getNumericValue().multiply(count);
    if (!QuantityInfo.canStoreWithoutRounding(total)) {
      errors.rejectValue(
          field,
          "errors.inventory.operation.totalNotStorable",
          "The created subsamples hold more in total than a quantity can store.");
    }
  }
}
