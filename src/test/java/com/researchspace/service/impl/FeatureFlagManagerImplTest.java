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
import com.researchspace.featureflags.FeatureFlagSource;
import com.researchspace.featureflags.FeatureFlagState;
import com.researchspace.featureflags.FeatureFlags;
import com.researchspace.model.User;
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

@ExtendWith(MockitoExtension.class)
class FeatureFlagManagerImplTest {

  @Mock private FeatureFlagDao featureFlagDao;
  @Mock private FeatureFlagManifestLoader manifestLoader;
  @Mock private FeatureFlagPropertiesLoader propertiesLoader;
  @Mock private UserManager userManager;

  private FeatureFlagManagerImpl featureFlagManager;

  @BeforeEach
  void setUp() {
    featureFlagManager = createManager("", "false");
    Subject subject = org.mockito.Mockito.mock(Subject.class);
    ThreadContext.bind(subject);
  }

  @AfterEach
  void tearDown() {
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
    FeatureFlagState state =
        featureFlagManager.getFeatureFlagStates(null).get(FeatureFlags.BOOKING_ENABLED);
    assertFalse(state.value());
    assertEquals(FeatureFlagSource.DEFAULT, state.source());
  }

  @Test
  void forcedValueWinsOverUserOverrideAndDisablesFurtherOverrides() {
    initialiseRuntime(Map.of(FeatureFlags.BOOKING_ENABLED, false), true);
    User user = user("user", 5L);
    when(featureFlagDao.getOverridesForUser(5L))
        .thenReturn(Map.of(FeatureFlags.BOOKING_ENABLED, false));

    FeatureFlagState state =
        featureFlagManager.getFeatureFlagStates(user).get(FeatureFlags.BOOKING_ENABLED);

    assertTrue(state.value());
    assertTrue(state.baselineValue());
    assertFalse(state.canOverride());
    assertEquals(FeatureFlagSource.PROPERTIES_FILE, state.source());
    assertThrows(
        FeatureFlagReadOnlyException.class,
        () -> featureFlagManager.setUserOverride(FeatureFlags.BOOKING_ENABLED, false, user));
  }

  @Test
  void devModeAllowsUsersToOverrideTheirOwnFlags() {
    featureFlagManager = createManager("true", "false");
    initialiseRuntime(Map.of(FeatureFlags.BOOKING_ENABLED, false), false);
    User user = user("user", 7L);

    featureFlagManager.setUserOverride(FeatureFlags.BOOKING_ENABLED, true, user);

    verify(featureFlagDao).setOverride(7L, FeatureFlags.BOOKING_ENABLED, true);
  }

  @Test
  void nonSysadminCannotChangeBaselineEvenInDevMode() {
    featureFlagManager = createManager("true", "false");
    initialiseRuntime(Map.of(FeatureFlags.BOOKING_ENABLED, false), false);

    assertThrows(
        FeatureFlagPermissionException.class,
        () -> featureFlagManager.setBaseline(FeatureFlags.BOOKING_ENABLED, true, user("user", 9L)));
  }

  @Test
  void invalidDevModeIsRejectedWhenCapabilityIsEvaluated() {
    featureFlagManager = createManager("invalid", "false");

    assertThrows(
        IllegalStateException.class, () -> featureFlagManager.canUseDevtools(user("user", 10L)));
  }

  @Test
  void sysadminCanChangeBaselineOutsideDevMode() {
    initialiseRuntime(Map.of(FeatureFlags.BOOKING_ENABLED, false), false);
    User sysadmin = TestFactory.createAnyUserWithRole("sysadmin", Constants.SYSADMIN_ROLE);
    sysadmin.setId(11L);

    featureFlagManager.setBaseline(FeatureFlags.BOOKING_ENABLED, true, sysadmin);

    verify(featureFlagDao).upsertBaseline(FeatureFlags.BOOKING_ENABLED, true);
    assertTrue(
        featureFlagManager
            .getFeatureFlagStates(null)
            .get(FeatureFlags.BOOKING_ENABLED)
            .baselineValue());
  }

  @Test
  void settingBaselineToManifestDefaultWithoutDatabaseRowIsNoOp() {
    initialiseRuntime(Map.of(), false);
    User sysadmin = TestFactory.createAnyUserWithRole("sysadmin", Constants.SYSADMIN_ROLE);
    sysadmin.setId(12L);

    featureFlagManager.setBaseline(FeatureFlags.BOOKING_ENABLED, false, sysadmin);

    verify(featureFlagDao, never()).upsertBaseline(FeatureFlags.BOOKING_ENABLED, false);
    FeatureFlagState state =
        featureFlagManager.getFeatureFlagStates(null).get(FeatureFlags.BOOKING_ENABLED);
    assertFalse(state.baselineValue());
    assertEquals(FeatureFlagSource.DEFAULT, state.source());
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
    return new FeatureFlagManagerImpl(
        featureFlagDao,
        manifestLoader,
        propertiesLoader,
        userManager,
        devModeEnabled,
        reactDevMode);
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

  private User user(String username, Long id) {
    User user = TestFactory.createAnyUser(username);
    user.setId(id);
    return user;
  }
}
