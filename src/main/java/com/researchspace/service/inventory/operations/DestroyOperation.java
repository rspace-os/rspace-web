package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Destroy: empties each origin and creates nothing, recording the disposal date on the origin
 * itself.
 */
@Component
public class DestroyOperation implements InventoryOperation<ApiInventoryOperationRequests.Destroy> {

  static final String DISPOSED_FIELD_KEY = "operations.destroy.disposedField";

  @Override
  public String key() {
    return "destroy";
  }

  @Override
  public boolean takesAmount(ApiInventoryOperationRequests.Destroy request) {
    return false;
  }

  @Override
  public boolean emptiesOrigin(ApiInventoryOperationRequests.Destroy request) {
    return true;
  }

  @Override
  public ApiInventoryOperationPost build(
      ApiInventoryOperationRequests.Destroy request,
      List<OriginState> origins,
      LabelResolver labels,
      LocalDate today) {
    ApiInventoryOperationPost built = new ApiInventoryOperationPost();
    built.setEmptiesOrigin(emptiesOrigin(request));
    for (OriginState origin : origins) {
      ApiInventoryOperationOriginUpdate update = new ApiInventoryOperationOriginUpdate();
      update.setId(origin.id());
      update.setAmountTaken(Amounts.wholeOf(origin));
      update.setExtraFields(
          List.of(
              OperationFieldNames.text(
                  labels.resolve(DISPOSED_FIELD_KEY), DISPOSED_FIELD_KEY, today.toString())));
      built.getOrigins().add(update);
    }
    return built;
  }
}
