package com.researchspace.service.impl;

import com.researchspace.dao.FeatureFlagDao;
import com.researchspace.featureflags.FeatureFlagBooleanParser;
import com.researchspace.featureflags.FeatureFlagDefinition;
import com.researchspace.featureflags.FeatureFlagManifestLoader;
import com.researchspace.featureflags.FeatureFlagNotFoundException;
import com.researchspace.featureflags.FeatureFlagPermissionException;
import com.researchspace.featureflags.FeatureFlagPropertiesLoader;
import com.researchspace.featureflags.FeatureFlagReadOnlyException;
import com.researchspace.featureflags.FeatureFlagResource;
import com.researchspace.featureflags.FeatureFlagSource;
import com.researchspace.model.User;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.UserManager;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service("featureFlagManager")
@Slf4j
public class FeatureFlagManagerImpl implements FeatureFlagManager {

  private static final Set<String> DEVELOPMENT_PROFILES = Set.of("dev", "run", "prod-test");

  private final FeatureFlagDao featureFlagDao;
  private final FeatureFlagManifestLoader manifestLoader;
  private final FeatureFlagPropertiesLoader propertiesLoader;
  private final UserManager userManager;
  private final ApplicationEventPublisher events;
  private final String devModeEnabled;
  private final String reactDevMode;
  private final String activeProfiles;

  private volatile RuntimeFeatureFlags runtime;

