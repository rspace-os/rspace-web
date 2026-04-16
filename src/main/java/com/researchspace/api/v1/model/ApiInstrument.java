/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.inventory.Instrument;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** API representation of an Inventory Sample, with information about SubSamples. */
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
  "quantity",
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
  "template",
  "revisionId",
  "version",
  "historicalVersion",
  "subSampleAlias",
  "subSamplesCount",
  "storageTempMin",
  "storageTempMax",
  "sampleSource",
  "expiryDate",
  "sharedWith",
  "fields",
  "extraFields",
  "subSamples",
  "_links"
})
public class ApiInstrument
    extends ApiInstrumentEntity { // TODO[nik]: IN PROGRESS----> fix the inheritance (check with
  // Samples)

  /** default constructor used by jackson deserializer */
  public ApiInstrument() {
    super();
    super.setType(ApiInventoryRecordType.INSTRUMENT);
    super.setTemplate(false);
    super.setCanBeDeleted(true);
  }

  public ApiInstrument(Instrument instrument) {
    super(instrument);
    super.setCanBeDeleted(!instrument.isStoredInContainer());
  }
}
