package com.researchspace.service.impl;

import static com.researchspace.model.booking.BookableTargetType.INSTRUMENT;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.booking.service.BookingConfigurationManager;
import com.researchspace.booking.service.TimeSlotBookingManager;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;

/** Adds idempotent bookable-item and booking fixtures to development deployments. */
public class BookingFixturesAppInitialiser extends AbstractAppInitializor {

  private static final String FIXTURE_USER = "user1a";
  private static final ZoneId FIXTURE_DATE_ZONE = ZoneId.of("Europe/Berlin");

  @Value("${default.user.password}")
  private String devUserPassword;

  @Autowired private UserDao userDao;
  @Autowired private InstrumentDao instrumentDao;
  @Autowired private InstrumentEntityApiManager instrumentManager;
  @Autowired private BookingConfigurationManager configurationManager;
  @Autowired private TimeSlotBookingManager bookingManager;

  @Autowired
  @Qualifier("bookingConfigurationDao")
  private BookingConfigurationDao configurationDao;

  @Autowired
  @Qualifier("timeSlotBookingDao")
  private TimeSlotBookingDao bookingDao;

  @Override
  public void onAppStartup(ApplicationContext applicationContext) {
    User owner = userDao.getUserByUsername(FIXTURE_USER);
    User sysadmin = userDao.getUserByUsername(SYSADMIN_UNAME);
    if (owner == null || sysadmin == null) {
      log.info("Skipping booking fixtures because the Docker development users are unavailable");
      return;
    }

    List<Instrument> instruments;
    try {
      login(new UsernamePasswordToken(FIXTURE_USER, devUserPassword, false));
      instruments =
          List.of(
              ensureInstrument("Confocal microscope", owner),
              ensureInstrument("Electron microscope", owner),
              ensureInstrument("Mass spectrometer", owner),
              ensureInstrument("Flow cytometer", owner));
    } finally {
      logout();
    }

    List<BookingConfiguration> configurations;
    try {
      login(new UsernamePasswordToken(SYSADMIN_UNAME, SYSADMIN_PWD, false));
      configurations =
          List.of(
              ensureConfiguration(instruments.get(0), "Europe/Berlin", sysadmin),
              ensureConfiguration(instruments.get(1), "America/New_York", sysadmin),
              ensureConfiguration(instruments.get(2), "UTC", sysadmin),
              ensureConfiguration(instruments.get(3), "Asia/Singapore", sysadmin));
    } finally {
      logout();
    }

    LocalDate today = LocalDate.now(FIXTURE_DATE_ZONE);
    try {
      login(new UsernamePasswordToken(FIXTURE_USER, devUserPassword, false));
      ensureBooking(
          instruments.get(0), configurations.get(0), today, 9, 0, 10, 30, "Cell imaging", owner);
      ensureBooking(
          instruments.get(0), configurations.get(0), today, 13, 0, 14, 0, "Calibration run", owner);
      ensureBooking(
          instruments.get(1),
          configurations.get(1),
          today,
          10,
          0,
          12,
          0,
          "Ultrastructure imaging",
          owner);
      ensureBooking(
          instruments.get(2), configurations.get(2), today, 8, 0, 9, 30, "Proteomics run", owner);
      ensureBooking(
          instruments.get(3), configurations.get(3), today, 14, 0, 16, 0, "Cell sorting", owner);
      ensureBooking(
          instruments.get(3),
          configurations.get(3),
          today,
          23,
          30,
          0,
          30,
          "Overnight analysis",
          owner);
    } finally {
      logout();
    }
  }

  private Instrument ensureInstrument(String name, User owner) {
    return instrumentDao.findInstrumentsByName(name, owner).stream()
        .filter(instrument -> !instrument.isDeleted())
        .findFirst()
        .orElseGet(
            () -> {
              ApiInstrument request = new ApiInstrument();
              request.setName(name);
              request.setDescription("Docker development booking fixture");
              ApiInstrument created = instrumentManager.createNewApiInstrument(request, owner);
              return instrumentDao.get(created.getId());
            });
  }

  private BookingConfiguration ensureConfiguration(
      Instrument instrument, String timeZone, User sysadmin) {
    ResolvedBookableTarget target = target(instrument);
    return configurationDao
        .findByTarget(target.reference())
        .orElseGet(
            () ->
                configurationManager.createConfiguration(
                    new BookingConfigurationManager.Create(true, timeZone, target),
                    sysadmin,
                    sysadmin));
  }

  private void ensureBooking(
      Instrument instrument,
      BookingConfiguration configuration,
      LocalDate date,
      int startHour,
      int startMinute,
      int endHour,
      int endMinute,
      String purpose,
      User owner) {
    if (!configuration.isEnabled()) {
      return;
    }
    ZoneId zone = ZoneId.of(configuration.getTimeZone());
    LocalTime startTime = LocalTime.of(startHour, startMinute);
    LocalTime endTime = LocalTime.of(endHour, endMinute);
    Date start = Date.from(date.atTime(startTime).atZone(zone).toInstant());
    LocalDate endDate = endTime.isAfter(startTime) ? date : date.plusDays(1);
    Date end = Date.from(endDate.atTime(endTime).atZone(zone).toInstant());
    if (!bookingDao.overlaps(configuration.getId(), start, end, null)) {
      bookingManager.createBooking(
          new TimeSlotBookingManager.Create(target(instrument), start, end, purpose), owner, owner);
    }
  }

  private static ResolvedBookableTarget target(Instrument instrument) {
    return new ResolvedBookableTarget(
        new BookableTargetReference(INSTRUMENT, instrument.getId()), instrument);
  }
}
