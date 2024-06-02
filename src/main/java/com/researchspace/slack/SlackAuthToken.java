package com.researchspace.slack;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import org.apache.shiro.authc.AuthenticationToken;

@Data
@AllArgsConstructor
public class SlackAuthToken implements AuthenticationToken {
  /** */
  private static final long serialVersionUID = 1L;

  @Getter private String principal; // Username

  @Getter private String credentials; // "SlackUserId.SlackTeamId"
}
