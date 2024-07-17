/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import java.util.LinkedList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.web.util.UriComponentsBuilder;

/** API representation of an Inventory Sample, with information about SubSamples. */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
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
  "subSamplesInContainer",
  "_links"
})
public class ApiSample extends ApiSampleWithoutSubSamples {

  @JsonProperty("subSamples")
  private List<ApiSubSampleInfo> subSamples = new LinkedList<>();

  @JsonProperty(value = "subSamplesInContainer", access = Access.READ_ONLY)
  private List<ApiSubSampleInfo> subSamplesInContainer = new LinkedList<>();

  /* this will be `true` only when `subSamplesInContainer` is null or empty */
  @JsonProperty(value = "canBeDeleted", access = Access.READ_ONLY)
  private Boolean canBeDeleted;

  public ApiSample(Sample sample) {
    super(sample);

    for (SubSample subSample : sample.getActiveSubSamples()) {
      ApiSubSampleInfo subSampInfo = new ApiSubSampleInfo(subSample);
      this.subSamples.add(subSampInfo);
      if (subSample.isStoredInContainer()) {
        this.subSamplesInContainer.add(subSampInfo);
      }
    }
    this.canBeDeleted = this.subSamplesInContainer.isEmpty();
  }

  @Override
  public void buildAndAddInventoryRecordLinks(UriComponentsBuilder inventoryApiBaseUrl) {
    super.buildAndAddInventoryRecordLinks(inventoryApiBaseUrl);

    List<ApiSubSampleInfo> subSamples = getSubSamples();
    if (subSamples != null) {
      for (ApiSubSampleInfo subSample : subSamples) {
        subSample.buildAndAddInventoryRecordLinks(inventoryApiBaseUrl);

        /* needed as ApiSubSampleInfo doesn't have access to parent sample, so simple
         * call to buildAndAddInventoryRecordLinks will not create image links */
        if (!subSample.isCustomImage()) {
          subSample.addImageLinksFromParentSample(inventoryApiBaseUrl, this);
        }
      }
    }
  }

  @Override
  protected void nullifyListsForLimitedView(ApiInventoryRecordInfo apiInvRec) {
    ApiSample apiSample = (ApiSample) apiInvRec;
    super.nullifyListsForLimitedView(apiSample);
    apiSample.setSubSamples(null);
  }
}
