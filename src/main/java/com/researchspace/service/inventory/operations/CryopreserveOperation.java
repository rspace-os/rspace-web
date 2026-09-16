package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import java.math.BigDecimal;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

/** Cryopreserve: freezes part of a subsample down, optionally recording the cryomedium used. */
@Component
public class CryopreserveOperation
    extends CreatingOperation<ApiInventoryOperationRequests.Cryopreserve> {

  static final String CRYOMEDIUM_FIELD_KEY = "operations.cryopreserve.cryomediumField";

  /** Above this, the sample is not cryopreserved in any useful sense. */
  private static final BigDecimal MAX_CELSIUS = new BigDecimal("-18");

  @Override
  public String key() {
    return "cryopreserve";
  }

  @Override
  protected String linkRelation() {
    return "IsDerivedFrom";
  }

  @Override
  protected String linkFieldNameKey() {
    return "operations.cryopreserve.linkFieldName";
  }

  @Override
  public void validate(ApiInventoryOperationRequests.Cryopreserve request, Errors errors) {
    super.validate(request, errors);
    OperationQuantityRules.temperature(
        request.getStorageTemp(), "storageTemp", null, MAX_CELSIUS, errors);
  }

  @Override
  protected ApiQuantityInfo storageTemp(ApiInventoryOperationRequests.Cryopreserve request) {
    return request.getStorageTemp();
  }

  @Override
  protected List<ApiExtraField> textFields(
      ApiInventoryOperationRequests.Cryopreserve request,
      List<OriginState> origins,
      LabelResolver labels) {
    return StringUtils.isBlank(request.getCryomedium())
        ? List.of()
        : List.of(
            OperationFieldNames.text(
                labels.resolve(CRYOMEDIUM_FIELD_KEY),
                CRYOMEDIUM_FIELD_KEY,
                request.getCryomedium()));
  }
}
