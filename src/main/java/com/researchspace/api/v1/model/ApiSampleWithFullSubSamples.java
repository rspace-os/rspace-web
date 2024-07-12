/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiSampleWithFullSubSamples extends ApiSampleWithoutSubSamples {

  @JsonProperty("subSamples")
  private List<ApiSubSample> subSamples = new ArrayList<>();

  @JsonProperty(value = "newSampleSubSamplesCount", access = Access.WRITE_ONLY)
  private Integer newSampleSubSamplesCount;

  @JsonProperty(value = "newSampleSubSampleTargetLocations", access = Access.WRITE_ONLY)
  private List<ApiSampleSubSampleTargetLocation> newSampleSubSampleTargetLocations;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApiSampleSubSampleTargetLocation {
    @JsonProperty("containerId")
    private Long containerId;

    @JsonProperty("location")
    private ApiContainerLocation containerLocation;
  }

  public ApiSampleWithFullSubSamples(Sample sample) {
    super(sample);

    for (SubSample subSample : sample.getActiveSubSamples()) {
      subSamples.add(new ApiSubSample(subSample));
    }
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
}
