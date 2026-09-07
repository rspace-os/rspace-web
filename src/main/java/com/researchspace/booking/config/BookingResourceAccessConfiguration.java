package com.researchspace.booking.config;

import com.researchspace.booking.service.BookingEventReadAccess;
import com.researchspace.booking.service.BookingResourceRoleScheme;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.service.resourceaccess.ResourceRoleReadAccess;
import com.researchspace.service.resourceaccess.ResourceRoleSchemeRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers Booking's shared collection description with its resource-role read constraint. */
@Configuration(proxyBeanMethods = false)
public class BookingResourceAccessConfiguration {

  public static final String BOOKING_CONFIGURATION_DESCRIPTION =
      "bookingConfigurationCollectionDescription";
  public static final String TIME_SLOT_BOOKING_DESCRIPTION = "timeSlotBookingCollectionDescription";

  @Bean(name = BOOKING_CONFIGURATION_DESCRIPTION)
  CollectionDescription<BookingConfiguration> bookingConfigurationCollectionDescription(
      ResourceRoleSchemeRegistry schemes) {
    return ApiV2BookingConfigurationResource.description(
        new ResourceRoleReadAccess(
            schemes, BookingResourceRoleScheme.SCHEME_KEY, "resourceAccess.id"));
  }

  @Bean(name = TIME_SLOT_BOOKING_DESCRIPTION)
  CollectionDescription<TimeSlotBooking> timeSlotBookingCollectionDescription(
      ResourceRoleSchemeRegistry schemes) {
    return ApiV2TimeSlotBookingResource.description(new BookingEventReadAccess(schemes));
  }
}
