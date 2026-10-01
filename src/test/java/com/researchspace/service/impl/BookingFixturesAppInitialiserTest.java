package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.booking.BookingSchedulingSettings;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
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
    BookingSchedulingSettings settings =
        creatable(BookingFixturesAppInitialiser.VIEW_ONLY_FIXTURE_SETTINGS);

    assertClosed(settings, DayOfWeek.WEDNESDAY);
    assertHours(settings, DayOfWeek.THURSDAY, "10:00", "14:00");
    assertHours(settings, DayOfWeek.MONDAY, "08:00", "17:00");
  }

  @Test
  void weekdayFixtureClosesWeekendsAndShortensFridays() {
    BookingSchedulingSettings settings =
        creatable(BookingFixturesAppInitialiser.WEEKDAY_FIXTURE_SETTINGS);

    assertHours(settings, DayOfWeek.MONDAY, "08:00", "18:00");
    assertHours(settings, DayOfWeek.FRIDAY, "08:00", "12:00");
    assertClosed(settings, DayOfWeek.SATURDAY);
    assertClosed(settings, DayOfWeek.SUNDAY);
  }

  @Test
  void doubleBookingFixtureOpensAllDayOnWednesdaysAndClosesSundays() {
    BookingSchedulingSettings settings =
        creatable(BookingFixturesAppInitialiser.DOUBLE_BOOKING_FIXTURE_SETTINGS);

    assertTrue(settings.allowDoubleBooking());
    assertHours(settings, DayOfWeek.TUESDAY, "08:00", "20:00");
    assertHours(settings, DayOfWeek.WEDNESDAY, "00:00", "24:00");
    assertHours(settings, DayOfWeek.SATURDAY, "08:00", "20:00");
    assertClosed(settings, DayOfWeek.SUNDAY);
  }

  @Test
  void weekdayAllDayFixtureIsOpenAroundTheClockOnWeekdaysOnly() {
    BookingSchedulingSettings settings =
        creatable(BookingFixturesAppInitialiser.WEEKDAY_ALL_DAY_FIXTURE_SETTINGS);

    assertHours(settings, DayOfWeek.MONDAY, "00:00", "24:00");
    assertHours(settings, DayOfWeek.TUESDAY, "00:00", "24:00");
    assertClosed(settings, DayOfWeek.SATURDAY);
    assertClosed(settings, DayOfWeek.SUNDAY);
  }

  @Test
  void midnightCloseFixtureIsOpenAroundTheClockExceptSundays() {
    BookingSchedulingSettings settings =
        creatable(BookingFixturesAppInitialiser.MIDNIGHT_CLOSE_FIXTURE_SETTINGS);

    assertHours(settings, DayOfWeek.SATURDAY, "00:00", "24:00");
    assertClosed(settings, DayOfWeek.SUNDAY);
  }

  @Test
  void aucklandFixtureOpensEightToFiveEveryDay() {
    BookingSchedulingSettings settings =
        creatable(BookingFixturesAppInitialiser.AUCKLAND_FIXTURE_SETTINGS);

    for (DayOfWeek day : DayOfWeek.values()) {
      assertHours(settings, day, "08:00", "17:00");
    }
  }

  @Test
  void honoluluFixtureOpensInTheEveningAndClosesSundays() {
    BookingSchedulingSettings settings =
        creatable(BookingFixturesAppInitialiser.HONOLULU_FIXTURE_SETTINGS);

    assertHours(settings, DayOfWeek.SATURDAY, "17:00", "23:00");
    assertClosed(settings, DayOfWeek.SUNDAY);
  }

  @Test
  void kolkataFixtureUsesQuarterHourSlotsEveryDay() {
    BookingSchedulingSettings settings =
        creatable(BookingFixturesAppInitialiser.KOLKATA_FIXTURE_SETTINGS);

    assertEquals(15, settings.slotGranularityMinutes());
    for (DayOfWeek day : DayOfWeek.values()) {
      assertHours(settings, day, "09:00", "17:30");
    }
  }

  @Test
  void closedDayFixtureClosesOnlyTheGivenWeekdays() {
    BookingSchedulingSettings settings =
        creatable(
            BookingFixturesAppInitialiser.closedDayFixtureSettings(
                DayOfWeek.SUNDAY, DayOfWeek.MONDAY));

    assertClosed(settings, DayOfWeek.SUNDAY);
    assertClosed(settings, DayOfWeek.MONDAY);
    assertEquals(List.of(2, 3, 4, 5, 6), settings.openDays());
    assertHours(settings, DayOfWeek.TUESDAY, "00:00", "24:00");
  }

  @Test
  void firstOpenDayOnOrAfterKeepsAnOpenDate() {
    BookingSchedulingSettings weekdays =
        creatable(BookingFixturesAppInitialiser.WEEKDAY_FIXTURE_SETTINGS);
    LocalDate friday = LocalDate.of(2026, 10, 2);

    assertEquals(friday, BookingFixturesAppInitialiser.firstOpenDayOnOrAfter(friday, weekdays));
  }

  @Test
  void firstOpenDayOnOrAfterWrapsPastAClosedWeekend() {
    BookingSchedulingSettings weekdays =
        creatable(BookingFixturesAppInitialiser.WEEKDAY_FIXTURE_SETTINGS);
    LocalDate monday = LocalDate.of(2026, 10, 5);

    assertEquals(
        monday,
        BookingFixturesAppInitialiser.firstOpenDayOnOrAfter(LocalDate.of(2026, 10, 3), weekdays));
    assertEquals(
        monday,
        BookingFixturesAppInitialiser.firstOpenDayOnOrAfter(LocalDate.of(2026, 10, 4), weekdays));
  }

  /**
   * Merges a fixture patch into the seeded defaults and checks every rule configuration creation
   * enforces, because one rejected configuration aborts the whole booking fixture stage.
   */
  private static BookingSchedulingSettings creatable(BookingSchedulingSettings.Patch patch) {
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
    assertTrue(BookingSchedulingSettings.isGranularityValid(settings.slotGranularityMinutes()));
    assertTrue(
        BookingSchedulingSettings.areOpeningHoursValid(
            settings.openingStart(), settings.openingEnd()));
    assertTrue(BookingSchedulingSettings.isBufferValid(settings.bufferBeforeMinutes()));
    assertTrue(BookingSchedulingSettings.isBufferValid(settings.bufferAfterMinutes()));
    assertTrue(
        BookingSchedulingSettings.isMaximumDurationValid(
            settings.maxBookingDurationMinutes(), settings.slotGranularityMinutes()));
    assertTrue(BookingSchedulingSettings.areOpenDaysValid(settings.openDays()));
    assertTrue(BookingSchedulingSettings.areOpeningExceptionsValid(settings.openingExceptions()));
    assertTrue(
        BookingSchedulingSettings.areOpeningExceptionsOnOpenDays(
            settings.openingExceptions(), settings.openDays()));
    return settings;
  }

  private static void assertHours(
      BookingSchedulingSettings settings, DayOfWeek day, String start, String end) {
    assertEquals(
        Optional.of(new BookingSchedulingSettings.DailyHours(start, end)),
        settings.effectiveHours(day.getValue()));
  }

  private static void assertClosed(BookingSchedulingSettings settings, DayOfWeek day) {
    assertEquals(Optional.empty(), settings.effectiveHours(day.getValue()));
  }

  private ApplicationContext context(String... profiles) {
    ApplicationContext context = mock(ApplicationContext.class);
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(profiles);
    when(context.getEnvironment()).thenReturn(environment);
    return context;
  }
}
