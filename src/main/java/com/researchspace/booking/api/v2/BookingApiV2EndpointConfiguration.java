package com.researchspace.booking.api.v2;

import com.researchspace.api.v2.resource.ApiV2EndpointSpec;
import com.researchspace.model.collection.AccessFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Contributes calendar endpoint authentication to the shared transport. */
@Configuration(proxyBeanMethods = false)
public class BookingApiV2EndpointConfiguration {
  @Bean
  public ApiV2EndpointSpec bookingCalendarEndpointSpec() {
    return new ApiV2EndpointSpec(BookingCalendarController.class, AccessFunction.authenticated());
  }
}
