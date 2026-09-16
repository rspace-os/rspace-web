package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import org.springframework.stereotype.Component;

/**
 * Pool: combines part of several subsamples into one new sample.
 *
 * <p>The only multi-origin operation. Either each origin says how much it contributes, or {@code
 * takeAll} pours every origin out completely, in which case the amounts are the origins' own live
 * quantities read at processing time, exactly as Destroy does.
 */
@Component
public class PoolOperation extends CreatingOperation<ApiInventoryOperationRequests.Pool> {

  @Override
  public String key() {
    return "pool";
  }

  @Override
  public boolean requiresMultiple() {
    return true;
  }

  @Override
  public boolean takesAmount(ApiInventoryOperationRequests.Pool request) {
    return !request.takesAll();
  }

  @Override
  public boolean emptiesOrigin(ApiInventoryOperationRequests.Pool request) {
    return request.takesAll();
  }

  @Override
  public String amountNotApplicableCode() {
    // Pool takes a chosen amount unless the caller asked for takeAll, so this is the only way an
    // amount can be refused here, and saying which choice refused it is what the caller can act on.
    return "errors.inventory.operation.amountTakenNotWithTakeAll";
  }

  @Override
  protected String linkRelation() {
    return "HasPart";
  }

  @Override
  protected String linkFieldNameKey() {
    return "operations.pool.linkFieldName";
  }

  @Override
  protected ApiQuantityInfo amountTakenFrom(
      ApiInventoryOperationRequests.Pool request, OriginState origin, int index) {
    return request.takesAll()
        ? Amounts.wholeOf(origin)
        : super.amountTakenFrom(request, origin, index);
  }
}
