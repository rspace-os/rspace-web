package com.researchspace.inventory.api.v2;

import com.researchspace.api.v2.resource.ApiV2RelationshipTargetSpec;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import jakarta.ws.rs.NotFoundException;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers Instruments as readable polymorphic relationship targets without adding CRUD routes.
 */
@Configuration(proxyBeanMethods = false)
public class InstrumentRelationshipTargetConfig {

  private final InstrumentEntityApiManager instrumentManager;

  public InstrumentRelationshipTargetConfig(InstrumentEntityApiManager instrumentManager) {
    this.instrumentManager = instrumentManager;
  }

  @Bean
  ApiV2RelationshipTargetSpec<Instrument, Long> instrumentApiV2RelationshipTarget() {
    return new ApiV2RelationshipTargetSpec<>(
        ApiV2InstrumentResource.DESCRIPTION, this::findReadable);
  }

  private Optional<Instrument> findReadable(Long id, User actor) {
    try {
      return Optional.of(instrumentManager.assertUserCanReadInstrument(id, actor));
    } catch (NotFoundException ex) {
      return Optional.empty();
    }
  }
}
