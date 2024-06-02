package com.researchspace.webapp.integrations.helper;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

/** Encapsulates authorization failure information for presentation in UI */
@Data
@Builder
public class OauthAuthorizationError {
  /** Human-readable application name */
  @NonNull private String appName;

  /** Human-readable error message */
  @NonNull private String errorMsg;

  /** Optional extra details */
  private String errorDetails;
}
