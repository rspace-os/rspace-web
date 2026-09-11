package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.model.units.Quantifiable;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.QuantityUtils;
import com.researchspace.model.units.RSUnitDef;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.validation.Errors;

/**
 * Checks a supplied inputs map against an operation definition's {@code inputs[]}: present when
 * required, of the declared type, within the declared bounds, and structurally storable. It looks
 * only at the inputs map, never at a sample, because under the server-built design the sample does
 * not exist yet when this runs. Every problem is a field error on the input's own key.
 */
public final class InventoryOperationInputValidator {

  /** Stateless, so one instance serves every caller. */
  private static final QuantityUtils QUANTITY_UTILS = new QuantityUtils();

  private InventoryOperationInputValidator() {}

  /**
   * The inputs with every absent optional input that declares a {@code default} filled in, typed as
   * the input's type wants it (an integer, a Celsius temperature, text), so a typed facade client
   * may omit {@code count} or Revive's {@code storageTemp} (M0 D7). Applied before {@link
   * #validate}, so a default outside its own bounds is still rejected rather than trusted. The
   * wizard fills the same defaults client-side and always sends them.
   */
  public static Map<String, Object> withDefaults(
      InventoryOperationConfig definition, Map<String, ?> inputs) {
    Map<String, Object> filled = new LinkedHashMap<>();
    if (inputs != null) {
      filled.putAll(inputs);
    }
    for (InventoryOperationConfig.Input input : definition.inputs()) {
      if (input.defaultValue() == null || !isAbsent(filled.get(input.key()))) {
        continue;
      }
      Object value = input.defaultValue();
      filled.put(
          input.key(),
          switch (String.valueOf(input.type())) {
            case "integer" -> value instanceof Number n ? n.intValue() : value;
            case "temperature" ->
                value instanceof Number n
                    ? new ApiQuantityInfo(new BigDecimal(n.toString()), RSUnitDef.CELSIUS)
                    : value;
            default -> value;
          });
    }
    return filled;
  }

  public static void validate(
      InventoryOperationConfig definition, Map<String, ?> inputs, Errors errors) {
    for (InventoryOperationConfig.Input input : definition.inputs()) {
      Object value = inputs == null ? null : inputs.get(input.key());
      if (isAbsent(value)) {
        if (input.required()) {
          errors.rejectValue(
              input.key(),
              "errors.inventory.operation.inputRequired",
              new Object[] {input.key()},
              "This operation requires this input.");
        }
        continue;
      }
      boolean rightType =
          switch (String.valueOf(input.type())) {
            case "text" -> value instanceof CharSequence;
            case "integer" -> isIntegral(value);
            case "quantity", "temperature" -> isQuantity(value);
            default -> true; // the registry rejects any type outside INTERPRETED_INPUT_TYPES
          };
      if (!rightType) {
        errors.rejectValue(
            input.key(),
            "errors.inventory.operation.inputWrongType",
            new Object[] {input.key(), input.type()},
            "This input is not of the type the operation declares.");
        continue;
      }
      switch (String.valueOf(input.type())) {
        case "integer" -> validateBounds(new BigDecimal(value.toString()), input, errors);
        case "quantity" -> validateAmount((Quantifiable) value, input, errors);
        case "temperature" -> validateTemperature((Quantifiable) value, input, errors);
        default -> {}
      }
    }
  }

  /**
   * {@code min} and {@code max} are declared on integer inputs only (count). The max is what turns
   * a count the request builder refuses (over MAX_SUBSAMPLES) into a 400 instead of a 500.
   */
  private static void validateBounds(
      BigDecimal value, InventoryOperationConfig.Input input, Errors errors) {
    if (input.min() != null && value.compareTo(input.min()) < 0) {
      errors.rejectValue(
          input.key(),
          "errors.inventory.operation.inputBelowMinimum",
          new Object[] {input.key(), input.min()},
          "This input is below the minimum the operation declares.");
    }
    if (input.max() != null && value.compareTo(input.max()) > 0) {
      errors.rejectValue(
          input.key(),
          "errors.inventory.operation.inputAboveMaximum",
          new Object[] {input.key(), input.max()},
          "This input is above the maximum the operation declares.");
    }
  }

