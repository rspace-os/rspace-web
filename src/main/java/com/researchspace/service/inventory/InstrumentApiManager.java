package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentEntity;

/** Handles API actions around Inventory Instrument. */
public interface InstrumentApiManager extends InventoryApiManager<InstrumentEntity> {

  /** Checks if instrument with given id exists */
  boolean exists(long id);

  /**
   * Creates the Instrument according to provided apiInstrument definition, including fields, extra
   * fields.
   *
   * @returns newly created instrument
   */
  ApiInstrument createNewApiInstrument(ApiInstrument apiInstrument, User user);

  /**
   * @returns ApiInstrument with a given id
   */
  ApiInstrument getApiInstrumentById(Long id, User user);

  ApiInstrument getApiInstrumentTemplateById(Long id, User user);

  //
  //  InstrumentTemplate getInstrumentTemplateByIdWithPopulatedFields(Long id, User user);

  Instrument assertUserCanEditInstrument(Long dbId, User user);

  Instrument assertUserCanReadInstrument(Long dbId, User user);
}
