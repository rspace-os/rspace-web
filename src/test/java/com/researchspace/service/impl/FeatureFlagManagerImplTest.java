package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.dao.FeatureFlagDao;
import com.researchspace.featureflags.FeatureFlagDefinition;
import com.researchspace.featureflags.FeatureFlagManifestLoader;
import com.researchspace.featureflags.FeatureFlagPermissionException;
import com.researchspace.featureflags.FeatureFlagPropertiesLoader;
import com.researchspace.featureflags.FeatureFlagReadOnlyException;
import com.researchspace.featureflags.FeatureFlagResource;
import com.researchspace.featureflags.FeatureFlagSource;
import com.researchspace.featureflags.FeatureFlags;
import com.researchspace.model.User;
import com.researchspace.service.FeatureFlagManager.Patch;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class FeatureFlagManagerImplTest {

  @Mock private FeatureFlagDao featureFlagDao;
  @Mock private FeatureFlagManifestLoader manifestLoader;
  @Mock private FeatureFlagPropertiesLoader propertiesLoader;
  @Mock private UserManager userManager;
  @Mock private ApplicationEventPublisher events;

  private FeatureFlagManagerImpl featureFlagManager;

  @BeforeEach
  void setUp() {
    featureFlagManager = createManager("", "false");
    Subject subject = org.mockito.Mockito.mock(Subject.class);
    ThreadContext.bind(subject);
  }

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    ThreadContext.unbindSubject();
  }

  @Test
  void reconcileWithoutBaselineRowReportsDefinitionDefault() {
    when(manifestLoader.loadDefinitions()).thenReturn(List.of(bookingFlag(false)));
    when(propertiesLoader.loadForcedValues(Set.of(FeatureFlags.BOOKING_ENABLED)))
        .thenReturn(Map.of());
    when(featureFlagDao.getBaselineValues()).thenReturn(Map.of());

    featureFlagManager.reconcileOnStartup();

    verify(featureFlagDao).deleteBaselinesNotIn(Set.of(FeatureFlags.BOOKING_ENABLED));
    verify(featureFlagDao).deleteOverridesNotIn(Set.of(FeatureFlags.BOOKING_ENABLED));
    FeatureFlagResource state = resource(null);
    assertFalse(state.isValue());
    assertEquals(FeatureFlagSource.DEFAULT.name(), state.getSource());
  }

  @Test
  void forcedValueWinsOverUserOverrideAndDisablesFurtherOverrides() {
    initialiseRuntime(Map.of(FeatureFlags.BOOKING_ENABLED, false), true);
    User user = user("user", 5L);
    when(featureFlagDao.getOverridesForUser(5L))
        .thenReturn(Map.of(FeatureFlags.BOOKING_ENABLED, false));

    FeatureFlagResource state = resource(user);

    assertTrue(state.isValue());
    assertTrue(state.isBaselineValue());
    assertFalse(state.isCanOverride());
    assertEquals(FeatureFlagSource.PROPERTIES_FILE.name(), state.getSource());
    assertThrows(
        FeatureFlagReadOnlyException.class,
        () ->
            featureFlagManager.updateFeatureFlag(
                FeatureFlags.BOOKING_ENABLED, override(false), user));
  }

  @Test
  void devModeAllowsUsersToOverrideTheirOwnFlags() {
    featureFlagManager = createManager("true", "false");
    initialiseRuntime(Map.of(FeatureFlags.BOOKING_ENABLED, false), false);
    User user = user("user", 7L);

    featureFlagManager.updateFeatureFlag(FeatureFlags.BOOKING_ENABLED, override(true), user);

    verify(featureFlagDao).setOverride(7L, FeatureFlags.BOOKING_ENABLED, true);
    verify(events)
        .publishEvent(org.mockito.ArgumentMatchers.any(FeatureFlagResourceChangedEvent.class));
  }

  @Test
  void devModeCannotBeEnabledOnAProductionSystem() {
    featureFlagManager = createManager("true", "false", "prod");

    assertFalse(featureFlagManager.canUseDevtools(user("user", 8L)));
  }

  @Test
  void reactDevModeCannotEnableDevtoolsOnAProductionSystem() {
    featureFlagManager = createManager("", "true", "prod");

    assertFalse(featureFlagManager.canUseDevtools(user("user", 18L)));
  }

  @Test
  void nonSysadminCannotChangeBaselineEvenInDevMode() {
    featureFlagManager = createManager("true", "false");
    initialiseRuntime(Map.of(FeatureFlags.BOOKING_ENABLED, false), false);

    assertThrows(
        FeatureFlagPermissionException.class,
        () ->
            featureFlagManager.updateFeatureFlag(
                FeatureFlags.BOOKING_ENABLED, baseline(true), user("user", 9L)));
  }

  @Test
  void invalidDevModeIsRejectedWhenCapabilityIsEvaluated() {
    featureFlagManager = createManager("invalid", "false");

    assertThrows(
        IllegalStateException.class, () -> featureFlagManager.canUseDevtools(user("user", 10L)));
  }

  @Test
  void baselineSnapshotChangesOnlyAfterTransactionCommit() {
    initialiseRuntime(Map.of(FeatureFlags.BOOKING_ENABLED, false), false);
    User sysadmin = TestFactory.createAnyUserWithRole("sysadmin", Constants.SYSADMIN_ROLE);
    sysadmin.setId(16L);
    TransactionSynchronizationManager.initSynchronization();

    FeatureFlagResource response =
        featureFlagManager
            .updateFeatureFlag(FeatureFlags.BOOKING_ENABLED, baseline(true), sysadmin)
            .orElseThrow();

    assertTrue(response.isBaselineValue());
    assertFalse(resource(null).isBaselineValue());
    TransactionSynchronizationManager.getSynchronizations().stream()
        .forEach(TransactionSynchronization::afterCommit);
    assertTrue(resource(null).isBaselineValue());
  }

  @Test
  void rolledBackBaselineDoesNotChangeRuntimeSnapshot() {
    initialiseRuntime(Map.of(FeatureFlags.BOOKING_ENABLED, false), false);
    User sysadmin = TestFactory.createAnyUserWithRole("sysadmin", Constants.SYSADMIN_ROLE);
    sysadmin.setId(17L);
    TransactionSynchronizationManager.initSynchronization();

    featureFlagManager.updateFeatureFlag(FeatureFlags.BOOKING_ENABLED, baseline(true), sysadmin);
    TransactionSynchronizationManager.clearSynchronization();

    assertFalse(resource(null).isBaselineValue());
  }

  @Test
  void evaluationBeforeStartupReconciliationFailsWithoutInitialisingState() {
    assertThrows(
        IllegalStateException.class,
        () -> featureFlagManager.isFeatureFlagEnabled(FeatureFlags.BOOKING_ENABLED, (User) null));
    verify(manifestLoader, never()).loadDefinitions();
  }

  @Test
  void settingBaselineToManifestDefaultWithoutDatabaseRowIsNoOp() {
    initialiseRuntime(Map.of(), false);
    User sysadmin = TestFactory.createAnyUserWithRole("sysadmin", Constants.SYSADMIN_ROLE);
    sysadmin.setId(12L);

    featureFlagManager.updateFeatureFlag(FeatureFlags.BOOKING_ENABLED, baseline(false), sysadmin);

    verify(featureFlagDao, never()).upsertBaseline(FeatureFlags.BOOKING_ENABLED, false);
    verify(events, never())
        .publishEvent(org.mockito.ArgumentMatchers.any(FeatureFlagResourceChangedEvent.class));
    FeatureFlagResource state = resource(null);
    assertFalse(state.isBaselineValue());
    assertEquals(FeatureFlagSource.DEFAULT.name(), state.getSource());
  }

  @Test
  void clearingMissingOverrideIsNoOp() {
    featureFlagManager = createManager("true", "false");
    initialiseRuntime(Map.of(FeatureFlags.BOOKING_ENABLED, false), false);
    User user = user("user", 15L);
    when(featureFlagDao.getOverridesForUser(15L)).thenReturn(Map.of());

    featureFlagManager.updateFeatureFlag(FeatureFlags.BOOKING_ENABLED, clearOverride(), user);

    verify(featureFlagDao, never()).clearOverride(15L, FeatureFlags.BOOKING_ENABLED);
    verify(events, never())
        .publishEvent(org.mockito.ArgumentMatchers.any(FeatureFlagResourceChangedEvent.class));
  }

  @Test
  void isFeatureFlagEnabledForExplicitUserHonoursOverrideAndFallsBackToBaseline() {
    initialiseRuntime(Map.of(FeatureFlags.BOOKING_ENABLED, false), false);
    User user = user("user", 13L);
    when(featureFlagDao.getOverridesForUser(13L))
        .thenReturn(Map.of(FeatureFlags.BOOKING_ENABLED, true));

    assertTrue(featureFlagManager.isFeatureFlagEnabled(FeatureFlags.BOOKING_ENABLED, user));
    assertFalse(featureFlagManager.isFeatureFlagEnabled(FeatureFlags.BOOKING_ENABLED, (User) null));
  }

  @Test
  void isFeatureFlagEnabledDoesNotLoadUserOverridesForForcedFlag() {
    initialiseRuntime(Map.of(FeatureFlags.BOOKING_ENABLED, false), true);
    User user = user("user", 14L);

    assertTrue(featureFlagManager.isFeatureFlagEnabled(FeatureFlags.BOOKING_ENABLED, user));

    verify(featureFlagDao, never()).getOverridesForUser(14L);
  }

  private FeatureFlagManagerImpl createManager(String devModeEnabled, String reactDevMode) {
    return createManager(devModeEnabled, reactDevMode, "dev");
  }

  private FeatureFlagManagerImpl createManager(
      String devModeEnabled, String reactDevMode, String activeProfiles) {
    return new FeatureFlagManagerImpl(
        featureFlagDao,
        manifestLoader,
        propertiesLoader,
        userManager,
        events,
        devModeEnabled,
        reactDevMode,
        activeProfiles);
  }

  private void initialiseRuntime(Map<String, Boolean> baselines, boolean forcedValue) {
    when(manifestLoader.loadDefinitions()).thenReturn(List.of(bookingFlag(false)));
    Map<String, Boolean> forcedValues =
        forcedValue ? Map.of(FeatureFlags.BOOKING_ENABLED, true) : Map.of();
    when(propertiesLoader.loadForcedValues(Set.of(FeatureFlags.BOOKING_ENABLED)))
        .thenReturn(forcedValues);
    when(featureFlagDao.getBaselineValues()).thenReturn(baselines);

    featureFlagManager.reconcileOnStartup();
  }

  private FeatureFlagDefinition bookingFlag(boolean defaultValue) {
    return new FeatureFlagDefinition(FeatureFlags.BOOKING_ENABLED, defaultValue);
  }

  private FeatureFlagResource resource(User actor) {
    return featureFlagManager.getFeatureFlag(FeatureFlags.BOOKING_ENABLED, actor).orElseThrow();
  }

  private static Patch baseline(boolean value) {
    return new Patch(value, false, null);
  }

  private static Patch override(boolean value) {
    return new Patch(null, true, value);
  }

  private static Patch clearOverride() {
    return new Patch(null, true, null);
  }

  private User user(String username, Long id) {
    User user = TestFactory.createAnyUser(username);
    user.setId(id);
    return user;
  }
}
