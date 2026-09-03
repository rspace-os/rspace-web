package com.researchspace.booking.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.hibernate.Hibernate;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Database contract for the deliberately destructive booking-configuration operation. */
public class BookingConfigurationPermanentDeleteIT extends RealTransactionSpringTestBase {

  @Autowired private BookingConfigurationManager configurationManager;
  @Autowired private TimeSlotBookingManager bookingManager;
  @Autowired private InstrumentDao instrumentDao;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  public void permanentlyDeletesOnlyLiveGraphAndRetainsEnversHistory() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Permanent-delete scope");
    Instrument instrument = initializedInstrument(created.getId());
    BookableTargetReference target =
        new BookableTargetReference(BookableTargetType.INSTRUMENT, instrument.getId());
    ResolvedBookableTarget resolvedTarget = new ResolvedBookableTarget(target, instrument);
    BookingConfiguration configuration =
        configurationManager.createConfiguration(
            new BookingConfigurationManager.Create(true, "UTC", resolvedTarget), owner, owner);
    Long configurationId = configuration.getId();
    Long accessId = configuration.getResourceAccess().getId();

    Instant start = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    Long bookingId =
        bookingManager
            .createBooking(
                new TimeSlotBookingManager.Create(
                    resolvedTarget,
                    Date.from(start),
                    Date.from(start.plus(1, ChronoUnit.HOURS)),
                    "Retained only in audit history"),
                owner,
                owner)
            .getId();
    ApiInstrument unrelatedCreated =
        createBasicInstrumentForUser(owner, "Unrelated permanent-delete scope");
    Instrument unrelatedInstrument = initializedInstrument(unrelatedCreated.getId());
    ResolvedBookableTarget unrelatedTarget =
        new ResolvedBookableTarget(
            new BookableTargetReference(BookableTargetType.INSTRUMENT, unrelatedInstrument.getId()),
            unrelatedInstrument);
    BookingConfiguration unrelated =
        configurationManager.createConfiguration(
            new BookingConfigurationManager.Create(true, "UTC", unrelatedTarget), owner, owner);
    bookingManager.createBooking(
        new TimeSlotBookingManager.Create(
            unrelatedTarget,
            Date.from(start.plus(2, ChronoUnit.HOURS)),
            Date.from(start.plus(3, ChronoUnit.HOURS)),
            "Unrelated booking"),
        owner,
        owner);
    jdbcTemplate.update(
        "INSERT INTO BookableItemCalendarSubscription"
            + " (bookingConfiguration_id, user_id, tokenHash, rawToken, updatedAt)"
            + " VALUES (?, ?, ?, ?, NOW(6))",
        configurationId,
        owner.getId(),
        "a".repeat(64),
        "permanent-delete-test-token");

    assertEquals(1, count("BookingConfiguration", "id", configurationId));
    assertEquals(1, count("TimeSlotBooking", "bookingConfiguration_id", configurationId));
    assertEquals(
        1, count("BookableItemCalendarSubscription", "bookingConfiguration_id", configurationId));
    assertTrue(count("ResourceRoleAssignment", "resourceAccess_id", accessId) > 0);

    long currentVersion =
        jdbcTemplate.queryForObject(
            "SELECT configurationVersion FROM BookingConfiguration WHERE id = ?",
            Long.class,
            configurationId);
    User sysadmin = getSysAdminUser();
    assertEquals(
        configurationId,
        configurationManager
            .permanentlyDeleteConfiguration(configurationId, currentVersion, sysadmin, sysadmin)
            .orElseThrow());

    assertFalse(configurationManager.getConfiguration(configurationId, sysadmin).isPresent());
    assertEquals(0, count("BookingConfiguration", "id", configurationId));
    assertEquals(0, count("TimeSlotBooking", "bookingConfiguration_id", configurationId));
    assertEquals(
        0, count("BookableItemCalendarSubscription", "bookingConfiguration_id", configurationId));
    assertEquals(0, count("ResourceRoleAssignment", "resourceAccess_id", accessId));
    assertEquals(0, count("ResourceAccess", "id", accessId));
    assertEquals(1, count("BookingConfiguration", "id", unrelated.getId()));
    assertEquals(1, count("TimeSlotBooking", "bookingConfiguration_id", unrelated.getId()));
    assertEquals(1, count("InstrumentEntity", "id", instrument.getId()));
    assertEquals(1, count("InstrumentEntity", "id", unrelatedInstrument.getId()));
    assertEquals(1, count("User", "id", owner.getId()));

    assertTrue(count("BookingConfiguration_AUD", "id", configurationId) >= 2);
    assertEquals(1, deletedRevisionCount("BookingConfiguration_AUD", "id", configurationId));
    assertEquals(1, deletedRevisionCount("TimeSlotBooking_AUD", "id", bookingId));
    assertEquals(1, deletedRevisionCount("ResourceAccess_AUD", "id", accessId));
    assertTrue(count("ResourceRoleAssignment_AUD", "resourceAccess_id", accessId) > 0);
  }

  private Instrument initializedInstrument(Long instrumentId) {
    openTransaction();
    Instrument instrument = instrumentDao.get(instrumentId);
    Hibernate.initialize(instrument.getOwner());
    commitTransaction();
    return instrument;
  }

  private int count(String table, String column, Long id) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, id);
  }

  private int deletedRevisionCount(String table, String column, Long id) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ? AND REVTYPE = 2",
        Integer.class,
        id);
  }
}
