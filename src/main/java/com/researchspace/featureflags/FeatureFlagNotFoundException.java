package com.researchspace.featureflags;

import lombok.Getter;

public class FeatureFlagNotFoundException extends RuntimeException {

  @Getter private final String flagName;

  public FeatureFlagNotFoundException(String flagName) {
    super("Unknown feature flag: " + flagName);
    this.flagName = flagName;
  }
}
