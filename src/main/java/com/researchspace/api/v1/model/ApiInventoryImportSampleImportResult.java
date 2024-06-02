package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.apiutils.ApiError;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;

@Getter
@JsonPropertyOrder({"templateResult", "templateCreated"})
public class ApiInventoryImportSampleImportResult extends ApiInventoryImportPartialResult {

  @JsonProperty("templateResult")
  private ApiInventoryBulkOperationRecordResult template;

  @JsonProperty("templateCreated")
  private boolean templateCreated;

  @JsonIgnore private Set<Integer> sampleResultNumberWithNonDefaultSubSamples = new HashSet<>();

  public ApiInventoryImportSampleImportResult() {
    super(ApiInventoryRecordInfo.ApiInventoryRecordType.SAMPLE);
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

  public ApiInventoryImportSampleImportResult copyWithTemplateResultAndImportIdsOnly() {
    ApiInventoryImportSampleImportResult copy = new ApiInventoryImportSampleImportResult();
    copy.template = template;
    copy.templateCreated = templateCreated;
    copyImportIdMaps(copy);
    return copy;
  }

  public boolean addSampleRecordNumberWithNonDefaultSubSample(Integer resultNumber) {
    return sampleResultNumberWithNonDefaultSubSamples.add(resultNumber);
  }

  public boolean isSampleRecordNumberWithNonDefaultSubSample(Integer resultNumber) {
    return sampleResultNumberWithNonDefaultSubSamples.contains(resultNumber);
  }
}
