package com.researchspace.booking.service;

import static com.researchspace.booking.service.TimeSlotBookingManagerIT.sevenDaysFromNow;
import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.service.TimeSlotBookingManagerIT.Setup;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ProtectedResourceAccess;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public class TimeSlotBookingManagerAccessSnapshotIT extends RealTransactionSpringTestBase {

  private final TimeSlotBookingManager bookingManager;
  private final BookingConfigurationManager configurationManager;
  private final BookingConfigurationDao configurationDao;
  private final InstrumentDao instrumentDao;
  private final JdbcTemplate jdbcTemplate;
  private final ResourceAccessManager resourceAccessManager;
  private final ProtectedResourceAccess<BookingConfiguration, Long> protectedAccess;
  private final FeatureFlagManager featureFlags;

  @Autowired
  TimeSlotBookingManagerAccessSnapshotIT(
      TimeSlotBookingManager bookingManager,
      BookingConfigurationManager configurationManager,
      BookingConfigurationDao configurationDao,
      InstrumentDao instrumentDao,
      JdbcTemplate jdbcTemplate,
      ResourceAccessManager resourceAccessManager,
      ProtectedResourceAccess<BookingConfiguration, Long> protectedAccess,
      FeatureFlagManager featureFlags) {
    this.bookingManager = bookingManager;
    this.configurationManager = configurationManager;
    this.configurationDao = configurationDao;
    this.instrumentDao = instrumentDao;
    this.jdbcTemplate = jdbcTemplate;
    this.resourceAccessManager = resourceAccessManager;
    this.protectedAccess = protectedAccess;
    this.featureFlags = featureFlags;
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

  @ParameterizedTest(name = "{0}")
  @EnumSource(SnapshotMutation.class)
  public void editRejectsAccessOrConfigurationChangesCommittedAfterItsInitialReadSnapshot(
      SnapshotMutation mutation) {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument instrument = createBasicInstrumentForUser(owner, "Access snapshot scope");
    Setup setup = persistConfiguration(owner, instrument.getId(), false, 0, 0);
    User requester = owner;
    User newOwner = createInitAndLoginAnyUser();
    Instant start = sevenDaysFromNow();
    User bookingRequester = requester;
    TimeSlotBooking existing =
        bookingManager.createBooking(
            new TimeSlotBookingManager.Create(
                new ResolvedBookableTarget(setup.target(), setup.instrument()),
                Date.from(start),
                Date.from(start.plus(1, ChronoUnit.HOURS)),
                null),
            bookingRequester,
            bookingRequester);
    var competing = new TransactionTemplate(getTxMger());
    competing.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    User subject = bookingRequester;

    Runnable updateFromStaleSnapshot =
        () ->
            new TransactionTemplate(getTxMger())
                .executeWithoutResult(
                    ignored -> {
                      bookingManager.getBooking(existing.getId(), subject).orElseThrow();
                      competing.executeWithoutResult(
                          other -> {
                            if (mutation == SnapshotMutation.CHANGE_ITEM_OWNER) {
                              instrumentDao.get(instrument.getId()).setOwner(newOwner);
                            } else {
                              configurationManager.updateConfiguration(
                                  setup.configurationId(),
                                  new BookingConfigurationManager.Patch(false, null),
                                  owner,
                                  owner);
                            }
                          });
                      if (mutation == SnapshotMutation.CHANGE_ITEM_OWNER) {
                        assertTrue(
                            bookingManager
                                .updateBooking(
                                    existing.getId(),
                                    new TimeSlotBookingManager.Patch(
                                        null, null, true, "Revoked edit", null),
                                    subject,
                                    subject)
                                .isEmpty());
                      } else {
                        bookingManager.updateBooking(
                            existing.getId(),
                            new TimeSlotBookingManager.Patch(
                                Date.from(start.plus(2, ChronoUnit.HOURS)),
                                Date.from(start.plus(3, ChronoUnit.HOURS)),
                                false,
                                null,
                                null),
                            owner,
                            owner);
                      }
                    });
    // The locking configuration read sees the committed disable, so the target is unavailable.
    Class<? extends RuntimeException> expected =
        mutation == SnapshotMutation.DISABLE_CONFIGURATION
            ? BookingTargetUnavailableException.class
            : BookingConcurrentModificationException.class;
    assertThrows(expected, updateFromStaleSnapshot::run);

    if (mutation == SnapshotMutation.CHANGE_ITEM_OWNER) {
      assertTrue(
          bookingManager
              .updateBooking(
                  existing.getId(),
                  new TimeSlotBookingManager.Patch(null, null, true, "Revoked edit", null),
                  subject,
                  subject)
              .isEmpty());
    }
  }

  private enum SnapshotMutation {
    CHANGE_ITEM_OWNER,
    DISABLE_CONFIGURATION
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
}
