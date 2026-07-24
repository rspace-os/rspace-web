package com.researchspace.service.impl;

import com.researchspace.dao.FeatureFlagDao;
import com.researchspace.featureflags.FeatureFlagBooleanParser;
import com.researchspace.featureflags.FeatureFlagDefinition;
import com.researchspace.featureflags.FeatureFlagManifestLoader;
import com.researchspace.featureflags.FeatureFlagNotFoundException;
import com.researchspace.featureflags.FeatureFlagPermissionException;
import com.researchspace.featureflags.FeatureFlagPropertiesLoader;
import com.researchspace.featureflags.FeatureFlagReadOnlyException;
import com.researchspace.featureflags.FeatureFlagSource;
import com.researchspace.featureflags.FeatureFlagState;
import com.researchspace.model.User;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.UserManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service("featureFlagManager")
@Slf4j
public class FeatureFlagManagerImpl implements FeatureFlagManager {

  private final FeatureFlagDao featureFlagDao;
  private final FeatureFlagManifestLoader manifestLoader;
  private final FeatureFlagPropertiesLoader propertiesLoader;
  private final UserManager userManager;
  private final String devModeEnabled;
  private final String reactDevMode;

  private volatile RuntimeFeatureFlags runtime;

  public FeatureFlagManagerImpl(
      FeatureFlagDao featureFlagDao,
      FeatureFlagManifestLoader manifestLoader,
      FeatureFlagPropertiesLoader propertiesLoader,
      UserManager userManager,
      @Value("${dev.mode.enabled:}") String devModeEnabled,
      @Value("${reactDevMode:false}") String reactDevMode) {
    this.featureFlagDao = featureFlagDao;
    this.manifestLoader = manifestLoader;
    this.propertiesLoader = propertiesLoader;
    this.userManager = userManager;
    this.devModeEnabled = devModeEnabled;
    this.reactDevMode = reactDevMode;
  }

  @Override
  public synchronized void reconcileOnStartup() {
    List<FeatureFlagDefinition> loadedDefinitions = manifestLoader.loadDefinitions();
    Map<String, FeatureFlagDefinition> definitions = new LinkedHashMap<>();
    for (FeatureFlagDefinition definition : loadedDefinitions) {
      definitions.put(definition.name(), definition);
    }
    Map<String, Boolean> forcedValues = propertiesLoader.loadForcedValues(definitions.keySet());

    int deletedBaselines = featureFlagDao.deleteBaselinesNotIn(definitions.keySet());
    int deletedOverrides = featureFlagDao.deleteOverridesNotIn(definitions.keySet());
    if (deletedBaselines > 0 || deletedOverrides > 0) {
      log.info(
          "Cleaned up retired feature flags: {} baseline rows, {} user override rows",
          deletedBaselines,
          deletedOverrides);
    }

    runtime =
        new RuntimeFeatureFlags(definitions, featureFlagDao.getBaselineValues(), forcedValues);
  }

  @Override
  public Map<String, FeatureFlagState> getFeatureFlagStates(User user) {
    ensureReady();
    RuntimeFeatureFlags snapshot = runtime;
    Map<String, Boolean> userOverrides = getUserOverrides(user);
    boolean canOverride = canOverrideFeatureFlags(user);

    Map<String, FeatureFlagState> states = new LinkedHashMap<>();
    for (FeatureFlagDefinition definition : snapshot.definitions().values()) {
      states.put(definition.name(), snapshot.resolveState(definition, userOverrides, canOverride));
    }
    return states;
  }

  @Override
  public boolean isFeatureFlagEnabled(String flagName) {
    return isFeatureFlagEnabled(flagName, getCurrentUser());
  }

  @Override
  public boolean isFeatureFlagEnabled(String flagName, boolean useUserContext) {
    return isFeatureFlagEnabled(flagName, useUserContext ? getCurrentUser() : null);
  }

  @Override
  public boolean isFeatureFlagEnabled(String flagName, User user) {
    ensureReady();
    RuntimeFeatureFlags snapshot = runtime;
    FeatureFlagDefinition definition = snapshot.definitions().get(flagName);
    if (definition == null) {
      throw new FeatureFlagNotFoundException(flagName);
    }
    Map<String, Boolean> userOverrides =
        snapshot.isForced(flagName) ? Map.of() : getUserOverrides(user);
    return snapshot.resolveState(definition, userOverrides, false).value();
  }

  @Override
  public void setUserOverride(String flagName, boolean value, User user) {
    assertKnownAndWritable(flagName);
    if (!canOverrideFeatureFlags(user)) {
      throw new FeatureFlagPermissionException();
    }
    featureFlagDao.setOverride(user.getId(), flagName, value);
  }

