package com.researchspace.api.v1;

import com.researchspace.model.User;
import com.researchspace.model.units.RSUnitDef;
import java.util.Collection;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Scientific measurement units */
@RequestMapping("/api/v1/units")
public interface UnitsApi {

  @GetMapping()
  Collection<RSUnitDef> units(User user);
}
