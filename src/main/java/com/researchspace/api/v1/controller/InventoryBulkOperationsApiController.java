package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.InventoryBulkOperationsApi;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationPost;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationPost.BulkApiOperationType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.InventoryBulkOperationStatus;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.model.User;
import com.researchspace.service.inventory.InventoryBulkOperationApiManager;
import com.researchspace.service.inventory.impl.InventoryBulkOperationHandler;
import com.researchspace.service.inventory.impl.InventoryBulkOperationHandler.InventoryBulkOperationException;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.SmartValidator;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;

@ApiController
public class InventoryBulkOperationsApiController extends BaseApiInventoryController
    implements InventoryBulkOperationsApi {

  @Autowired private InventoryBulkOperationApiManager bulkOperationManager;

  @Autowired private InventoryBulkOperationHandler bulkOperationHandler;

  @Autowired private SmartValidator mvcValidator;
  @Autowired private ApiControllerAdvice apiControllerAdvice;

  @Setter
  @Getter
  @AllArgsConstructor
  public static class InventoryBulkOperationConfig {

    private BulkApiOperationType operationType;
    private List<ApiInventoryRecordInfo> records = new ArrayList<>();

    private boolean onErrorStopWithException = true;
    private User user;

    public InventoryBulkOperationConfig(ApiInventoryBulkOperationPost apiRequest, User user) {
      operationType = apiRequest.getOperationType();
      records = apiRequest.getRecords();
      onErrorStopWithException = apiRequest.isRollbackOnError();
      this.user = user;
    }
  }

  @Override
  public ApiInventoryBulkOperationResult executeBulkOperation(
      @RequestBody @Valid ApiInventoryBulkOperationPost bulkApiRequest,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    throwBindExceptionIfErrors(errors);

    InventoryBulkOperationConfig bulkOpConfig =
        new InventoryBulkOperationConfig(bulkApiRequest, user);
    if (bulkApiRequest.isRollbackOnError()) {
      ApiInventoryBulkOperationResult initialValidationResult =
          validateRecordsBeforeRunningBulkOperation(bulkOpConfig.getRecords(), errors);
      if (initialValidationResult.getErrorCount() > 0) {
        return initialValidationResult;
      }
      return runBulkOperationInSingleTransaction(bulkOpConfig);
    }
    return runBulkOperationInSeparateTransactions(bulkOpConfig);
  }

  /**
   * Runs Spring validation against records considered for bulk operation.
   *
   * @return result object with 'results' list that stops on first found validation error
   */
  private ApiInventoryBulkOperationResult validateRecordsBeforeRunningBulkOperation(
      List<ApiInventoryRecordInfo> records, BindingResult errors) {

    ApiInventoryBulkOperationResult result = new ApiInventoryBulkOperationResult();
    for (ApiInventoryRecordInfo recInfo : records) {
      BindException recordErrors = new BindException(recInfo, "record");
      mvcValidator.validate(recInfo, recordErrors);
      if (recordErrors.hasErrors()) {
        result.addError(apiControllerAdvice.getApiErrorFromBindException(recordErrors));
        result.setErrorStatusAndResetSuccessCount(InventoryBulkOperationStatus.PREVALIDATION_ERROR);
        break;
      } else {
        result.addSuccessResult(null);
      }
    }
    return result;
  }

  /** Calls bulkOperationHandler through bulk manager, i.e. within single transaction */
  private ApiInventoryBulkOperationResult runBulkOperationInSingleTransaction(
      InventoryBulkOperationConfig bulkOperationConfig) {

    ApiInventoryBulkOperationResult result = null;
    try {
      result = bulkOperationManager.runBulkOperation(bulkOperationConfig);
      result.setStatus(InventoryBulkOperationStatus.COMPLETED);
    } catch (InventoryBulkOperationException boe) {
      result = boe.getPartialResult();
      result.setErrorStatusAndResetSuccessCount(InventoryBulkOperationStatus.REVERTED_ON_ERROR);
    }
    return result;
  }

  /**
   * Calls bulkOperationHandler directly. That will process every record in a separate transaction
   */
  private ApiInventoryBulkOperationResult runBulkOperationInSeparateTransactions(
      InventoryBulkOperationConfig bulkOperationConfig) {

    ApiInventoryBulkOperationResult result =
        bulkOperationHandler.runBulkOperation(bulkOperationConfig);
    result.setStatus(InventoryBulkOperationStatus.COMPLETED);
    return result;
  }
}
