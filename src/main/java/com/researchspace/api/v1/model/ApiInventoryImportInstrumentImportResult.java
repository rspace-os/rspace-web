package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.apiutils.ApiError;
import lombok.Getter;

@Getter
@JsonPropertyOrder({"templateResult", "templateCreated"})
public class ApiInventoryImportInstrumentImportResult extends ApiInventoryImportPartialResult {

  @JsonProperty("templateResult")
  private ApiInventoryBulkOperationRecordResult template;

  @JsonProperty("templateCreated")
  private boolean templateCreated;

  public ApiInventoryImportInstrumentImportResult() {
    super(ApiInventoryRecordInfo.ApiInventoryRecordType.INSTRUMENT);
  }

  public void addCreatedTemplateResult(ApiInventoryRecordInfo template) {
    this.template = new ApiInventoryBulkOperationRecordResult(template, null);
    this.templateCreated = true;
  }

  public void addExistingTemplateResult(ApiInventoryRecordInfo template) {
    this.template = new ApiInventoryBulkOperationRecordResult(template, null);
    this.templateCreated = false;
  }

  public void addTemplateError(ApiError error) {
    this.template = new ApiInventoryBulkOperationRecordResult(null, error);
    templateCreated = false;
  }

  public ApiInventoryImportInstrumentImportResult copyWithTemplateResultAndImportIdsOnly() {
    ApiInventoryImportInstrumentImportResult copy = new ApiInventoryImportInstrumentImportResult();
    copy.template = template;
    copy.templateCreated = templateCreated;
    copyImportIdMaps(copy);
    return copy;
  }
}
