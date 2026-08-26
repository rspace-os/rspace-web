package com.researchspace.service.impl;

import static com.researchspace.model.booking.BookableTargetType.INSTRUMENT;
import static com.researchspace.model.inventory.Container.ContainerType.LIST;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.booking.service.BookingConfigurationManager;
import com.researchspace.booking.service.TimeSlotBookingManager;
import com.researchspace.dao.ContainerDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;

/** Adds idempotent bookable-item and booking fixtures to development deployments. */
public class BookingFixturesAppInitialiser extends AbstractAppInitializor {

  private static final String FIXTURE_USER = "user1a";
  private static final String RESTRICTED_INSTRUMENT_OWNER = "user2b";
  private static final String RESTRICTED_CONTAINER_OWNER = "user3c";
  private static final String FIXTURE_DESCRIPTION_KEY = "bookingFixtures.description";
  private static final ZoneId FIXTURE_DATE_ZONE = ZoneId.of("Europe/Berlin");
  private static final Locale FIXTURE_LOCALE = Locale.forLanguageTag("en-US");

  @Value("${default.user.password}")
  private String devUserPassword;

  @Autowired private UserDao userDao;
  @Autowired private ContainerDao containerDao;
  @Autowired private InstrumentDao instrumentDao;
  @Autowired private ContainerApiManager containerManager;
  @Autowired private InstrumentEntityApiManager instrumentManager;
  @Autowired private MessageSourceUtils messages;
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
    User restrictedInstrumentOwner = userDao.getUserByUsername(RESTRICTED_INSTRUMENT_OWNER);
    User restrictedContainerOwner = userDao.getUserByUsername(RESTRICTED_CONTAINER_OWNER);
    User sysadmin = userDao.getUserByUsername(SYSADMIN_UNAME);
    if (owner == null
        || restrictedInstrumentOwner == null
        || restrictedContainerOwner == null
        || sysadmin == null) {
      log.info("Skipping booking fixtures because the Docker development users are unavailable");
      return;
    }

    List<Instrument> instruments = new ArrayList<>();
    try {
      login(new UsernamePasswordToken(FIXTURE_USER, devUserPassword, false));
      Instrument confocal =
          ensureInstrument(message("bookingFixtures.instruments.confocal"), owner);
      moveToParent(
          confocal,
          ensureContainer(message("bookingFixtures.containers.imagingLab"), owner, false));
      instruments.add(confocal);

      Instrument electron =
          ensureInstrument(message("bookingFixtures.instruments.electronMicroscope"), owner);
      moveToParent(
          electron,
          ensureContainer(message("bookingFixtures.containers.cryoEmSuite"), owner, false));
      instruments.add(electron);

      Instrument massSpectrometer =
          ensureInstrument(message("bookingFixtures.instruments.massSpectrometer"), owner);
      moveToParent(massSpectrometer, containerDao.getWorkbenchForUser(owner));
      instruments.add(massSpectrometer);

      Instrument flowCytometer =
          ensureInstrument(message("bookingFixtures.instruments.flowCytometer"), owner);
      moveToParent(
          flowCytometer,
          ensureContainer(
              message("bookingFixtures.containers.longCellAnalysisFacility"), owner, false));
      instruments.add(flowCytometer);

      Instrument noParent =
          ensureInstrument(message("bookingFixtures.instruments.noParentCentrifuge"), owner);
      removeFromParent(noParent);
      instruments.add(noParent);

      Instrument deletedLocation =
          ensureInstrument(message("bookingFixtures.instruments.deletedLocationSequencer"), owner);
      Container deletedParent =
          ensureContainer(message("bookingFixtures.containers.deletedInstrumentRoom"), owner, true);
      if (deletedParent.isDeleted() && !hasParent(deletedLocation, deletedParent)) {
        deletedParent.setRecordDeleted(false);
        containerDao.save(deletedParent);
      }
      moveToParent(deletedLocation, deletedParent);
      if (!deletedParent.isDeleted()) {
        deletedParent.setRecordDeleted(true);
        containerDao.save(deletedParent);
      }
      instruments.add(deletedLocation);
    } finally {
      logout();
    }

    Instrument restrictedLocation;
    try {
      login(new UsernamePasswordToken(RESTRICTED_INSTRUMENT_OWNER, devUserPassword, false));
      restrictedLocation =
          ensureInstrument(
              message("bookingFixtures.instruments.restrictedLocationPlateReader"),
              restrictedInstrumentOwner);
      restrictedLocation.setSharingMode(InventorySharingMode.OWNER_ONLY);
      instrumentDao.save(restrictedLocation);
    } finally {
      logout();
    }

