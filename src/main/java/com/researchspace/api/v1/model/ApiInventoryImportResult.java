package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.InventoryBulkOperationStatus;
import com.researchspace.model.User;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonPropertyOrder({
  "status",
  "defaultContainer",
  "containerResults",
  "sampleResults",
  "subSampleResults",
})
public class ApiInventoryImportResult {

  @JsonProperty("status")
  private InventoryBulkOperationStatus status = InventoryBulkOperationStatus.NOT_STARTED;

  @JsonProperty("defaultContainer")
  private ApiContainer defaultContainer;

  @JsonProperty("containerResults")
  private ApiInventoryImportPartialResult containerResult;

  @JsonProperty("sampleResults")
  private ApiInventoryImportSampleImportResult sampleResult;

  @JsonProperty("subSampleResults")
  private ApiInventoryImportSubSampleImportResult subSampleResult;

  @JsonIgnore private String containerCsvFilename;
  @JsonIgnore private String sampleCsvFilename;
  @JsonIgnore private String subSampleCsvFilename;
  @JsonIgnore private User currentUser;

  public ApiInventoryImportResult(User user) {
    this.currentUser = user;
  }

  /** Convenience constructor setting all subresults */
  public ApiInventoryImportResult(
      ApiInventoryImportPartialResult containerResult,
      ApiInventoryImportSampleImportResult sampleResult,
      ApiInventoryImportSubSampleImportResult subSampleResult,
      User user) {

    this(user);
    this.containerResult = containerResult;
    this.sampleResult = sampleResult;
    this.subSampleResult = subSampleResult;
  }

  public void setStatusForAllResults(InventoryBulkOperationStatus status) {
    this.status = status;
    Stream.of(containerResult, sampleResult, subSampleResult)
        .filter(Objects::nonNull)
        .forEach(result -> result.setStatus(status));
  }

  public boolean hasPrevalidationError() {
    return (containerResult != null
            && containerResult.getStatus().equals(InventoryBulkOperationStatus.PREVALIDATION_ERROR))
        || (sampleResult != null
            && sampleResult.getStatus().equals(InventoryBulkOperationStatus.PREVALIDATION_ERROR))
        || (subSampleResult != null
            && subSampleResult
                .getStatus()
                .equals(InventoryBulkOperationStatus.PREVALIDATION_ERROR));
  }
}
