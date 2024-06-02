package com.researchspace.extmessages.msteams;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.extmessages.base.ExternalMessageSender;
import java.util.List;
import lombok.Data;

@Data
class MSCard {
  @JsonProperty("@type")
  private String type = "MessageCard";

  @JsonProperty("@context")
  private String context = "http://schema.org/extensions";

  private String summary;
  private String title;
  private String text;
  private String themeColor = ExternalMessageSender.RSPACE_BLUE;
  private List<Section> sections;

  public String toJSON() {
    return JacksonUtil.toJson(this);
  }
}
