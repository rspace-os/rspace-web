package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.InventoryOperationPostValidator.COMPUTED_CONTENT_SHAPES;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.units.QuantityUtils;
import com.researchspace.service.inventory.InventoryOperationConfig;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;

/**
 * The low-level rules {@link InventoryOperationPostValidator} and its per-region collaborators all
 * apply: rejecting a property no operation definition declares, finding and content-checking a
 * field the definition names, and the shape a quantity must have. Stateless and static; it exists
 * so the same rule is written once rather than once per payload region.
 */
final class OperationValidationSupport {

  private OperationValidationSupport() {}

  /** Stateless, so one instance serves every caller. */
  static final QuantityUtils quantityUtils = new QuantityUtils();

  /**
   * Rejects a property the operation definition does not declare. Absent means null, an empty
   * collection or a blank string, so a client that spells out the DTO's own defaults ({@code
   * "tags": []}) is not punished for sending nothing.
   */
  static void rejectIfPresent(Errors errors, String field, Object value) {
    boolean present = value != null;
    if (value instanceof Collection<?> collection) {
      present = !collection.isEmpty();
    } else if (value instanceof String string) {
      present = !string.isBlank();
    }
    if (present) {
      String property = field.substring(field.lastIndexOf('.') + 1);
      errors.rejectValue(
          field,
          "errors.inventory.operation.undeclaredProperty",
          new Object[] {property},
          "This operation does not accept this property on the sample it creates.");
    }
  }

  static List<ApiExtraField> fieldsWithKey(List<ApiExtraField> fields, String key) {
    return fields.stream()
        .filter(field -> field != null && key.equals(field.getOperationFieldKey()))
        .toList();
  }

  /**
   * The content of a declared field. A field fed by a computed value is checked against the shape
   * that function promises; a field fed by a plain input carries free text, required exactly when
   * that input is (Cryopreserve's optional cryomedium may be blank).
   */
  static void validateDeclaredContent(
      String content,
      String key,
      String contentFrom,
      InventoryOperationConfig config,
      String path,
      Errors errors) {
    Optional<InventoryOperationConfig.Computed> computed =
        config.effect().computed().stream()
            .filter(entry -> entry.into() != null && entry.into().equals(contentFrom))
            .findFirst();
    if (computed.isPresent()) {
      Predicate<String> shape = COMPUTED_CONTENT_SHAPES.get(computed.get().fn());
      if (shape != null && (content == null || !shape.test(content.trim()))) {
        errors.rejectValue(
            path + ".content",
            "errors.inventory.operation.computedContentInvalid",
            new Object[] {key, computed.get().fn()},
            "This field's content does not match what the operation computes for it.");
      }
      return;
    }
    boolean fedByRequiredInput =
        config.inputs().stream()
            .anyMatch(
                input ->
                    input.key() != null && input.key().equals(contentFrom) && input.required());
    if (fedByRequiredInput && StringUtils.isBlank(content)) {
      rejectDeclaredField(errors, path + ".content", key);
    }
  }

  static void rejectDeclaredField(Errors errors, String field, String key) {
    errors.rejectValue(
        field,
        "errors.inventory.operation.declaredFieldMissing",
        new Object[] {key},
        "The request must contain exactly the fields this operation declares.");
  }

  /**
   * A valid amount-taken is a non-negative numeric value carrying a real unit. The unit is required
   * because the manager converts it to a {@link com.researchspace.model.units.QuantityInfo}
   * (unit-aware subtraction); a null or non-positive unit would fail there with a 500 rather than a
   * clean 400. The frontend uses a non-positive unit id (UNSET_UNIT = 0) as an "unset" marker, so
   * the unit id must be present and greater than zero. A zero numeric value is still allowed (a
   * no-op decrement, e.g. Passage); a non-positive unit id is not.
   */
  static boolean isValidAmountTaken(ApiQuantityInfo quantity) {
    return quantity != null
        && quantity.getNumericValue() != null
        && quantity.getNumericValue().compareTo(BigDecimal.ZERO) >= 0
        && quantity.getUnitId() != null
        && quantity.getUnitId() > 0;
  }

  /** The field type a definition declares, defaulting to text like {@link ApiExtraField} does. */
  static FieldType declaredFieldType(String declaredType) {
    return declaredType == null ? FieldType.TEXT : FieldType.valueOf(declaredType.toUpperCase());
  }
}
