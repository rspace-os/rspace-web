package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.api.v1.controller.ApiControllerAdvice;
import com.researchspace.api.v1.controller.QuantitiesApiController;
import com.researchspace.apiutils.ApiError;
import com.researchspace.model.field.LocalizedIllegalArgumentException;
import com.researchspace.model.field.LocalizedIllegalStateException;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.QuantityUtils;
import com.researchspace.model.units.RSUnitDef;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

class QuantitiesMessagesTest {

  private final QuantitiesApiController controller = new QuantitiesApiController();
  private final ApiControllerAdvice advice = new ApiControllerAdvice();

  @BeforeEach
  void useRealCatalogue() {
    ReflectionTestUtils.setField(
        advice, "messages", new MessageSourceUtils(new JsonMessageSource()));
  }

  @Test
  void invalidUnitLookupResolvesInApiError() {
    LocalizedIllegalArgumentException exception =
        assertThrows(LocalizedIllegalArgumentException.class, () -> RSUnitDef.getUnitById(-1));

    ResponseEntity<Object> response =
        advice.handleLocalizedIllegalArgumentException(exception, null);

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
    assertEquals("invalid ID for unit", ((ApiError) response.getBody()).getMessage());
  }

  @Test
  void differentUnitCategoriesResolveInApiError() {
    LocalizedIllegalArgumentException exception =
        assertThrows(
            LocalizedIllegalArgumentException.class,
            () ->
                controller.addTwoQuantities(
                    BigDecimal.ONE,
                    RSUnitDef.GRAM.getId(),
                    BigDecimal.ONE,
                    RSUnitDef.LITRE.getId(),
                    null));

    ResponseEntity<Object> response =
        advice.handleLocalizedIllegalArgumentException(exception, null);

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
    assertEquals(
        "items to sum are not commensurate - they have different unit categories",
        ((ApiError) response.getBody()).getMessage());
  }

  @Test
  void incompatibleUsageQuantityResolvesInApiError() {
    QuantityInfo available = QuantityInfo.of(1, RSUnitDef.GRAM);
    QuantityInfo used = QuantityInfo.of(1, RSUnitDef.LITRE);
    LocalizedIllegalArgumentException exception =
        assertThrows(
            LocalizedIllegalArgumentException.class,
            () -> new QuantityUtils().subtract(available, used));

    ResponseEntity<Object> response =
        advice.handleLocalizedIllegalArgumentException(exception, null);

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
    assertEquals(
        "items to subtract are not commensurate - they have different unit categories",
        ((ApiError) response.getBody()).getMessage());
  }

  @Test
  void unsupportedUnitTypeResolvesInApiError() {
    LocalizedIllegalStateException exception =
        assertThrows(
            LocalizedIllegalStateException.class,
            () ->
                controller.addTwoQuantities(
                    BigDecimal.ONE,
                    RSUnitDef.MOLAR.getId(),
                    BigDecimal.ONE,
                    RSUnitDef.MOLAR.getId(),
                    null));

    ResponseEntity<Object> response = advice.handleLocalizedIllegalStateException(exception, null);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals(
        "Unsupported unit type: " + RSUnitDef.MOLAR.getDefinition(),
        ((ApiError) response.getBody()).getMessage());
  }
}
