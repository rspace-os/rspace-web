package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.booking.BookingSchedulingSettings;
import java.time.DayOfWeek;
import java.util.Optional;
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

  @Test
  void viewOnlyFixtureClosesWednesdaysAndShortensThursdays() {
    BookingSchedulingSettings.Patch patch =
        BookingFixturesAppInitialiser.VIEW_ONLY_FIXTURE_SETTINGS;
    BookingSchedulingSettings settings =
        patch.merge(
            new BookingSchedulingSettings(
                BookingSchedulingSettings.DEFAULT_SLOT_GRANULARITY_MINUTES,
                BookingSchedulingSettings.DEFAULT_OPENING_START,
                BookingSchedulingSettings.DEFAULT_OPENING_END,
                BookingSchedulingSettings.DEFAULT_OPEN_DAYS,
                BookingSchedulingSettings.DEFAULT_OPENING_EXCEPTIONS,
                BookingSchedulingSettings.DEFAULT_BUFFER_MINUTES,
                BookingSchedulingSettings.DEFAULT_BUFFER_MINUTES,
                BookingSchedulingSettings.DEFAULT_MAX_BOOKING_DURATION_MINUTES,
                BookingSchedulingSettings.DEFAULT_ALLOW_DOUBLE_BOOKING));

    // Configuration creation rejects these, which would abort the whole fixture stage.
    assertTrue(BookingSchedulingSettings.areOpenDaysValid(settings.openDays()));
    assertTrue(BookingSchedulingSettings.areOpeningExceptionsValid(settings.openingExceptions()));
    assertTrue(
        BookingSchedulingSettings.areOpeningExceptionsOnOpenDays(
            settings.openingExceptions(), settings.openDays()));
    assertEquals(Optional.empty(), settings.effectiveHours(DayOfWeek.WEDNESDAY.getValue()));
    assertEquals(
        Optional.of(new BookingSchedulingSettings.DailyHours("10:00", "14:00")),
        settings.effectiveHours(DayOfWeek.THURSDAY.getValue()));
    assertEquals(
        Optional.of(new BookingSchedulingSettings.DailyHours("08:00", "17:00")),
        settings.effectiveHours(DayOfWeek.MONDAY.getValue()));
  }

  private ApplicationContext context(String... profiles) {
    ApplicationContext context = mock(ApplicationContext.class);
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(profiles);
    when(context.getEnvironment()).thenReturn(environment);
    return context;
  }
}
