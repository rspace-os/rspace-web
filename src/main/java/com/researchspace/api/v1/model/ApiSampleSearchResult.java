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
@JsonPropertyOrder({"totalHits", "pageNumber", "samples", "_links"})
public class ApiSampleSearchResult extends ApiPaginatedResultList<ApiSampleInfo> {

  @JsonProperty("samples")
  private List<ApiSampleInfo> samples = new ArrayList<>();

  @Override
  protected String getSearchEndpoint() {
    return BaseApiInventoryController.SAMPLES_ENDPOINT;
  }

  @Override
  public void setItems(List<ApiSampleInfo> items) {
    this.samples = items;
  }
}
