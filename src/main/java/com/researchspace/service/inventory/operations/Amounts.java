package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import java.math.BigDecimal;

final class Amounts {

  private static final int UNSET_UNIT = 0;

  private Amounts() {}

  static ApiQuantityInfo copy(ApiQuantityInfo quantity) {
    return new ApiQuantityInfo(quantity.getNumericValue(), quantity.getUnitId());
  }

  /**
   * An origin holding nothing yields a unit-less zero rather than failing here, which the core's
   * originEmpty rule then rejects.
   */
  static ApiQuantityInfo wholeOf(OriginState origin) {
    return origin.quantity() == null
        ? new ApiQuantityInfo(BigDecimal.ZERO, UNSET_UNIT)
        : copy(origin.quantity());
  }

  static ApiQuantityInfo noneFrom(OriginState origin, ApiQuantityInfo fallbackUnit) {
    ApiQuantityInfo source = origin.quantity() != null ? origin.quantity() : fallbackUnit;
    Integer unitId = source == null ? null : source.getUnitId();
    return new ApiQuantityInfo(BigDecimal.ZERO, unitId == null ? UNSET_UNIT : unitId);
  }
}
