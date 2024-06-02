package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.model.User;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.testutils.SpringTransactionalTest;
import java.math.BigDecimal;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;

public class QuantitiesApiControllerTest extends SpringTransactionalTest {

  private @Autowired QuantitiesApiController quantitiesApi;

  private User testUser;

  @Before
  public void setUp() {
    testUser = createInitAndLoginAnyUser();
  }

  @Test
  public void addTwoQuantities() throws BindException {

    // basic add
    ApiQuantityInfo sum =
        quantitiesApi.addTwoQuantities(
            BigDecimal.valueOf(120.21),
            RSUnitDef.MILLI_LITRE.getId(),
            BigDecimal.valueOf(230.29),
            RSUnitDef.MILLI_LITRE.getId(),
            testUser);
    assertEquals("350.5 ml", sum.toQuantityInfo().toPlainString());

    // add with different units
    sum =
        quantitiesApi.addTwoQuantities(
            BigDecimal.valueOf(120.21),
            RSUnitDef.MILLI_LITRE.getId(),
            BigDecimal.valueOf(590),
            RSUnitDef.MICRO_LITRE.getId(),
            testUser);
    assertEquals("120.8 ml", sum.toQuantityInfo().toPlainString());

    // subtract
    sum =
        quantitiesApi.addTwoQuantities(
            BigDecimal.valueOf(250.21),
            RSUnitDef.MILLI_LITRE.getId(),
            BigDecimal.valueOf(-230.01),
            RSUnitDef.MILLI_LITRE.getId(),
            testUser);
    assertEquals("20.2 ml", sum.toQuantityInfo().toPlainString());

    // subtract with different units
    sum =
        quantitiesApi.addTwoQuantities(
            BigDecimal.valueOf(120.21),
            RSUnitDef.MILLI_LITRE.getId(),
            BigDecimal.valueOf(-60),
            RSUnitDef.MICRO_LITRE.getId(),
            testUser);
    assertEquals("120.15 ml", sum.toQuantityInfo().toPlainString());
  }
}
