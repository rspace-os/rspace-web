package com.researchspace.featureflags;

public class FeatureFlagReadOnlyException extends RuntimeException {

  private final String flagName;

  public FeatureFlagReadOnlyException(String flagName) {
    super("Feature flag is controlled by properties file: " + flagName);
    this.flagName = flagName;
  }

  public String getFlagName() {
    return flagName;
  }
}
