package com.researchspace.dao;

import java.util.Collection;
import java.util.Map;

public interface FeatureFlagDao {

  Map<String, Boolean> getBaselineValues();

  void upsertBaseline(String flagName, boolean enabled);

  int deleteBaselinesNotIn(Collection<String> flagNames);

  int deleteOverridesNotIn(Collection<String> flagNames);

  Map<String, Boolean> getOverridesForUser(Long userId);

  void setOverride(Long userId, String flagName, boolean enabled);

  void clearOverride(Long userId, String flagName);
}
