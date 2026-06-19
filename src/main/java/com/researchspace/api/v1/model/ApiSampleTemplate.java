/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.commons.lang3.Validate;

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
  "sharedWith",
  "templateId",
  "templateVersion",
  "template",
  "revisionId",
  "version",
  "historicalVersion",
  "samplesToUpdateCount",
  "subSampleAlias",
  "defaultUnitId",
  "subSamplesCount",
  "storageTempMin",
  "storageTempMax",
  "sampleSource",
  "expiryDate",
  "fields",
  "extraFields",
  "subSamples",
  "_links"
})
public class ApiSampleTemplate extends ApiSample {

  @JsonProperty("defaultUnitId")
  private Integer defaultUnitId;

  /**
   * Number of the requesting user's samples that were created from an older version of this
   * template and could be updated to its latest version. Drives whether the UI offers the "Update
   * Samples" action; 0 means there is nothing to update. Populated on outgoing single-template
   * responses only (not on the lighter list/search view), and left 0 on limited (no-read) views.
   */
  @JsonProperty("samplesToUpdateCount")
  private int samplesToUpdateCount;

  public ApiSampleTemplate() {
    setTemplate(true);
    setType(ApiInventoryRecordType.SAMPLE_TEMPLATE);
  }

  public ApiSampleTemplate(Sample template) {
    super(template);
    setType(ApiInventoryRecordType.SAMPLE_TEMPLATE);
    Validate.isTrue(
        template.isTemplate(), String.format("Sample '%d' is not a template", template.getId()));
    setDefaultUnitId(template.getDefaultUnitId());
  }

  public boolean applyChangesToDatabaseTemplate(Sample dbSample, User user) {
    boolean contentChanged = false;

    if (defaultUnitId != null && !defaultUnitId.equals(dbSample.getDefaultUnitId())) {
      dbSample.setDefaultUnitId(defaultUnitId);
      contentChanged = true;
    }
    if (getSubSampleAlias() != null
        && !dbSample.isSubSampleAliasEqualTo(
            getSubSampleAlias().getAlias(), getSubSampleAlias().getPlural())) {
      dbSample.setSubSampleAliases(getSubSampleAlias().getAlias(), getSubSampleAlias().getPlural());
      contentChanged = true;
    }

    return contentChanged;
  }

  @Override
  protected void populateLimitedViewCopy(ApiInventoryRecordInfo apiInvRecCopy) {
    super.populateLimitedViewCopy(apiInvRecCopy);
    ApiSampleTemplate limitedViewCopy = (ApiSampleTemplate) apiInvRecCopy;
    limitedViewCopy.setSubSampleAlias(getSubSampleAlias());
    limitedViewCopy.setSampleSource(getSampleSource());
    limitedViewCopy.setDefaultUnitId(getDefaultUnitId());
  }
}
