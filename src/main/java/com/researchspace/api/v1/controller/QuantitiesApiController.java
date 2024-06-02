package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.QuantitiesApi;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.model.User;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.QuantityUtils;
import java.math.BigDecimal;
import java.util.Arrays;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;

@ApiController
public class QuantitiesApiController extends BaseApiController implements QuantitiesApi {

  private QuantityUtils qUtils = new QuantityUtils();

  @Override
  public ApiQuantityInfo addTwoQuantities(
      @RequestParam(name = "value1") BigDecimal value1,
      @RequestParam(name = "unitId1") Integer unitId1,
      @RequestParam(name = "value2") BigDecimal value2,
      @RequestParam(name = "unitId2") Integer unitId2,
      @RequestAttribute(name = "user") User user) {

    QuantityInfo q1 = new QuantityInfo(value1, unitId1);
    QuantityInfo q2 = new QuantityInfo(value2, unitId2);

    QuantityInfo sum = qUtils.sum(Arrays.asList(q1, q2));
    return new ApiQuantityInfo(sum);
  }
}
