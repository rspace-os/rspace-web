package com.researchspace.dao;

import java.util.Collection;
import java.util.Map;

public interface FeatureFlagDao {

  Map<String, Boolean> getBaselineValues();

  /** Reads the committed baseline with a shared row lock on the caller's connection. */
  java.util.Optional<Boolean> getCurrentBaseline(String flagName);

  /** Reads the committed personal override with a shared row lock on the caller's connection. */
  java.util.Optional<Boolean> getCurrentOverride(String flagName, Long userId);

  void upsertBaseline(String flagName, boolean enabled);

  int deleteBaselinesNotIn(Collection<String> flagNames);

  int deleteOverridesNotIn(Collection<String> flagNames);

  Map<String, Boolean> getOverridesForUser(Long userId);

  Map<Long, Boolean> getOverridesForUsers(String flagName, java.util.Set<Long> userIds);

  void setOverride(Long userId, String flagName, boolean enabled);

  void clearOverride(Long userId, String flagName);
}
