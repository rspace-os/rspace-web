package com.researchspace.booking.api.v2;

import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.booking.service.BookingConfigurationTargetManager;
import com.researchspace.model.booking.ApiV2BookingInstrumentResource;
import com.researchspace.model.inventory.Instrument;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers Booking's safe Instrument relationship without exposing a collection route. */
@Configuration(proxyBeanMethods = false)
public class BookingInstrumentRelationshipOperations {

  @Bean
  ApiV2ResourceSpec<Instrument, Long> bookingInstrumentRelationshipResource(
      BookingConfigurationTargetManager targetManager) {
    return new ApiV2ResourceSpec<>(
        ApiV2BookingInstrumentResource.DESCRIPTION,
        new com.researchspace.api.v2.resource.ResourceOperations<Instrument, Long>() {
          public com.researchspace.model.collection.ResourcePage<Instrument> find(
              com.researchspace.model.collection.ResourceRequest request,
              com.researchspace.model.User actor) {
            throw new UnsupportedOperationException();
          }

          public long count(
              com.researchspace.model.collection.ResourceRequest request,
              com.researchspace.model.User actor) {
            throw new UnsupportedOperationException();
          }

          public java.util.Optional<Instrument> findById(
              Long id, com.researchspace.model.User actor) {
            return java.util.Optional.ofNullable(
                targetManager.resolveRelationshipTargets(java.util.Set.of(id)).get(id));
          }
        },
        Long::valueOf,
        "create-error",
        "update-error",
        java.util.Set.of(),
        java.util.Map.of());
  }
}
