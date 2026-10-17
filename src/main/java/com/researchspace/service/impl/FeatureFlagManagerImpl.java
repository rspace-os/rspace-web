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
import com.researchspace.service.AfterCommit;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.FeatureFlagManager.Patch;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service("featureFlagManager")
@Slf4j
public class FeatureFlagManagerImpl implements FeatureFlagManager {

  private static final Set<String> DEVELOPMENT_PROFILES = Set.of("dev", "run", "prod-test");

  private final FeatureFlagDao featureFlagDao;
  private final FeatureFlagManifestLoader manifestLoader;
  private final FeatureFlagPropertiesLoader propertiesLoader;
  private final ApplicationEventPublisher events;
  private final String devModeEnabled;
  private final String reactDevMode;
  private final String activeProfiles;

  private TransactionTemplate baselineReadTransaction;
  private volatile RuntimeFeatureFlags runtime;

  public FeatureFlagManagerImpl(
      FeatureFlagDao featureFlagDao,
      FeatureFlagManifestLoader manifestLoader,
      FeatureFlagPropertiesLoader propertiesLoader,
      ApplicationEventPublisher events,
      @Value("${dev.mode.enabled:}") String devModeEnabled,
      @Value("${reactDevMode:false}") String reactDevMode,
      @Value("${spring.profiles.active:}") String activeProfiles) {
    this.featureFlagDao = featureFlagDao;
    this.manifestLoader = manifestLoader;
    this.propertiesLoader = propertiesLoader;
    this.events = events;
    this.devModeEnabled = devModeEnabled;
    this.reactDevMode = reactDevMode;
    this.activeProfiles = activeProfiles;
  }

  @Autowired
  public void setTransactionManager(PlatformTransactionManager transactionManager) {
    baselineReadTransaction = new TransactionTemplate(transactionManager);
    baselineReadTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    baselineReadTransaction.setReadOnly(true);
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
  public List<FeatureFlagResource> getFeatureFlags(User actor) {
    RuntimeFeatureFlags snapshot = requireRuntime();
    Map<String, Boolean> overrides = getUserOverrides(actor);
    boolean canOverride = canOverrideFeatureFlags(actor);
    return snapshot.definitions().values().stream()
        .map(definition -> snapshot.resolveResource(definition, overrides, canOverride))
        .toList();
  }

  @Override
  public Optional<FeatureFlagResource> getFeatureFlag(String flagName, User actor) {
    return resolveResource(flagName, actor, requireRuntime());
  }

  @Override
  public synchronized Optional<FeatureFlagResource> updateFeatureFlag(
      String flagName, Patch patch, User subject, User actor) {
    RuntimeFeatureFlags snapshot = requireRuntime();
    if (!snapshot.definitions().containsKey(flagName)) {
      return Optional.empty();
    }
    assertWritable(flagName, snapshot);
    boolean changesBaseline = patch.baselineValue() != null;
    boolean changesOverride = patch.updateOverride();
    if (changesBaseline && !canChangeFeatureFlagBaselines(subject)) {
      throw new FeatureFlagPermissionException();
    }
    if (changesOverride && !canOverrideFeatureFlags(subject)) {
      throw new FeatureFlagPermissionException();
    }

    boolean changed = false;
    RuntimeFeatureFlags responseSnapshot = snapshot;
    if (changesBaseline) {
      BaselineUpdate baselineUpdate = setBaselineValue(snapshot, flagName, patch.baselineValue());
      responseSnapshot = baselineUpdate.snapshot();
      changed |= baselineUpdate.changed();
    }
    if (changesOverride) {
      Boolean value = patch.overrideValue();
      Map<String, Boolean> currentOverrides = getUserOverrides(subject);
      boolean hasOverride = currentOverrides.containsKey(flagName);
      if (value == null && hasOverride) {
        featureFlagDao.clearOverride(subject.getId(), flagName);
        changed = true;
      } else if (value != null && (!hasOverride || !value.equals(currentOverrides.get(flagName)))) {
        featureFlagDao.setOverride(subject.getId(), flagName, value);
        changed = true;
      }
    }
    FeatureFlagResource updated =
        resolveResource(flagName, subject, responseSnapshot).orElseThrow();
    if (changed) {
      events.publishEvent(new FeatureFlagResourceChangedEvent(actor, subject, updated));
    }
    return Optional.of(updated);
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

  private BaselineUpdate setBaselineValue(
      RuntimeFeatureFlags snapshot, String flagName, boolean value) {
    FeatureFlagDefinition definition = snapshot.definitions().get(flagName);
    RuntimeFeatureFlags current =
        new RuntimeFeatureFlags(
            snapshot.definitions(), featureFlagDao.getBaselineValues(), snapshot.forcedValues());
    boolean currentBaseline = current.baselineValue(definition);
    if (currentBaseline == value) {
      return new BaselineUpdate(current, false);
    }
    featureFlagDao.upsertBaseline(flagName, value);
    RuntimeFeatureFlags updated = current.withBaseline(flagName, value);
    updateRuntimeAfterCommit();
    return new BaselineUpdate(updated, true);
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
    if (snapshot.isForced(flagName)) {
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

  private void updateRuntimeAfterCommit() {
    AfterCommit.run(
        () -> {
          synchronized (this) {
            RuntimeFeatureFlags configured = requireRuntime();
            // The new transaction reads current DB state; this lock serializes publication.
            Map<String, Boolean> baselines =
                baselineReadTransaction.execute(status -> featureFlagDao.getBaselineValues());
            runtime =
                new RuntimeFeatureFlags(
                    configured.definitions(), baselines, configured.forcedValues());
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

  private record BaselineUpdate(RuntimeFeatureFlags snapshot, boolean changed) {}
}
