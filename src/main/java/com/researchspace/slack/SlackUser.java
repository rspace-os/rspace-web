package com.researchspace.slack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SlackUser {
  private @JsonProperty("id") String userId;
  private @JsonProperty("team_id") String teamId;
  private @JsonProperty("profile") SlackUserProfile profile;
  private @JsonProperty("tz") String timezone;

  public SlackUser(String userId, String teamId) {
    this.userId = userId;
    this.teamId = teamId;
  }

  @Data
  public static class SlackUserProfile {
    private @JsonProperty("real_name") String realName;
    private @JsonProperty("display_name") String displayName;
  }
}
