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
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.BookingOpeningHoursEntry;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import com.researchspace.service.AfterCommit;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Adds booking fixtures on first deployment when feature-branch instance seeding is enabled. */
public class BookingFixturesAppInitialiser extends AbstractAppInitializor {

  private static final String FIXTURE_USER = "user1a";
  private static final String RESTRICTED_INSTRUMENT_OWNER = "user2b";
  private static final String RESTRICTED_CONTAINER_OWNER = "user3c";
  private static final ZoneId FIXTURE_DATE_ZONE = ZoneId.of("Europe/Berlin");
  private static final int BUSY_CALENDAR_INSTRUMENT_COUNT = 500;
  private static final int BUSY_CALENDAR_EVENTS_PER_INSTRUMENT = 2;
  private static final List<String> SEARCH_EVENT_FIXTURE_MESSAGE_KEYS =
      List.of(
          "bookingFixtures.purposes.auroraCalibration",
          "bookingFixtures.purposes.beaconCellImaging",
          "bookingFixtures.purposes.cometProteomics");
  private static final int SEARCH_EVENT_FIXTURE_COUNT = SEARCH_EVENT_FIXTURE_MESSAGE_KEYS.size();
  private static final List<BookingOpeningHoursEntry> STANDARD_FIXTURE_OPENING_HOURS =
      schedule("08:00", "17:00", 1, 2, 3, 4, 5, 6, 7);
  private static final BookingSchedulingSettings.Patch BLOCKOUT_FIXTURE_SETTINGS =
      new BookingSchedulingSettings.Patch(
          null, STANDARD_FIXTURE_OPENING_HOURS, null, null, null, null);
  private static final BookingSchedulingSettings.Patch ALERT_FIXTURE_SETTINGS =
      new BookingSchedulingSettings.Patch(
          15L, STANDARD_FIXTURE_OPENING_HOURS, null, null, 60L, false);

  /**
   * The restricted-location plate reader is view-only for {@code user1a}: as PI of the owner's
   * group it reads the owner-only item but can neither book it nor edit its configuration. Closed
   * on Wednesdays with shorter Thursday hours, so the Calendar shades closures on a read-only row.
   */
  static final BookingSchedulingSettings.Patch VIEW_ONLY_FIXTURE_SETTINGS =
      new BookingSchedulingSettings.Patch(
          null,
          List.of(
              new BookingOpeningHoursEntry(1, "08:00", "17:00"),
              new BookingOpeningHoursEntry(2, "08:00", "17:00"),
              new BookingOpeningHoursEntry(4, "10:00", "14:00"),
              new BookingOpeningHoursEntry(5, "08:00", "17:00"),
              new BookingOpeningHoursEntry(6, "08:00", "17:00"),
              new BookingOpeningHoursEntry(7, "08:00", "17:00")),
          null,
          null,
          null,
          null);

  /** The New York electron microscope closes at weekends and at noon on Fridays. */
  static final BookingSchedulingSettings.Patch WEEKDAY_FIXTURE_SETTINGS =
      new BookingSchedulingSettings.Patch(
          null,
          List.of(
              new BookingOpeningHoursEntry(1, "08:00", "18:00"),
              new BookingOpeningHoursEntry(2, "08:00", "18:00"),
              new BookingOpeningHoursEntry(3, "08:00", "18:00"),
              new BookingOpeningHoursEntry(4, "08:00", "18:00"),
              new BookingOpeningHoursEntry(5, "08:00", "12:00")),
          null,
          null,
          null,
          null);

  /**
   * The UTC mass spectrometer accepts overlapping bookings. It is closed on Sundays and open all
   * day on Wednesdays, although its shared hours are partial.
   */
  static final BookingSchedulingSettings.Patch DOUBLE_BOOKING_FIXTURE_SETTINGS =
      new BookingSchedulingSettings.Patch(
          null,
          List.of(
              new BookingOpeningHoursEntry(1, "08:00", "20:00"),
              new BookingOpeningHoursEntry(2, "08:00", "20:00"),
              new BookingOpeningHoursEntry(3, "00:00", "24:00"),
              new BookingOpeningHoursEntry(4, "08:00", "20:00"),
              new BookingOpeningHoursEntry(5, "08:00", "20:00"),
              new BookingOpeningHoursEntry(6, "08:00", "20:00")),
          null,
          null,
          null,
          true);

