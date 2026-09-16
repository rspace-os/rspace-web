package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

/** Revive: thaws part of a cryopreserved subsample back into working stock. */
@Component
public class ReviveOperation extends CreatingOperation<ApiInventoryOperationRequests.Revive> {

  /** Refrigerated, the temperature revived stock is held at unless the caller says otherwise. */
  private static final BigDecimal DEFAULT_CELSIUS = new BigDecimal("4");

  private static final BigDecimal MIN_CELSIUS = DEFAULT_CELSIUS;

  /** Above this is sterilisation, not incubation. */
  private static final BigDecimal MAX_CELSIUS = new BigDecimal("120");

  @Override
  public String key() {
    return "revive";
  }

  @Override
  protected String linkRelation() {
    return "IsDerivedFrom";
  }

  @Override
  protected String linkFieldNameKey() {
    return "operations.revive.linkFieldName";
  }

  @Override
  public void validate(ApiInventoryOperationRequests.Revive request, Errors errors) {
    super.validate(request, errors);
    OperationQuantityRules.temperature(
        request.getStorageTemp(), "storageTemp", MIN_CELSIUS, MAX_CELSIUS, errors);
  }

  @Override
  protected ApiQuantityInfo storageTemp(ApiInventoryOperationRequests.Revive request) {
    return request.getStorageTemp() == null
        ? new ApiQuantityInfo(DEFAULT_CELSIUS, RSUnitDef.CELSIUS)
        : request.getStorageTemp();
  }
}
