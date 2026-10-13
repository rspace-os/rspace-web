package com.researchspace.service.inventory.operations;

import static com.researchspace.service.inventory.operations.OperationTestFixtures.KEYS;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.celsius;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.errorsFor;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.millilitres;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.originState;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.requestOrigin;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.validation.BeanPropertyBindingResult;

class ReviveOperationTest {

  private static final ReviveOperation REVIVE = new ReviveOperation();
  private static final LocalDate TODAY = LocalDate.parse("2026-08-20");

  private static ApiInventoryOperationRequests.Revive request() {
    ApiInventoryOperationRequests.Revive request = new ApiInventoryOperationRequests.Revive();
    request.setSampleName("HeLa revived");
    request.setCount(new BigDecimal("1"));
    request.setEachAmount(millilitres("5"));
    request.setOrigin(requestOrigin(100, millilitres("1")));
    return request;
  }

  @Test
  void storesTheRevivedSampleAtFourDegreesWhenNoTemperatureIsSent() {
    assertEquals(
        celsius("4"),
        REVIVE
            .build(request(), List.of(originState(100, "10")), KEYS, TODAY)
            .getNewSample()
            .getStorageTempMin());
  }

  @ParameterizedTest
  @CsvSource({
    "3, errors.inventory.operation.storageTempBelowMin",
    "121, errors.inventory.operation.storageTempAboveMax"
  })
  void rejectsATemperatureOutsideTheRefrigeratedToIncubationRange(
      String storageTempCelsius, String expectedCode) {
    ApiInventoryOperationRequests.Revive request = request();
    request.setStorageTemp(celsius(storageTempCelsius));
    BeanPropertyBindingResult errors = errorsFor(request);

    REVIVE.validate(request, errors);

    assertEquals(expectedCode, errors.getFieldError("storageTemp").getCode());
  }
}