  @Override
  public void clearUserOverride(String flagName, User user) {
    assertKnownAndWritable(flagName);
    if (!canOverrideFeatureFlags(user)) {
      throw new FeatureFlagPermissionException();
    }
    featureFlagDao.clearOverride(user.getId(), flagName);
  }

  @Override
  public synchronized void setBaseline(String flagName, boolean value, User user) {
    assertKnownAndWritable(flagName);
    if (!canChangeFeatureFlagBaselines(user)) {
      throw new FeatureFlagPermissionException();
    }
    RuntimeFeatureFlags snapshot = runtime;
    FeatureFlagDefinition definition = snapshot.definitions().get(flagName);
    boolean currentBaseline = snapshot.baselineValue(definition);
    if (currentBaseline == value) {
      return;
    }
    featureFlagDao.upsertBaseline(flagName, value);
    Map<String, Boolean> updatedBaselines = new LinkedHashMap<>(snapshot.baselineValues());
    updatedBaselines.put(flagName, value);
    runtime =
        new RuntimeFeatureFlags(snapshot.definitions(), updatedBaselines, snapshot.forcedValues());
  }

  @Override
  public boolean canUseDevtools(User user) {
    return user != null && (isRealSysadmin(user) || isDevModeEnabled());
  }

  @Override
  public boolean canOverrideFeatureFlags(User user) {
    return canUseDevtools(user);
  }

  @Override
  public boolean canChangeFeatureFlagBaselines(User user) {
    return user != null && isRealSysadmin(user);
  }

  private Map<String, Boolean> getUserOverrides(User user) {
    return user == null || user.getId() == null
        ? Map.of()
        : featureFlagDao.getOverridesForUser(user.getId());
  }

  private void ensureReady() {
    if (runtime == null) {
      reconcileOnStartup();
    }
  }

  private void assertKnownAndWritable(String flagName) {
    ensureReady();
    RuntimeFeatureFlags snapshot = runtime;
    if (!snapshot.definitions().containsKey(flagName)) {
      throw new FeatureFlagNotFoundException(flagName);
    }
    if (snapshot.forcedValues().containsKey(flagName)) {
      throw new FeatureFlagReadOnlyException(flagName);
    }
  }

  private boolean isDevModeEnabled() {
    return devModeEnabled == null || devModeEnabled.isBlank()
        ? Boolean.parseBoolean(reactDevMode)
        : FeatureFlagBooleanParser.parse(devModeEnabled, "dev.mode.enabled");
  }

  private boolean isRealSysadmin(User user) {
    return user.hasSysadminRole() && !isRunAs();
  }

  private boolean isRunAs() {
    try {
      Subject subject = SecurityUtils.getSubject();
      return subject != null && subject.isRunAs();
    } catch (RuntimeException e) {
      log.warn(
          "Could not determine Shiro run-as state; denying feature flag sysadmin capability", e);
      return true;
    }
  }

  private User getCurrentUser() {
    try {
      Subject subject = SecurityUtils.getSubject();
      if (subject == null || subject.getPrincipal() == null) {
        return null;
      }
      return userManager.getUserByUsername(subject.getPrincipal().toString());
    } catch (RuntimeException e) {
      log.warn("Could not determine the current user; evaluating feature flags at baseline", e);
      return null;
    }
  }

  private record RuntimeFeatureFlags(
      Map<String, FeatureFlagDefinition> definitions,
      Map<String, Boolean> baselineValues,
      Map<String, Boolean> forcedValues) {

    private FeatureFlagState resolveState(
        FeatureFlagDefinition definition, Map<String, Boolean> userOverrides, boolean canOverride) {
      String flagName = definition.name();
      Boolean forcedValue = forcedValues.get(flagName);
      if (forcedValue != null) {
        return new FeatureFlagState(
            forcedValue, forcedValue, FeatureFlagSource.PROPERTIES_FILE, false);
      }

      boolean baselineValue = baselineValue(definition);
      Boolean userOverride = userOverrides.get(flagName);
      if (userOverride != null) {
        return new FeatureFlagState(
            userOverride, baselineValue, FeatureFlagSource.USER_OVERRIDE, canOverride);
      }
      FeatureFlagSource source =
          baselineValues.containsKey(flagName)
              ? FeatureFlagSource.DATABASE
              : FeatureFlagSource.DEFAULT;
      return new FeatureFlagState(baselineValue, baselineValue, source, canOverride);
    }

    private boolean baselineValue(FeatureFlagDefinition definition) {
      return baselineValues.getOrDefault(definition.name(), definition.defaultValue());
    }

    private boolean isForced(String flagName) {
      return forcedValues.containsKey(flagName);
    }
  }
}
