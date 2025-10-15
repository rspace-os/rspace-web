package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonPropertyOrder({
  "bannerImgSrc",
  "visibleTabs",
  "userDetails",
  "extraHelpLinks",
  "incomingMaintenance",
  "operatedAs"
})
public class ApiUiNavigationData {

  private String bannerImgSrc;

  private ApiUiNavigationVisibleTabs visibleTabs;

  private ApiUiNavigationUserDetails userDetails;

  private ApiUiNavigationScheduledMaintenance nextMaintenance;

  private List<ApiUiNavigationExtraHelpLink> extraHelpLinks;

  private boolean operatedAs;

  @Data
  public static class ApiUiNavigationVisibleTabs {

    private boolean inventory;
    private boolean myLabGroups;
    private boolean published;
    private boolean system;
  }

  @Data
  public static class ApiUiNavigationUserDetails {

    private String username;
    private String fullName;
    private String email;
    private String orcidId;
    private boolean orcidAvailable;
    private String profileImgSrc;

    @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
    private Long lastSession;
  }

  @Data
  public static class ApiUiNavigationScheduledMaintenance {

    @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
    private Long startDate;

    @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
    private Long endDate;

    @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
    private Long stopUserLoginDate;

    private String message;
    private boolean canUserLoginNow;
    private boolean activeNow;

    public ApiUiNavigationScheduledMaintenance(ScheduledMaintenance maintenance) {
      setStartDate(maintenance.getStartDate().getTime());
      setEndDate(maintenance.getEndDate().getTime());
      setStopUserLoginDate(maintenance.getStopUserLoginDate().getTime());
      setMessage(maintenance.getMessage());
      setCanUserLoginNow(maintenance.getCanUserLoginNow());
      setActiveNow(maintenance.isActiveNow());
    }
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApiUiNavigationExtraHelpLink {
    private String label;
    private String url;
  }
}
