package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

/**
 * Destroy: empties each origin and creates nothing, recording the disposal date on the origin
 * itself. The only terminal operation, and the only one with no request values of its own.
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
  public void validate(ApiInventoryOperationRequests.Destroy request, Errors errors) {
    // Nothing beyond the origin rules every operation shares: Destroy takes no values.
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
      // The date is the CALLER's, not the server's: a disposal recorded late in the evening in
      // one timezone would otherwise be dated tomorrow.
      update.setExtraFields(
          List.of(
              OperationFieldNames.text(
                  labels.resolve(DISPOSED_FIELD_KEY), DISPOSED_FIELD_KEY, today.toString())));
      built.getOrigins().add(update);
    }
    return built;
  }
}
