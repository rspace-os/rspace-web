package com.researchspace.booking.config;

import com.researchspace.booking.service.BookingEventReadAccess;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
import com.researchspace.service.resourceaccess.ResourceRoleSchemeRegistry;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers Booking's shared collection description with its resource-role read constraint. */
@Configuration(proxyBeanMethods = false)
public class BookingResourceAccessConfiguration {

  public static final String BOOKING_CONFIGURATION_DESCRIPTION =
      "bookingConfigurationCollectionDescription";
  public static final String TIME_SLOT_BOOKING_DESCRIPTION = "timeSlotBookingCollectionDescription";

  @Bean(name = BOOKING_CONFIGURATION_DESCRIPTION)
  CollectionDescription<BookingConfiguration> bookingConfigurationCollectionDescription() {
    return ApiV2BookingConfigurationResource.description(currentItemReadAccess());
  }

  @Bean(name = TIME_SLOT_BOOKING_DESCRIPTION)
  CollectionDescription<TimeSlotBooking> timeSlotBookingCollectionDescription(
      ResourceRoleSchemeRegistry schemes) {
    return ApiV2TimeSlotBookingResource.description(new BookingEventReadAccess(schemes));
  }

  /** Restricts configuration rows through the target relationship's registered item policy. */
  static AccessFunction currentItemReadAccess() {
    return AccessFunction.documented(
        "A logged-in user may read Booking configurations for currently readable Inventory items.",
        Set.of(AccessPolicy.AUTHENTICATION_REQUIRED),
        context -> {
          if (!context.isAuthenticated()
              || !context.user().isEnabled()
              || context.user().isAccountLocked()) {
            return com.researchspace.model.collection.AccessResult.denied(
                AccessPolicy.AUTHENTICATION_REQUIRED);
          }
          return com.researchspace.model.collection.AccessResult.allowedWhere(
              new FilterExpression.Comparison("target", Operator.EXISTS, List.of(true), false));
        });
  }
}
