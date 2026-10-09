package com.researchspace.featureflags;

import lombok.Getter;

public class FeatureFlagReadOnlyException extends RuntimeException {

  @Getter private final String flagName;

  public FeatureFlagReadOnlyException(String flagName) {
    super("Feature flag is controlled by properties file: " + flagName);
    this.flagName = flagName;
  }
}