  /** The deleted-location sequencer is open around the clock on weekdays only. */
  static final BookingSchedulingSettings.Patch WEEKDAY_ALL_DAY_FIXTURE_SETTINGS =
      new BookingSchedulingSettings.Patch(
          null, schedule("00:00", "24:00", 1, 2, 3, 4, 5), null, null, null, null);

  /**
   * The no-parent centrifuge is open around the clock except on Sundays, so a Saturday booking can
   * end at the midnight before the closed day.
   */
  static final BookingSchedulingSettings.Patch MIDNIGHT_CLOSE_FIXTURE_SETTINGS =
      new BookingSchedulingSettings.Patch(
          null, schedule("00:00", "24:00", 1, 2, 3, 4, 5, 6), null, null, null, null);

  /** Auckland's working day falls overnight for a European viewer. */
  static final BookingSchedulingSettings.Patch AUCKLAND_FIXTURE_SETTINGS =
      new BookingSchedulingSettings.Patch(
          null, STANDARD_FIXTURE_OPENING_HOURS, null, null, null, null);

  /** Honolulu's evening hours fall on the next European day, so the weekdays differ. */
  static final BookingSchedulingSettings.Patch HONOLULU_FIXTURE_SETTINGS =
      new BookingSchedulingSettings.Patch(
          null, schedule("17:00", "23:00", 1, 2, 3, 4, 5, 6), null, null, null, null);

