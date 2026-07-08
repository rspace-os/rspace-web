/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.inventory.SubSample;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.web.util.UriComponentsBuilder;

/** API representation of an Inventory SubSample */
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
  "deleted",
  "deletedDate",
  "iconId",
  "quantity",
  "tags",
  "attachments",
  "barcodes",
  "identifiers",
  "type",
  "owner",
  "permittedActions",
  "sharingMode",
  "sample",
  "parentContainers",
  "parentLocation",
  "lastNonWorkbenchParent",
  "lastMoveDate",
  "revisionId",
  "deletedOnSampleDeletion",
  "_links"
})
public class ApiSubSampleInfoWithSampleInfo extends ApiSubSampleInfo {

  @JsonProperty("sample")
  private ApiSampleWithoutSubSamples sampleInfo;

  public ApiSubSampleInfoWithSampleInfo(SubSample subSample) {
    super(subSample);

    setSampleInfo(new ApiSampleWithoutSubSamples(subSample.getSample()));
  }

  @Override
  public void buildAndAddInventoryRecordLinks(UriComponentsBuilder inventoryApiBaseUrl) {
    super.buildAndAddInventoryRecordLinks(inventoryApiBaseUrl);

    if (getSampleInfo() != null) {
      getSampleInfo().buildAndAddInventoryRecordLinks(inventoryApiBaseUrl);
      if (!isCustomImage()) {
        addImageLinksFromParentSample(inventoryApiBaseUrl, getSampleInfo());
      }
    }
  }

  @Override
  public void removeImageLinksForLimitedView() {
    super.removeImageLinksForLimitedView();

    /* The limited-view copy keeps the raw parent sample, whose permitted actions are never
     * evaluated for the viewer, so its own limited-read check cannot fire. A limited-read
     * subsample implies the viewer cannot fetch the sample's image either, so strip it
     * unconditionally. */
    if (isLimitedReadItem() && getSampleInfo() != null) {
      getSampleInfo().removeImageLinks();
    }
  }

  @Override
  protected void populateLimitedViewCopy(ApiInventoryRecordInfo apiInvRecCopy) {
    super.populateLimitedViewCopy(apiInvRecCopy);
    ApiSubSampleInfoWithSampleInfo publicViewCopy = (ApiSubSampleInfoWithSampleInfo) apiInvRecCopy;
    publicViewCopy.setSampleInfo(getSampleInfo());
  }

  protected void populatePublicViewCopy(ApiInventoryRecordInfo apiInvRecCopy) {
    super.populatePublicViewCopy(apiInvRecCopy);
    ApiSubSampleInfoWithSampleInfo publicViewCopy = (ApiSubSampleInfoWithSampleInfo) apiInvRecCopy;
    publicViewCopy.setSampleInfo((ApiSampleWithoutSubSamples) getSampleInfo().getPublicViewCopy());
  }
}
