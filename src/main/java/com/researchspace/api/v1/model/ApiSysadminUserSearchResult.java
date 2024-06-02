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
@JsonPropertyOrder({"totalHits", "pageNumber", "users", "_links"})
public class ApiSysadminUserSearchResult extends ApiPaginatedResultList<ApiUser> {

  @JsonProperty("users")
  private List<ApiUser> users = new ArrayList<>();

  @Override
  protected String getSearchEndpoint() {
    return BaseApiController.SYSADMIN_USERS_ENDPOINT;
  }

  @Override
  public void setItems(List<ApiUser> items) {
    this.users = items;
  }
}
