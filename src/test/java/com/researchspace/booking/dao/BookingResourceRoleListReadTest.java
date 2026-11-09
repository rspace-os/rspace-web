package com.researchspace.booking.dao;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.Constants;
import com.researchspace.booking.config.BookingResourceAccessConfiguration;
import com.researchspace.booking.service.BookingResourceRoleScheme;
import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceAudience;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import com.researchspace.testutils.SpringTransactionalTest;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

class BookingResourceRoleListReadTest extends SpringTransactionalTest {

  private final TimeSlotBookingDao bookingDao;
  private final CollectionDescription<TimeSlotBooking> description;

  @Autowired
  BookingResourceRoleListReadTest(
      TimeSlotBookingDao bookingDao,
      @Qualifier(BookingResourceAccessConfiguration.TIME_SLOT_BOOKING_DESCRIPTION)
          CollectionDescription<TimeSlotBooking> description) {
    this.bookingDao = bookingDao;
    this.description = description;
  }

  @Test
  void listsBookingsThroughSeparateDirectGroupAndAllUsersAssignments() {
    User owner = createInitAndLoginAnyUser();
    User reader = createInitAndLoginAnyUser();
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("grantPi"), Constants.PI_ROLE);
    Group group = createGroup(getRandomAlphabeticString("grantGroup"), pi);
    group.addMember(reader, RoleInGroup.DEFAULT);

    TimeSlotBooking direct =
        bookingFor(owner, ResourceRoleAssignment.forUser(BookingResourceRoleScheme.BOOKER, reader));
    TimeSlotBooking viaGroup =
        bookingFor(owner, ResourceRoleAssignment.forGroup(BookingResourceRoleScheme.BOOKER, group));
    TimeSlotBooking allUsers =
        bookingFor(
            owner,
            ResourceRoleAssignment.forAudience(
                BookingResourceRoleScheme.BOOKER, ResourceAudience.ALL_USERS, "All users"));
    var session = sessionFactory.getCurrentSession();
    session.flush();
    session.clear();
    User persistedReader = session.find(User.class, reader.getId());
    assertEquals(
        List.of(Constants.USER_ROLE),
        persistedReader.getRoles().stream().map(Role::getName).toList());
    assertTrue(persistedReader.getGroups().stream().anyMatch(g -> g.getId().equals(group.getId())));

    var access =
        description
            .accessPolicy()
            .readAccess()
            .check(
                new AccessContext(
                    persistedReader, AccessContext.Operation.READ, description.resourceName()));
    // Require a database predicate, so an implicit global role cannot make this test pass.
    var constraint = access.constraintOrEmpty().orElseThrow();
    ResourceRequest request =
        new ResourceRequest(
                null,
                // Newest first: the development fixtures add other readable bookings to this
                // schema.
                List.of(new Sort("id", false)),
                new ResourceRequest.Page(1, 10),
                FieldSelection.all(),
                IncludeTree.empty())
            .restrict(constraint);

    var page = bookingDao.getReadableResources(request, RelationshipReadAccess.none());
    List<Long> ids = page.resources().stream().map(TimeSlotBooking::getId).toList();

    assertAll(
        () -> assertTrue(ids.contains(direct.getId()), "Direct-user assignment missing: " + ids),
        () -> assertTrue(ids.contains(viaGroup.getId()), "Group assignment missing: " + ids),
        () -> assertTrue(ids.contains(allUsers.getId()), "All-users assignment missing: " + ids));
  }

  private TimeSlotBooking bookingFor(User owner, ResourceRoleAssignment assignment) {
    var session = sessionFactory.getCurrentSession();
    ResourceAccess access =
        new ResourceAccess(BookingResourceRoleScheme.SCHEME_KEY, owner, new Date());
    access.addAssignment(assignment);
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setEnabled(true);
    configuration.setTimeZone("UTC");
    configuration.replaceTarget(
        new BookableTargetReference(
            BookableTargetType.INSTRUMENT,
            createBasicInstrumentForUser(owner, "Separate grant scope").getId()));
    configuration.setResourceAccess(access);
    session.persist(configuration);
    // The reader is never the requester, owner, or creator, and each access has exactly one grant.
    return BookingDaoTestFixtures.booking(
        session,
        configuration,
        owner,
        Date.from(Instant.parse("2026-08-17T10:00:00Z")),
        Date.from(Instant.parse("2026-08-17T11:00:00Z")),
        BookingState.CONFIRMED,
        false,
        null,
        null);
  }
}
