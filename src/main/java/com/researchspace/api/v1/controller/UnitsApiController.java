package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.UnitsApi;
import com.researchspace.model.User;
import com.researchspace.model.units.RSUnitDef;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;

@ApiController
public class UnitsApiController extends BaseApiController implements UnitsApi {

  /** Every unit, by category then size. The frontend's units.json mirrors this order. */
  public static final List<RSUnitDef> SORTED_UNITS =
      EnumSet.allOf(RSUnitDef.class).stream()
          .sorted(
              Comparator.comparing(RSUnitDef::getCategory).thenComparingInt(RSUnitDef::getOrder))
          .toList();

  @GetMapping
  public Collection<RSUnitDef> units(User user) {
    return SORTED_UNITS;
  }
}
