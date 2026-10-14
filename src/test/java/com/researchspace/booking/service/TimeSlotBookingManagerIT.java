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
  public void sharedBufferGapIsSymmetricAcrossInsertionOrdersAndAllowsItsExactBoundary() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Buffer boundary scope");
    Setup setup = persistConfiguration(owner, created.getId(), false, 1L, 10, 20);
    ResolvedBookableTarget target = new ResolvedBookableTarget(setup.target(), setup.instrument());
    Instant start =
        LocalDate.ofInstant(institutionClock.instant(), ZoneOffset.UTC)
            .plusDays(7)
            .atTime(10, 0)
            .toInstant(ZoneOffset.UTC);

    create(target, owner, start, start.plus(1, ChronoUnit.HOURS));

    Instant afterStart15 = start.plus(1, ChronoUnit.HOURS).plus(15, ChronoUnit.MINUTES);
    assertThrows(
        BookingBufferConflictException.class,
        () -> create(target, owner, afterStart15, afterStart15.plus(1, ChronoUnit.HOURS)));
    Instant afterStart19 = start.plus(1, ChronoUnit.HOURS).plus(19, ChronoUnit.MINUTES);
    assertThrows(
        BookingBufferConflictException.class,
        () -> create(target, owner, afterStart19, afterStart19.plus(1, ChronoUnit.HOURS)));
    Instant afterStart20 = start.plus(1, ChronoUnit.HOURS).plus(20, ChronoUnit.MINUTES);
    create(target, owner, afterStart20, afterStart20.plus(1, ChronoUnit.HOURS));

    Instant laterStart = start.plus(1, ChronoUnit.DAYS);
    create(target, owner, laterStart, laterStart.plus(1, ChronoUnit.HOURS));
    Instant beforeStart15 = laterStart.minus(1, ChronoUnit.HOURS).minus(15, ChronoUnit.MINUTES);
    assertThrows(
        BookingBufferConflictException.class,
        () -> create(target, owner, beforeStart15, beforeStart15.plus(1, ChronoUnit.HOURS)));
    Instant beforeStart19 = laterStart.minus(1, ChronoUnit.HOURS).minus(19, ChronoUnit.MINUTES);
    assertThrows(
        BookingBufferConflictException.class,
        () -> create(target, owner, beforeStart19, beforeStart19.plus(1, ChronoUnit.HOURS)));
    Instant beforeStart20 = laterStart.minus(1, ChronoUnit.HOURS).minus(20, ChronoUnit.MINUTES);
    create(target, owner, beforeStart20, beforeStart20.plus(1, ChronoUnit.HOURS));
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
      long bufferBeforeMinutes,
      long bufferAfterMinutes) {
    return TimeSlotBookingManagerIT.persistConfiguration(
        configurationManager,
        instrumentDao,
        this::openTransaction,
        this::commitTransaction,
        owner,
        instrumentId,
        allowDoubleBooking,
        bufferBeforeMinutes,
        bufferAfterMinutes);
  }

  private Setup persistConfiguration(
      User owner,
      Long instrumentId,
      boolean allowDoubleBooking,
      Long slotGranularityMinutes,
      long bufferBeforeMinutes,
      long bufferAfterMinutes) {
    return TimeSlotBookingManagerIT.persistConfiguration(
        configurationManager,
        instrumentDao,
        this::openTransaction,
        this::commitTransaction,
        owner,
        instrumentId,
        allowDoubleBooking,
        slotGranularityMinutes,
        bufferBeforeMinutes,
        bufferAfterMinutes);
  }

  static Setup persistConfiguration(
      BookingConfigurationManager configurationManager,
      InstrumentDao instrumentDao,
      Runnable openTransaction,
      Runnable commitTransaction,
      User owner,
      Long instrumentId,
      boolean allowDoubleBooking,
      long bufferBeforeMinutes,
      long bufferAfterMinutes) {
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
        bufferBeforeMinutes,
        bufferAfterMinutes);
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
      long bufferBeforeMinutes,
      long bufferAfterMinutes) {
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
                    bufferBeforeMinutes,
                    bufferAfterMinutes,
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
