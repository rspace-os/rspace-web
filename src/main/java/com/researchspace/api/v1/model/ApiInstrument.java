/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.inventory.Instrument;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** API representation of an Inventory Instrument, with information about Instruments. */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonPropertyOrder({
  "id",
  "globalId",
  "name",
  "description",
  "created",
  "createdBy",
  "lastModified",
  "modifiedBy",
  "modifiedByFullName",
  "canBeDeleted",
  "deleted",
  "deletedDate",
  "iconId",
  "tags",
  "type",
  "attachments",
  "barcodes",
  "identifiers",
  "owner",
  "permittedActions",
  "sharingMode",
  "templateId",
  "templateVersion",
  "revisionId",
  "version",
  "historicalVersion",
  "fields",
  "extraFields",
  "sharedWith",
  "parentContainers",
  "parentLocation",
  "lastNonWorkbenchParent",
  "lastMoveDate",
  "storedInContainer",
  "skippedRelatedIdentifiers",
  "_links"
})
public class ApiInstrument extends ApiInstrumentEntity {

  @JsonProperty(value = "newTargetLocation", access = Access.WRITE_ONLY)
  private ApiTargetLocation newTargetLocation;

  @JsonProperty("parentContainers")
  private List<ApiContainerInfo> parentContainers = new ArrayList<>();

  @JsonProperty("parentLocation")
  private ApiContainerLocation parentLocation;

  @JsonProperty(value = "lastNonWorkbenchParent", access = Access.READ_ONLY)
  private ApiContainerInfo lastNonWorkbenchParent;

  @EqualsAndHashCode.Exclude
  @JsonProperty(value = "lastMoveDate", access = Access.READ_ONLY)
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long lastMoveDateMillis;

  /* location fields */
  @JsonProperty(value = "storedInContainer", access = Access.READ_ONLY)
  private boolean storedInContainer;

  /**
   * Set only by the PIDINST import (RSDEV-1528): the record's Measurement Technique and Calibration
   * related identifiers that could not become links, each with the reason. Absent from every other
   * response, ignored in any request; nothing about them is stored. Not READ_ONLY: that would stop
   * a Java client binding it from the response.
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  @JsonProperty("skippedRelatedIdentifiers")
  private List<ApiPidinstSkippedRelatedIdentifier> skippedRelatedIdentifiers = new ArrayList<>();

  /** default constructor used by jackson deserializer */
  public ApiInstrument() {
    super();
    super.setType(ApiInventoryRecordType.INSTRUMENT);
    super.setTemplate(false);
    super.setCanBeDeleted(true);
  }

  public ApiInstrument(Instrument instrument) {
    super(instrument);

    if (instrument.getParentLocation() != null) {
      parentLocation = new ApiContainerLocation(instrument.getParentLocation());
      populateParentContainers(instrument.getParentContainer());
    }
    if (instrument.getLastNonWorkbenchParent() != null) {
      setLastNonWorkbenchParent(new ApiContainerInfo(instrument.getLastNonWorkbenchParent()));
    }
    if (instrument.getLastMoveDate() != null) {
      setLastMoveDateMillis(Date.from(instrument.getLastMoveDate()).getTime());
    }
    setStoredInContainer(instrument.isStoredInContainer());

    super.setCanBeDeleted(!instrument.isStoredInContainer());
  }
}
