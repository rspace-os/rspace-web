package com.researchspace.service.impl;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.booking.service.BookingConfigurationManager;
import com.researchspace.booking.service.TimeSlotBookingManager;
import com.researchspace.dao.ContainerDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.GroupType;
import com.researchspace.model.RoleInGroup;
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
import com.researchspace.service.IContentInitializer;
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
  private static final List<String> SEARCH_EVENT_FIXTURE_PURPOSES =
      List.of(
          BookingFixtureText.PURPOSE_AURORA_CALIBRATION,
          BookingFixtureText.PURPOSE_BEACON_CELL_IMAGING,
          BookingFixtureText.PURPOSE_COMET_PROTEOMICS);
  private static final int SEARCH_EVENT_FIXTURE_COUNT = SEARCH_EVENT_FIXTURE_PURPOSES.size();
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
  private final BookingConfigurationManager configurationManager;
  private final TimeSlotBookingManager bookingManager;
  private final BookingConfigurationDao configurationDao;
  private final TimeSlotBookingDao bookingDao;
  private final TransactionTemplate fixtureTransaction;
  private final boolean featureBranchInstance;
  private final IContentInitializer contentInitializer;
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
      BookingConfigurationManager configurationManager,
      TimeSlotBookingManager bookingManager,
      BookingConfigurationDao configurationDao,
      TimeSlotBookingDao bookingDao,
      PlatformTransactionManager transactionManager,
      boolean featureBranchInstance,
      IContentInitializer contentInitializer) {
    this.devUserPassword = devUserPassword;
    this.featureBranchInstance = featureBranchInstance;
    this.contentInitializer = contentInitializer;
    this.userDao = userDao;
    this.containerDao = containerDao;
    this.instrumentDao = instrumentDao;
    this.containerManager = containerManager;
    this.instrumentManager = instrumentManager;
    this.configurationManager = configurationManager;
    this.bookingManager = bookingManager;
    this.configurationDao = configurationDao;
    this.bookingDao = bookingDao;
    this.inventoryFixtures =
        new BookingInventoryFixtureFactory(
            containerDao, instrumentDao, containerManager, instrumentManager);
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
    AfterCommit.runAfterCompletion(this::createFixturesInCommittedStages);
  }

  // Start only after committed completion has cleared the startup transaction's booking state.
  // Each fixture stage still needs its own transaction.
  private void createFixturesInCommittedStages() {
    FixtureIds fixtureIds = fixtureTransaction.execute(ignored -> createInventoryFixtures());
    if (fixtureIds == null) {
      return;
    }
    fixtureTransaction.executeWithoutResult(ignored -> createBookingFixtures(fixtureIds));
  }

  private FixtureIds createInventoryFixtures() {
    User owner = userDao.getOptionalUserByUsername(FIXTURE_USER).orElse(null);
    User restrictedInstrumentOwner =
        userDao.getOptionalUserByUsername(RESTRICTED_INSTRUMENT_OWNER).orElse(null);
    User restrictedContainerOwner =
        userDao.getOptionalUserByUsername(RESTRICTED_CONTAINER_OWNER).orElse(null);
    User sysadmin = userDao.getOptionalUserByUsername(SYSADMIN_UNAME).orElse(null);
    if (owner == null
        || restrictedInstrumentOwner == null
        || restrictedContainerOwner == null
        || sysadmin == null
        || owner.getId() == null
        || restrictedInstrumentOwner.getId() == null
        || restrictedContainerOwner.getId() == null
        || sysadmin.getId() == null) {
      log.info("Skipping booking fixtures because the Docker development users are unavailable");
      return null;
    }

    for (User user : List.of(owner, restrictedInstrumentOwner, restrictedContainerOwner)) {
      if (!user.isContentInitialized()) {
        asUser(user.getUsername(), devUserPassword, () -> contentInitializer.init(user.getId()));
      }
    }
    boolean viewOnlyRelationshipExists =
        restrictedInstrumentOwner.getGroups().stream()
            .filter(group -> group.isLabGroup())
            .anyMatch(group -> owner.hasRoleInGroup(group, RoleInGroup.PI));
    if (!viewOnlyRelationshipExists) {
      asUser(
          SYSADMIN_UNAME,
          SYSADMIN_PWD,
          () ->
              grpStrategy.createAndSaveGroup(
                  owner, sysadmin, GroupType.LAB_GROUP, owner, restrictedInstrumentOwner));
    }

    List<Instrument> instruments = new ArrayList<>();
    List<Instrument> schedulingEdgeInstruments = new ArrayList<>();
    List<Instrument> bookingCardInstruments = new ArrayList<>();
    List<Instrument> busyCalendarInstruments =
        asUser(
            FIXTURE_USER,
            devUserPassword,
            () -> {
              Instrument confocal = ensureInstrument(BookingFixtureText.INSTRUMENT_CONFOCAL, owner);
              moveToParent(
                  confocal,
                  ensureContainer(BookingFixtureText.CONTAINER_IMAGING_LAB, owner, false));
              instruments.add(confocal);

              Instrument electron =
                  ensureInstrument(BookingFixtureText.INSTRUMENT_ELECTRON_MICROSCOPE, owner);
              moveToParent(
                  electron,
                  ensureContainer(BookingFixtureText.CONTAINER_CRYO_EM_SUITE, owner, false));
              instruments.add(electron);

              Instrument massSpectrometer =
                  ensureInstrument(BookingFixtureText.INSTRUMENT_MASS_SPECTROMETER, owner);
              moveToParent(massSpectrometer, containerDao.getWorkbenchForUser(owner));
              instruments.add(massSpectrometer);

              Instrument flowCytometer =
                  ensureInstrument(BookingFixtureText.INSTRUMENT_FLOW_CYTOMETER, owner);
              moveToParent(
                  flowCytometer,
                  ensureContainer(
                      BookingFixtureText.CONTAINER_LONG_CELL_ANALYSIS_FACILITY, owner, false));
              instruments.add(flowCytometer);

              Instrument noParent =
                  ensureInstrument(BookingFixtureText.INSTRUMENT_NO_PARENT_CENTRIFUGE, owner);
              removeFromParent(noParent);
              instruments.add(noParent);

              Instrument deletedLocation =
                  ensureInstrument(BookingFixtureText.INSTRUMENT_DELETED_LOCATION_SEQUENCER, owner);
              Container deletedParent =
                  ensureContainer(
                      BookingFixtureText.CONTAINER_DELETED_INSTRUMENT_ROOM, owner, true);
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
                  ensureInstrument(BookingFixtureText.INSTRUMENT_BOOKING_CARD_SETUP, owner));
              bookingCardInstruments.add(
                  ensureInstrument(BookingFixtureText.INSTRUMENT_BOOKING_CARD_BOOK, owner));
              bookingCardInstruments.add(
                  ensureInstrument(BookingFixtureText.INSTRUMENT_BOOKING_CARD_DISABLED, owner));
              bookingCardInstruments.add(
                  ensureInstrument(BookingFixtureText.INSTRUMENT_BOOKING_CARD_ARCHIVED, owner));

              List<Instrument> busyCalendar = ensureBusyCalendarInstruments(owner);
              instruments.add(
                  ensureInstrument(BookingFixtureText.INSTRUMENT_BOOKING_ALERTS, owner));
              schedulingEdgeInstruments.add(
                  ensureInstrument(BookingFixtureText.INSTRUMENT_AUCKLAND_PCR_CYCLER, owner));
              schedulingEdgeInstruments.add(
                  ensureInstrument(BookingFixtureText.INSTRUMENT_HONOLULU_DIFFRACTOMETER, owner));
              schedulingEdgeInstruments.add(
                  ensureInstrument(BookingFixtureText.INSTRUMENT_KOLKATA_NMR_SPECTROMETER, owner));
              schedulingEdgeInstruments.add(
                  ensureInstrument(BookingFixtureText.INSTRUMENT_CLOSED_DAY_INCUBATOR, owner));
              return busyCalendar;
            });

    Instrument restrictedLocation =
        asUser(
            RESTRICTED_INSTRUMENT_OWNER,
            devUserPassword,
            () -> {
              Instrument instrument =
                  ensureInstrument(
                      BookingFixtureText.INSTRUMENT_RESTRICTED_LOCATION_PLATE_READER,
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
                      BookingFixtureText.CONTAINER_RESTRICTED_INSTRUMENT_ROOM,
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
              BookingFixtureText.PURPOSE_CELL_IMAGING,
              owner);
          ensureBooking(
              instruments.get(0),
              configurations.get(0),
              fixtureDate,
              LocalTime.of(13, 0),
              LocalTime.of(14, 0),
              BookingFixtureText.PURPOSE_CALIBRATION_RUN,
              owner);
          ensureBooking(
              instruments.get(1),
              configurations.get(1),
              firstOpenDayOnOrAfter(
                  fixtureDate, BookingSchedulingSettings.from(configurations.get(1))),
              LocalTime.of(10, 0),
              LocalTime.of(12, 0),
              BookingFixtureText.PURPOSE_ULTRASTRUCTURE_IMAGING,
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
              BookingFixtureText.PURPOSE_PROTEOMICS_RUN,
              owner);
          ensureBooking(
              instruments.get(2),
              configurations.get(2),
              massSpectrometerDate,
              LocalTime.of(8, 30),
              LocalTime.of(9, 30),
              BookingFixtureText.PURPOSE_OVERLAPPING_PROTEOMICS_RUN,
              owner);
          ensureBooking(
              instruments.get(3),
              configurations.get(3),
              fixtureDate,
              LocalTime.of(14, 0),
              LocalTime.of(16, 0),
              BookingFixtureText.PURPOSE_CELL_SORTING,
              owner);
          ensureBooking(
              instruments.get(3),
              configurations.get(3),
              fixtureDate,
              LocalTime.of(23, 30),
              LocalTime.of(0, 30),
              BookingFixtureText.PURPOSE_OVERNIGHT_ANALYSIS,
              owner);
          ensureBooking(
              instruments.get(4),
              configurations.get(4),
              fixtureDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)),
              LocalTime.of(22, 0),
              LocalTime.of(0, 0),
              BookingFixtureText.PURPOSE_LATE_SPIN,
              owner);
          LocalDate sequencerMonday = fixtureDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
          ensureEvent(
              instruments.get(5),
              configurations.get(5),
              sequencerMonday.atStartOfDay(),
              sequencerMonday.plusDays(2).atStartOfDay(),
              BookingEventKind.BOOKING,
              BookingFixtureText.PURPOSE_TWO_DAY_SEQUENCING_RUN,
              owner);
          // Auckland's 09:00 is the previous European evening, so start a day later to stay future.
          ensureBooking(
              instruments.get(8),
              configurations.get(8),
              fixtureDate.plusDays(1),
              LocalTime.of(9, 0),
              LocalTime.of(10, 0),
              BookingFixtureText.PURPOSE_PCR_AMPLIFICATION,
              owner);
          ensureBooking(
              instruments.get(9),
              configurations.get(9),
              firstOpenDayOnOrAfter(
                  fixtureDate, BookingSchedulingSettings.from(configurations.get(9))),
              LocalTime.of(18, 0),
              LocalTime.of(19, 0),
              BookingFixtureText.PURPOSE_CRYSTAL_SCREENING,
              owner);
          ensureBooking(
              instruments.get(10),
              configurations.get(10),
              fixtureDate,
              LocalTime.of(10, 15),
              LocalTime.of(11, 30),
              BookingFixtureText.PURPOSE_QUARTER_HOUR_NMR,
              owner);
          ensureBooking(
              instruments.get(11),
              configurations.get(11),
              fixtureDate.plusDays(2),
              LocalTime.of(10, 0),
              LocalTime.of(11, 0),
              BookingFixtureText.PURPOSE_CLOSED_DAY_BOOKING,
              owner);

          LocalDate busyCalendarWeekStart =
              fixtureDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
          String busyCalendarPurpose = BookingFixtureText.PURPOSE_BUSY_CALENDAR;
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
              searchIndex < SEARCH_EVENT_FIXTURE_PURPOSES.size();
              searchIndex++) {
            BusyCalendarFixture searchFixture = busyCalendarFixtures.get(searchIndex);
            ensureBooking(
                searchFixture.instrument(),
                searchFixture.configuration(),
                busyCalendarWeekStart.plusDays(searchIndex),
                LocalTime.of(18, 0),
                LocalTime.of(18, 45),
                SEARCH_EVENT_FIXTURE_PURPOSES.get(searchIndex),
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
              BookingFixtureText.PURPOSE_BOOKING_ALERTS_PAST,
              sysadmin);
          ensureBooking(
              instruments.get(6),
              configurations.get(6),
              fixtureDate,
              LocalTime.of(10, 0),
              LocalTime.of(11, 0),
              BookingFixtureText.PURPOSE_BOOKING_ALERTS_OVERLAP,
              sysadmin);

          LocalDate maintenanceDate = fixtureDate.plusDays(3);
          ensureEvent(
              instruments.get(11),
              configurations.get(11),
              maintenanceDate.atTime(10, 0),
              maintenanceDate.atTime(12, 0),
              BookingEventKind.MAINTENANCE,
              BookingFixtureText.PURPOSE_CLOSED_DAY_MAINTENANCE,
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
    return new BookingSchedulingSettings.Patch(null, null, openingHours, null, null, null, null);
  }

  private static List<BookingOpeningHoursEntry> schedule(String start, String end, int... days) {
    return Arrays.stream(days)
        .mapToObj(day -> new BookingOpeningHoursEntry(day, start, end))
        .toList();
  }
}
