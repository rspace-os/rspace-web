package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.views.CompositeRecordOperationResult;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@JsonPropertyOrder(
    value = {"numFilesInput", "numFilesSucceed", "numFilesFailed", "fileInfoDetails", "_links"})
public class ApiExternalStorageOperationResult extends LinkableApiObject {

  @JsonProperty("fileInfoDetails")
  private Set<ApiExternalStorageOperationInfo> fileInfoDetails = new TreeSet<>();

  @JsonProperty("numFilesInput")
  private Integer numFilesInput;

  @JsonProperty("numFilesSucceed")
  private Integer numFilesSucceed;

  @JsonProperty("numFilesFailed")
  private Integer numFilesFailed;

  public boolean add(ApiExternalStorageOperationInfo resultInfo) {
    return fileInfoDetails.add(resultInfo);
  }

  public boolean addAll(Set<ApiExternalStorageOperationInfo> resultInfo) {
    return fileInfoDetails.addAll(resultInfo);
  }

  @JsonIgnore
  public Set<ApiExternalStorageOperationInfo> getSucceededRecords() {
    return fileInfoDetails.stream()
        .filter(info -> info.getSucceeded())
        .collect(Collectors.toUnmodifiableSet());
  }

  @JsonIgnore
  public Set<Long> getSucceededRecordIds() {
    return fileInfoDetails.stream()
        .filter(info -> info.getSucceeded())
        .map(i -> i.getRecordId())
        .collect(Collectors.toUnmodifiableSet());
  }

  @JsonIgnore
  public Set<ApiExternalStorageOperationInfo> getFailedRecords() {
    return fileInfoDetails.stream()
        .filter(info -> !info.getSucceeded())
        .collect(Collectors.toUnmodifiableSet());
  }

  public Set<ApiExternalStorageOperationInfo> getFileInfoDetails() {
    return Collections.unmodifiableSet(fileInfoDetails);
  }

  public Integer getNumFilesInput() {
    return fileInfoDetails.size();
  }

  public Integer getNumFilesSucceed() {
    return getSucceededRecords().size();
  }

  public Integer getNumFilesFailed() {
    return getFailedRecords().size();
  }

  public static ApiExternalStorageOperationResult of(
      CompositeRecordOperationResult<EcatMediaFile> compositeResult) {
    ApiExternalStorageOperationResult result = new ApiExternalStorageOperationResult();
    for (Entry<EcatMediaFile, String> currentEntry :
        compositeResult.getReasonByRecordMap().entrySet()) {
      result.add(
          new ApiExternalStorageOperationInfo(
              currentEntry.getKey().getId(),
              currentEntry.getKey().getFileName(),
              StringUtils.isBlank(currentEntry.getValue()),
              currentEntry.getValue()));
    }
    return result;
  }
}
