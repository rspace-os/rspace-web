package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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
import com.researchspace.model.User;
import com.researchspace.service.FeatureFlagManager.Patch;
import com.researchspace.testutils.TestFactory;
import java.util.LinkedHashMap;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class FeatureFlagManagerImplTest {

  private static final String FLAG = "testFlag";

  @Mock private FeatureFlagDao featureFlagDao;
  @Mock private FeatureFlagManifestLoader manifestLoader;
  @Mock private FeatureFlagPropertiesLoader propertiesLoader;
  @Mock private ApplicationEventPublisher events;

  private FeatureFlagManagerImpl featureFlagManager;

  @BeforeEach
  void setUp() {
    featureFlagManager = createManager("", "false");
    Subject subject = mock(Subject.class);
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
    when(manifestLoader.loadDefinitions()).thenReturn(List.of(testFlag()));
    when(propertiesLoader.loadForcedValues(Set.of(FLAG))).thenReturn(Map.of());
    when(featureFlagDao.getBaselineValues()).thenReturn(Map.of("newerReleaseFlag", true));

    featureFlagManager.reconcileOnStartup();

    verify(featureFlagDao, never()).deleteBaselinesNotIn(any());
    verify(featureFlagDao, never()).deleteOverridesNotIn(any());
    FeatureFlagResource state = resource(null);
    assertFalse(state.isValue());
    assertEquals(FeatureFlagSource.DEFAULT.name(), state.getSource());
  }

  @Test
  void forcedValueWinsOverUserOverrideAndDisablesFurtherOverrides() {
    initialiseRuntime(Map.of(FLAG, false), true);
    User user = user("user", 5L);
    when(featureFlagDao.getOverridesForUser(5L)).thenReturn(Map.of(FLAG, false));

    FeatureFlagResource state = resource(user);

    assertTrue(state.isValue());
    assertTrue(state.isBaselineValue());
    assertFalse(state.isCanOverride());
    assertEquals(FeatureFlagSource.PROPERTIES_FILE.name(), state.getSource());
    assertThrows(
        FeatureFlagReadOnlyException.class,
        () -> featureFlagManager.updateFeatureFlag(FLAG, override(false), user, user));
  }

  @Test
  void devModeAllowsUsersToOverrideTheirOwnFlags() {
    featureFlagManager = createManager("true", "false");
    initialiseRuntime(Map.of(FLAG, false), false);
    User user = user("user", 7L);

    featureFlagManager.updateFeatureFlag(FLAG, override(true), user, user);

    verify(featureFlagDao).setOverride(7L, FLAG, true);
    verify(events).publishEvent(any(FeatureFlagResourceChangedEvent.class));
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
    initialiseRuntime(Map.of(FLAG, false), false);
    User user = user("user", 9L);

    assertThrows(
        FeatureFlagPermissionException.class,
        () -> featureFlagManager.updateFeatureFlag(FLAG, baseline(true), user, user));
  }

  @Test
  void invalidDevModeIsRejectedWhenCapabilityIsEvaluated() {
    featureFlagManager = createManager("invalid", "false");

    assertThrows(
        IllegalStateException.class, () -> featureFlagManager.canUseDevtools(user("user", 10L)));
  }

  @Test
  void baselineSnapshotChangesOnlyAfterTransactionCommit() {
    initialiseRuntime(Map.of(FLAG, false), false);
    stubPersistedBaselines(false);
    User sysadmin = TestFactory.createAnyUserWithRole("sysadmin", Constants.SYSADMIN_ROLE);
    sysadmin.setId(16L);
    TransactionSynchronizationManager.initSynchronization();

    FeatureFlagResource response =
        featureFlagManager
            .updateFeatureFlag(FLAG, baseline(true), sysadmin, sysadmin)
            .orElseThrow();

    assertTrue(response.isBaselineValue());
    assertFalse(resource(null).isBaselineValue());
    TransactionSynchronizationManager.getSynchronizations().stream()
        .forEach(TransactionSynchronization::afterCommit);
    assertTrue(resource(null).isBaselineValue());
  }

  @Test
  void rolledBackBaselineDoesNotChangeRuntimeSnapshot() {
    initialiseRuntime(Map.of(FLAG, false), false);
    User sysadmin = TestFactory.createAnyUserWithRole("sysadmin", Constants.SYSADMIN_ROLE);
    sysadmin.setId(17L);
    TransactionSynchronizationManager.initSynchronization();

    featureFlagManager.updateFeatureFlag(FLAG, baseline(true), sysadmin, sysadmin);
    TransactionSynchronizationManager.clearSynchronization();

    assertFalse(resource(null).isBaselineValue());
  }

  @Test
  void evaluationBeforeStartupReconciliationFailsWithoutInitialisingState() {
    assertThrows(
        IllegalStateException.class,
        () -> featureFlagManager.isFeatureFlagEnabled(FLAG, (User) null));
    verify(manifestLoader, never()).loadDefinitions();
  }

  @Test
  void settingBaselineToManifestDefaultWithoutDatabaseRowIsNoOp() {
    initialiseRuntime(Map.of(), false);
    User sysadmin = TestFactory.createAnyUserWithRole("sysadmin", Constants.SYSADMIN_ROLE);
    sysadmin.setId(12L);

    FeatureFlagResource response =
        featureFlagManager
            .updateFeatureFlag(FLAG, baseline(false), sysadmin, sysadmin)
            .orElseThrow();
    assertEquals(FeatureFlagSource.DEFAULT.name(), response.getSource());

    verify(featureFlagDao, never()).upsertBaseline(FLAG, false);
    verify(events, never()).publishEvent(any(FeatureFlagResourceChangedEvent.class));
    FeatureFlagResource state = resource(null);
    assertFalse(state.isBaselineValue());
    assertEquals(FeatureFlagSource.DEFAULT.name(), state.getSource());
  }

  @Test
  void clearingMissingOverrideIsNoOp() {
    featureFlagManager = createManager("true", "false");
    initialiseRuntime(Map.of(FLAG, false), false);
    User user = user("user", 15L);
    when(featureFlagDao.getOverridesForUser(15L)).thenReturn(Map.of());

    featureFlagManager.updateFeatureFlag(FLAG, clearOverride(), user, user);

    verify(featureFlagDao, never()).clearOverride(15L, FLAG);
    verify(events, never()).publishEvent(any(FeatureFlagResourceChangedEvent.class));
  }

  @Test
  void isFeatureFlagEnabledForExplicitUserHonoursOverrideAndFallsBackToBaseline() {
    initialiseRuntime(Map.of(FLAG, false), false);
    User user = user("user", 13L);
    when(featureFlagDao.getOverridesForUser(13L)).thenReturn(Map.of(FLAG, true));

    assertTrue(featureFlagManager.isFeatureFlagEnabled(FLAG, user));
    assertFalse(featureFlagManager.isFeatureFlagEnabled(FLAG, (User) null));
  }

  @Test
  void snapshotReadUsesPersistedBaselineInsteadOfRuntimeCache() {
    initialiseRuntime(Map.of(FLAG, true), false);
    when(featureFlagDao.getBaselineValues()).thenReturn(Map.of(FLAG, false));
    assertTrue(featureFlagManager.isFeatureFlagEnabled(FLAG, (User) null));
    assertFalse(featureFlagManager.isFeatureFlagEnabledInSnapshot(FLAG, null));
  }

  @Test
  void snapshotReadPreservesForcedDeploymentValue() {
    initialiseRuntime(Map.of(FLAG, false), true);
    assertTrue(featureFlagManager.isFeatureFlagEnabledInSnapshot(FLAG, null));
  }

  @Test
  void isFeatureFlagEnabledDoesNotLoadUserOverridesForForcedFlag() {
    initialiseRuntime(Map.of(FLAG, false), true);
    User user = user("user", 14L);

    assertTrue(featureFlagManager.isFeatureFlagEnabled(FLAG, user));

    verify(featureFlagDao, never()).getOverridesForUser(14L);
  }

  private FeatureFlagManagerImpl createManager(String devModeEnabled, String reactDevMode) {
    return createManager(devModeEnabled, reactDevMode, "dev");
  }

  private FeatureFlagManagerImpl createManager(
      String devModeEnabled, String reactDevMode, String activeProfiles) {
    FeatureFlagManagerImpl manager =
        new FeatureFlagManagerImpl(
            featureFlagDao,
            manifestLoader,
            propertiesLoader,
            events,
            devModeEnabled,
            reactDevMode,
            activeProfiles);
    manager.setTransactionManager(new NoOpTransactionManager());
    return manager;
  }

  private void initialiseRuntime(Map<String, Boolean> baselines, boolean forcedValue) {
    when(manifestLoader.loadDefinitions()).thenReturn(List.of(testFlag()));
    Map<String, Boolean> forcedValues = forcedValue ? Map.of(FLAG, true) : Map.of();
    when(propertiesLoader.loadForcedValues(Set.of(FLAG))).thenReturn(forcedValues);
    when(featureFlagDao.getBaselineValues()).thenReturn(baselines);

    featureFlagManager.reconcileOnStartup();
  }

  private Map<String, Boolean> stubPersistedBaselines(boolean enabled) {
    Map<String, Boolean> persistedBaselines = new LinkedHashMap<>(Map.of(FLAG, enabled));
    when(featureFlagDao.getBaselineValues())
        .thenAnswer(invocation -> new LinkedHashMap<>(persistedBaselines));
    doAnswer(
            invocation -> {
              persistedBaselines.put(
                  invocation.getArgument(0, String.class),
                  invocation.getArgument(1, Boolean.class));
              return null;
            })
        .when(featureFlagDao)
        .upsertBaseline(any(), anyBoolean());
    return persistedBaselines;
  }

  private FeatureFlagDefinition testFlag() {
    return new FeatureFlagDefinition(FLAG, false);
  }

  private FeatureFlagResource resource(User actor) {
    return featureFlagManager.getFeatureFlag(FLAG, actor).orElseThrow();
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

  private static class NoOpTransactionManager implements PlatformTransactionManager {
    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {}

    @Override
    public void rollback(TransactionStatus status) {}
  }
}
