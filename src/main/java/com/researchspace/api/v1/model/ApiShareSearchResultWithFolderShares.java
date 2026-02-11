package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonPropertyOrder({"totalHits", "pageNumber", "shares", "folderShares", "_links"})
public class ApiShareSearchResultWithFolderShares extends ApiShareSearchResult {

  @JsonProperty("folderShares")
  private List<ApiShareInfo> folderShares = new ArrayList<>();

  public ApiShareSearchResultWithFolderShares(ApiShareSearchResult mixedResult) {
    setTotalHits(mixedResult.getTotalHits());
    setPageNumber(mixedResult.getPageNumber());
    setLinks(mixedResult.getLinks());

    for (ApiShareInfo share : mixedResult.getShares()) {
      if (share.getId() != null) {
        getShares().add(share);
      } else {
        getFolderShares().add(share);
      }
    }
  }
}
