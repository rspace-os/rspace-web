/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.api.v1.controller.BaseApiInventoryController;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Search result that may combine different types of inventory records. */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonPropertyOrder({"totalHits", "pageNumber", "records", "_links"})
public class ApiInventorySearchResult extends ApiPaginatedResultList<ApiInventoryRecordInfo> {

  @JsonProperty("records")
  private List<ApiInventoryRecordInfo> records = new ArrayList<>();

  @Override
  protected String getSearchEndpoint() {
    return BaseApiInventoryController.SEARCH_ENDPOINT;
  }

  @Override
  public void setItems(List<ApiInventoryRecordInfo> items) {
    this.records = items;
  }

  public static ApiInventorySearchResult emptyResult() {
    ApiInventorySearchResult emptyResult = new ApiInventorySearchResult();
    emptyResult.setTotalHits(0L);
    emptyResult.setPageNumber(0);
    emptyResult.setItems(Collections.emptyList());
    return emptyResult;
  }
}
