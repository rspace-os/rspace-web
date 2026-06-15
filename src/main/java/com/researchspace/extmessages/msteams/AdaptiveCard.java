package com.researchspace.extmessages.msteams;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** The Adaptive Card carried by an {@link Attachment}. */
@Data
class AdaptiveCard {

  @JsonProperty("$schema")
  private String schema = "http://adaptivecards.io/schemas/adaptive-card.json";

  private String type = "AdaptiveCard";
  private String version = "1.4";
  private List<CardElement> body = new ArrayList<>();
  private MsTeamsProperties msteams = new MsTeamsProperties();

  /** Teams-specific rendering hints; uses the full channel width rather than a narrow column. */
  @Data
  static class MsTeamsProperties {
    private String width = "Full";
  }
}
