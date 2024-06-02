package com.researchspace.extmessages.msteams;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URL;
import java.util.List;
import lombok.Data;

@Data
class Section {
  private String activityTitle, activitySubtitle, activityText;

  private URL activityImage;
  List<Fact> facts;

  @JsonProperty("markdown")
  private boolean markdown = true;
}
