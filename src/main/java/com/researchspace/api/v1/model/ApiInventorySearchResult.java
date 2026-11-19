/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonInclude;
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

  /**
   * Only present, as {@code true}, on a structure search combined with a text query when the text
   * query matched more records than the search considers. Structure matches outside that limit are
   * missing from the results; a narrower text query finds them.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean textSearchLimitReached;

  /**
   * Only present, as {@code true}, on a structure search that matched more readable records than
   * the structure search limit. Records past the limit are missing from the results; a narrower
   * structure or more filters find them.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean structureSearchLimitReached;

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
