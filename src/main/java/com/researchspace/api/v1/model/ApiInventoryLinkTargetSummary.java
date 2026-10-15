package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Summary of an InventoryLink's target as seen at read time. */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"globalId", "name", "type", "deleted", "readable", "pinnedVersionFound"})
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

  /**
   * True only when the actor can read the target. False deliberately conflates "unreadable" with
   * "nonexistent": both produce field-for-field identical payloads so callers cannot probe which
   * records exist (see DevDocs/adr/0002-link-target-state-non-disclosure.md).
   */
  @JsonProperty("readable")
  private boolean readable;

  /**
   * Whether the version pinned in the requested id has a recorded revision (RSDEV-1530). Null, and
   * so omitted, unless the id carried a {@code vN} suffix and the actor can read the target: a
   * redacted summary says nothing about versions (ADR-0002).
   */
  @JsonProperty("pinnedVersionFound")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean pinnedVersionFound;
}
