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
 *
 * <p>Every rule names the caller's own field, so an operation passes the field name it validated.
 */
public final class OperationQuantityRules {

  private static final QuantityUtils QUANTITY_UTILS = new QuantityUtils();

  private OperationQuantityRules() {}

  /**
   * The quantity each created subsample is given: a real amount unit, storable at 3dp, and greater
   * than zero. Zero would create a subsample holding nothing while still deducting real stock from
   * the origin.
   */
  public static void createdAmount(ApiQuantityInfo amount, String field, Errors errors) {
    if (amount == null) {
      return; // absence is the body's own @NotNull
    }
    // @NotNull asserts the object is present, not that it carries a number, so a body sending only
    // a unit arrives here. Left unchecked that null is copied into every created subsample while
    // the origin is still decremented, so the operation yields empty stock or a 500.
    if (amount.getNumericValue() == null || amount.getNumericValue().signum() <= 0) {
      errors.rejectValue(
          field,
          "errors.inventory.operation.createdAmountNotPositive",
          "Each created subsample must be given a quantity greater than zero.");
    }
    if (amount.getNumericValue() == null) {
      return; // the remaining rules read the number
    }
    storableAmountUnit(amount, field, "errors.inventory.operation.inputNotStorable", errors);
  }

  /**
   * An amount taken from an origin: non-negative, a real amount unit, storable at 3dp. Zero is
   * allowed here (the manager's live-state rules decide whether this operation may take nothing).
   */
  public static void amountTaken(ApiQuantityInfo amount, String field, Errors errors) {
    if (amount == null
        || amount.getNumericValue() == null
        || amount.getUnitId() == null
        || amount.getUnitId() <= 0
        || amount.getNumericValue().compareTo(BigDecimal.ZERO) < 0) {
      // The frontend uses a non-positive unit id (UNSET_UNIT = 0) as an "unset" marker, and the
      // manager subtracts unit-aware, so an absent or unusable unit is malformed rather than a
      // lookup failure deeper in.
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
      // surrogate the caller never sent (0.0004 ml would take nothing at all).
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
   * A storage temperature: a real temperature unit, storable at 3dp, and inside the operation's
   * Celsius bounds. Bounds are compared on the temperature the value DENOTES, not its raw number,
   * so a value sent in Kelvin or Fahrenheit is judged correctly.
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
      // @NotNull asserts the object is present, not that it carries a number. The sample's own
      // @ValidTemperature treats a number-less temperature as unresolved and passes it too, so
      // unchecked it reached the INSERT and failed there as a 500.
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
    if (!TemperatureValidator.validate(temperature)) {
      // The one remaining thing the validator judges that the checks above do not: a temperature
      // below absolute zero. An operation's own bounds need not exclude it (Cryopreserve sets no
      // lower bound at all), so without this -300 C reached the INSERT.
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
   * The created sample's total quantity is the sum of the subsamples built from {@code count} and
   * {@code eachAmount}, recalculated while the sample is persisted. Each child fits the
   * DECIMAL(19,3) quantity column while their product may not, and nothing between here and the
   * INSERT checks the sum. Without this, an otherwise valid request reached the origins,
   * decremented them, then failed inside the transaction as a 500.
   *
   * <p>Reported on the amount, not the count, since the amount is the value the caller chose freely
   * (the body already bounds the count). Skipped when the amount was already rejected on its own
   * terms, so the caller sees one problem per field.
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
