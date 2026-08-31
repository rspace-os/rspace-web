package com.researchspace.booking.api.v2;

import com.researchspace.api.v2.resource.ApiV2RelationshipTargetSpec;
import com.researchspace.booking.service.BookingConfigurationTargetManager;
import com.researchspace.model.booking.ApiV2BookingInstrumentResource;
import com.researchspace.model.inventory.Instrument;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers Booking's safe Instrument relationship without exposing a collection route. */
@Configuration(proxyBeanMethods = false)
public class BookingInstrumentRelationshipOperations {

  private final BookingConfigurationTargetManager targetManager;

  public BookingInstrumentRelationshipOperations(BookingConfigurationTargetManager targetManager) {
    this.targetManager = targetManager;
  }

  @Bean
  ApiV2RelationshipTargetSpec<Instrument, Long> bookingInstrumentRelationshipResource() {
    return new ApiV2RelationshipTargetSpec<>(
        ApiV2BookingInstrumentResource.DESCRIPTION,
        Long.class,
        (ids, ignored) -> targetManager.resolveRelationshipTargets(ids));
  }
}
