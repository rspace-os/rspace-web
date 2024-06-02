/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.api.v1.controller.BaseApiInventoryController;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** ContainerSearchResults */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonPropertyOrder({"totalHits", "pageNumber", "containers", "_links"})
public class ApiContainerSearchResult extends ApiPaginatedResultList<ApiContainerInfo> {

  @JsonProperty("containers")
  private List<ApiContainerInfo> containers = new ArrayList<>();

  @Override
  protected String getSearchEndpoint() {
    return BaseApiInventoryController.CONTAINERS_ENDPOINT;
  }

  @Override
  public void setItems(List<ApiContainerInfo> items) {
    this.containers = items;
  }
}
