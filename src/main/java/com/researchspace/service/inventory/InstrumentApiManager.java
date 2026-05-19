package com.researchspace.service.inventory;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentEntity;
import com.researchspace.api.v1.model.ApiInstrumentSearchResult;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.InventoryRecord;

/** Handles API actions around Inventory Instrument. */
public interface InstrumentApiManager extends InventoryApiManager<InstrumentEntity> {

  /** Checks if instrument with given id exists */
  boolean instrumentExists(long id);

  boolean instrumentTemplateExists(long id);

  /**
   * Creates the Instrument according to provided apiInstrument definition, including fields, extra
   * fields.
   *
   * @returns newly created instrument
   */
  ApiInstrument createNewApiInstrument(ApiInstrument apiInstrument, User user);

  /** Returns a paginated list of instruments visible to the given user. */
  ApiInstrumentSearchResult getInstrumentsForUser(
      PaginationCriteria<Instrument> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      User user);

  /**
   * @returns ApiInstrument with a given id
   */
  ApiInstrument getApiInstrumentById(Long id, User user);

  ApiInstrumentEntity getApiInstrumentTemplateById(Long id, User user);

  /** Updates the instrument with the provided data. */
  ApiInstrument updateApiInstrument(ApiInstrument apiInstrument, User user);

  /** Soft-deletes the instrument, removing it from its parent container if stored in one. */
  ApiInstrument markInstrumentAsDeleted(Long instrumentId, User user);

  /** Restores a previously soft-deleted instrument. */
  ApiInstrument restoreDeletedInstrument(Long instrumentId, User user);

  /** Changes the owner of the instrument. */
  ApiInstrument changeApiInstrumentOwner(ApiInstrument apiInstrument, User user);

  /** Creates a copy of the instrument. */
  ApiInstrument duplicateInstrument(Long instrumentId, User user);

  /** Returns true if an instrument with the given name already exists for the user. */
  boolean nameExistsForUser(String name, User user);

  Instrument assertUserCanEditInstrument(Long dbId, User user);

  Instrument assertUserCanReadInstrument(Long dbId, User user);

  Instrument assertUserCanDeleteInstrument(Long dbId, User user);

  Instrument assertUserCanTransferInstrument(Long dbId, User user);

  InstrumentTemplate assertUserCanEditInstrumentTemplate(Long dbId, User user);

  InstrumentTemplate assertUserCanReadInstrumentTemplate(Long dbId, User user);

  InventoryRecord assertUserCanEditInventoryEntityField(Long id, User user);

  InventoryRecord assertUserCanReadInventoryEntityField(Long id, User user);
}
