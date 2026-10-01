package com.researchspace.booking.api.v2;

import com.researchspace.api.v2.resource.ApiV2RelationshipTargetSpec;
import com.researchspace.booking.service.BookingConfigurationTargetManager;
import com.researchspace.model.booking.ApiV2BookingInstrumentResource;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.inventory.InstrumentReadAccess;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers Booking's safe Instrument relationship without exposing a collection route. */
@Configuration(proxyBeanMethods = false)
public class BookingInstrumentRelationshipOperations {

  private final BookingConfigurationTargetManager targetManager;
  private final InstrumentReadAccess instrumentReadAccess;

  public BookingInstrumentRelationshipOperations(
      BookingConfigurationTargetManager targetManager, InstrumentReadAccess instrumentReadAccess) {
    this.targetManager = targetManager;
    this.instrumentReadAccess = instrumentReadAccess;
  }

  @Bean
  ApiV2RelationshipTargetSpec<Instrument, Long> bookingInstrumentRelationshipResource() {
    return new ApiV2RelationshipTargetSpec<>(
        ApiV2BookingInstrumentResource.description(instrumentReadAccess),
        Long.class,
        (ids, caller) -> targetManager.resolveRelationshipTargets(ids, caller),
        "instruments");
  }
}
