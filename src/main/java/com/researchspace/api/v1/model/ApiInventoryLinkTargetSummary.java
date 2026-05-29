package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Summary of an InventoryLink's target as seen at read time. */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"globalId", "name", "type", "deleted"})
public class ApiInventoryLinkTargetSummary {

  @JsonProperty("globalId")
  private String globalId;

  @JsonProperty("name")
  private String name;

  /** Item type, e.g. SAMPLE/SUBSAMPLE/CONTAINER/INSTRUMENT */
  @JsonProperty("type")
  private String type;

  @JsonProperty("deleted")
  private boolean deleted;
}
