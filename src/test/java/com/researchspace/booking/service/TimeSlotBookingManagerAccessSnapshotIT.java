package com.researchspace.booking.service;

import static com.researchspace.booking.service.TimeSlotBookingManagerIT.sevenDaysFromNow;
import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.researchspace.service.resourceaccess.ReplaceResourceAccess;
import com.researchspace.service.resourceaccess.ResourceAccessException;
import com.researchspace.service.resourceaccess.ResourceAccessGrant;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    if (mutation == SnapshotMutation.REVOKE_BOOKER_ACCESS) {
      requester = createInitAndLoginAnyUser();
      setAudienceRole(setup.configurationId(), owner, "BOOKER");
    }
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
                            if (mutation == SnapshotMutation.REVOKE_BOOKER_ACCESS) {
                              setAudienceRole(setup.configurationId(), owner, "NO_ACCESS");
                            } else {
                              configurationManager.updateConfiguration(
                                  setup.configurationId(),
                                  new BookingConfigurationManager.Patch(false, null),
                                  owner,
                                  owner);
                            }
                          });
                      if (mutation == SnapshotMutation.REVOKE_BOOKER_ACCESS) {
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
    if (mutation == SnapshotMutation.REVOKE_BOOKER_ACCESS) {
      ResourceAccessException exception =
          assertThrows(ResourceAccessException.class, updateFromStaleSnapshot::run);
      assertEquals(ResourceAccessException.Reason.STALE, exception.reason());
    } else {
      assertThrows(BookingConcurrentModificationException.class, updateFromStaleSnapshot::run);
    }

    if (mutation == SnapshotMutation.REVOKE_BOOKER_ACCESS) {
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

  @Test
  public void editReadsAssignmentsCurrentWhenAccessWasNotLoadedBeforeTheRevocation() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument instrument = createBasicInstrumentForUser(owner, "Uncached access scope");
    Setup setup = persistConfiguration(owner, instrument.getId(), false, 0, 0);
    User booker = createInitAndLoginAnyUser();
    setAudienceRole(setup.configurationId(), owner, "BOOKER");
    Instant start = sevenDaysFromNow();
    var existing =
        bookingManager.createBooking(
            new TimeSlotBookingManager.Create(
                new ResolvedBookableTarget(setup.target(), setup.instrument()),
                Date.from(start),
                Date.from(start.plus(1, ChronoUnit.HOURS)),
                null),
            booker,
            booker);
    var competing = new TransactionTemplate(getTxMger());
    competing.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    try {
      new TransactionTemplate(getTxMger())
          .executeWithoutResult(
              ignored -> {
                configurationDao.getSafeNull(setup.configurationId()).orElseThrow();
                competing.executeWithoutResult(
                    other -> setAudienceRole(setup.configurationId(), owner, "NO_ACCESS"));
                assertTrue(
                    bookingManager
                        .updateBooking(
                            existing.getId(),
                            new TimeSlotBookingManager.Patch(
                                null, null, true, "Revoked edit", null),
                            booker,
                            booker)
                        .isEmpty());
              });
    } catch (BookingConcurrentModificationException exception) {
      // MariaDB can reject a stale-snapshot locking read instead of returning current rows.
    } catch (ResourceAccessException exception) {
      assertEquals(ResourceAccessException.Reason.STALE, exception.reason());
    }
    assertEquals(
        "CONFIRMED",
        jdbcTemplate.queryForObject(
            "SELECT state FROM TimeSlotBooking WHERE id = ?", String.class, existing.getId()));
  }

  private void setAudienceRole(long id, User owner, String role) {
    long version = resourceAccessManager.get(protectedAccess, id, owner).version();
    resourceAccessManager.replace(
        protectedAccess,
        new ReplaceResourceAccess<>(
            id,
            version,
            List.of(
                new ResourceAccessGrant("user:" + owner.getId(), "OWNER"),
                new ResourceAccessGrant("audience:all-users", role))),
        owner,
        owner);
  }

  private enum SnapshotMutation {
    REVOKE_BOOKER_ACCESS,
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
