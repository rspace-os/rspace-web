/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.api.v1.controller.BaseApiController;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** FileSearchResult */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonPropertyOrder({"totalHits", "pageNumber", "files", "_links"})
public class ApiFileSearchResult extends ApiPaginatedResultList<ApiFile> {

  @JsonProperty("files")
  private List<ApiFile> files = new ArrayList<>();

  @Override
  protected String getSearchEndpoint() {
    return BaseApiController.FILES_ENDPOINT;
  }

  @Override
  public void setItems(List<ApiFile> items) {
    this.files = items;
  }
}
