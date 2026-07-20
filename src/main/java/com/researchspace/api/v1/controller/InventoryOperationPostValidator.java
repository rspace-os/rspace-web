package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.model.units.QuantityUtils;
import java.math.BigDecimal;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Validates an {@link ApiInventoryOperationPost}. Operation-agnostic: it only enforces the
 * invariants every operation shares (a named new sample, and each origin identifying a subsample
 * with a non-negative amount-taken - zero meaning "link to but do not decrement this origin", e.g.
 * Passage; see adr/0002). Operation-specific shaping lives in the frontend config, not here.
 */
@Component
public class InventoryOperationPostValidator implements Validator {

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiInventoryOperationPost.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiInventoryOperationPost request = (ApiInventoryOperationPost) target;

    if (request.getNewSample() == null) {
      errors.rejectValue(
          "newSample",
          "errors.inventory.operation.newSampleRequired",
          "A new sample must be provided for the operation");
    } else if (!StringUtils.hasText(request.getNewSample().getName())) {
      errors.rejectValue(
          "newSample.name",
          "errors.inventory.operation.sampleNameRequired",
          "The new sample must have a name");
    }

    if (CollectionUtils.isEmpty(request.getOrigins())) {
      errors.rejectValue(
          "origins",
          "errors.inventory.operation.originsRequired",
          "At least one origin subsample must be provided");
      return;
    }

    int index = 0;
    for (ApiInventoryOperationOriginUpdate origin : request.getOrigins()) {
      errors.pushNestedPath(String.format("origins[%d]", index++));
      if (origin.getId() == null) {
        errors.rejectValue(
            "id", "errors.inventory.operation.originIdRequired", "Each origin must have an id");
      }
      if (!isNonNegativeQuantity(origin.getAmountTaken())) {
        errors.rejectValue(
            "amountTaken",
            "errors.inventory.operation.amountTakenInvalid",
            "Each origin must specify a non-negative amount to take from it");
      }
      errors.popNestedPath();
    }
  }

  private static boolean isNonNegativeQuantity(ApiQuantityInfo quantity) {
    return quantity != null
        && quantity.getNumericValue() != null
        && quantity.getNumericValue().compareTo(BigDecimal.ZERO) >= 0;
  }

  /**
   * Whether the amount taken from an origin exceeds that origin's current quantity (adr/0005). The
   * comparison is unit-aware within a measurement category (e.g. 0.006 kg against a 5 g origin), so
   * a cross-unit entry in the same category is compared correctly. This needs the origin's live
   * quantity, which the stateless {@link Validator} contract cannot load, so the controller loads
   * each origin and calls this. A null amount, or a pair in different categories (which the UI
   * never produces), is not treated as over-removal. A null/absent origin quantity means the origin
   * holds nothing (a subsample whose quantity was never set), so any positive amount taken from it
   * is over-removal.
   */
  public static boolean amountTakenExceedsOrigin(
      ApiQuantityInfo amountTaken, ApiQuantityInfo originQuantity) {
    if (amountTaken == null || amountTaken.getNumericValue() == null) {
      return false;
    }
    if (originQuantity == null || originQuantity.getNumericValue() == null) {
      // Origin holds nothing: any positive amount taken is over-removal.
      return amountTaken.getNumericValue().compareTo(BigDecimal.ZERO) > 0;
    }
    QuantityUtils quantityUtils = new QuantityUtils();
    if (!quantityUtils.isComparableQuantities(amountTaken, originQuantity)) {
      return false;
    }
    return quantityUtils.getComparatorFor(originQuantity).compare(amountTaken, originQuantity) > 0;
  }
}
