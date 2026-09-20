package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ApiInventoryOperationRequestsBeanValidationTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void buildValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    factory.close();
  }

  private static ApiQuantityInfo millilitres(String value) {
    return new ApiQuantityInfo(new BigDecimal(value), 3);
  }

  private static ApiInventoryOperationRequests.Origin origin() {
    ApiInventoryOperationRequests.Origin origin = new ApiInventoryOperationRequests.Origin();
    origin.setGlobalId("SS100");
    origin.setAmountTaken(millilitres("1"));
    return origin;
  }

  private static ApiInventoryOperationRequests.Aliquot aliquot() {
    ApiInventoryOperationRequests.Aliquot request = new ApiInventoryOperationRequests.Aliquot();
    request.setOrigin(origin());
    request.setSampleName("Aliquots");
    request.setCount(new BigDecimal("2"));
    request.setEachAmount(millilitres("0.5"));
    return request;
  }

  private static Set<String> keysFor(Object request, String property) {
    return validator.validate(request).stream()
        .filter(violation -> property.equals(violation.getPropertyPath().toString()))
        .map(ConstraintViolation::getMessageTemplate)
        .map(template -> template.replaceAll("^\\{|\\}$", ""))
        .collect(Collectors.toSet());
  }

  @Test
  void aWellFormedRequestHasNothingToSay() {
    assertTrue(validator.validate(aliquot()).isEmpty());
  }

  @Test
  void aFractionalCountIsRejected() {
    ApiInventoryOperationRequests.Aliquot request = aliquot();
    request.setCount(new BigDecimal("1.9"));

    assertEquals(Set.of("errors.inventory.operation.countNotWhole"), keysFor(request, "count"));
  }

  @Test
  void aCountOutsideItsBoundsIsRejected() {
    ApiInventoryOperationRequests.Aliquot tooMany = aliquot();
    tooMany.setCount(new BigDecimal("101"));
    assertEquals(Set.of("errors.inventory.operation.inputAboveMaximum"), keysFor(tooMany, "count"));

    ApiInventoryOperationRequests.Aliquot tooFew = aliquot();
    tooFew.setCount(BigDecimal.ZERO);
    assertEquals(Set.of("errors.inventory.operation.inputBelowMinimum"), keysFor(tooFew, "count"));
  }

  @Test
  void anAbsentCountIsAllowedBecauseItDefaultsToOne() {
    ApiInventoryOperationRequests.Aliquot request = aliquot();
    request.setCount(null);

    assertTrue(keysFor(request, "count").isEmpty());
  }

  @Test
  void aCreatingRequestNeedsASampleNameAndAnEachAmount() {
    ApiInventoryOperationRequests.Aliquot request = aliquot();
    request.setSampleName("   ");
    request.setEachAmount(null);

    assertEquals(
        Set.of("errors.inventory.operation.inputRequired"), keysFor(request, "sampleName"));
    assertEquals(
        Set.of("errors.inventory.operation.inputRequired"), keysFor(request, "eachAmount"));
  }

  @Test
  void aSampleNameLongerThanItsColumnIsRejected() {
    ApiInventoryOperationRequests.Aliquot request = aliquot();
    request.setSampleName("x".repeat(256));

    assertEquals(Set.of("errors.inventory.operation.inputTooLong"), keysFor(request, "sampleName"));
  }

  @Test
  void aCryomediumLongerThanTheFieldContentColumnIsRejected() {
    ApiInventoryOperationRequests.Cryopreserve request =
        new ApiInventoryOperationRequests.Cryopreserve();
    request.setOrigin(origin());
    request.setSampleName("Frozen");
    request.setEachAmount(millilitres("0.5"));
    request.setStorageTemp(new ApiQuantityInfo(new BigDecimal("-80"), 8));
    request.setCryomedium("m".repeat(251));

    assertEquals(Set.of("errors.inventory.operation.inputTooLong"), keysFor(request, "cryomedium"));
  }

  @Test
  void cryopreserveNeedsAStorageTemperature() {
    ApiInventoryOperationRequests.Cryopreserve request =
        new ApiInventoryOperationRequests.Cryopreserve();
    request.setOrigin(origin());
    request.setSampleName("Frozen");
    request.setEachAmount(millilitres("0.5"));

    assertEquals(
        Set.of("errors.inventory.operation.inputRequired"), keysFor(request, "storageTemp"));
  }

  @Test
  void deriveNeedsAProcessName() {
    ApiInventoryOperationRequests.Derive request = new ApiInventoryOperationRequests.Derive();
    request.setOrigin(origin());
    request.setSampleName("Derived");
    request.setEachAmount(millilitres("0.5"));

    assertEquals(
        Set.of("errors.inventory.operation.inputRequired"), keysFor(request, "processName"));
  }

  @Test
  void aSingleOriginRequestNeedsItsOrigin() {
    ApiInventoryOperationRequests.Aliquot request = aliquot();
    request.setOrigin(null);

    assertEquals(Set.of("errors.inventory.operation.originsRequired"), keysFor(request, "origin"));
  }

  @Test
  void poolNeedsAnOriginsListOfAtLeastTwo() {
    ApiInventoryOperationRequests.Pool absent = pool(null);
    assertEquals(Set.of("errors.inventory.operation.originsRequired"), keysFor(absent, "origins"));

    ApiInventoryOperationRequests.Pool one = pool(List.of(origin()));
    assertEquals(Set.of("errors.inventory.operation.originCountMinimum"), keysFor(one, "origins"));
  }

  @Test
  void poolRefusesMoreOriginsThanTheEndpointWillProcess() {
    ApiInventoryOperationRequests.Pool tooMany = pool(java.util.Collections.nCopies(101, origin()));

    assertEquals(Set.of("errors.inventory.operation.tooManyOrigins"), keysFor(tooMany, "origins"));
  }

  private static ApiInventoryOperationRequests.Pool pool(
      List<ApiInventoryOperationRequests.Origin> origins) {
    ApiInventoryOperationRequests.Pool request = new ApiInventoryOperationRequests.Pool();
    request.setOrigins(origins);
    request.setSampleName("Pooled");
    request.setEachAmount(millilitres("0.5"));
    return request;
  }
}
