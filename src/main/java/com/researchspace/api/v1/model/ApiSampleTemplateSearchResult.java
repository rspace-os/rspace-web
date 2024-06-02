/** RSpace Inventory API Access your RSpace Inventory Sample Templates programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.api.v1.controller.BaseApiInventoryController;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** DocumentSearchResults */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonPropertyOrder({"totalHits", "pageNumber", "templates", "_links"})
public class ApiSampleTemplateSearchResult extends ApiPaginatedResultList<ApiSampleTemplateInfo> {

  @JsonProperty("templates")
  private List<ApiSampleTemplateInfo> templates = new ArrayList<>();

  @JsonIgnore
  public List<ApiSampleTemplateInfo> getTemplates() {
    return templates;
  }

  @Override
  protected String getSearchEndpoint() {
    return BaseApiInventoryController.SAMPLE_TEMPLATES_ENDPOINT;
  }

  @Override
  public void setItems(List<ApiSampleTemplateInfo> items) {
    setTemplates(items);
  }
}
