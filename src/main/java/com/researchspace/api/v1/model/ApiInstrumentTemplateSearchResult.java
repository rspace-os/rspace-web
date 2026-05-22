/** RSpace Inventory API Access your RSpace Inventory Instrument Templates programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.api.v1.controller.BaseApiInventoryController;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonPropertyOrder({"totalHits", "pageNumber", "templates", "_links"})
public class ApiInstrumentTemplateSearchResult
    extends ApiPaginatedResultList<ApiInstrumentEntityInfo> {

  @JsonProperty("templates")
  private List<ApiInstrumentEntityInfo> templates = new ArrayList<>();

  @JsonIgnore
  public List<ApiInstrumentEntityInfo> getTemplates() {
    return templates;
  }

  @Override
  protected String getSearchEndpoint() {
    return BaseApiInventoryController.INSTRUMENT_TEMPLATES_ENDPOINT;
  }

  @Override
  public void setItems(List<ApiInstrumentEntityInfo> items) {
    setTemplates(items);
  }
}
