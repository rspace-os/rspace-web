package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/** Information about resource generated after copy/moving a single item to an external storage */
@Getter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false, of = "recordId")
@JsonPropertyOrder(value = {"recordId", "fileName", "succeeded", "reason"})
public class ApiExternalStorageOperationInfo
    implements Comparable<ApiExternalStorageOperationInfo> {

  @JsonProperty("recordId")
  private Long recordId;

  @JsonProperty("fileName")
  private String fileName;

  @JsonProperty("succeeded")
  private Boolean succeeded;

  @JsonInclude(Include.NON_NULL)
  @JsonProperty("reason")
  private String reason;

  public ApiExternalStorageOperationInfo(
      Long recordId, String fileName, Boolean succeeded, String reason) {
    this.recordId = recordId;
    this.fileName = fileName;
    this.succeeded = succeeded;
    this.reason = reason;
  }

  public ApiExternalStorageOperationInfo(Long recordId, String fileName, Boolean succeeded) {
    this(recordId, fileName, succeeded, null);
  }

  @Override
  public int compareTo(@NotNull ApiExternalStorageOperationInfo o) {
    return recordId.compareTo(o.getRecordId());
  }
}
