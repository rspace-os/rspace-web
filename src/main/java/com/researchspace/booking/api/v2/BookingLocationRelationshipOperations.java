package com.researchspace.booking.api.v2;

import com.researchspace.api.v2.resource.ApiV2RelationshipTargetSpec;
import com.researchspace.booking.service.BookingLocationFilterManager;
import com.researchspace.model.booking.ApiV2BookingLocationResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the target of the filter-only Booking {@code location} relationship without a
 * collection route. The picker searches {@code /api/v2/booking-catalogue/locations}.
 */
@Configuration(proxyBeanMethods = false)
public class BookingLocationRelationshipOperations {

  private final BookingLocationFilterManager locations;

  public BookingLocationRelationshipOperations(BookingLocationFilterManager locations) {
    this.locations = locations;
  }

  @Bean
  ApiV2RelationshipTargetSpec<ApiV2BookingLocationResource.Location, Long>
      bookingLocationRelationshipResource() {
    return new ApiV2RelationshipTargetSpec<>(
        ApiV2BookingLocationResource.DESCRIPTION,
        Long.class,
        (ids, caller) -> locations.findReadableLocations(ids, caller));
  }
}