  /** Kolkata's UTC+05:30 offset moves quarter-hour slots onto :15 and :45 for most viewers. */
  static final BookingSchedulingSettings.Patch KOLKATA_FIXTURE_SETTINGS =
      new BookingSchedulingSettings.Patch(
          15L, schedule("09:00", "17:30", 1, 2, 3, 4, 5, 6, 7), null, null, null, null);

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
  private final boolean featureBranchInstance;
  private boolean initialDeployment;
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
      PlatformTransactionManager transactionManager,
      boolean featureBranchInstance) {
    this.devUserPassword = devUserPassword;
    this.featureBranchInstance = featureBranchInstance;
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
  public void onInitialAppDeployment() {
    initialDeployment = true;
  }

  @Override
  public void onAppStartup(ApplicationContext applicationContext) {
    if (!initialDeployment || !featureBranchInstance) {
      return;
    }
    initialDeployment = false;
    AfterCommit.run(this::createFixturesInCommittedStages);
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
    List<Instrument> schedulingEdgeInstruments = new ArrayList<>();
    List<Instrument> bookingCardInstruments = new ArrayList<>();
    List<Instrument> busyCalendarInstruments =
        asUser(
            FIXTURE_USER,
            devUserPassword,
            () -> {
              Instrument confocal =
                  ensureInstrument(message("bookingFixtures.instruments.confocal"), owner);
              moveToParent(
                  confocal,
                  ensureContainer(message("bookingFixtures.containers.imagingLab"), owner, false));
              instruments.add(confocal);

              Instrument electron =
                  ensureInstrument(
                      message("bookingFixtures.instruments.electronMicroscope"), owner);
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
                      message("bookingFixtures.containers.longCellAnalysisFacility"),
                      owner,
                      false));
              instruments.add(flowCytometer);

              Instrument noParent =
                  ensureInstrument(
                      message("bookingFixtures.instruments.noParentCentrifuge"), owner);
              removeFromParent(noParent);
              instruments.add(noParent);

              Instrument deletedLocation =
                  ensureInstrument(
                      message("bookingFixtures.instruments.deletedLocationSequencer"), owner);
              Container deletedParent =
                  ensureContainer(
                      message("bookingFixtures.containers.deletedInstrumentRoom"), owner, true);
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
                  ensureInstrument(
                      message("bookingFixtures.instruments.bookingCardDisabled"), owner));
              bookingCardInstruments.add(
                  ensureInstrument(
                      message("bookingFixtures.instruments.bookingCardArchived"), owner));

              List<Instrument> busyCalendar = ensureBusyCalendarInstruments(owner);
              instruments.add(
                  ensureInstrument(message("bookingFixtures.instruments.bookingAlerts"), owner));
              schedulingEdgeInstruments.add(
                  ensureInstrument(
                      message("bookingFixtures.instruments.aucklandPcrCycler"), owner));
              schedulingEdgeInstruments.add(
                  ensureInstrument(
                      message("bookingFixtures.instruments.honoluluDiffractometer"), owner));
              schedulingEdgeInstruments.add(
                  ensureInstrument(
                      message("bookingFixtures.instruments.kolkataNmrSpectrometer"), owner));
              schedulingEdgeInstruments.add(
                  ensureInstrument(
                      message("bookingFixtures.instruments.closedDayIncubator"), owner));
              return busyCalendar;
            });

    Instrument restrictedLocation =
        asUser(
            RESTRICTED_INSTRUMENT_OWNER,
            devUserPassword,
            () -> {
              Instrument instrument =
                  ensureInstrument(
                      message("bookingFixtures.instruments.restrictedLocationPlateReader"),
                      restrictedInstrumentOwner);
              instrument.setSharingMode(InventorySharingMode.OWNER_ONLY);
              instrumentDao.save(instrument);
              return instrument;
            });

    Container restrictedParent =
        asUser(
            RESTRICTED_CONTAINER_OWNER,
            devUserPassword,
            () -> {
              Container container =
                  ensureContainer(
                      message("bookingFixtures.containers.restrictedInstrumentRoom"),
                      restrictedContainerOwner,
                      false);
              container.setSharingMode(InventorySharingMode.OWNER_ONLY);
              containerDao.save(container);
              return container;
            });
    moveToParent(restrictedLocation, restrictedParent);
    instruments.add(restrictedLocation);
    // Indices 8 to 11 follow the plate reader so the earlier fixture indices stay stable.
    instruments.addAll(schedulingEdgeInstruments);

    return new FixtureIds(
        owner.getId(),
        sysadmin.getId(),
        instruments.stream().map(Instrument::getId).toList(),
        bookingCardInstruments.stream().map(Instrument::getId).toList(),
        busyCalendarInstruments.stream().map(Instrument::getId).toList());
  }

  private void createBookingFixtures(FixtureIds fixtureIds) {
    User owner = userDao.get(fixtureIds.ownerId());
    User sysadmin = userDao.get(fixtureIds.sysadminId());
    List<Instrument> instruments = reloadInstruments(fixtureIds.instrumentIds());
    List<Instrument> bookingCardInstruments =
        reloadInstruments(fixtureIds.bookingCardInstrumentIds());
    List<Instrument> busyCalendarInstruments =
        reloadInstruments(fixtureIds.busyCalendarInstrumentIds());
    FixtureConfigurationSet fixtureConfigurationSet =
        asUser(
            SYSADMIN_UNAME,
            SYSADMIN_PWD,
            () -> {
              List<BookingConfiguration> configurations =
                  List.of(
                      ensureConfiguration(
                          instruments.get(0), "Europe/Berlin", BLOCKOUT_FIXTURE_SETTINGS, sysadmin),
                      ensureConfiguration(
                          instruments.get(1),
                          "America/New_York",
                          WEEKDAY_FIXTURE_SETTINGS,
                          sysadmin),
                      ensureConfiguration(
                          instruments.get(2), "UTC", DOUBLE_BOOKING_FIXTURE_SETTINGS, sysadmin),
                      ensureConfiguration(instruments.get(3), "Asia/Singapore", sysadmin),
                      ensureConfiguration(
                          instruments.get(4),
                          "Europe/Berlin",
                          MIDNIGHT_CLOSE_FIXTURE_SETTINGS,
                          sysadmin),
                      ensureConfiguration(
                          instruments.get(5),
                          "Europe/Berlin",
                          WEEKDAY_ALL_DAY_FIXTURE_SETTINGS,
                          sysadmin),
                      ensureConfiguration(
                          instruments.get(6), "Europe/Berlin", ALERT_FIXTURE_SETTINGS, sysadmin),
                      ensureConfiguration(
                          instruments.get(7),
                          "Europe/Berlin",
                          VIEW_ONLY_FIXTURE_SETTINGS,
                          sysadmin),
                      ensureConfiguration(
                          instruments.get(8),
                          "Pacific/Auckland",
                          AUCKLAND_FIXTURE_SETTINGS,
                          sysadmin),
                      ensureConfiguration(
                          instruments.get(9),
                          "Pacific/Honolulu",
                          HONOLULU_FIXTURE_SETTINGS,
                          sysadmin),
                      ensureConfiguration(
                          instruments.get(10), "Asia/Kolkata", KOLKATA_FIXTURE_SETTINGS, sysadmin),
                      // Opens every day until its events exist; the last sysadmin block closes two
                      // days.
                      ensureConfiguration(instruments.get(11), "Europe/Berlin", sysadmin));

              ensureConfigurationState(
                  bookingCardInstruments.get(1), true, BookingConfigurationState.ACTIVE, sysadmin);
              ensureConfigurationState(
                  bookingCardInstruments.get(2), false, BookingConfigurationState.ACTIVE, sysadmin);
              ensureConfigurationState(
                  bookingCardInstruments.get(3),
                  true,
                  BookingConfigurationState.ARCHIVED,
                  sysadmin);

              List<BusyCalendarFixture> busyCalendarFixtures =
                  busyCalendarInstruments.stream()
                      .map(
                          instrument ->
                              new BusyCalendarFixture(
                                  instrument,
                                  ensureConfigurationState(
                                      instrument,
                                      true,
                                      BookingConfigurationState.ACTIVE,
                                      sysadmin)))
                      .toList();
              return new FixtureConfigurationSet(configurations, busyCalendarFixtures);
            });

    List<BookingConfiguration> configurations = fixtureConfigurationSet.configurations();
    List<BusyCalendarFixture> busyCalendarFixtures = fixtureConfigurationSet.busyCalendarFixtures();

    LocalDate fixtureDate = LocalDate.now(FIXTURE_DATE_ZONE).plusDays(1);
    asUser(
        FIXTURE_USER,
        devUserPassword,
        () -> {
          ensureBooking(
              instruments.get(0),
              configurations.get(0),
              fixtureDate,
              LocalTime.of(9, 0),
              LocalTime.of(10, 30),
              message("bookingFixtures.purposes.cellImaging"),
              owner);
          ensureBooking(
              instruments.get(0),
              configurations.get(0),
              fixtureDate,
              LocalTime.of(13, 0),
              LocalTime.of(14, 0),
              message("bookingFixtures.purposes.calibrationRun"),
              owner);
          ensureBooking(
              instruments.get(1),
              configurations.get(1),
              firstOpenDayOnOrAfter(
                  fixtureDate, BookingSchedulingSettings.from(configurations.get(1))),
              LocalTime.of(10, 0),
              LocalTime.of(12, 0),
              message("bookingFixtures.purposes.ultrastructureImaging"),
              owner);
          LocalDate massSpectrometerDate =
              firstOpenDayOnOrAfter(
                  fixtureDate, BookingSchedulingSettings.from(configurations.get(2)));
          ensureBooking(
              instruments.get(2),
              configurations.get(2),
              massSpectrometerDate,
              LocalTime.of(8, 0),
              LocalTime.of(9, 30),
              message("bookingFixtures.purposes.proteomicsRun"),
              owner);
          ensureBooking(
              instruments.get(2),
              configurations.get(2),
              massSpectrometerDate,
              LocalTime.of(8, 30),
              LocalTime.of(9, 30),
              message("bookingFixtures.purposes.overlappingProteomicsRun"),
              owner);
          ensureBooking(
              instruments.get(3),
              configurations.get(3),
              fixtureDate,
              LocalTime.of(14, 0),
              LocalTime.of(16, 0),
              message("bookingFixtures.purposes.cellSorting"),
              owner);
          ensureBooking(
              instruments.get(3),
              configurations.get(3),
              fixtureDate,
              LocalTime.of(23, 30),
              LocalTime.of(0, 30),
              message("bookingFixtures.purposes.overnightAnalysis"),
              owner);
          ensureBooking(
              instruments.get(4),
              configurations.get(4),
              fixtureDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)),
              LocalTime.of(22, 0),
              LocalTime.of(0, 0),
              message("bookingFixtures.purposes.lateSpin"),
              owner);
          LocalDate sequencerMonday = fixtureDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
          ensureEvent(
              instruments.get(5),
              configurations.get(5),
              sequencerMonday.atStartOfDay(),
              sequencerMonday.plusDays(2).atStartOfDay(),
              BookingEventKind.BOOKING,
              message("bookingFixtures.purposes.twoDaySequencingRun"),
              owner);
          // Auckland's 09:00 is the previous European evening, so start a day later to stay future.
          ensureBooking(
              instruments.get(8),
              configurations.get(8),
              fixtureDate.plusDays(1),
              LocalTime.of(9, 0),
              LocalTime.of(10, 0),
              message("bookingFixtures.purposes.pcrAmplification"),
              owner);
          ensureBooking(
              instruments.get(9),
              configurations.get(9),
              firstOpenDayOnOrAfter(
                  fixtureDate, BookingSchedulingSettings.from(configurations.get(9))),
              LocalTime.of(18, 0),
              LocalTime.of(19, 0),
              message("bookingFixtures.purposes.crystalScreening"),
              owner);
          ensureBooking(
              instruments.get(10),
              configurations.get(10),
              fixtureDate,
              LocalTime.of(10, 15),
              LocalTime.of(11, 30),
              message("bookingFixtures.purposes.quarterHourNmr"),
              owner);
          ensureBooking(
              instruments.get(11),
              configurations.get(11),
              fixtureDate.plusDays(2),
              LocalTime.of(10, 0),
              LocalTime.of(11, 0),
              message("bookingFixtures.purposes.closedDayBooking"),
              owner);

          LocalDate busyCalendarWeekStart =
              fixtureDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
          String busyCalendarPurpose = message("bookingFixtures.purposes.busyCalendar");
          for (int instrumentIndex = 0;
              instrumentIndex < busyCalendarFixtures.size();
              instrumentIndex++) {
            LocalDate bookingDate = busyCalendarWeekStart.plusDays(instrumentIndex % 7);
            for (int eventIndex = 0;
                eventIndex < BUSY_CALENDAR_EVENTS_PER_INSTRUMENT;
                eventIndex++) {
              int startHour = 8 + (instrumentIndex % 4) + (eventIndex * 5);
              ensureBooking(
                  busyCalendarFixtures.get(instrumentIndex).instrument(),
                  busyCalendarFixtures.get(instrumentIndex).configuration(),
                  bookingDate,
                  LocalTime.of(startHour, 0),
                  LocalTime.of(startHour, 45),
                  busyCalendarPurpose,
                  owner);
            }
          }
          for (int searchIndex = 0;
              searchIndex < SEARCH_EVENT_FIXTURE_MESSAGE_KEYS.size();
              searchIndex++) {
            BusyCalendarFixture searchFixture = busyCalendarFixtures.get(searchIndex);
            ensureBooking(
                searchFixture.instrument(),
                searchFixture.configuration(),
                busyCalendarWeekStart.plusDays(searchIndex),
                LocalTime.of(18, 0),
                LocalTime.of(18, 45),
                message(SEARCH_EVENT_FIXTURE_MESSAGE_KEYS.get(searchIndex)),
                owner);
          }
          log.info(
              "Ensured {} instruments and {} events for the busy calendar week starting {}",
              BUSY_CALENDAR_INSTRUMENT_COUNT,
              BUSY_CALENDAR_INSTRUMENT_COUNT * BUSY_CALENDAR_EVENTS_PER_INSTRUMENT
                  + SEARCH_EVENT_FIXTURE_COUNT,
              busyCalendarWeekStart);
          return null;
        });

    asUser(
        SYSADMIN_UNAME,
        SYSADMIN_PWD,
        () -> {
          ensureBooking(
              instruments.get(6),
              configurations.get(6),
              fixtureDate.minusDays(1),
              LocalTime.of(10, 0),
              LocalTime.of(11, 0),
              message("bookingFixtures.purposes.bookingAlertsPast"),
              sysadmin);
          ensureBooking(
              instruments.get(6),
              configurations.get(6),
              fixtureDate,
              LocalTime.of(10, 0),
              LocalTime.of(11, 0),
              message("bookingFixtures.purposes.bookingAlertsOverlap"),
              sysadmin);

          LocalDate maintenanceDate = fixtureDate.plusDays(3);
          ensureEvent(
              instruments.get(11),
              configurations.get(11),
              maintenanceDate.atTime(10, 0),
              maintenanceDate.atTime(12, 0),
              BookingEventKind.MAINTENANCE,
              message("bookingFixtures.purposes.closedDayMaintenance"),
              sysadmin);
          // Reruns rely on findFirstOverlap skipping both events before createBooking rejects them.
          ensureConfiguration(
              instruments.get(11),
              "Europe/Berlin",
              closedDayFixtureSettings(
                  fixtureDate.plusDays(2).getDayOfWeek(), maintenanceDate.getDayOfWeek()),
              sysadmin);
          return null;
        });
  }

  private <T> T asUser(String username, String password, Supplier<T> action) {
    try {
      login(new UsernamePasswordToken(username, password, false));
      return action.get();
    } finally {
      logout();
    }
  }

  private List<Instrument> reloadInstruments(List<Long> instrumentIds) {
    return instrumentIds.stream().map(instrumentDao::get).toList();
  }

  private record FixtureConfigurationSet(
      List<BookingConfiguration> configurations, List<BusyCalendarFixture> busyCalendarFixtures) {}

  private record BusyCalendarFixture(Instrument instrument, BookingConfiguration configuration) {}

  private record FixtureIds(
      Long ownerId,
      Long sysadminId,
      List<Long> instrumentIds,
      List<Long> bookingCardInstrumentIds,
      List<Long> busyCalendarInstrumentIds) {}

  private List<Instrument> ensureBusyCalendarInstruments(User owner) {
    return inventoryFixtures.ensureBusyCalendarInstruments(owner, BUSY_CALENDAR_INSTRUMENT_COUNT);
  }

  private void ensureEvent(
      Instrument instrument,
      BookingConfiguration configuration,
      LocalDateTime localStart,
      LocalDateTime localEnd,
      BookingEventKind kind,
      String purpose,
      User owner) {
    schedulingFixtures.ensureEvent(
        instrument, configuration, localStart, localEnd, kind, purpose, owner);
  }

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
      LocalTime startTime,
      LocalTime endTime,
      String purpose,
      User owner) {
    schedulingFixtures.ensureBooking(
        instrument, configuration, date, startTime, endTime, purpose, owner);
  }

  private static ResolvedBookableTarget target(Instrument instrument) {
    return BookingSchedulingFixtureFactory.target(instrument);
  }

  /** Returns {@code date} when it is an open weekday, otherwise the next open date after it. */
  static LocalDate firstOpenDayOnOrAfter(LocalDate date, BookingSchedulingSettings settings) {
    for (int offset = 0; offset < 7; offset++) {
      LocalDate candidate = date.plusDays(offset);
      if (settings.effectiveHours(candidate.getDayOfWeek().getValue()).isPresent()) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("Booking fixture settings have no open weekday");
  }

  /**
   * Closes {@code closedDays} on the all-day closed-day incubator. Applied only after both of its
   * events exist, so a booking and a maintenance event sit on days that are now closed.
   */
  static BookingSchedulingSettings.Patch closedDayFixtureSettings(DayOfWeek... closedDays) {
    List<DayOfWeek> closed = List.of(closedDays);
    List<BookingOpeningHoursEntry> openingHours =
        Arrays.stream(DayOfWeek.values())
            .filter(day -> !closed.contains(day))
            .map(day -> new BookingOpeningHoursEntry(day.getValue(), "00:00", "24:00"))
            .toList();
    return new BookingSchedulingSettings.Patch(null, openingHours, null, null, null, null);
  }

  private static List<BookingOpeningHoursEntry> schedule(String start, String end, int... days) {
    return Arrays.stream(days)
        .mapToObj(day -> new BookingOpeningHoursEntry(day, start, end))
        .toList();
  }
}
