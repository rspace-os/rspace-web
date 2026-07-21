package com.researchspace.service.impl;

import com.researchspace.dao.FeatureFlagDao;
import com.researchspace.featureflags.ApiV2FeatureFlagResource;
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
import com.researchspace.model.collection.InMemoryCollectionQuery;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.UserManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service("featureFlagManager")
@Slf4j
public class FeatureFlagManagerImpl implements FeatureFlagManager {

  private final FeatureFlagDao featureFlagDao;
  private final FeatureFlagManifestLoader manifestLoader;
  private final FeatureFlagPropertiesLoader propertiesLoader;
  private final UserManager userManager;
  private final ApplicationEventPublisher events;
  private final String devModeEnabled;
  private final String reactDevMode;

  private volatile RuntimeFeatureFlags runtime;

  private final InMemoryCollectionQuery<FeatureFlagResource> resourceQuery =
      new InMemoryCollectionQuery<>(ApiV2FeatureFlagResource.DESCRIPTION);

  public FeatureFlagManagerImpl(
      FeatureFlagDao featureFlagDao,
      FeatureFlagManifestLoader manifestLoader,
      FeatureFlagPropertiesLoader propertiesLoader,
      UserManager userManager,
      ApplicationEventPublisher events,
      @Value("${dev.mode.enabled:}") String devModeEnabled,
      @Value("${reactDevMode:false}") String reactDevMode) {
    this.featureFlagDao = featureFlagDao;
    this.manifestLoader = manifestLoader;
    this.propertiesLoader = propertiesLoader;
    this.userManager = userManager;
    this.events = events;
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
  public ResourcePage<FeatureFlagResource> getResources(ResourceRequest request, User actor) {
    return resourceQuery.page(resources(actor), request);
  }

  @Override
  public long countResources(ResourceRequest request, User actor) {
    return resourceQuery.count(resources(actor), request);
  }

  @Override
  public Optional<FeatureFlagResource> getResource(String flagName, User actor) {
    ensureReady();
    FeatureFlagDefinition definition = runtime.definitions().get(flagName);
    if (definition == null) {
      return Optional.empty();
    }
    Map<String, Boolean> overrides = getUserOverrides(actor);
    return Optional.of(
        runtime.resolveResource(definition, overrides, canOverrideFeatureFlags(actor)));
  }

  @Override
  public synchronized Optional<FeatureFlagResource> updateResource(
      String flagName, ParsedDocument patch, User actor) {
    ensureReady();
    if (!runtime.definitions().containsKey(flagName)) {
      return Optional.empty();
    }
    if (patch.operation() != WriteOperation.UPDATE) {
      throw new IllegalArgumentException("Feature flag change requires an update document");
    }
    assertWritable(flagName);
    boolean changesBaseline = patch.values().containsKey("baselineValue");
    boolean changesOverride = patch.values().containsKey("overrideValue");
    if (changesBaseline && !canChangeFeatureFlagBaselines(actor)) {
      throw new FeatureFlagPermissionException();
    }
    if (changesOverride && !canOverrideFeatureFlags(actor)) {
      throw new FeatureFlagPermissionException();
    }

    boolean changed = false;
    if (changesBaseline) {
      changed = setBaselineValue(flagName, (boolean) patch.values().get("baselineValue"));
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
    FeatureFlagResource updated = getResource(flagName, actor).orElseThrow();
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
    ensureReady();
    RuntimeFeatureFlags snapshot = runtime;
    FeatureFlagDefinition definition = snapshot.definitions().get(flagName);
    if (definition == null) {
      throw new FeatureFlagNotFoundException(flagName);
    }
    Map<String, Boolean> userOverrides =
        snapshot.isForced(flagName) ? Map.of() : getUserOverrides(user);
    return snapshot.resolveResource(definition, userOverrides, false).isValue();
  }

  private boolean setBaselineValue(String flagName, boolean value) {
    RuntimeFeatureFlags snapshot = runtime;
    FeatureFlagDefinition definition = snapshot.definitions().get(flagName);
    boolean currentBaseline = snapshot.baselineValue(definition);
    if (currentBaseline == value) {
      return false;
    }
    featureFlagDao.upsertBaseline(flagName, value);
    Map<String, Boolean> updatedBaselines = new LinkedHashMap<>(snapshot.baselineValues());
    updatedBaselines.put(flagName, value);
    runtime =
        new RuntimeFeatureFlags(snapshot.definitions(), updatedBaselines, snapshot.forcedValues());
    return true;
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

  private void assertWritable(String flagName) {
    ensureReady();
    if (runtime.forcedValues().containsKey(flagName)) {
      throw new FeatureFlagReadOnlyException(flagName);
    }
  }

  private List<FeatureFlagResource> resources(User actor) {
    ensureReady();
    RuntimeFeatureFlags snapshot = runtime;
    Map<String, Boolean> overrides = getUserOverrides(actor);
    boolean canOverride = canOverrideFeatureFlags(actor);
    return snapshot.definitions().values().stream()
        .map(definition -> snapshot.resolveResource(definition, overrides, canOverride))
        .toList();
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
  }
}
