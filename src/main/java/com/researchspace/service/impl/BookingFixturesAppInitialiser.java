package com.researchspace.service.impl;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.booking.service.BookingConfigurationManager;
import com.researchspace.booking.service.TimeSlotBookingManager;
import com.researchspace.dao.ContainerDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.booking.BookingOpeningHoursEntry;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Adds idempotent bookable-item and booking fixtures to development deployments. */
public class BookingFixturesAppInitialiser extends AbstractAppInitializor {

  private static final String FIXTURE_USER = "user1a";
  private static final String RESTRICTED_INSTRUMENT_OWNER = "user2b";
  private static final String RESTRICTED_CONTAINER_OWNER = "user3c";
  private static final ZoneId FIXTURE_DATE_ZONE = ZoneId.of("Europe/Berlin");
  private static final BookingSchedulingSettings.Patch BLOCKOUT_FIXTURE_SETTINGS =
      new BookingSchedulingSettings.Patch(
          null,
          IntStream.rangeClosed(1, 7)
              .mapToObj(day -> new BookingOpeningHoursEntry(day, "08:00", "17:00"))
              .toList(),
          null,
          null,
          null,
          null);

  private final String devUserPassword;
  private final UserDao userDao;
  private final ContainerDao containerDao;
  private final InstrumentDao instrumentDao;
  private final ContainerApiManager containerManager;
  private final InstrumentEntityApiManager instrumentManager;
  private final MessageSourceUtils messages;
  private final BookingConfigurationManager configurationManager;
  private final TimeSlotBookingManager bookingManager;
  private final BookingConfigurationDao configurationDao;
  private final TimeSlotBookingDao bookingDao;
  private final TransactionTemplate fixtureTransaction;
  private final BookingInventoryFixtureFactory inventoryFixtures;
  private final BookingSchedulingFixtureFactory schedulingFixtures;

