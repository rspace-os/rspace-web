package com.researchspace.service;

import com.researchspace.featureflags.FeatureFlagState;
import com.researchspace.model.User;
import java.util.Map;

public interface FeatureFlagManager {

  /** Reconciles persisted feature flag state with the manifest. */
  void reconcileOnStartup();

  /** Returns all states for a user, or baselines when {@code user} is {@code null}. */
  Map<String, FeatureFlagState> getFeatureFlagStates(User user);

  /** Returns whether a flag is enabled for the current user, or its unauthenticated baseline. */
  boolean isFeatureFlagEnabled(String flagName);

  /** Returns whether a flag is enabled, optionally applying current-user overrides. */
  boolean isFeatureFlagEnabled(String flagName, boolean useUserContext);

  /** Returns whether a flag is enabled for a user, or its baseline when {@code user} is null. */
  boolean isFeatureFlagEnabled(String flagName, User user);

  /** Stores an explicit override for a user. */
  void setUserOverride(String flagName, boolean value, User user);

  /** Clears a user's explicit override; missing overrides are ignored. */
  void clearUserOverride(String flagName, User user);

  /** Sets the instance baseline for a real sysadmin; writing its current value is ignored. */
  void setBaseline(String flagName, boolean value, User user);

  /** Returns whether the user may access internal devtools. */
  boolean canUseDevtools(User user);

  /** Returns whether the user may set their own overrides. */
  boolean canOverrideFeatureFlags(User user);

  /** Returns whether the user may change instance baselines. */
  boolean canChangeFeatureFlagBaselines(User user);
}
