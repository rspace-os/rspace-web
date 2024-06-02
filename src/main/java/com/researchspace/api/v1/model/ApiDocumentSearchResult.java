/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.api.v1.controller.BaseApiController;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** DocumentSearchResults */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonPropertyOrder({"totalHits", "pageNumber", "documents", "_links"})
public class ApiDocumentSearchResult extends ApiPaginatedResultList<ApiDocumentInfo> {

  @JsonProperty("documents")
  private List<ApiDocumentInfo> documents = new ArrayList<>();

  @Override
  protected String getSearchEndpoint() {
    return BaseApiController.DOCUMENTS_ENDPOINT;
  }

  @Override
  public void setItems(List<ApiDocumentInfo> items) {
    this.documents = items;
  }
}
