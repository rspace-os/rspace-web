package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.InventoryOperationsApi;
import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.service.inventory.InventoryOperationManager;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Thin coordinator endpoint for configured Inventory operations. Validates the request, then
 * delegates to the transactional {@link InventoryOperationManager}, which performs the whole effect
 * atomically. No per-operation logic lives here (see DevDocs/adr/0006).
 */
@ApiController
public class InventoryOperationsApiController extends BaseApiInventoryController
    implements InventoryOperationsApi {

  @Autowired InventoryOperationManager inventoryOperationManager;
  @Autowired InventoryOperationPostValidator operationPostValidator;
  @Autowired InventoryOperationConfigRegistry operationConfigs;

  @Override
  public ApiSampleWithFullSubSamples performOperation(
      @RequestBody @Valid ApiInventoryOperationPost request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    inputValidator.validate(request, operationPostValidator, errors);
    // Live-state checks (DevDocs/adr/0010, DevDocs/adr/0015): every origin must currently hold
    // something, the amount taken may not exceed what an origin holds, and an origin-emptying
    // operation (e.g. Destroy) must take exactly what the origin holds. These need each origin's
    // live quantity, which the stateless structural validator cannot load, so they run here where
    // the user (hence read permission) is available. Only when the structural checks passed, so
    // every origin has a valid id to load and the operation type is known. Same 400/BindException
    // contract as the other rules.
    // The read runs in its own transaction, separate from the later performOperation mutation, so
    // under concurrency these checks are advisory: they can act on a slightly stale quantity. That
    // is safe because registerApiSubSampleUsage subtracts and clamps at zero (an origin can only
    // ever decrease, never go negative), so the worst case is an origin ending at zero rather than
    // a 400.
    if (!errors.hasErrors()) {
      boolean emptiesOrigin =
          operationConfigs
              .get(request.getOperationType())
              .map(config -> config.effect().emptiesOrigin())
              .orElse(false);
      int index = 0;
      for (ApiInventoryOperationOriginUpdate origin : request.getOrigins()) {
        errors.pushNestedPath(String.format("origins[%d]", index++));
        // try/finally so the nested-path stack is always restored, even if getApiSubSampleById
        // throws
        // (missing origin / permission edge cases); otherwise a thrown read would leave the
        // BindingResult's path stack unbalanced.
        try {
          ApiSubSample current = subSampleApiMgr.getApiSubSampleById(origin.getId(), user);
          if (InventoryOperationPostValidator.originHoldsNothing(current.getQuantity())) {
            errors.rejectValue(
                "id",
                "errors.inventory.operation.originEmpty",
                "An origin subsample that currently holds nothing cannot be operated on.");
          } else if (InventoryOperationPostValidator.amountTakenExceedsOrigin(
              origin.getAmountTaken(), current.getQuantity())) {
            errors.rejectValue(
                "amountTaken",
                "errors.inventory.operation.amountTakenExceedsOrigin",
                "Cannot take more from an origin than it currently holds.");
          } else if (emptiesOrigin
              && !InventoryOperationPostValidator.amountTakenEmptiesOrigin(
                  origin.getAmountTaken(), current.getQuantity())) {
            errors.rejectValue(
                "amountTaken",
                "errors.inventory.operation.mustEmptyOrigin",
                "This operation must take the origin's entire remaining quantity.");
          }
        } finally {
          errors.popNestedPath();
        }
      }
    }
    throwBindExceptionIfErrors(errors);
    return inventoryOperationManager.performOperation(request, user);
  }
}
