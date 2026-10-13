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
   * Set only by {@link com.researchspace.service.inventory.csvimport.CsvLinkValueParser}: skips the
   * target check entirely, so CSV import stores the link whatever state its target is in, missing
   * and unreadable included (RSDEV-1354, ADR-0002). Not settable from JSON, so link create and
   * update payloads cannot set it; the CSV import endpoint, through the UI or the API, can.
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
