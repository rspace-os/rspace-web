package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.booking.config.BookingTimeConfig;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.function.BiConsumer;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public class TimeSlotBookingManagerIT extends RealTransactionSpringTestBase {

  private final TimeSlotBookingManager bookingManager;
  private final BookingConfigurationManager configurationManager;
  private final InstrumentDao instrumentDao;
  private final JdbcTemplate jdbcTemplate;
  private final FeatureFlagManager featureFlags;
  private final Clock institutionClock;

  @Autowired
  TimeSlotBookingManagerIT(
      TimeSlotBookingManager bookingManager,
      BookingConfigurationManager configurationManager,
      InstrumentDao instrumentDao,
      JdbcTemplate jdbcTemplate,
      FeatureFlagManager featureFlags,
      @Qualifier(BookingTimeConfig.INSTITUTION_CLOCK) Clock institutionClock) {
    this.bookingManager = bookingManager;
    this.configurationManager = configurationManager;
    this.instrumentDao = instrumentDao;
    this.jdbcTemplate = jdbcTemplate;
    this.featureFlags = featureFlags;
    this.institutionClock = institutionClock;
  }

  private boolean originalBookingBaseline;

  @BeforeEach
  public void enableBooking() {
    User sysadmin = getSysAdminUser();
    originalBookingBaseline =
        featureFlags.getFeatureFlag(BOOKING_ENABLED, sysadmin).orElseThrow().isBaselineValue();
    featureFlags.updateFeatureFlag(
        BOOKING_ENABLED, new FeatureFlagManager.Patch(true, false, null), sysadmin, sysadmin);
  }

  @AfterEach
  public void restoreBookingBaseline() {
    User sysadmin = getSysAdminUser();
    featureFlags.updateFeatureFlag(
        BOOKING_ENABLED,
        new FeatureFlagManager.Patch(originalBookingBaseline, false, null),
        sysadmin,
        sysadmin);
  }

  @Test
  public void bufferGapsAllowTheirExactBoundariesInBothInsertionOrders() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Buffer boundary scope");
    Setup setup = persistConfiguration(owner, created.getId(), false, 1L, 10, 20);
    ResolvedBookableTarget target = new ResolvedBookableTarget(setup.target(), setup.instrument());
    Instant start =
        LocalDate.ofInstant(institutionClock.instant(), ZoneOffset.UTC)
            .plusDays(7)
            .atTime(10, 0)
            .toInstant(ZoneOffset.UTC);

    // Bookings keep both buffers between them, whichever was created first.
    create(target, owner, start, start.plus(1, ChronoUnit.HOURS));
    Instant afterStart25 = start.plus(1, ChronoUnit.HOURS).plus(25, ChronoUnit.MINUTES);
    assertThrows(
        BookingBufferConflictException.class,
        () -> create(target, owner, afterStart25, afterStart25.plus(1, ChronoUnit.HOURS)));
    Instant afterStart29 = start.plus(1, ChronoUnit.HOURS).plus(29, ChronoUnit.MINUTES);
    assertThrows(
        BookingBufferConflictException.class,
        () -> create(target, owner, afterStart29, afterStart29.plus(1, ChronoUnit.HOURS)));
    Instant afterStart30 = start.plus(1, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES);
    create(target, owner, afterStart30, afterStart30.plus(1, ChronoUnit.HOURS));

    Instant laterStart = start.plus(1, ChronoUnit.DAYS);
    create(target, owner, laterStart, laterStart.plus(1, ChronoUnit.HOURS));
    Instant beforeStart25 = laterStart.minus(1, ChronoUnit.HOURS).minus(25, ChronoUnit.MINUTES);
    assertThrows(
        BookingBufferConflictException.class,
        () -> create(target, owner, beforeStart25, beforeStart25.plus(1, ChronoUnit.HOURS)));
    Instant beforeStart29 = laterStart.minus(1, ChronoUnit.HOURS).minus(29, ChronoUnit.MINUTES);
    assertThrows(
        BookingBufferConflictException.class,
        () -> create(target, owner, beforeStart29, beforeStart29.plus(1, ChronoUnit.HOURS)));
    Instant beforeStart30 = laterStart.minus(1, ChronoUnit.HOURS).minus(30, ChronoUnit.MINUTES);
    create(target, owner, beforeStart30, beforeStart30.plus(1, ChronoUnit.HOURS));
  }

  @Test
  public void maintenanceCanTouchMaintenanceButKeepsBookingsBufferInBothInsertionOrders() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Maintenance buffer scope");
    Setup setup = persistConfiguration(owner, created.getId(), false, 1L, 20, 20);
    ResolvedBookableTarget target = new ResolvedBookableTarget(setup.target(), setup.instrument());
    Instant start =
        LocalDate.ofInstant(institutionClock.instant(), ZoneOffset.UTC)
            .plusDays(7)
            .atTime(10, 0)
            .toInstant(ZoneOffset.UTC);
    BiConsumer<Instant, Instant> maintenance =
        (from, to) ->
            bookingManager.createBooking(
                new TimeSlotBookingManager.Create(
                    target, Date.from(from), Date.from(to), null, BookingEventKind.MAINTENANCE),
                owner,
                owner);
    maintenance.accept(start, start.plus(1, ChronoUnit.HOURS));
    maintenance.accept(start.plus(1, ChronoUnit.HOURS), start.plus(2, ChronoUnit.HOURS));
    assertThrows(
        BookingBufferConflictException.class,
        () ->
            create(
                target, owner, start.plus(2, ChronoUnit.HOURS), start.plus(3, ChronoUnit.HOURS)));
    create(target, owner, start.plus(140, ChronoUnit.MINUTES), start.plus(200, ChronoUnit.MINUTES));
    Instant bookingStart = start.plus(4, ChronoUnit.HOURS);
    create(target, owner, bookingStart, bookingStart.plus(1, ChronoUnit.HOURS));
    assertThrows(
        BookingBufferConflictException.class,
        () ->
            maintenance.accept(
                bookingStart.plus(1, ChronoUnit.HOURS), bookingStart.plus(2, ChronoUnit.HOURS)));
    maintenance.accept(
        bookingStart.plus(80, ChronoUnit.MINUTES), bookingStart.plus(140, ChronoUnit.MINUTES));
  }

  @Test
  public void zeroBuffersAllowTouchingRawBookingIntervals() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Zero buffer boundary scope");
    Setup setup = persistConfiguration(owner, created.getId(), false, 0, 0);
    ResolvedBookableTarget target = new ResolvedBookableTarget(setup.target(), setup.instrument());
    Instant start =
        LocalDate.ofInstant(institutionClock.instant(), ZoneOffset.UTC)
            .plusDays(7)
            .atTime(10, 0)
            .toInstant(ZoneOffset.UTC);

    create(target, owner, start, start.plus(1, ChronoUnit.HOURS));
    create(target, owner, start.plus(1, ChronoUnit.HOURS), start.plus(2, ChronoUnit.HOURS));
  }

  @Test
  public void oversizedCalendarSourceDoesNotMarkTheCallerTransactionForRollback() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Oversized calendar scope");
    Setup setup = persistConfiguration(owner, created.getId(), false, 0, 0);
    ResolvedBookableTarget target = new ResolvedBookableTarget(setup.target(), setup.instrument());
    Instant start = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    bookingManager.createBooking(
        new TimeSlotBookingManager.Create(
            target, Date.from(start), Date.from(start.plus(1, ChronoUnit.HOURS)), null),
        owner,
        owner);
    bookingManager.createBooking(
        new TimeSlotBookingManager.Create(
            target,
            Date.from(start.plus(2, ChronoUnit.HOURS)),
            Date.from(start.plus(3, ChronoUnit.HOURS)),
            null),
        owner,
        owner);

    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored ->
                assertThrows(
                    TimeSlotBookingManager.CalendarSourceTooLargeException.class,
                    () ->
                        bookingManager.getCalendarSource(
                            setup.configurationId(), owner, new Date(), 1)));
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            ignored ->
                assertThrows(
                    TimeSlotBookingManager.CalendarSourceTooLargeException.class,
                    () -> bookingManager.getUserCalendarSource(owner, new Date(), 1)));
  }

  private TimeSlotBooking create(
      ResolvedBookableTarget target, User owner, Instant start, Instant end) {
    return bookingManager.createBooking(
        new TimeSlotBookingManager.Create(target, Date.from(start), Date.from(end), null),
        owner,
        owner);
  }

  private Setup persistConfiguration(
      User owner,
      Long instrumentId,
      boolean allowDoubleBooking,
      long bufferMinutes,
      long otherGap) {
    return TimeSlotBookingManagerIT.persistConfiguration(
        configurationManager,
        instrumentDao,
        this::openTransaction,
        this::commitTransaction,
        owner,
        instrumentId,
        allowDoubleBooking,
        bufferMinutes,
        otherGap);
  }

  private Setup persistConfiguration(
      User owner,
      Long instrumentId,
      boolean allowDoubleBooking,
      Long slotGranularityMinutes,
      long bufferMinutes,
      long otherGap) {
    return TimeSlotBookingManagerIT.persistConfiguration(
        configurationManager,
        instrumentDao,
        this::openTransaction,
        this::commitTransaction,
        owner,
        instrumentId,
        allowDoubleBooking,
        slotGranularityMinutes,
        bufferMinutes,
        otherGap);
  }

  static Setup persistConfiguration(
      BookingConfigurationManager configurationManager,
      InstrumentDao instrumentDao,
      Runnable openTransaction,
      Runnable commitTransaction,
      User owner,
      Long instrumentId,
      boolean allowDoubleBooking,
      long bufferMinutes,
      long otherGap) {
    // Five-minute increments keep the default buffer boundaries on the grid.
    return persistConfiguration(
        configurationManager,
        instrumentDao,
        openTransaction,
        commitTransaction,
        owner,
        instrumentId,
        allowDoubleBooking,
        5L,
        bufferMinutes,
        otherGap);
  }

  static Setup persistConfiguration(
      BookingConfigurationManager configurationManager,
      InstrumentDao instrumentDao,
      Runnable openTransaction,
      Runnable commitTransaction,
      User owner,
      Long instrumentId,
      boolean allowDoubleBooking,
      Long slotGranularityMinutes,
      long bufferMinutes,
      long otherGap) {
    openTransaction.run();
    Instrument instrument = instrumentDao.get(instrumentId);
    Hibernate.initialize(instrument.getOwner());
    BookableTargetReference target =
        new BookableTargetReference(BookableTargetType.INSTRUMENT, instrumentId);
    commitTransaction.run();
    BookingConfiguration configuration =
        configurationManager.createConfiguration(
            new BookingConfigurationManager.Create(
                true,
                "UTC",
                new ResolvedBookableTarget(target, instrument),
                new BookingSchedulingSettings.Patch(
                    slotGranularityMinutes,
                    null,
                    bufferMinutes,
                    otherGap,
                    null,
                    allowDoubleBooking)),
            owner,
            owner);
    return new Setup(configuration.getId(), target, instrument);
  }

  static Instant sevenDaysFromNow() {
    return Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
  }

  record Setup(Long configurationId, BookableTargetReference target, Instrument instrument) {}
}
