/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** SampleSearchResults */
@Data
@EqualsAndHashCode
@JsonPropertyOrder({"revisionsCount", "revisions"})
public class ApiInventoryRecordRevisionList {

  @JsonPropertyOrder({"revisionId", "revisionType", "record"})
  @AllArgsConstructor
  @NoArgsConstructor
  @Getter
  public static class ApiInventoryRecordRevision {
    @JsonProperty("record")
    ApiInventoryRecordInfo record;

    @JsonProperty("revisionId")
    Long revisionId;

    @JsonProperty("revisionType")
    String revisionType;
  }

  @JsonProperty("revisions")
  private List<ApiInventoryRecordRevision> revisions = new ArrayList<>();

  @JsonProperty("revisionsCount")
  private Integer revisionsCount;
}
