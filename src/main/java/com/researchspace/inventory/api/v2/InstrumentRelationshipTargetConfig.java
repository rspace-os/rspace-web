package com.researchspace.inventory.api.v2;

import com.researchspace.api.v2.resource.ApiV2RelationshipTargetSpec;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
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
        ApiV2InstrumentResource.DESCRIPTION, Long.class, instrumentManager::findReadableInstrument);
  }
}
