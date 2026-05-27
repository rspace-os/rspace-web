/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.api.v1.controller.BaseApiInventoryController;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonPropertyOrder({"totalHits", "pageNumber", "instruments", "_links"})
public class ApiInstrumentSearchResult extends ApiPaginatedResultList<ApiInstrumentEntityInfo> {

  @JsonProperty("instruments")
  private List<ApiInstrumentEntityInfo> instruments = new ArrayList<>();

  @Override
  protected String getSearchEndpoint() {
    return BaseApiInventoryController.INSTRUMENTS_ENDPOINT;
  }

  @Override
  public void setItems(List<ApiInstrumentEntityInfo> items) {
    this.instruments = items;
  }
}
