package com.researchspace.testutils;

import com.researchspace.api.v2.resource.ApiV2ResourceException;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.ResolvedResourceReference;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.inventory.Instrument;
import java.util.Date;

public final class BookingResourceTestFixtures {

  private BookingResourceTestFixtures() {}

  public static ApiV2ResourceException translate(
      ApiV2ResourceSpec<?, ?> spec, ResourceOperation operation, RuntimeException failure) {
    return spec.errorMappings().get(operation).stream()
        .filter(mapping -> mapping.exceptionType() == failure.getClass())
        .findFirst()
        .orElseThrow()
        .translate(failure);
  }

  public static ResolvedResourceReference<BookableTargetType, Long> resolved(long id) {
    Instrument instrument = new Instrument();
    instrument.setId(id);
    return new ResolvedResourceReference<>(
        new ResourceReference<>(BookableTargetType.INSTRUMENT, id), instrument);
  }

  public static TimeSlotBooking booking(
      User requester, Date start, Date end, Date createdAt, Date updatedAt) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setTimeZone("Europe/Berlin");
    configuration.replaceTarget(new BookableTargetReference(BookableTargetType.INSTRUMENT, 12L));
    TimeSlotBooking booking = new TimeSlotBooking();
    booking.setId(41L);
    booking.setBookingConfiguration(configuration);
    booking.setRequester(requester);
    booking.setStartTime(start);
    booking.setEndTime(end);
    booking.setState(BookingState.CONFIRMED);
    booking.setPurpose("Secret");
    if (createdAt != null) {
      booking.setCreatedAt(createdAt);
    }
    if (updatedAt != null) {
      booking.setUpdatedAt(updatedAt);
    }
    return booking;
  }
}
