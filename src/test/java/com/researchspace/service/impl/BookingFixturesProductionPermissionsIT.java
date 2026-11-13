package com.researchspace.service.impl;

import static com.researchspace.booking.service.BookingResourceRoleScheme.CREATE_BOOKING;
import static com.researchspace.booking.service.BookingResourceRoleScheme.EDIT_CONFIGURATION;
import static com.researchspace.booking.service.BookingResourceRoleScheme.READ_RESOURCE;
import static com.researchspace.booking.service.BookingResourceRoleScheme.VIEWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.service.BookingItemPermissions;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import com.researchspace.service.GlobalInitManager;
import com.researchspace.service.IApplicationInitialisor;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

/** Runs against a fresh, isolated database with the documented run,dev-test seed. */
@ActiveProfiles(profiles = "prod-test", inheritProfiles = false)
@TestPropertySource(
    properties = {
      "deployment.test.fb.instance=true",
      "rs.dev.groupcreation=false",
      "license.server.active=false"
    })
@EnabledIfSystemProperty(named = "rspace.booking.fixture.integration", matches = "true")
class BookingFixturesProductionPermissionsIT extends RealTransactionSpringTestBase {

  private final GlobalInitManager globalInitManager;
  private final BookingItemPermissions permissions;
  private final BookingConfigurationDao configurations;
  private final IApplicationInitialisor fixtures;

  @Autowired
  BookingFixturesProductionPermissionsIT(
      GlobalInitManager globalInitManager,
      BookingItemPermissions permissions,
      BookingConfigurationDao configurations,
      @Qualifier("bookingFixtures") IApplicationInitialisor fixtures) {
    this.globalInitManager = globalInitManager;
    this.permissions = permissions;
    this.configurations = configurations;
    this.fixtures = fixtures;
  }

  @Override
  public void registerDatabaseCleaner() {
    // Retain the explicitly opted-in isolated database; the base cleanup deletes unrelated seeds.
  }

  @Test
  void productionStartupCreatesOnlyTheRequiredViewOnlyRelationshipAndIsIdempotent()
      throws Exception {
    assertTrue(
        List.of(applicationContext.getEnvironment().getActiveProfiles()).contains("prod-test"));
    assertFalse(
        globalInitManager.getApplicationInitialisors().stream()
            .anyMatch(initialiser -> initialiser instanceof DeveloperGroupSetup));
    List<Long> counts = doInTransaction(this::assertPermissionsAndCountFixtures);

    fixtures.onInitialAppDeployment();
    ReflectionTestUtils.setField(globalInitManager, "contextRefreshEventHandled", false);
    globalInitManager.onApplicationEvent(new ContextRefreshedEvent(applicationContext));

    assertEquals(counts, doInTransaction(this::assertPermissionsAndCountFixtures));
  }

  private List<Long> assertPermissionsAndCountFixtures() {
    User viewer = userDao.getUserByUsername("user1a");
    User owner = userDao.getUserByUsername("user2b");
    assertEquals(
        1,
        owner.getGroups().stream()
            .filter(group -> group.isLabGroup() && viewer.hasRoleInGroup(group, RoleInGroup.PI))
            .count());
    assertTrue(viewer.isContentInitialized());
    assertTrue(owner.isContentInitialized());
    assertTrue(userDao.getUserByUsername("user3c").getGroups().isEmpty());
    String readerName =
        messages.getMessage(
            "bookingFixtures.instruments.restrictedLocationPlateReader", new Object[0], Locale.US);
    List<Instrument> readers = instrumentDao.findInstrumentsByName(readerName, owner);
    assertEquals(1, readers.size());
    Instrument reader = readers.get(0);
    assertEquals(InventorySharingMode.OWNER_ONLY, reader.getSharingMode());
    BookingConfiguration configuration =
        configurations
            .findByTarget(
                new BookableTargetReference(BookableTargetType.INSTRUMENT, reader.getId()))
            .orElseThrow();
    assertTrue(
        bookingConfigurationManager.getConfiguration(configuration.getId(), viewer).isPresent());
    ResolvedResourceAccess access = permissions.resolve(configuration, viewer);
    assertEquals(
        access, permissions.resolveAll(List.of(configuration), viewer).get(configuration.getId()));
    assertEquals(Optional.of(VIEWER), access.effectiveRole());
    assertTrue(access.hasCapability(READ_RESOURCE));
    assertFalse(access.hasCapability(CREATE_BOOKING));
    assertFalse(access.hasCapability(EDIT_CONFIGURATION));
    return List.of(
        count("Group"),
        count("Instrument"),
        count("BookingConfiguration"),
        count("TimeSlotBooking"));
  }

  private long count(String entity) {
    return sessionFactory
        .getCurrentSession()
        .createQuery("select count(e) from " + entity + " e", Long.class)
        .getSingleResult();
  }
}
