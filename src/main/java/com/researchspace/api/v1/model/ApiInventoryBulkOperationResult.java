package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.apiutils.ApiError;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
public class ApiInventoryBulkOperationResult {

  @AllArgsConstructor
  @NoArgsConstructor
  @Getter
  @Setter
  public static class ApiInventoryBulkOperationRecordResult {
    @JsonProperty("record")
    ApiInventoryRecordInfo record;

    @JsonProperty("error")
    ApiError error;
  }

  public static enum InventoryBulkOperationStatus {
    NOT_STARTED,
    PREVALIDATED,
    PREVALIDATION_ERROR,
    REVERTED_ON_ERROR,
    COMPLETED
  }

  @JsonProperty("results")
  private List<ApiInventoryBulkOperationRecordResult> results = new ArrayList<>();

  @JsonProperty("successCount")
  private int successCount;

  @JsonProperty("successCountBeforeFirstError")
  private int successCountBeforeFirstError;

  @JsonProperty("errorCount")
  private int errorCount;

  @JsonProperty("status")
  private InventoryBulkOperationStatus status = InventoryBulkOperationStatus.NOT_STARTED;

  public ApiInventoryBulkOperationRecordResult addSuccessResult(ApiInventoryRecordInfo record) {
    ApiInventoryBulkOperationRecordResult recordResult =
        new ApiInventoryBulkOperationRecordResult(record, null);
    results.add(recordResult);
    successCount++;
    if (errorCount == 0) {
      successCountBeforeFirstError = successCount;
    }
    return recordResult;
  }

  public void addAllSuccessResult(List<ApiInventoryRecordInfo> recordList) {
    for (ApiInventoryRecordInfo record : recordList) {
      results.add(new ApiInventoryBulkOperationRecordResult(record, null));
    }
    successCount += recordList.size();
    if (errorCount == 0) {
      successCountBeforeFirstError = successCount;
    }
  }

  public void addError(ApiError error) {
    results.add(new ApiInventoryBulkOperationRecordResult(null, error));
    errorCount++;
  }

  public void addErrorWithRecord(ApiInventoryRecordInfo operationResult, ApiError error) {
    ApiInventoryBulkOperationRecordResult result =
        new ApiInventoryBulkOperationRecordResult(operationResult, error);
    result.record = operationResult;
    result.error = error;
    results.add(result);
    errorCount++;
  }

  public void changeIntoErrorResult(ApiInventoryBulkOperationRecordResult result, ApiError error) {
    result.record = null;
    result.error = error;
    errorCount++;
    successCount--;
    resetSuccessCountBeforeFirstError();
  }

  private void resetSuccessCountBeforeFirstError() {
    for (int i = 0; i < results.size(); i++) {
      if (results.get(i).getError() != null) {
        successCountBeforeFirstError = i;
        return;
      }
    }
    successCountBeforeFirstError = successCount;
  }

  public void setStatus(InventoryBulkOperationStatus status) {
    this.status = status;
  }

  @JsonIgnore
  public void setErrorStatusAndResetSuccessCount(InventoryBulkOperationStatus failureStatus) {
    status = failureStatus;
    successCount = 0;
  }
}
