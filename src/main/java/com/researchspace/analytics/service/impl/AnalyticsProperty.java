package com.researchspace.analytics.service.impl;

/**
 * Enum defining traits (fields) used in messages forwarded to analytics.
 *
 * <p>More info about traits: https://segment.com/docs/spec/identify/#traits
 */
public enum AnalyticsProperty {

  /**
   * common trait - unique, persistent, and non-personally identifiable string representing each
   * signed-in user
   */
  USER_ID("userId"),

  /** reserved trait - the first name of a user */
  FIRST_NAME("firstName"),

  /** reserved trait - the last name of a user */
  LAST_NAME("lastName"),

  /** reserved trait - the email address of a user */
  EMAIL("email"),

  /** reserved trait - the role a user */
  ROLE("Role"),

  /**
   * reserved trait - the date the user’s account was first created. the value should match ISO-8601
   * format
   */
  CREATED_AT("createdAt"),

  /** Custom trait - is the user in a group */
  IN_GROUP("inGroup"),

  /** Custom trait - has the user got autoshare enabled in any of their groups */
  AUTOSHARE_ENABLED("autoshareEnabled"),

  /**
   * custom trait - the username of RSpace user. it differs from reserved 'username' trait as it
   * doesn't need to be globally unique
   */
  RS_USERNAME("rsUsername"),

  /** custom trait - the affiliation of RSpace user */
  INSTITUTION("institution"),

  /** custom trait - rspace version */
  RSPACE_VERSION("rspaceVersion"),

  RSPACE_URL("rspaceUrl"),

  /** custom trait - the name of customer managing RSpace server the user belongs to */
  CUSTOMER_NAME("customerName"),

  /** custom property - RSpace user's roles */
  ROLES("roles"),

  /** custom property - client ip address (taken from RemoteAddr header) */
  CLIENT_REMOTE_ADDR("clientRemoteAddr"),

  /** custom property - email address of user invited to RSpace */
  INVITED_EMAIL("invitedEmail"),

  /** custom property - boolean value saying whether invitation link was used on signup */
  SIGNUP_FROM_INVITATION("signupFromInvitation"),

  /** custom property - specific type of BaseRecord */
  RS_RECORD_TYPE("rsRecordType"),

  /** custom property - name of the RSForm */
  RS_FORM("rsForm"),

  /** custom property - file usage of an individual user */
  DISK_USAGE("diskUsageSize"),

  /** custom property - method e.g. post, put or get */
  API_METHOD("apiHttpMethod"),

  /** custom property - method the API URI */
  API_URI("apiUri"),

  CHAT_EVENT_TYPE("chatEventType"),
  RECENTLY_SIGNED_UP("recentlySignedUp"),
  ONBOARDING_ENABLED("onboardingEnabled"),
  DAYS_SINCE_LAST_GROUP_JOIN("daysSinceLastGroupJoin"),
  DAYS_SINCE_LAST_AUTOSHARE("daysSinceLastAutoshare");

  private final String label;

  AnalyticsProperty(String propertyLabel) {
    this.label = propertyLabel;
  }

  public String getLabel() {
    return label;
  }
}
