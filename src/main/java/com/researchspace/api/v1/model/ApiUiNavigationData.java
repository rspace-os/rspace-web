package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import lombok.Data;

@Data
@JsonPropertyOrder({
  "bannerImgSrc",
  "visibleTabs",
  "userDetails",
  "incomingMaintenance",
  "operatedAs"
})
public class ApiUiNavigationData {

  private String bannerImgSrc;

  private ApiUiNavigationVisibleTabs visibleTabs;

  private ApiUiNavigationUserDetails userDetails;

  private boolean incomingMaintenance;

  private boolean operatedAs;

  @Data
  public static class ApiUiNavigationVisibleTabs {
    @JsonProperty("inventory")
    private boolean inventory;

    @JsonProperty("myLabGroups")
    private boolean myLabGroups;

    @JsonProperty("published")
    private boolean published;

    @JsonProperty("system")
    private boolean system;
  }

  @Data
  public static class ApiUiNavigationUserDetails {

    @JsonProperty("username")
    private String username;

    private String fullName;
    private String email;
    private String orcidId;
    private String profileImgSrc;

    @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
    @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
    private Long lastSession;
  }
}
