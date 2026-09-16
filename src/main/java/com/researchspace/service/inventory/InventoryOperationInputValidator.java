package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.EditInfo;
import com.researchspace.model.units.Quantifiable;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.QuantityUtils;
import com.researchspace.model.units.RSUnitDef;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.validation.Errors;

/**
 * Checks a supplied inputs map against an operation definition's {@code inputs[]}: present when
 * required, of the declared type, within the declared bounds, and structurally storable. Looks only
 * at the inputs map, never at a sample, since the sample doesn't exist yet when this runs. Every
 * problem is a field error on the input's own key.
 */
public final class InventoryOperationInputValidator {

  private static final QuantityUtils QUANTITY_UTILS = new QuantityUtils();

  private InventoryOperationInputValidator() {}

  /**
   * Fills in every absent optional input that declares a {@code default}, typed as the input wants
   * it (an integer, a Celsius temperature, text), so a caller may omit an optional input. Applied
   * before {@link #validate}, so a default outside its own bounds is still rejected rather than
   * trusted.
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
            "This input is not of the type the operation declares.");
        continue;
      }
      boolean isCreatedAmount = input.key().equals(definition.effect().eachAmountFrom());
      switch (String.valueOf(input.type())) {
        case "text" -> validateTextLength(value.toString(), definition, input, errors);
        case "integer" -> validateBounds(new BigDecimal(value.toString()), input, errors);
        case "quantity" -> validateAmount((Quantifiable) value, input, isCreatedAmount, errors);
        case "temperature" -> validateTemperature((Quantifiable) value, input, errors);
        default -> {}
      }
    }
    rejectUndeclaredInputs(definition, inputs, errors);
    rejectUnstorableTotal(definition, inputs, errors);
  }

  /**
   * Rejects an input key the definition does not declare.
   *
   * <p>The loop in {@link #validate} walks the DEFINITION's inputs and reads each declared key out
   * of the caller's map, never the map itself, so an undeclared key was unreachable rather than
   * wrong. A misspelled required key still failed, since the declared key was then absent; a
   * misspelled optional one did not, and its value was silently dropped - e.g. "Count" for "count"
   * created one subsample instead of the four asked for, since count declares a default of 1.
   *
   * <p>The undeclared key is the caller's field here, so it is also the field the error names:
   * pointing at the declared key would name something the caller never sent.
   */
  private static void rejectUndeclaredInputs(
      InventoryOperationConfig definition, Map<String, ?> inputs, Errors errors) {
    if (inputs == null) {
      return;
    }
    Set<String> declared =
        definition.inputs().stream()
            .map(InventoryOperationConfig.Input::key)
            .collect(Collectors.toSet());
    for (String key : inputs.keySet()) {
      if (!declared.contains(key)) {
        errors.rejectValue(
            key,
            "errors.inventory.operation.inputUnknown",
            "This operation does not declare this input.");
      }
    }
  }

  /**
   * The created sample's total quantity is the sum of the children the builder emits, recalculated
   * while the sample is persisted. Each child fits the DECIMAL(19,3) quantity column while {@code
   * count x eachAmount} may not, and nothing between here and the INSERT checks the sum - the
   * delegated sample validators see only the children. Without this, an otherwise valid request
   * reached the origins, decremented them, then failed inside the transaction as a 500.
   *
   * <p>Reported on the amount, not the count, since the amount is the value the caller chose freely
   * (the definition already bounds the count). Skipped when either input was already rejected on
   * its own terms, so the caller sees one problem per field.
   */
  private static void rejectUnstorableTotal(
      InventoryOperationConfig definition, Map<String, ?> inputs, Errors errors) {
    InventoryOperationConfig.Effect effect = definition.effect();
    if (definition.noOutput() || effect.countFrom() == null || effect.eachAmountFrom() == null) {
      return;
    }
    Object count = inputs == null ? null : inputs.get(effect.countFrom());
    Object each = inputs == null ? null : inputs.get(effect.eachAmountFrom());
    if (!isIntegral(count)
        || !(each instanceof Quantifiable amount)
        || amount.getNumericValue() == null
        || errors.getFieldErrorCount(effect.countFrom()) > 0
        || errors.getFieldErrorCount(effect.eachAmountFrom()) > 0) {
      return;
    }
    BigDecimal total = amount.getNumericValue().multiply(new BigDecimal(count.toString()));
    if (!QuantityInfo.canStoreWithoutRounding(total)) {
      errors.rejectValue(
          effect.eachAmountFrom(),
          "errors.inventory.operation.totalNotStorable",
          "The created subsamples hold more in total than a quantity can store.");
    }
  }

