package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.InventoryBulkOperationsApi;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationPost;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationPost.BulkApiOperationType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.ApiInventoryBulkOperationRecordResult;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.InventoryBulkOperationStatus;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.model.User;
import com.researchspace.service.inventory.InventoryBulkOperationApiManager;
import com.researchspace.service.inventory.impl.InventoryBulkOperationHandler;
import com.researchspace.service.inventory.impl.InventoryBulkOperationHandler.InventoryBulkOperationException;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
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

  @Autowired private InstrumentsApiController instrumentsApiController;

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
    pushExternalMetadataUpdates(result, bulkOperationConfig);
    return result;
  }

  /**
   * Sends the remapped PIDINST metadata of every instrument this batch changed to its provider
   * (RSDEV-1251, ADR 0008), once the batch's transaction has committed.
   *
   * <p>Here rather than in the controller methods the batch re-enters, because those push from
   * inside the batch's own transaction and {@code InventoryIdentifierExternalUpdateService}
   * declines that: a later record failing would roll the change back locally and leave the provider
   * holding metadata RSpace no longer has. The web interface only ever transfers instruments this
   * way - {@code Search.transferRecords} sends rollbackOnError true - so without a push out here
   * the transfer half of the feature never reached a provider from the UI at all.
   *
   * <p>The per-record path needs nothing added: it commits each record before the controller
   * returns, so the push already runs with no transaction open.
   *
   * <p>Only the two operations that change what a provider holds, matching the single-record
   * controllers, and only a batch that committed: a reverted one changed nothing to send.
   */
  private void pushExternalMetadataUpdates(
      ApiInventoryBulkOperationResult result, InventoryBulkOperationConfig config) {

    boolean changesProviderMetadata =
        config.getOperationType() == BulkApiOperationType.UPDATE
            || config.getOperationType() == BulkApiOperationType.CHANGE_OWNER;
    if (!changesProviderMetadata || result.getStatus() != InventoryBulkOperationStatus.COMPLETED) {
      return;
    }
    result.getResults().stream()
        .map(ApiInventoryBulkOperationRecordResult::getRecord)
        .filter(ApiInstrument.class::isInstance)
        .map(ApiInstrument.class::cast)
        .forEach(
            instrument ->
                instrumentsApiController.pushExternalMetadataUpdates(instrument, config.getUser()));
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
