package com.researchspace.service;

import com.researchspace.featureflags.FeatureFlagResource;
import com.researchspace.model.User;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import java.util.Optional;

public interface FeatureFlagManager {

  /** Reconciles persisted feature flag state with the manifest. */
  void reconcileOnStartup();

  /** Returns one page of caller-specific feature flag resources. */
  ResourcePage<FeatureFlagResource> getResources(ResourceRequest request, User actor);

  /** Counts caller-specific resources that match the request filter. */
  long countResources(ResourceRequest request, User actor);

  /** Returns one caller-specific resource when the flag exists. */
  Optional<FeatureFlagResource> getResource(String flagName, User actor);

  /** Applies a validated patch and returns the updated caller-specific resource. */
  Optional<FeatureFlagResource> updateResource(String flagName, ParsedDocument patch, User actor);

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
