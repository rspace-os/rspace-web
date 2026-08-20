package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.InventoryOperationPostValidatorTest.aliquotRequest;
import static com.researchspace.api.v1.controller.InventoryOperationPostValidatorTest.destroyRequest;
import static com.researchspace.api.v1.controller.InventoryOperationPostValidatorTest.millilitres;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.dtos.DTOControllerValidatorImpl;
import com.researchspace.service.inventory.InventoryOperationManager;
import com.researchspace.service.inventory.SubSampleApiManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;

/**
 * Unit coverage for the controller's live-state pass (DevDocs/adr/0010, DevDocs/adr/0015): the
 * checks that need each origin's current quantity, which the stateless structural validator cannot
 * load. Uses the real validators over mocked managers, so every request shape runs the exact
 * production validation chain without a Spring context.
 */
class InventoryOperationsApiControllerTest {

  private final InventoryOperationsApiController controller =
      new InventoryOperationsApiController();
  private final SubSampleApiManager subSampleApiMgr = mock(SubSampleApiManager.class);
  private final InventoryOperationManager operationManager = mock(InventoryOperationManager.class);
  private final User user = mock(User.class);

  @BeforeEach
  void wireController() {
    controller.subSampleApiMgr = subSampleApiMgr;
    controller.inputValidator = new DTOControllerValidatorImpl();
    controller.operationPostValidator = InventoryOperationPostValidatorTest.newValidator();
    controller.operationConfigs = new InventoryOperationConfigRegistry();
    controller.inventoryOperationManager = operationManager;
  }

  private void originHolds(long originId, ApiSubSample current) {
    when(subSampleApiMgr.getApiSubSampleById(originId, user)).thenReturn(current);
  }

  private static ApiSubSample subSampleHolding(String millilitresHeld) {
    ApiSubSample subSample = new ApiSubSample();
    subSample.setQuantity(millilitresHeld == null ? null : millilitres(millilitresHeld));
    return subSample;
  }

  private BindException performExpectingRejection(ApiInventoryOperationPost request) {
    BindException rejection =
        assertThrows(
            BindException.class,
            () ->
                controller.performOperation(
                    request, new BeanPropertyBindingResult(request, "request"), user));
    verifyNoInteractions(operationManager);
    return rejection;
  }

  @Test
  void validRequestReachesTheManagerAndReturnsItsResult() throws Exception {
    ApiInventoryOperationPost request = aliquotRequest();
    originHolds(100L, subSampleHolding("5"));
    ApiSampleWithFullSubSamples created = new ApiSampleWithFullSubSamples("Aliquots");
    when(operationManager.performOperation(request, user)).thenReturn(created);

    ApiSampleWithFullSubSamples returned =
        controller.performOperation(
            request, new BeanPropertyBindingResult(request, "request"), user);

    assertSame(created, returned);
    verify(operationManager).performOperation(request, user);
  }

  @Test
  void rejectsOperatingOnAnOriginThatCurrentlyHoldsNothing() {
    for (ApiSubSample empty : List.of(subSampleHolding("0"), subSampleHolding(null))) {
      ApiInventoryOperationPost request = aliquotRequest();
      originHolds(100L, empty);
      BindException rejection = performExpectingRejection(request);
      assertEquals(
          "errors.inventory.operation.originEmpty",
          rejection.getFieldErrors("origins[0].id").get(0).getCode());
    }
  }

  @Test
  void rejectsTakingMoreThanTheOriginCurrentlyHolds() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.getOrigins().get(0).setAmountTaken(millilitres("6"));
    originHolds(100L, subSampleHolding("5"));
    BindException rejection = performExpectingRejection(request);
    assertEquals(
        "errors.inventory.operation.amountTakenExceedsOrigin",
        rejection.getFieldErrors("origins[0].amountTaken").get(0).getCode());
  }

  @Test
  void rejectsOriginEmptyingOperationThatTakesLessThanTheOriginHolds() {
    ApiInventoryOperationPost request = destroyRequest();
    request.getOrigins().get(0).setAmountTaken(millilitres("3"));
    originHolds(100L, subSampleHolding("5"));
    BindException rejection = performExpectingRejection(request);
    assertEquals(
        "errors.inventory.operation.mustEmptyOrigin",
        rejection.getFieldErrors("origins[0].amountTaken").get(0).getCode());
  }

  @Test
  void allowsOriginEmptyingOperationTakingExactlyWhatTheOriginHolds() throws Exception {
    ApiInventoryOperationPost request = destroyRequest();
    originHolds(100L, subSampleHolding("5"));

    controller.performOperation(request, new BeanPropertyBindingResult(request, "request"), user);

    verify(operationManager).performOperation(request, user);
  }

  @Test
  void structuralFailureShortCircuitsTheLiveChecks() {
    ApiInventoryOperationPost request = aliquotRequest();
    request.setOperationType("teleport");
    assertThrows(
        BindException.class,
        () ->
            controller.performOperation(
                request, new BeanPropertyBindingResult(request, "request"), user));
    verifyNoInteractions(operationManager);
    verify(subSampleApiMgr, never()).getApiSubSampleById(anyLong(), any());
  }
}
