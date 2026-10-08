package com.researchspace.service.inventory.operations;

import static com.researchspace.service.inventory.operations.OperationTestFixtures.KEYS;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.errorsFor;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.millilitres;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.originState;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.requestOrigin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;

class PoolOperationTest {

  private static final PoolOperation POOL = new PoolOperation();
  private static final LocalDate TODAY = LocalDate.parse("2026-08-20");

  private static final List<OriginState> ORIGINS =
      List.of(originState(100, "6"), originState(101, "4"));

  private static ApiInventoryOperationRequests.Pool request(
      Boolean takeAll, ApiInventoryOperationRequests.Origin... origins) {
    ApiInventoryOperationRequests.Pool request = new ApiInventoryOperationRequests.Pool();
    request.setSampleName("Pooled lysate");
    request.setCount(new BigDecimal("1"));
    request.setEachAmount(millilitres("10"));
    request.setTakeAll(takeAll);
    request.setOrigins(List.of(origins));
    return request;
  }

  @Test
  void needsMoreThanOneOrigin() {
    assertTrue(POOL.requiresMultiple());
    assertEquals("pool", POOL.key());
  }

  @Test
  void takesTheChosenAmountFromEachOrigin() {
    ApiInventoryOperationPost built =
        POOL.build(
            request(
                null, requestOrigin(100, millilitres("2")), requestOrigin(101, millilitres("3"))),
            ORIGINS,
            KEYS,
            TODAY);

    assertEquals(millilitres("2"), built.getOrigins().get(0).getAmountTaken());
    assertEquals(millilitres("3"), built.getOrigins().get(1).getAmountTaken());
    assertEquals(false, built.isEmptiesOrigin());
  }

  @Test
  void takeAllEmptiesEveryOriginOfItsLiveQuantity() {
    ApiInventoryOperationPost built =
        POOL.build(
            request(true, requestOrigin(100, null), requestOrigin(101, null)),
            ORIGINS,
            KEYS,
            TODAY);

    assertEquals(millilitres("6"), built.getOrigins().get(0).getAmountTaken());
    assertEquals(millilitres("4"), built.getOrigins().get(1).getAmountTaken());
    assertTrue(built.isEmptiesOrigin(), "the core must hold each origin to emptying");
  }

  @Test
  void rejectsAnAmountSentAlongsideTakeAll() {
    ApiInventoryOperationRequests.Pool request =
        request(true, requestOrigin(100, null), requestOrigin(101, millilitres("3")));
    BeanPropertyBindingResult errors = errorsFor(request);

    OperationOriginRules.validate(POOL, request, errors);

    assertNull(errors.getFieldError("origins[0].amountTaken"));
    assertEquals(
        "errors.inventory.operation.amountTakenNotWithTakeAll",
        errors.getFieldError("origins[1].amountTaken").getCode());
  }

  @Test
  void acceptsTakeAllWhenNoOriginCarriesAnAmount() {
    ApiInventoryOperationRequests.Pool request =
        request(true, requestOrigin(100, null), requestOrigin(101, null));
    BeanPropertyBindingResult errors = errorsFor(request);

    OperationOriginRules.validate(POOL, request, errors);
    POOL.validate(request, errors);

    assertEquals(0, errors.getErrorCount());
  }

  @Test
  void linksTheCreatedSampleToEveryPooledOrigin() {
    List<ApiExtraField> fields =
        POOL.build(
                request(
                    null,
                    requestOrigin(100, millilitres("2")),
                    requestOrigin(101, millilitres("3"))),
                ORIGINS,
                KEYS,
                TODAY)
            .getNewSample()
            .getExtraFields();

    assertEquals(2, fields.size());
    assertTrue(fields.stream().allMatch(f -> "HasPart".equals(f.getLink().getRelationType())));
    assertEquals(
        List.of("SS100", "SS101"),
        fields.stream().map(f -> f.getLink().getTargetGlobalId()).toList());
  }

  @Test
  void givesPooledOriginsSharingANameDistinctLinkFieldNames() {
    List<OriginState> twins =
        List.of(
            new OriginState(100L, "SS100", "aliquot", millilitres("6"), List.of()),
            new OriginState(101L, "SS101", "aliquot", millilitres("4"), List.of()));

    List<ApiExtraField> fields =
        POOL.build(
                request(
                    null,
                    requestOrigin(100, millilitres("2")),
                    requestOrigin(101, millilitres("3"))),
                twins,
                KEYS,
                TODAY)
            .getNewSample()
            .getExtraFields();

    assertEquals(
        2,
        fields.stream().map(ApiExtraField::getName).distinct().count(),
        "a record cannot hold two fields with the same name");
  }

  @Test
  void takeAllLeavesNoAmountForTheCallerToChoose() {
    assertTrue(POOL.takesAmount(request(null, requestOrigin(100, millilitres("2")))));
    assertEquals(false, POOL.takesAmount(request(true, requestOrigin(100, null))));
    assertTrue(POOL.emptiesOrigin(request(true, requestOrigin(100, null))));
  }
}
