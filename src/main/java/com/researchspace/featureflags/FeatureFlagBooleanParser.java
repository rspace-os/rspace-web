package com.researchspace.featureflags;

public final class FeatureFlagBooleanParser {

  private FeatureFlagBooleanParser() {}

  public static boolean parse(String value, String description) {
    return switch (value == null ? "" : value.trim()) {
      case "true" -> true;
      case "false" -> false;
      default -> throw new IllegalStateException(description + " must be exactly true or false");
    };
  }
}