  /**
   * Bounded by the column the built sample stores it in: {@code effect().nameFrom()} becomes the
   * sample's name ({@code EditInfo.name}, varchar 255); a {@code textFields[]}/{@code
   * originFields[]} entry's {@code contentFrom} becomes that field's content ({@code
   * EditInfo.description}, varchar 250). Without this, an over-long name failed only after the
   * origins were read and came back as {@code newSample.name} - a field the caller never sent - and
   * an over-long field content reached the INSERT as a 500 after the origins were already
   * decremented. Any other text input gets the record-name limit as a ceiling.
   *
   * <p>The message carries the limit only, not the input key or type, to avoid leaking a raw config
   * identifier into a user-facing message.
   */
  private static void validateTextLength(
      String value,
      InventoryOperationConfig definition,
      InventoryOperationConfig.Input input,
      Errors errors) {
    int max =
        isStoredAsFieldContent(definition, input.key())
            ? EditInfo.DESCRIPTION_LENGTH
            : BaseRecord.DEFAULT_VARCHAR_LENGTH;
    if (value.length() > max) {
      errors.rejectValue(
          input.key(),
          "errors.inventory.operation.inputTooLong",
          new Object[] {max},
          "This input is longer than the column it is stored in.");
    }
  }

  private static boolean isStoredAsFieldContent(InventoryOperationConfig definition, String key) {
    return definition.effect().textFields().stream()
            .anyMatch(field -> key.equals(field.contentFrom()))
        || definition.effect().originFields().stream()
            .anyMatch(field -> key.equals(field.contentFrom()));
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
          new Object[] {input.min()},
          "This input is below the minimum the operation declares.");
    }
    if (input.max() != null && value.compareTo(input.max()) > 0) {
      errors.rejectValue(
          input.key(),
          "errors.inventory.operation.inputAboveMaximum",
          new Object[] {input.max()},
          "This input is above the maximum the operation declares.");
    }
  }

  /**
   * A temperature must carry a real temperature unit and be storable, then is compared against the
   * definition's Celsius bounds on the temperature it denotes, not its raw number - so a value sent
   * in Kelvin or Fahrenheit is judged correctly.
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

  private static boolean isQuantity(Object value) {
    return value instanceof Quantifiable quantity && quantity.getNumericValue() != null;
  }

  /**
   * Quantities persist at 3 decimal places (HALF_UP); the storability check here catches a finer
   * value before it gets silently rounded to an amount the caller never sent.
   */
  private static void validateAmount(
      Quantifiable quantity,
      InventoryOperationConfig.Input input,
      boolean isCreatedAmount,
      Errors errors) {
    if (isCreatedAmount && quantity.getNumericValue().signum() <= 0) {
      // Zero would create a subsample holding nothing while still deducting real stock from the
      // origin. Only negatives were rejected here, so a positive amountTaken paired with a zero
      // eachAmount was accepted. Scoped to eachAmountFrom, so an operation that deliberately takes
      // nothing from its origin is untouched.
      errors.rejectValue(
          input.key(),
          "errors.inventory.operation.createdAmountNotPositive",
          "Each created subsample must be given a quantity greater than zero.");
    } else if (quantity.getNumericValue().signum() < 0) {
      errors.rejectValue(
          input.key(), "errors.inventory.quantity.negative", "The quantity must not be negative.");
    }
    if (!QuantityInfo.canStoreWithoutRounding(quantity.getNumericValue())) {
      errors.rejectValue(
          input.key(),
          "errors.inventory.operation.inputNotStorable",
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
   * Whole numbers only: Jackson binds a JSON integer to Integer/Long by magnitude and a fractional
   * literal to Double, so a Double here is a genuine fractional count, not a representation quirk.
   * BigInteger is accepted because it's also whole, not by accident.
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
