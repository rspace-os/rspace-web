/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.api.v1.controller.BaseApiInventoryController;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** SampleRequestSearchResults */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonPropertyOrder({"totalHits", "pageNumber", "requests", "_links"})
public class ApiSampleRequestSearchResult extends ApiPaginatedResultList<ApiSampleRequestInfo> {

  @JsonProperty("requests")
  private List<ApiSampleRequestInfo> requests = new ArrayList<>();

  @Override
  protected String getSearchEndpoint() {
    return BaseApiInventoryController.SAMPLE_REQUESTS_ENDPOINT;
  }

  @Override
  public void setItems(List<ApiSampleRequestInfo> items) {
    this.requests = items;
  }
}
