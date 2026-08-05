package com.researchspace.webapp.integrations.helper;

import lombok.Data;
import lombok.NonNull;

/** Encapsulates authorization failure information for presentation in UI */
@Data
public class OauthAuthorizationError {
  /** Human-readable application name */
  @NonNull private String appName;

  /** Human-readable error message */
  @NonNull private String errorMsg;

  /** Optional extra details */
  private String errorDetails;

  public static OauthAuthorizationErrorBuilder builder() {
    return new OauthAuthorizationErrorBuilder();
  }

  public static class OauthAuthorizationErrorBuilder {
    private String appName;
    private String errorMsg;
    private String errorDetails;

    public OauthAuthorizationErrorBuilder appName(String appName) {
      this.appName = appName;
      return this;
    }

    public OauthAuthorizationErrorBuilder errorMsg(String errorMsg) {
      this.errorMsg = errorMsg;
      return this;
    }

    public OauthAuthorizationErrorBuilder errorDetails(String errorDetails) {
      this.errorDetails = errorDetails;
      return this;
    }

    public OauthAuthorizationError build() {
      OauthAuthorizationError error = new OauthAuthorizationError(appName, errorMsg);
      error.setErrorDetails(errorDetails);
      return error;
    }
  }
}
