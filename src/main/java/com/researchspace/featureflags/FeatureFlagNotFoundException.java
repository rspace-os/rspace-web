package com.researchspace.featureflags;

public class FeatureFlagNotFoundException extends RuntimeException {

  public FeatureFlagNotFoundException(String flagName) {
    super("Unknown feature flag: " + flagName);
  }
}
