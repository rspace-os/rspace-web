package com.researchspace.service.inventory.operations;

import static com.researchspace.service.inventory.operations.OperationTestFixtures.errorsFor;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.millilitres;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.requestOrigin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.MapBindingResult;

class OperationOriginRulesTest {

  private static final AliquotOperation ALIQUOT = new AliquotOperation();
  private static final PoolOperation POOL = new PoolOperation();
  private static final DestroyOperation DESTROY = new DestroyOperation();

  private static ApiInventoryOperationRequests.Aliquot aliquot(
      ApiInventoryOperationRequests.Origin origin) {
    ApiInventoryOperationRequests.Aliquot request = new ApiInventoryOperationRequests.Aliquot();
    request.setSampleName("Aliquots");
    request.setCount(BigDecimal.ONE);
    request.setEachAmount(millilitres("1"));
    request.setOrigin(origin);
    return request;
  }

  private static ApiInventoryOperationRequests.Pool pool(
      List<ApiInventoryOperationRequests.Origin> origins) {
    ApiInventoryOperationRequests.Pool request = new ApiInventoryOperationRequests.Pool();
    request.setSampleName("Pooled");
    request.setEachAmount(millilitres("1"));
    request.setOrigins(origins);
    return request;
  }

  @Test
  void namesASingleOriginRequestsOriginInTheSingular() {
    assertEquals("origin", OperationOriginRules.originField(true, 0));
    assertEquals("origins[2]", OperationOriginRules.originField(false, 2));
  }

  @Test
  void refusesTheSameSubsampleTwiceInOneOperation() {
    // Each origin's amount is checked against its own original quantity, but the decrements are
    // applied in order, so a repeat would be checked once and taken twice.
    ApiInventoryOperationRequests.Pool request =
        pool(List.of(requestOrigin(100, millilitres("1")), requestOrigin(100, millilitres("1"))));
    BeanPropertyBindingResult errors = errorsFor(request);

    OperationOriginRules.validate(POOL, request, errors);

    assertEquals(
        "errors.inventory.operation.duplicateOrigin",
        errors.getFieldError("origins[1].globalId").getCode());
    assertNull(errors.getFieldError("origins[0].globalId"));
  }

  @Test
  void refusesTheSameSubsampleUnderTwoSpellingsOfItsGlobalId() {
    ApiInventoryOperationRequests.Pool request =
        pool(
            List.of(
                requestOrigin("SS100", millilitres("1")),
                requestOrigin("SS0100", millilitres("1")),
                requestOrigin("SS100v1", millilitres("1"))));
    BeanPropertyBindingResult errors = errorsFor(request);

    OperationOriginRules.validate(POOL, request, errors);

    assertEquals(
        "errors.inventory.operation.duplicateOrigin",
        errors.getFieldError("origins[1].globalId").getCode());
    assertEquals(
        "errors.inventory.operation.duplicateOrigin",
        errors.getFieldError("origins[2].globalId").getCode());
    assertNull(errors.getFieldError("origins[0].globalId"));
  }

  @Test
  void treatsTwoDifferentSubsamplesAsDistinctHoweverTheyAreSpelled() {
    ApiInventoryOperationRequests.Pool request =
        pool(
            List.of(
                requestOrigin("SS100", millilitres("1")),
                requestOrigin("SS1000", millilitres("1"))));
    BeanPropertyBindingResult errors = errorsFor(request);

    OperationOriginRules.validate(POOL, request, errors);

    assertNull(errors.getFieldError("origins[1].globalId"));
  }

  @Test
  void refusesANullOriginEntryAtItsOwnIndex() {
    ApiInventoryOperationRequests.Pool request =
        pool(Arrays.asList(requestOrigin(100, millilitres("1")), null));
    BeanPropertyBindingResult errors = errorsFor(request);

    OperationOriginRules.validate(POOL, request, errors);

    assertEquals(
        "errors.inventory.operation.originIdRequired",
        errors.getFieldError("origins[1]").getCode());
  }

  @Test
  void requiresAPositiveAmountWhereTheOperationTakesOne() {
    for (String amount : new String[] {null, "0"}) {
      ApiInventoryOperationRequests.Aliquot request =
          aliquot(requestOrigin(100, amount == null ? null : millilitres(amount)));
      BeanPropertyBindingResult errors = errorsFor(request);

      OperationOriginRules.validate(ALIQUOT, request, errors);

      assertEquals(
          amount == null
              ? "errors.inventory.operation.amountTakenInvalid"
              : "errors.inventory.operation.amountTakenPositive",
          errors.getFieldError("origin.amountTaken").getCode(),
          String.valueOf(amount));
    }
  }

  @Test
  void refusesAnAmountWhereTheOperationDecidesForItself() {
    ApiInventoryOperationRequests.Destroy request = new ApiInventoryOperationRequests.Destroy();
    request.setOrigin(requestOrigin(100, millilitres("1")));
    BeanPropertyBindingResult errors = errorsFor(request);

    OperationOriginRules.validate(DESTROY, request, errors);

    assertEquals(
        "errors.inventory.operation.amountTakenNotApplicable",
        errors.getFieldError("origin.amountTaken").getCode());
  }

  @Test
  void acceptsAnOriginWithNoAmountWhereTheOperationTakesNone() {
    ApiInventoryOperationRequests.Destroy request = new ApiInventoryOperationRequests.Destroy();
    request.setOrigin(requestOrigin(100, null));
    BeanPropertyBindingResult errors = errorsFor(request);

    OperationOriginRules.validate(DESTROY, request, errors);

    assertTrue(errors.getAllErrors().isEmpty(), errors.getAllErrors()::toString);
  }

  @Test
  void aDocumentationTargetMustNameARecordKindThePickerOffers() {
    ApiInventoryOperationRequests.Aliquot request = aliquot(requestOrigin(100, millilitres("1")));
    for (String target : List.of("SS42", "IC7", "nonsense")) {
      request.setDocumentedByGlobalId(target);
      BeanPropertyBindingResult errors = errorsFor(request);

      OperationOriginRules.validate(ALIQUOT, request, errors);

      assertEquals(
          "errors.inventory.operation.documentationLinkTargetInvalid",
          errors.getFieldError("documentedByGlobalId").getCode(),
          target);
    }
    for (String target : List.of("SD7", "NB7", "GL7")) {
      request.setDocumentedByGlobalId(target);
      BeanPropertyBindingResult errors = errorsFor(request);

      OperationOriginRules.validate(ALIQUOT, request, errors);

      assertNull(errors.getFieldError("documentedByGlobalId"), target);
    }
  }

  @Test
  void readsASubsampleIdOnlyFromAWellFormedSubsampleGlobalId() {
    MapBindingResult errors = new MapBindingResult(new java.util.HashMap<>(), "request");

    assertEquals(
        Long.valueOf(100L), OperationOriginRules.subSampleId("SS100", "origin.globalId", errors));
    assertNull(errors.getFieldError("origin.globalId"));

    assertNull(OperationOriginRules.subSampleId("SA100", "origin.globalId", errors));
    assertEquals(
        "errors.inventory.operation.originGlobalIdInvalid",
        errors.getFieldError("origin.globalId").getCode());
  }
}
