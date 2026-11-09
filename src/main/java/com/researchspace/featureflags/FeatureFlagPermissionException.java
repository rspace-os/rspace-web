package com.researchspace.featureflags;

public class FeatureFlagPermissionException extends RuntimeException {

  public FeatureFlagPermissionException() {
    super("Feature flag operation is not permitted");
  }
}
