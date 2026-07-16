package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.InventoryOperationsApi;
import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.service.inventory.InventoryOperationManager;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Thin coordinator endpoint for configured Inventory operations. Validates the request, then
 * delegates to the transactional {@link InventoryOperationManager}, which performs the whole effect
 * atomically. No per-operation logic lives here (see adr/0001).
 */
@ApiController
public class InventoryOperationsApiController extends BaseApiInventoryController
    implements InventoryOperationsApi {

  @Autowired private InventoryOperationManager inventoryOperationManager;
  @Autowired private InventoryOperationPostValidator operationPostValidator;

  @Override
  public ApiSampleWithFullSubSamples performOperation(
      @RequestBody @Valid ApiInventoryOperationPost request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    inputValidator.validate(request, operationPostValidator, errors);
    // Over-removal check (adr/0005): reject taking more than an origin holds. This needs each
    // origin's live quantity, which the stateless structural validator cannot load, so it runs here
    // where the user (hence read permission) is available. Only when the structural checks passed,
    // so
    // every origin has a valid id to load. Same 400/BindException contract as the other rules.
    if (!errors.hasErrors()) {
      int index = 0;
      for (ApiInventoryOperationOriginUpdate origin : request.getOrigins()) {
        errors.pushNestedPath(String.format("origins[%d]", index++));
        ApiSubSample current = subSampleApiMgr.getApiSubSampleById(origin.getId(), user);
        if (InventoryOperationPostValidator.amountTakenExceedsOrigin(
            origin.getAmountTaken(), current.getQuantity())) {
          errors.rejectValue(
              "amountTaken",
              "errors.inventory.operation.amountTakenExceedsOrigin",
              "Cannot take more from an origin than it currently holds");
        }
        errors.popNestedPath();
      }
    }
    throwBindExceptionIfErrors(errors);
    return inventoryOperationManager.performOperation(request, user);
  }
}