    Container restrictedParent;
    try {
      login(new UsernamePasswordToken(RESTRICTED_CONTAINER_OWNER, devUserPassword, false));
      restrictedParent =
          ensureContainer(
              message("bookingFixtures.containers.restrictedInstrumentRoom"),
              restrictedContainerOwner,
              false);
      restrictedParent.setSharingMode(InventorySharingMode.OWNER_ONLY);
      containerDao.save(restrictedParent);
    } finally {
      logout();
    }
    moveToParent(restrictedLocation, restrictedParent);
    instruments.add(restrictedLocation);

    List<BookingConfiguration> configurations;
    try {
      login(new UsernamePasswordToken(SYSADMIN_UNAME, SYSADMIN_PWD, false));
      configurations =
          List.of(
              ensureConfiguration(instruments.get(0), "Europe/Berlin", sysadmin),
              ensureConfiguration(instruments.get(1), "America/New_York", sysadmin),
              ensureConfiguration(instruments.get(2), "UTC", sysadmin),
              ensureConfiguration(instruments.get(3), "Asia/Singapore", sysadmin),
              ensureConfiguration(instruments.get(4), "Europe/Berlin", sysadmin),
              ensureConfiguration(instruments.get(5), "Europe/Berlin", sysadmin),
              ensureConfiguration(instruments.get(6), "Europe/Berlin", sysadmin));
    } finally {
      logout();
    }

    LocalDate today = LocalDate.now(FIXTURE_DATE_ZONE);
    try {
      login(new UsernamePasswordToken(FIXTURE_USER, devUserPassword, false));
      ensureBooking(
          instruments.get(0),
          configurations.get(0),
          today,
          9,
          0,
          10,
          30,
          message("bookingFixtures.purposes.cellImaging"),
          owner);
      ensureBooking(
          instruments.get(0),
          configurations.get(0),
          today,
          13,
          0,
          14,
          0,
          message("bookingFixtures.purposes.calibrationRun"),
          owner);
      ensureBooking(
          instruments.get(1),
          configurations.get(1),
          today,
          10,
          0,
          12,
          0,
          message("bookingFixtures.purposes.ultrastructureImaging"),
          owner);
      ensureBooking(
          instruments.get(2),
          configurations.get(2),
          today,
          8,
          0,
          9,
          30,
          message("bookingFixtures.purposes.proteomicsRun"),
          owner);
      ensureBooking(
          instruments.get(3),
          configurations.get(3),
          today,
          14,
          0,
          16,
          0,
          message("bookingFixtures.purposes.cellSorting"),
          owner);
      ensureBooking(
          instruments.get(3),
          configurations.get(3),
          today,
          23,
          30,
          0,
          30,
          message("bookingFixtures.purposes.overnightAnalysis"),
          owner);
    } finally {
      logout();
    }
  }

  private Instrument ensureInstrument(String name, User owner) {
    String fixtureDescription = message(FIXTURE_DESCRIPTION_KEY);
    return instrumentDao.findInstrumentsByName(name, owner).stream()
        .filter(instrument -> !instrument.isDeleted())
        .filter(instrument -> fixtureDescription.equals(instrument.getDescription()))
        .findFirst()
        .orElseGet(
            () -> {
              ApiInstrument request = new ApiInstrument();
              request.setName(name);
              request.setDescription(fixtureDescription);
              ApiInstrument created = instrumentManager.createNewApiInstrument(request, owner);
              return instrumentDao.get(created.getId());
            });
  }

  private Container ensureContainer(String name, User owner, boolean includeDeleted) {
    String fixtureDescription = message(FIXTURE_DESCRIPTION_KEY);
    return containerDao.getAll().stream()
        .filter(container -> name.equals(container.getName()))
        .filter(container -> owner.getId().equals(container.getOwner().getId()))
        .filter(container -> fixtureDescription.equals(container.getDescription()))
        .filter(container -> includeDeleted || !container.isDeleted())
        .findFirst()
        .orElseGet(
            () -> {
              ApiContainer request = new ApiContainer(name, LIST);
              request.setDescription(fixtureDescription);
              ApiContainer created = containerManager.createNewApiContainer(request, owner);
              return containerDao.get(created.getId());
            });
  }

  private String message(String key) {
    return messages.getMessageForLocale(key, FIXTURE_LOCALE);
  }

  private void moveToParent(Instrument instrument, Container parent) {
    if (!hasParent(instrument, parent)) {
      instrument.moveToNewParent(parent);
      instrumentDao.save(instrument);
    }
  }

  private boolean hasParent(Instrument instrument, Container parent) {
    return instrument.getParentContainer() != null
        && parent.getId().equals(instrument.getParentContainer().getId());
  }

  private void removeFromParent(Instrument instrument) {
    if (instrument.getParentContainer() != null) {
      instrument.removeFromCurrentParent();
      instrumentDao.save(instrument);
    }
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
