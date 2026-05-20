package com.researchspace.api.v1.controller;

import com.researchspace.model.inventory.Instrument;
import java.util.Set;

/*
 * Helper methods for validating properties of InstrumentEntities
 */
abstract class InstrumentApiValidator extends InventoryRecordValidator {

  private final Set<String> reservedInstrumentsFieldNames =
      (new Instrument()).getReservedFieldNames();

  @Override
  protected Set<String> getReservedFieldNames() {
    return reservedInstrumentsFieldNames;
  }
}
