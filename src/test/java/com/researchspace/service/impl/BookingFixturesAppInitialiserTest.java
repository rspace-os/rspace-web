package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class BookingFixturesAppInitialiserTest {
  private final BookingFixturesAppInitialiser initialiser = new BookingFixturesAppInitialiser();

  @BeforeEach
  void startStartupTransaction() {
    TransactionSynchronizationManager.initSynchronization();
  }

  @AfterEach
  void clearStartupTransaction() {
    TransactionSynchronizationManager.clearSynchronization();
  }

  @Test
  void existingDevelopmentDatabaseDoesNotScheduleFixturesOrAttemptLogin() {
    ReflectionTestUtils.setField(initialiser, "featureBranchInstance", true);
    initialiser.onAppStartup(context("run"));
    assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"run", "prod", "prod-test"})
  void optedInFirstDeploymentSchedulesFixturesOnlyOnceAfterCommit(String profile) {
    ReflectionTestUtils.setField(initialiser, "featureBranchInstance", true);
    initialiser.onInitialAppDeployment();
    initialiser.onAppStartup(context(profile));
    initialiser.onAppStartup(context(profile));
    assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
  }

  @Test
  void fixturesAreDisabledByDefaultInEveryProfile() {
    initialiser.onInitialAppDeployment();
    initialiser.onAppStartup(context("run"));
    initialiser.onAppStartup(context("prod"));
    initialiser.onAppStartup(context("prod-test"));
    initialiser.onAppStartup(context("run", "prod"));
    initialiser.onAppStartup(context("run", "prod-test"));
    assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
  }

  private ApplicationContext context(String... profiles) {
    ApplicationContext context = mock(ApplicationContext.class);
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(profiles);
    when(context.getEnvironment()).thenReturn(environment);
    return context;
  }
}
