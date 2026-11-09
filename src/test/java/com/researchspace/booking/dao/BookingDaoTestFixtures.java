package com.researchspace.booking.dao;

import com.researchspace.booking.service.BookingResourceRoleScheme;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import java.util.Date;
import org.hibernate.Session;

final class BookingDaoTestFixtures {

  private BookingDaoTestFixtures() {}

  static BookingConfiguration configuration(Session session, User owner, Long instrumentId) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setEnabled(true);
    configuration.setTimeZone("UTC");
    configuration.replaceTarget(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, instrumentId));
    ResourceAccess access =
        new ResourceAccess(BookingResourceRoleScheme.SCHEME_KEY, owner, new Date());
    access.addAssignment(ResourceRoleAssignment.forUser(BookingResourceRoleScheme.OWNER, owner));
    configuration.setResourceAccess(access);
    session.persist(configuration);
    return configuration;
  }

  static TimeSlotBooking booking(
      Session session,
      BookingConfiguration configuration,
      User requester,
      Date start,
      Date end,
      BookingState state,
      boolean deleted,
      Date createdAt,
      Date updatedAt) {
    TimeSlotBooking booking = new TimeSlotBooking();
    booking.setBookingConfiguration(configuration);
    booking.setRequester(requester);
    booking.setStartTime(start);
    booking.setEndTime(end);
    booking.setState(state);
    booking.setDeleted(deleted);
    if (createdAt != null) {
      booking.setCreatedAt(createdAt);
    }
    if (updatedAt != null) {
      booking.setUpdatedAt(updatedAt);
    }
    booking.setCreatedBy(requester);
    booking.setUpdatedBy(requester);
    session.persist(booking);
    session.flush();
    return booking;
  }
}
