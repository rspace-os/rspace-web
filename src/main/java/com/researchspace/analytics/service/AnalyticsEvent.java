package com.researchspace.analytics.service;

/**
 * Enum defining traits (fields) used in messages forwarded to analytics.
 *
 * <p>More info about traits: https://segment.com/docs/spec/identify/#traits
 */
public enum AnalyticsEvent {

  /**
   * triggered when external user is sent an email inviting them to join RSpace group as a member or
   * a PI
   */
  JOIN_GROUP_INVITE("joinGroupInvite"),

  /** triggered when external user is sent an email inviting them to see the shared record */
  SHARE_DOC_INVITE("shareDocInvite"),

  /** triggered whenever someone signs up (whether they were invited or not) */
  SIGNUP("signup"),

  /** triggered when RSpace user logs in */
  LOGIN("login"),

  /** triggered when RSpace user logs out */
  LOGOUT("logout"),

  /** triggered when base record (document/folder/notebook) is created */
  RECORD_CREATE("recordCreate"),

  /** triggered when user views or downloads a DMP */
  DMP_USED("dmpsUsed"),

  /** event with disk usage information */
  DISK_USAGE("diskUsage"),

  /** API call invoked */
  API_KEY_USAGE("apiKeyUsed"),

  API_OAUTH_TOKEN_USAGE("apiOAuthTokenUsed"),

  /** Slack feature call invoked */
  SLACK_USED("slackUsed"),

  /** MsTeams call invoked */
  TEAMS_USED("msTeamsUsed");

  private final String label;

  private AnalyticsEvent(String eventLabel) {
    this.label = eventLabel;
  }

  public String getLabel() {
    return label;
  }
}
