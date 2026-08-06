package com.researchspace.service;

import com.researchspace.featureflags.FeatureFlagResource;
import com.researchspace.model.User;
import java.util.List;
import java.util.Optional;

public interface FeatureFlagManager {

  /**
   * Values accepted when changing a feature flag. A null baseline is unchanged; an override is
   * unchanged when {@code updateOverride} is false and cleared when it is true with a null value.
   */
  record Patch(Boolean baselineValue, boolean updateOverride, Boolean overrideValue) {}

  /** Reconciles persisted feature flag state with the manifest. */
  void reconcileOnStartup();

  /** Returns all feature flags with caller-specific values resolved. */
  List<FeatureFlagResource> getFeatureFlags(User actor);

  /** Returns one feature flag with caller-specific values when it exists. */
  Optional<FeatureFlagResource> getFeatureFlag(String flagName, User actor);

  /** Applies a patch and returns the updated caller-specific feature flag when it exists. */
  Optional<FeatureFlagResource> updateFeatureFlag(String flagName, Patch patch, User actor);

  /** Returns whether a flag is enabled for the current user, or its unauthenticated baseline. */
  boolean isFeatureFlagEnabled(String flagName);

  /** Returns whether a flag is enabled, optionally applying current-user overrides. */
  boolean isFeatureFlagEnabled(String flagName, boolean useUserContext);

  /** Returns whether a flag is enabled for a user, or its baseline when {@code user} is null. */
  boolean isFeatureFlagEnabled(String flagName, User user);

  /** Returns whether the user may access internal devtools. */
  boolean canUseDevtools(User user);

  /** Returns whether the user may set their own overrides. */
  boolean canOverrideFeatureFlags(User user);

  /** Returns whether the user may change instance baselines. */
  boolean canChangeFeatureFlagBaselines(User user);
}
