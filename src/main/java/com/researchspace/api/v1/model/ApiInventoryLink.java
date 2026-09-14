package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.field.InventoryLink;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** API payload representing a Link extra-field's target/relation metadata. */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"relationType", "targetGlobalId", "versionPin"})
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiInventoryLink {

  @JsonProperty("relationType")
  private String relationType;

  @JsonProperty("targetGlobalId")
  private String targetGlobalId;

  @JsonProperty("versionPin")
  private Long versionPin;

  /**
   * Set only by the CSV importer: store the link even when its target is missing or unreadable on
   * this server, so an exported dangling link re-imports as a dangling link (RSDEV-1354). Never
   * settable from JSON, so API clients cannot bypass the target check.
   */
  // ponytail: a boolean on the DTO instead of threading an "import" flag through three managers;
  // promote to a dedicated createLink variant if a second lenient caller appears
  @JsonIgnore @EqualsAndHashCode.Exclude @ToString.Exclude private boolean skipTargetCheck;

  public ApiInventoryLink(InventoryLink link) {
    this.relationType = link.getRelationType();
    this.targetGlobalId = link.getTargetGlobalId();
    this.versionPin = link.getVersionPin();
  }

  /**
   * Returns the version pin derived from any "vN" suffix on the targetGlobalId. Returns null if the
   * id has no suffix or is unparseable.
   */
  public Long derivedVersionPin() {
    if (targetGlobalId == null) {
      return null;
    }
    try {
      GlobalIdentifier gid = new GlobalIdentifier(targetGlobalId);
      return gid.hasVersionId() ? gid.getVersionId() : null;
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
