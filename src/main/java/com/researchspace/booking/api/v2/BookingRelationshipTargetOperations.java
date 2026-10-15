package com.researchspace.booking.api.v2;

import com.researchspace.api.v2.resource.ApiV2RelationshipTargetSpec;
import com.researchspace.booking.service.BookingConfigurationTargetManager;
import com.researchspace.booking.service.BookingLocationFilterManager;
import com.researchspace.model.booking.ApiV2BookingInstrumentResource;
import com.researchspace.model.booking.ApiV2BookingLocationResource;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.inventory.InstrumentReadAccess;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers Booking relationship targets without exposing collection routes. */
@Configuration(proxyBeanMethods = false)
public class BookingRelationshipTargetOperations {

  @Bean
  ApiV2RelationshipTargetSpec<Instrument, Long> bookingInstrumentRelationshipResource(
      BookingConfigurationTargetManager targetManager, InstrumentReadAccess instrumentReadAccess) {
    return new ApiV2RelationshipTargetSpec<>(
        ApiV2BookingInstrumentResource.description(instrumentReadAccess),
        Long.class,
        targetManager::resolveRelationshipTargets,
        "instruments");
  }

  @Bean
  ApiV2RelationshipTargetSpec<ApiV2BookingLocationResource.Location, Long>
      bookingLocationRelationshipResource(BookingLocationFilterManager locations) {
    return new ApiV2RelationshipTargetSpec<>(
        ApiV2BookingLocationResource.DESCRIPTION, Long.class, locations::findReadableLocations);
  }
}
