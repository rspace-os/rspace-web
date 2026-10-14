package com.researchspace.service.impl;

import static com.researchspace.model.booking.BookableTargetType.INSTRUMENT;
import static com.researchspace.service.impl.AbstractAppInitializor.log;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.booking.service.BookingConfigurationManager;
import com.researchspace.booking.service.TimeSlotBookingManager;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.inventory.Instrument;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Set;

class BookingSchedulingFixtureFactory {

  private final BookingConfigurationManager configurationManager;
  private final TimeSlotBookingManager bookingManager;
  private final BookingConfigurationDao configurationDao;
  private final TimeSlotBookingDao bookingDao;

  BookingSchedulingFixtureFactory(
      BookingConfigurationManager configurationManager,
      TimeSlotBookingManager bookingManager,
      BookingConfigurationDao configurationDao,
      TimeSlotBookingDao bookingDao) {
    this.configurationManager = configurationManager;
    this.bookingManager = bookingManager;
    this.configurationDao = configurationDao;
    this.bookingDao = bookingDao;
  }

  BookingConfiguration ensureConfiguration(Instrument instrument, String timeZone, User sysadmin) {
    return ensureConfiguration(
        instrument, timeZone, BookingSchedulingSettings.Patch.empty(), sysadmin);
  }

  BookingConfiguration ensureConfiguration(
      Instrument instrument,
      String timeZone,
      BookingSchedulingSettings.Patch schedulingSettings,
      User sysadmin) {
    ResolvedBookableTarget target = target(instrument);
    BookingConfiguration configuration =
        configurationDao.findByTarget(target.reference()).orElse(null);
    if (configuration == null) {
      return configurationManager.createConfiguration(
          new BookingConfigurationManager.Create(true, timeZone, target, schedulingSettings),
          sysadmin,
          sysadmin);
    }
    BookingSchedulingSettings current = BookingSchedulingSettings.from(configuration);
    if (schedulingSettings.merge(current).equals(current)) {
      return configuration;
    }
    return configurationManager
        .updateConfiguration(
            configuration.getId(),
            new BookingConfigurationManager.Patch(null, null, schedulingSettings),
            sysadmin,
            sysadmin)
        .orElseThrow();
  }

  BookingConfiguration ensureConfigurationState(
      Instrument instrument, boolean enabled, BookingConfigurationState state, User sysadmin) {
    BookingConfiguration configuration = ensureConfiguration(instrument, "Europe/Berlin", sysadmin);
    if (state == BookingConfigurationState.ACTIVE
        && configuration.getState() == BookingConfigurationState.ARCHIVED) {
      configuration =
          configurationManager
              .updateConfiguration(
                  configuration.getId(),
                  new BookingConfigurationManager.Patch(
                      null,
                      null,
                      BookingSchedulingSettings.Patch.empty(),
                      BookingConfigurationState.ACTIVE),
                  sysadmin,
                  sysadmin)
              .orElseThrow();
    }
    if (configuration.isEnabled() != enabled) {
      configuration =
          configurationManager
              .updateConfiguration(
                  configuration.getId(),
                  new BookingConfigurationManager.Patch(enabled, null),
                  sysadmin,
                  sysadmin)
              .orElseThrow();
    }
    if (state == BookingConfigurationState.ARCHIVED
        && configuration.getState() == BookingConfigurationState.ACTIVE) {
      configuration =
          configurationManager
              .archiveConfiguration(
                  configuration.getId(),
                  configuration.getConfigurationVersion(),
                  sysadmin,
                  sysadmin)
              .orElseThrow();
    }
    return configuration;
  }

  void ensureBooking(
      Instrument instrument,
      BookingConfiguration configuration,
      LocalDate date,
      int startHour,
      int startMinute,
      int endHour,
      int endMinute,
      String purpose,
      User owner) {
    LocalTime startTime = LocalTime.of(startHour, startMinute);
    LocalTime endTime = LocalTime.of(endHour, endMinute);
    LocalDate endDate = endTime.isAfter(startTime) ? date : date.plusDays(1);
    ensureEvent(
        instrument,
        configuration,
        date.atTime(startTime),
        endDate.atTime(endTime),
        BookingEventKind.BOOKING,
        purpose,
        owner);
  }

  /** Creates one event between two wall-clock times in the item's time zone unless it exists. */
  void ensureEvent(
      Instrument instrument,
      BookingConfiguration configuration,
      LocalDateTime localStart,
      LocalDateTime localEnd,
      BookingEventKind kind,
      String purpose,
      User owner) {
    if (!configuration.isEnabled()) {
      return;
    }
    ZoneId zone = ZoneId.of(configuration.getTimeZone());
    Date start = Date.from(localStart.atZone(zone).toInstant());
    Date end = Date.from(localEnd.atZone(zone).toInstant());
    if (start.before(new Date())) {
      // The alert fixture intentionally represents a past window. Booking services reject new
      // past events, so keep startup resilient and let the dev-only SQL seed provide past rows.
      log.info("Skipping past booking fixture {}", purpose);
      return;
    }
    // A double-bookable item accepts overlapping bookings, so look for this exact booking instead.
    boolean exists =
        kind == BookingEventKind.BOOKING && configuration.isAllowDoubleBooking()
            ? bookingDao
                .findFutureConfirmedByConfiguration(configuration.getId(), new Date())
                .stream()
                .anyMatch(
                    existing ->
                        existing.getStartTime().getTime() == start.getTime()
                            && existing.getEndTime().getTime() == end.getTime()
                            && purpose.equals(existing.getPurpose()))
            : bookingDao
                .findFirstOverlap(
                    configuration.getId(),
                    start,
                    end,
                    null,
                    Set.of(BookingEventKind.BOOKING, BookingEventKind.MAINTENANCE))
                .isPresent();
    if (!exists) {
      bookingManager.createBooking(
          new TimeSlotBookingManager.Create(target(instrument), start, end, purpose, kind),
          owner,
          owner);
    }
  }

  static ResolvedBookableTarget target(Instrument instrument) {
    return new ResolvedBookableTarget(
        new BookableTargetReference(INSTRUMENT, instrument.getId()), instrument);
  }
}
