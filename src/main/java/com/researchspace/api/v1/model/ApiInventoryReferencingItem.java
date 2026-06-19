package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Single row of a referencing-items response (an item that links to the requested target). */
@Data
@NoArgsConstructor
@JsonPropertyOrder({
  "sourceGlobalId",
  "sourceName",
  "sourceType",
  "relationType",
  "versionPin",
  "modifiedAt"
})
public class ApiInventoryReferencingItem {

  @JsonProperty("sourceGlobalId")
  private String sourceGlobalId;

  @JsonProperty("sourceName")
  private String sourceName;

  /** Item type of the source: SAMPLE / SUBSAMPLE / CONTAINER / INSTRUMENT */
  @JsonProperty("sourceType")
  private String sourceType;

  @JsonProperty("relationType")
  private String relationType;

  @JsonProperty("versionPin")
  private Long versionPin;

  @JsonProperty("modifiedAt")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long modifiedAtMillis;
}
