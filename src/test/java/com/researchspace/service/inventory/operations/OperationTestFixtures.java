package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.validation.BeanPropertyBindingResult;

/** Shared builders for the per-operation tests: an origin, an amount, a label resolver. */
final class OperationTestFixtures {

  static final LabelResolver KEYS = (key, args) -> args.isEmpty() ? key : key + " " + args;

  private OperationTestFixtures() {}

  static ApiQuantityInfo millilitres(String value) {
    return new ApiQuantityInfo(new BigDecimal(value), RSUnitDef.MILLI_LITRE.getId());
  }

  static ApiQuantityInfo celsius(String value) {
    return new ApiQuantityInfo(new BigDecimal(value), RSUnitDef.CELSIUS.getId());
  }

  static OriginState originState(long id, String quantityMl) {
    return new OriginState(id, "SS" + id, "subsample " + id, millilitres(quantityMl), List.of());
  }

  static ApiInventoryOperationRequests.Origin requestOrigin(long id, ApiQuantityInfo amountTaken) {
    return requestOrigin("SS" + id, amountTaken);
  }

  /** An origin named by a literal global id, for the spellings that alias to the same subsample. */
  static ApiInventoryOperationRequests.Origin requestOrigin(
      String globalId, ApiQuantityInfo amountTaken) {
    ApiInventoryOperationRequests.Origin origin = new ApiInventoryOperationRequests.Origin();
    origin.setGlobalId(globalId);
    origin.setAmountTaken(amountTaken);
    return origin;
  }

  static BeanPropertyBindingResult errorsFor(Object target) {
    return new BeanPropertyBindingResult(target, "request");
  }

  static ApiExtraField fieldNamed(List<ApiExtraField> fields, String name) {
    return fields.stream()
        .filter(field -> name.equals(field.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no field named '" + name + "' in " + fields));
  }
}
