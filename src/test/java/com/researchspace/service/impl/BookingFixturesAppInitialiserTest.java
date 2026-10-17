package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.booking.service.BookingConfigurationManager;
import com.researchspace.booking.service.TimeSlotBookingManager;
import com.researchspace.dao.ContainerDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.service.IContentInitializer;
import com.researchspace.service.IGroupCreationStrategy;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class BookingFixturesAppInitialiserTest {
  private final UserDao users = mock(UserDao.class);
  private final IContentInitializer contentInitializer = mock(IContentInitializer.class);
  private final ApplicationContext applicationContext = mock(ApplicationContext.class);

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
    BookingFixturesAppInitialiser initialiser = createInitialiser(true);
    initialiser.onAppStartup(applicationContext);
    assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
  }

  @Test
  void optedInFirstDeploymentSchedulesFixturesOnlyOnceAfterCommit() {
    BookingFixturesAppInitialiser initialiser = createInitialiser(true);
    initialiser.onInitialAppDeployment();
    initialiser.onAppStartup(applicationContext);
    initialiser.onAppStartup(applicationContext);
    assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
  }

  @Test
  void fixturesAreDisabledByDefault() {
    BookingFixturesAppInitialiser initialiser = createInitialiser(false);
    initialiser.onInitialAppDeployment();
    initialiser.onAppStartup(applicationContext);
    assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"user1a", "user2b", "user3c", "sysadmin1"})
  void missingRequiredUserDoesNotInitializeContentOrCreateGroups(String missingUsername) {
    IGroupCreationStrategy groups = mock(IGroupCreationStrategy.class);
    BookingFixturesAppInitialiser initialiser = createInitialiser(false);
    for (String username : List.of("user1a", "user2b", "user3c", "sysadmin1")) {
      if (!username.equals(missingUsername)) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(users.getOptionalUserByUsername(username)).thenReturn(Optional.of(user));
      }
    }
    ReflectionTestUtils.setField(initialiser, "grpStrategy", groups);

    assertNull(ReflectionTestUtils.invokeMethod(initialiser, "createInventoryFixtures"));

    verifyNoInteractions(contentInitializer, groups);
  }

  @Test
  void unsavedRequiredUserDoesNotInitializeContentOrCreateGroups() {
    IGroupCreationStrategy groups = mock(IGroupCreationStrategy.class);
    BookingFixturesAppInitialiser initialiser = createInitialiser(false);
    for (String username : List.of("user1a", "user2b", "user3c", "sysadmin1")) {
      User user = mock(User.class);
      when(user.getId()).thenReturn(null);
      when(users.getOptionalUserByUsername(username)).thenReturn(Optional.of(user));
    }
    ReflectionTestUtils.setField(initialiser, "grpStrategy", groups);

    assertNull(ReflectionTestUtils.invokeMethod(initialiser, "createInventoryFixtures"));

    verifyNoInteractions(contentInitializer, groups);
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
    assertEquals(
        List.of(2, 3, 4, 5, 6),
        settings.openingHours().stream()
            .map(com.researchspace.model.booking.BookingOpeningHoursEntry::dayOfWeek)
            .toList());
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
                BookingSchedulingSettings.DEFAULT_OPENING_HOURS,
                BookingSchedulingSettings.DEFAULT_BUFFER_MINUTES,
                BookingSchedulingSettings.DEFAULT_BUFFER_MINUTES,
                BookingSchedulingSettings.DEFAULT_MAX_BOOKING_DURATION_MINUTES,
                BookingSchedulingSettings.DEFAULT_ALLOW_DOUBLE_BOOKING));
    assertTrue(BookingSchedulingSettings.isGranularityValid(settings.slotGranularityMinutes()));
    assertTrue(BookingSchedulingSettings.areOpeningHoursValid(settings.openingHours()));
    assertTrue(BookingSchedulingSettings.isBufferValid(settings.bufferBeforeMinutes()));
    assertTrue(BookingSchedulingSettings.isBufferValid(settings.bufferAfterMinutes()));
    assertTrue(
        BookingSchedulingSettings.isMaximumDurationValid(
            settings.maxBookingDurationMinutes(), settings.slotGranularityMinutes()));
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

  private BookingFixturesAppInitialiser createInitialiser(boolean featureBranchInstance) {
    return new BookingFixturesAppInitialiser(
        "fixture-password",
        users,
        mock(ContainerDao.class),
        mock(InstrumentDao.class),
        mock(ContainerApiManager.class),
        mock(InstrumentEntityApiManager.class),
        mock(MessageSourceUtils.class),
        mock(BookingConfigurationManager.class),
        mock(TimeSlotBookingManager.class),
        mock(BookingConfigurationDao.class),
        mock(TimeSlotBookingDao.class),
        mock(PlatformTransactionManager.class),
        featureBranchInstance,
        contentInitializer);
  }
}
