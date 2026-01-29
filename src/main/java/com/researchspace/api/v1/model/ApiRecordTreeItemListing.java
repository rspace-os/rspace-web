/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@JsonPropertyOrder({"folderId", "totalHits", "pageNumber", "records", "_links"})
public class ApiRecordTreeItemListing extends ApiPaginatedResultList<RecordTreeItemInfo> {

  /** The id of the folder for which the listing was calculated */
  @JsonProperty("folderId")
  private Long folderId;

  @JsonIgnore private boolean omitFolderIdInSearchEndpointString;

  private static String endpointFormat = BaseApiController.FOLDER_TREE_ENDPOINT + "/%d";

  @JsonProperty("records")
  private List<RecordTreeItemInfo> records = new ArrayList<>();

  @Override
  protected String getSearchEndpoint() {
    return folderId == null || omitFolderIdInSearchEndpointString
        ? BaseApiController.FOLDER_TREE_ENDPOINT
        : String.format(endpointFormat, folderId);
  }

  @Override
  public void setItems(List<RecordTreeItemInfo> items) {
    this.records = items;
  }
}
