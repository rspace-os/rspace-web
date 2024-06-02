/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.api.v1.controller.BaseApiInventoryController;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** SampleSearchResults */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonPropertyOrder({"totalHits", "pageNumber", "subSamples", "_links"})
public class ApiSubSampleSearchResult
    extends ApiPaginatedResultList<ApiSubSampleInfoWithSampleInfo> {

  @JsonProperty("subSamples")
  private List<ApiSubSampleInfoWithSampleInfo> subSamples = new ArrayList<>();

  @Override
  protected String getSearchEndpoint() {
    return BaseApiInventoryController.SUBSAMPLES_ENDPOINT;
  }

  @Override
  public void setItems(List<ApiSubSampleInfoWithSampleInfo> items) {
    this.subSamples = items;
  }
}
