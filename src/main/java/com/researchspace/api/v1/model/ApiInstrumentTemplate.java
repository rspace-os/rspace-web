package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.inventory.InstrumentTemplate;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

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
  "revisionId",
  "version",
  "historicalVersion",
  "instrumentsToUpdateCount",
  "fields",
  "extraFields",
  "sharedWith",
  "_links"
})
public class ApiInstrumentTemplate extends ApiInstrumentEntity {

  /**
   * Number of the current user's instruments created from an older version of this template.
   * Non-zero exactly when there are instruments the bulk-update endpoint would act on. Populated on
   * full detail responses only.
   */
  @JsonProperty("instrumentsToUpdateCount")
  private int instrumentsToUpdateCount;

  /** default constructor used by jackson deserializer */
  public ApiInstrumentTemplate() {
    super();
    setType(ApiInventoryRecordType.INSTRUMENT_TEMPLATE);
    super.setTemplate(true);
    super.setCanBeDeleted(true);
  }

  public ApiInstrumentTemplate(InstrumentTemplate instrumentTemplate) {
    super(instrumentTemplate);
    super.setCanBeDeleted(true); // always true for now
  }
}
