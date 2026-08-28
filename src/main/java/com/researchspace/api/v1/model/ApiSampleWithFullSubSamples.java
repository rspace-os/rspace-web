/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * API representation of an Inventory Sample with full SubSample objects.
 *
 * <p>To be used on create request, when API client should be able to define completely populated
 * Sample record.
 */
@Data
@EqualsAndHashCode(callSuper = true)
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
  "sharedWith",
  "subSampleAlias",
  "subSamplesCount",
  "storageTempMin",
  "storageTempMax",
  "sampleSource",
  "expiryDate",
  "templateId",
  "template",
  "fields",
  "extraFields",
  "subSamples",
  "_links"
})
public class ApiSampleWithFullSubSamples extends ApiSampleWithoutSubSamples {

  // Cascade (@Valid) so each explicit subsample's own constraints (image size, note length) hold
  // wherever this DTO is bound as a request body. Capped at 100 like newSampleSubSamplesCount
  // (SampleApiPostFullValidator): each subsample costs a full create cycle.
  @Valid
  @Size(max = 100, message = "{errors.inventory.sample.tooManySubSamples}")
  @JsonProperty("subSamples")
  private List<ApiSubSample> subSamples = new ArrayList<>();

  @JsonProperty(value = "canBeDeleted")
  private Boolean canBeDeleted;

  @JsonProperty(value = "newSampleSubSamplesCount", access = Access.WRITE_ONLY)
  private Integer newSampleSubSamplesCount;

  @JsonProperty(value = "newSampleSubSampleTargetLocations", access = Access.WRITE_ONLY)
  private List<ApiTargetLocation> newSampleSubSampleTargetLocations;

  public ApiSampleWithFullSubSamples(Sample sample) {
    super(sample);

    for (SubSample subSample : sample.getActiveSubSamples()) {
      subSamples.add(new ApiSubSample(subSample));
    }
    canBeDeleted = subSamples.stream().noneMatch(ApiSubSample::isStoredInContainer);
  }

  /** to simplify creation of a valid sample (must have name). */
  public ApiSampleWithFullSubSamples(String name) {
    setName(name);
  }

  @Override
  public void buildAndAddInventoryRecordLinks(UriComponentsBuilder inventoryApiBaseUrl) {
    super.buildAndAddInventoryRecordLinks(inventoryApiBaseUrl);

    for (ApiSubSample subSample : getSubSamples()) {
      subSample.buildAndAddInventoryRecordLinks(inventoryApiBaseUrl);
    }
  }

  @Override
  public void removeImageLinksForLimitedView() {
    super.removeImageLinksForLimitedView();

    for (ApiSubSample subSample : getSubSamples()) {
      subSample.removeImageLinksForLimitedView();
    }
  }
}