  /**
   * A temperature must carry a real temperature unit and be storable, and is then compared against
   * the definition's Celsius bounds on the temperature it denotes, not its number, so a value sent
   * in Kelvin or Fahrenheit is judged correctly. Each failure returns: the bounds can only be
   * checked against a real, storable temperature.
   */
  private static void validateTemperature(
      Quantifiable temperature, InventoryOperationConfig.Input input, Errors errors) {
    Integer unitId = temperature.getUnitId();
    if (unitId == null
        || !RSUnitDef.exists(unitId)
        || !RSUnitDef.getUnitById(unitId).isTemperature()) {
      errors.rejectValue(
          input.key(), "errors.inventory.temperature.invalidUnit", "Not a temperature unit.");
      return;
    }
    if (!QuantityInfo.canStoreWithoutRounding(temperature.getNumericValue())) {
      errors.rejectValue(
          input.key(),
          "errors.inventory.temperature.notStorable",
          "The temperature supports at most 3 decimal places.");
      return;
    }
    QuantityInfo value = QuantityInfo.of(temperature);
    if (input.maxCelsius() != null) {
      QuantityInfo maximum = QuantityInfo.of(input.maxCelsius(), RSUnitDef.CELSIUS);
      if (QUANTITY_UTILS.getComparatorFor(maximum).compare(value, maximum) > 0) {
        errors.rejectValue(
            input.key(),
            "errors.inventory.operation.storageTempAboveMax",
            new Object[] {input.maxCelsius()},
            "The temperature is above this operation's maximum.");
      }
    }
    if (input.minCelsius() != null) {
      QuantityInfo minimum = QuantityInfo.of(input.minCelsius(), RSUnitDef.CELSIUS);
      if (QUANTITY_UTILS.getComparatorFor(minimum).compare(value, minimum) < 0) {
        errors.rejectValue(
            input.key(),
            "errors.inventory.operation.storageTempBelowMin",
            new Object[] {input.minCelsius()},
            "The temperature is below this operation's minimum.");
      }
    }
  }

  /** A quantity is its number and its unit; one without a number is not a quantity. */
  private static boolean isQuantity(Object value) {
    return value instanceof Quantifiable quantity && quantity.getNumericValue() != null;
  }

  /**
   * Same rule and codes as the samples endpoint applies to a record quantity, plus the storability
   * check: quantities persist at 3 decimal places (HALF_UP), so a finer value would be silently
   * rounded to an amount the caller never sent.
   */
  private static void validateAmount(
      Quantifiable quantity, InventoryOperationConfig.Input input, Errors errors) {
    if (quantity.getNumericValue().signum() < 0) {
      errors.rejectValue(
          input.key(), "errors.inventory.quantity.negative", "The quantity must not be negative.");
    }
    if (!QuantityInfo.canStoreWithoutRounding(quantity.getNumericValue())) {
      errors.rejectValue(
          input.key(),
          "errors.inventory.operation.inputNotStorable",
          new Object[] {input.key()},
          "The quantity supports at most 3 decimal places.");
    }
    Integer unitId = quantity.getUnitId();
    if (unitId == null || !RSUnitDef.exists(unitId)) {
      errors.rejectValue(
          input.key(),
          "errors.inventory.quantity.unitInvalid",
          new Object[] {unitId},
          "The quantity's unit id is not a unit.");
    } else if (!RSUnitDef.getUnitById(unitId).isAmount()) {
      errors.rejectValue(
          input.key(),
          "errors.inventory.quantity.unitNotAmount",
          new Object[] {unitId},
          "The quantity's unit is not a mass, volume or dimensionless unit.");
    }
  }

  /**
   * Whole numbers only. Probed: Jackson binds a JSON integer to Integer or Long by magnitude and a
   * fractional literal to Double, so a Double here is a fractional count, not a representation
   * quirk. BigInteger is accepted because it is also a whole number, not because it was probed.
   */
  private static boolean isIntegral(Object value) {
    return value instanceof Integer
        || value instanceof Long
        || value instanceof Short
        || value instanceof Byte
        || value instanceof BigInteger;
  }

  /** Blank text counts as absent, so an optional text input may be sent as "". */
  private static boolean isAbsent(Object value) {
    return value == null || (value instanceof CharSequence text && text.toString().isBlank());
  }
}
