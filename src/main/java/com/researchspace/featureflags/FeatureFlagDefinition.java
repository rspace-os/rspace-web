package com.researchspace.featureflags;

public record FeatureFlagDefinition(String name, boolean defaultValue) {

  public static final int MAX_NAME_LENGTH = 128;
}
