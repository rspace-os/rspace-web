package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.model.User;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Scientific measurement units */
@RequestMapping("/api/v1/quantities")
public interface QuantitiesApi {

  @GetMapping("/add")
  ApiQuantityInfo addTwoQuantities(
      BigDecimal value1, Integer unitId1, BigDecimal value2, Integer unitId2, User user);
}