  public FeatureFlagManagerImpl(
      FeatureFlagDao featureFlagDao,
      FeatureFlagManifestLoader manifestLoader,
      FeatureFlagPropertiesLoader propertiesLoader,
      UserManager userManager,
      ApplicationEventPublisher events,
      @Value("${dev.mode.enabled:}") String devModeEnabled,
      @Value("${reactDevMode:false}") String reactDevMode,
      @Value("${spring.profiles.active:}") String activeProfiles) {
    this.featureFlagDao = featureFlagDao;
    this.manifestLoader = manifestLoader;
    this.propertiesLoader = propertiesLoader;
    this.userManager = userManager;
    this.events = events;
    this.devModeEnabled = devModeEnabled;
    this.reactDevMode = reactDevMode;
    this.activeProfiles = activeProfiles;
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
  public List<FeatureFlagResource> getResources(User actor) {
    RuntimeFeatureFlags snapshot = requireRuntime();
    Map<String, Boolean> overrides = getUserOverrides(actor);
    boolean canOverride = canOverrideFeatureFlags(actor);
    return snapshot.definitions().values().stream()
        .map(definition -> snapshot.resolveResource(definition, overrides, canOverride))
        .toList();
  }

  @Override
  public Optional<FeatureFlagResource> getResource(String flagName, User actor) {
    RuntimeFeatureFlags snapshot = requireRuntime();
    FeatureFlagDefinition definition = snapshot.definitions().get(flagName);
    if (definition == null) {
      return Optional.empty();
    }
    Map<String, Boolean> overrides = getUserOverrides(actor);
    return Optional.of(
        snapshot.resolveResource(definition, overrides, canOverrideFeatureFlags(actor)));
  }

  @Override
  public synchronized Optional<FeatureFlagResource> updateResource(
      String flagName, ParsedDocument patch, User actor) {
    RuntimeFeatureFlags snapshot = requireRuntime();
    if (!snapshot.definitions().containsKey(flagName)) {
      return Optional.empty();
    }
    if (patch.operation() != WriteOperation.UPDATE) {
      throw new IllegalArgumentException("Feature flag change requires an update document");
    }
    assertWritable(flagName, snapshot);
    boolean changesBaseline = patch.values().containsKey("baselineValue");
    boolean changesOverride = patch.values().containsKey("overrideValue");
    if (changesBaseline && !canChangeFeatureFlagBaselines(actor)) {
      throw new FeatureFlagPermissionException();
    }
    if (changesOverride && !canOverrideFeatureFlags(actor)) {
      throw new FeatureFlagPermissionException();
    }

    boolean changed = false;
    RuntimeFeatureFlags responseSnapshot = snapshot;
    if (changesBaseline) {
      Optional<RuntimeFeatureFlags> updatedSnapshot =
          setBaselineValue(snapshot, flagName, (boolean) patch.values().get("baselineValue"));
      if (updatedSnapshot.isPresent()) {
        responseSnapshot = updatedSnapshot.orElseThrow();
        changed = true;
      }
    }
    if (changesOverride) {
      Boolean value = (Boolean) patch.values().get("overrideValue");
      Map<String, Boolean> currentOverrides = getUserOverrides(actor);
      boolean hasOverride = currentOverrides.containsKey(flagName);
      if (value == null && hasOverride) {
        featureFlagDao.clearOverride(actor.getId(), flagName);
        changed = true;
      } else if (value != null && (!hasOverride || !value.equals(currentOverrides.get(flagName)))) {
        featureFlagDao.setOverride(actor.getId(), flagName, value);
        changed = true;
      }
    }
    FeatureFlagResource updated = resolveResource(flagName, actor, responseSnapshot).orElseThrow();
    if (changed) {
      events.publishEvent(new FeatureFlagResourceChangedEvent(actor, updated));
    }
    return Optional.of(updated);
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
    RuntimeFeatureFlags snapshot = requireRuntime();
    FeatureFlagDefinition definition = snapshot.definitions().get(flagName);
    if (definition == null) {
      throw new FeatureFlagNotFoundException(flagName);
    }
    Map<String, Boolean> userOverrides =
        snapshot.isForced(flagName) ? Map.of() : getUserOverrides(user);
    return snapshot.resolveResource(definition, userOverrides, false).isValue();
  }

  private Optional<RuntimeFeatureFlags> setBaselineValue(
      RuntimeFeatureFlags snapshot, String flagName, boolean value) {
    FeatureFlagDefinition definition = snapshot.definitions().get(flagName);
    boolean currentBaseline = snapshot.baselineValue(definition);
    if (currentBaseline == value) {
      return Optional.empty();
    }
    featureFlagDao.upsertBaseline(flagName, value);
    RuntimeFeatureFlags updated = snapshot.withBaseline(flagName, value);
    updateRuntimeAfterCommit(flagName, value);
    return Optional.of(updated);
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

  private RuntimeFeatureFlags requireRuntime() {
    RuntimeFeatureFlags snapshot = runtime;
    if (snapshot == null) {
      throw new IllegalStateException("Feature flags have not been reconciled");
    }
    return snapshot;
  }

  private void assertWritable(String flagName, RuntimeFeatureFlags snapshot) {
    if (snapshot.forcedValues().containsKey(flagName)) {
      throw new FeatureFlagReadOnlyException(flagName);
    }
  }

  private Optional<FeatureFlagResource> resolveResource(
      String flagName, User actor, RuntimeFeatureFlags snapshot) {
    FeatureFlagDefinition definition = snapshot.definitions().get(flagName);
    if (definition == null) {
      return Optional.empty();
    }
    return Optional.of(
        snapshot.resolveResource(
            definition, getUserOverrides(actor), canOverrideFeatureFlags(actor)));
  }

  private void updateRuntimeAfterCommit(String flagName, boolean value) {
    Runnable update =
        () -> {
          synchronized (this) {
            runtime = requireRuntime().withBaseline(flagName, value);
          }
        };
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      update.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            update.run();
          }
        });
  }

  private boolean isDevModeEnabled() {
    List<String> profiles =
        Arrays.stream(activeProfiles.split(","))
            .map(String::trim)
            .filter(profile -> !profile.isEmpty())
            .toList();
    boolean developmentSystem =
        !profiles.contains("prod")
            && (Boolean.parseBoolean(reactDevMode)
                || profiles.stream().anyMatch(DEVELOPMENT_PROFILES::contains));
    boolean enabled =
        devModeEnabled == null || devModeEnabled.isBlank()
            ? developmentSystem
            : FeatureFlagBooleanParser.parse(devModeEnabled, "dev.mode.enabled");
    return enabled && developmentSystem;
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

    private FeatureFlagResource resolveResource(
        FeatureFlagDefinition definition, Map<String, Boolean> userOverrides, boolean canOverride) {
      String flagName = definition.name();
      Boolean forcedValue = forcedValues.get(flagName);
      if (forcedValue != null) {
        return new FeatureFlagResource(
            flagName, forcedValue, forcedValue, null, FeatureFlagSource.PROPERTIES_FILE, false);
      }

      boolean baselineValue = baselineValue(definition);
      Boolean userOverride = userOverrides.get(flagName);
      if (userOverride != null) {
        return new FeatureFlagResource(
            flagName,
            userOverride,
            baselineValue,
            userOverride,
            FeatureFlagSource.USER_OVERRIDE,
            canOverride);
      }
      FeatureFlagSource source =
          baselineValues.containsKey(flagName)
              ? FeatureFlagSource.DATABASE
              : FeatureFlagSource.DEFAULT;
      return new FeatureFlagResource(
          flagName, baselineValue, baselineValue, null, source, canOverride);
    }

    private boolean baselineValue(FeatureFlagDefinition definition) {
      return baselineValues.getOrDefault(definition.name(), definition.defaultValue());
    }

    private boolean isForced(String flagName) {
      return forcedValues.containsKey(flagName);
    }

    private RuntimeFeatureFlags withBaseline(String flagName, boolean value) {
      Map<String, Boolean> updatedBaselines = new LinkedHashMap<>(baselineValues);
      updatedBaselines.put(flagName, value);
      return new RuntimeFeatureFlags(definitions, updatedBaselines, forcedValues);
    }
  }
}
