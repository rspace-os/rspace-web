package com.researchspace.service.inventory.operations;

import static com.researchspace.service.inventory.operations.OperationTestFixtures.KEYS;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.celsius;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.errorsFor;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.millilitres;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.originState;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.requestOrigin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;

class CryopreserveOperationTest {

  private static final CryopreserveOperation CRYOPRESERVE = new CryopreserveOperation();
  private static final LocalDate TODAY = LocalDate.parse("2026-08-20");

  private static ApiInventoryOperationRequests.Cryopreserve request() {
    ApiInventoryOperationRequests.Cryopreserve request =
        new ApiInventoryOperationRequests.Cryopreserve();
    request.setSampleName("HeLa frozen");
    request.setCount(new BigDecimal("2"));
    request.setEachAmount(millilitres("1"));
    request.setStorageTemp(celsius("-80"));
    request.setOrigin(requestOrigin(100, millilitres("2")));
    return request;
  }

  private static ApiSampleWithFullSubSamples build(
      ApiInventoryOperationRequests.Cryopreserve request) {
    return CRYOPRESERVE.build(request, List.of(originState(100, "10")), KEYS, TODAY).getNewSample();
  }

  @Test
  void storesTheCreatedSampleAtTheRequestedTemperature() {
    ApiSampleWithFullSubSamples sample = build(request());

    assertEquals(celsius("-80"), sample.getStorageTempMin());
    assertEquals(celsius("-80"), sample.getStorageTempMax());
  }

  @Test
  void recordsTheCryomediumAsAFieldOnTheCreatedSample() {
    ApiInventoryOperationRequests.Cryopreserve request = request();
    request.setCryomedium("10% DMSO");

    assertEquals(
        "10% DMSO",
        OperationTestFixtures.fieldNamed(
                build(request).getExtraFields(), "operations.cryopreserve.cryomediumField")
            .getContent());
  }

  @Test
  void addsNoCryomediumFieldWhenNoneWasGiven() {
    assertNull(
        build(request()).getExtraFields().stream()
            .filter(f -> "operations.cryopreserve.cryomediumField".equals(f.getOperationFieldKey()))
            .findFirst()
            .orElse(null));
  }

  @Test
  void rejectsATemperatureAboveTheCryogenicMaximum() {
    ApiInventoryOperationRequests.Cryopreserve request = request();
    request.setStorageTemp(celsius("-17"));
    BeanPropertyBindingResult errors = errorsFor(request);

    CRYOPRESERVE.validate(request, errors);

    assertEquals(
        "errors.inventory.operation.storageTempAboveMax",
        errors.getFieldError("storageTemp").getCode());
  }

  @Test
  void judgesTheBoundOnTheTemperatureDenotedNotTheRawNumber() {
    ApiInventoryOperationRequests.Cryopreserve request = request();
    // 200 K is -73 C, comfortably cryogenic, though 200 read as a number is not.
    request.setStorageTemp(
        new com.researchspace.api.v1.model.ApiQuantityInfo(
            new BigDecimal("200"), com.researchspace.model.units.RSUnitDef.KELVIN.getId()));
    BeanPropertyBindingResult errors = errorsFor(request);

    CRYOPRESERVE.validate(request, errors);

    assertNull(errors.getFieldError("storageTemp"));
  }
}