  public BookingFixturesAppInitialiser(
      String devUserPassword,
      UserDao userDao,
      ContainerDao containerDao,
      InstrumentDao instrumentDao,
      ContainerApiManager containerManager,
      InstrumentEntityApiManager instrumentManager,
      MessageSourceUtils messages,
      BookingConfigurationManager configurationManager,
      TimeSlotBookingManager bookingManager,
      BookingConfigurationDao configurationDao,
      TimeSlotBookingDao bookingDao,
      PlatformTransactionManager transactionManager) {
    this.devUserPassword = devUserPassword;
    this.userDao = userDao;
    this.containerDao = containerDao;
    this.instrumentDao = instrumentDao;
    this.containerManager = containerManager;
    this.instrumentManager = instrumentManager;
    this.messages = messages;
    this.configurationManager = configurationManager;
    this.bookingManager = bookingManager;
    this.configurationDao = configurationDao;
    this.bookingDao = bookingDao;
    this.inventoryFixtures =
        new BookingInventoryFixtureFactory(
            containerDao, instrumentDao, containerManager, instrumentManager, messages);
    this.schedulingFixtures =
        new BookingSchedulingFixtureFactory(
            configurationManager, bookingManager, configurationDao, bookingDao);
    this.fixtureTransaction = new TransactionTemplate(transactionManager);
    this.fixtureTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public void onAppStartup(ApplicationContext applicationContext) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              createFixturesInCommittedStages();
            }
          });
      return;
    }
    createFixturesInCommittedStages();
  }

  // GlobalInitManager is transactional. Run only after it commits so its locks cannot deadlock the
  // two independent fixture stages.
  private void createFixturesInCommittedStages() {
    FixtureIds fixtureIds = fixtureTransaction.execute(ignored -> createInventoryFixtures());
    if (fixtureIds == null) {
      return;
    }
    fixtureTransaction.executeWithoutResult(ignored -> createBookingFixtures(fixtureIds));
  }

  private FixtureIds createInventoryFixtures() {
    User owner = userDao.getUserByUsername(FIXTURE_USER);
    User restrictedInstrumentOwner = userDao.getUserByUsername(RESTRICTED_INSTRUMENT_OWNER);
    User restrictedContainerOwner = userDao.getUserByUsername(RESTRICTED_CONTAINER_OWNER);
    User sysadmin = userDao.getUserByUsername(SYSADMIN_UNAME);
    if (owner == null
        || restrictedInstrumentOwner == null
        || restrictedContainerOwner == null
        || sysadmin == null) {
      log.info("Skipping booking fixtures because the Docker development users are unavailable");
      return null;
    }

    List<Instrument> instruments = new ArrayList<>();
    List<Instrument> bookingCardInstruments = new ArrayList<>();
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

      bookingCardInstruments.add(
          ensureInstrument(message("bookingFixtures.instruments.bookingCardSetup"), owner));
      bookingCardInstruments.add(
          ensureInstrument(message("bookingFixtures.instruments.bookingCardBook"), owner));
      bookingCardInstruments.add(
          ensureInstrument(message("bookingFixtures.instruments.bookingCardDisabled"), owner));
      bookingCardInstruments.add(
          ensureInstrument(message("bookingFixtures.instruments.bookingCardArchived"), owner));
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

    return new FixtureIds(
        owner.getId(),
        sysadmin.getId(),
        instruments.stream().map(Instrument::getId).toList(),
        bookingCardInstruments.stream().map(Instrument::getId).toList());
  }

  private void createBookingFixtures(FixtureIds fixtureIds) {
    User owner = userDao.get(fixtureIds.ownerId());
    User sysadmin = userDao.get(fixtureIds.sysadminId());
    List<Instrument> instruments = reloadInstruments(fixtureIds.instrumentIds());
    List<Instrument> bookingCardInstruments =
        reloadInstruments(fixtureIds.bookingCardInstrumentIds());
    List<BookingConfiguration> configurations;
    try {
      login(new UsernamePasswordToken(SYSADMIN_UNAME, SYSADMIN_PWD, false));
      configurations =
          List.of(
              ensureConfiguration(
                  instruments.get(0), "Europe/Berlin", BLOCKOUT_FIXTURE_SETTINGS, sysadmin),
              ensureConfiguration(instruments.get(1), "America/New_York", sysadmin),
              ensureConfiguration(instruments.get(2), "UTC", sysadmin),
              ensureConfiguration(instruments.get(3), "Asia/Singapore", sysadmin),
              ensureConfiguration(instruments.get(4), "Europe/Berlin", sysadmin),
              ensureConfiguration(instruments.get(5), "Europe/Berlin", sysadmin),
              ensureConfiguration(instruments.get(6), "Europe/Berlin", sysadmin));

      ensureConfigurationState(
          bookingCardInstruments.get(1), true, BookingConfigurationState.ACTIVE, sysadmin);
      ensureConfigurationState(
          bookingCardInstruments.get(2), false, BookingConfigurationState.ACTIVE, sysadmin);
      ensureConfigurationState(
          bookingCardInstruments.get(3), true, BookingConfigurationState.ARCHIVED, sysadmin);
    } finally {
      logout();
    }

    LocalDate fixtureDate = LocalDate.now(FIXTURE_DATE_ZONE).plusDays(1);
    try {
      login(new UsernamePasswordToken(FIXTURE_USER, devUserPassword, false));
      ensureBooking(
          instruments.get(0),
          configurations.get(0),
          fixtureDate,
          9,
          0,
          10,
          30,
          message("bookingFixtures.purposes.cellImaging"),
          owner);
      ensureBooking(
          instruments.get(0),
          configurations.get(0),
          fixtureDate,
          13,
          0,
          14,
          0,
          message("bookingFixtures.purposes.calibrationRun"),
          owner);
      ensureBooking(
          instruments.get(1),
          configurations.get(1),
          fixtureDate,
          10,
          0,
          12,
          0,
          message("bookingFixtures.purposes.ultrastructureImaging"),
          owner);
      ensureBooking(
          instruments.get(2),
          configurations.get(2),
          fixtureDate,
          8,
          0,
          9,
          30,
          message("bookingFixtures.purposes.proteomicsRun"),
          owner);
      ensureBooking(
          instruments.get(3),
          configurations.get(3),
          fixtureDate,
          14,
          0,
          16,
          0,
          message("bookingFixtures.purposes.cellSorting"),
          owner);
      ensureBooking(
          instruments.get(3),
          configurations.get(3),
          fixtureDate,
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

  private List<Instrument> reloadInstruments(List<Long> instrumentIds) {
    return instrumentIds.stream().map(instrumentDao::get).toList();
  }

  private record FixtureIds(
      Long ownerId,
      Long sysadminId,
      List<Long> instrumentIds,
      List<Long> bookingCardInstrumentIds) {}

  private Instrument ensureInstrument(String name, User owner) {
    return inventoryFixtures.ensureInstrument(name, owner);
  }

  private Container ensureContainer(String name, User owner, boolean includeDeleted) {
    return inventoryFixtures.ensureContainer(name, owner, includeDeleted);
  }

  private String message(String key) {
    return inventoryFixtures.message(key);
  }

  private void moveToParent(Instrument instrument, Container parent) {
    inventoryFixtures.moveToParent(instrument, parent);
  }

  private boolean hasParent(Instrument instrument, Container parent) {
    return inventoryFixtures.hasParent(instrument, parent);
  }

  private void removeFromParent(Instrument instrument) {
    inventoryFixtures.removeFromParent(instrument);
  }

  private BookingConfiguration ensureConfiguration(
      Instrument instrument, String timeZone, User sysadmin) {
    return schedulingFixtures.ensureConfiguration(instrument, timeZone, sysadmin);
  }

  private BookingConfiguration ensureConfiguration(
      Instrument instrument,
      String timeZone,
      BookingSchedulingSettings.Patch schedulingSettings,
      User sysadmin) {
    return schedulingFixtures.ensureConfiguration(
        instrument, timeZone, schedulingSettings, sysadmin);
  }

  private BookingConfiguration ensureConfigurationState(
      Instrument instrument, boolean enabled, BookingConfigurationState state, User sysadmin) {
    return schedulingFixtures.ensureConfigurationState(instrument, enabled, state, sysadmin);
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
    schedulingFixtures.ensureBooking(
        instrument,
        configuration,
        date,
        startHour,
        startMinute,
        endHour,
        endMinute,
        purpose,
        owner);
  }

  private static ResolvedBookableTarget target(Instrument instrument) {
    return BookingSchedulingFixtureFactory.target(instrument);
  }
}
