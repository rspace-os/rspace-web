package com.researchspace.featureflags;

public class FeatureFlagNotFoundException extends RuntimeException {

  private final String flagName;

  public FeatureFlagNotFoundException(String flagName) {
    super("Unknown feature flag: " + flagName);
    this.flagName = flagName;
  }

  public String getFlagName() {
    return flagName;
  }
}
