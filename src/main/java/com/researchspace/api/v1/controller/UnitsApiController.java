package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.UnitsApi;
import com.researchspace.model.User;
import com.researchspace.model.units.RSUnitDef;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;

@ApiController
public class UnitsApiController extends BaseApiController implements UnitsApi {

  private List<RSUnitDef> sortedUnits;

  @GetMapping
  public Collection<RSUnitDef> units(User user) {
    if (sortedUnits == null) {
      sortedUnits =
          EnumSet.allOf(RSUnitDef.class).stream()
              .sorted(
                  Comparator.comparing(RSUnitDef::getCategory)
                      .thenComparingInt(RSUnitDef::getOrder))
              .collect(Collectors.toList());
    }
    return sortedUnits;
  }
}
