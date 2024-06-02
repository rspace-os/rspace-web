/** RSpace Inventory API Access your RSpace Inventory Sample Templates programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.inventory.Sample;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.Validate;

/** The SampleTemplate on which a Sample is based. Does not include fields. */
@Data
@EqualsAndHashCode(callSuper = true)
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
  "defaultUnitId",
  "_links"
})
public class ApiSampleTemplateInfo extends ApiSampleInfo {

  @JsonProperty("defaultUnitId")
  private Integer defaultUnitId;

  public ApiSampleTemplateInfo() {
    setTemplate(true);
    setType(ApiInventoryRecordType.SAMPLE_TEMPLATE);
  }

  public ApiSampleTemplateInfo(Sample template) {
    super(template);
    setType(ApiInventoryRecordType.SAMPLE_TEMPLATE);
    Validate.isTrue(
        template.isTemplate(), String.format("Sample '%d' is not a template", template.getId()));
    setDefaultUnitId(template.getDefaultUnitId());
  }
}
