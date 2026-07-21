package com.researchspace.featureflags;

public record FeatureFlagState(
    boolean value, boolean baselineValue, FeatureFlagSource source, boolean canOverride) {}
