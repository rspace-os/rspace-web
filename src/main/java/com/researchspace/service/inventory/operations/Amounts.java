package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import java.math.BigDecimal;

/** Small conversions every operation's {@code build} needs. */
final class Amounts {

  /** Used when a defaulted zero amount has no unit to inherit. */
  private static final int UNSET_UNIT = 0;

  private Amounts() {}

  static ApiQuantityInfo copy(ApiQuantityInfo quantity) {
    return new ApiQuantityInfo(quantity.getNumericValue(), quantity.getUnitId());
  }

  /**
   * The origin's whole current quantity, for an operation that consumes it. An origin holding
   * nothing yields a unit-less zero, which the core's originEmpty rule then rejects.
   */
  static ApiQuantityInfo wholeOf(OriginState origin) {
    return origin.quantity() == null
        ? new ApiQuantityInfo(BigDecimal.ZERO, UNSET_UNIT)
        : copy(origin.quantity());
  }

  /** A no-op decrement in the origin's own unit, for an operation that takes nothing. */
  static ApiQuantityInfo noneFrom(OriginState origin, ApiQuantityInfo fallbackUnit) {
    Integer unitId =
        origin.quantity() != null
            ? origin.quantity().getUnitId()
            : (fallbackUnit == null ? UNSET_UNIT : fallbackUnit.getUnitId());
    return new ApiQuantityInfo(BigDecimal.ZERO, unitId == null ? UNSET_UNIT : unitId);
  }
}
