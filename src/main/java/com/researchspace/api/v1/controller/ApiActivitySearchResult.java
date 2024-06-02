package com.researchspace.api.v1.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.api.v1.model.ApiActivity;
import com.researchspace.api.v1.model.ApiPaginatedResultList;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@JsonPropertyOrder({"totalHits", "pageNumber", "activities", "_links"})
public class ApiActivitySearchResult extends ApiPaginatedResultList<ApiActivity> {

  @JsonProperty("activities")
  private List<ApiActivity> activities = new ArrayList<>();

  @Override
  protected String getSearchEndpoint() {
    return BaseApiController.EVENTS_ENDPOINT;
  }

  @Override
  public void setItems(List<ApiActivity> items) {
    this.activities = items;
  }
}
