package com.researchspace.service.inventory;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentSearchResult;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInstrumentTemplateSearchResult;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.InventoryRecord;
import java.util.List;

/** Handles API actions around Inventory Instrument. */
public interface InstrumentEntityApiManager extends InventoryApiManager<InstrumentEntity> {

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

  /** Returns a paginated list of instrument templates visible to the given user. */
  ApiInstrumentTemplateSearchResult getTemplatesForUser(
      PaginationCriteria<InstrumentTemplate> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      User user);

  /**
   * @returns ApiInstrument with a given id
   */
  ApiInstrument getApiInstrumentById(Long id, User user);

  /**
   * @returns ApiInstrumentTemplate with a given id
   */
  ApiInstrumentTemplate getApiInstrumentTemplateById(Long id, User user);

  /** Returns a historical snapshot of the given instrument template at the requested version. */
  ApiInstrumentTemplate getApiInstrumentTemplateVersion(Long id, Long version, User user);

  /** Creates a new instrument template from the provided POST payload. */
  ApiInstrumentTemplate createInstrumentTemplate(ApiInstrumentTemplatePost post, User user);

  /** Updates the instrument with the provided data. */
  ApiInstrument updateApiInstrument(ApiInstrument apiInstrument, User user);

  /**
   * Updates the instrument template with the provided data; bumps its version on content change.
   */
  ApiInstrumentTemplate updateApiInstrumentTemplate(ApiInstrumentTemplate apiTemplate, User user);

  /** Soft-deletes the instrument, removing it from its parent container if stored in one. */
  ApiInstrument markInstrumentAsDeleted(Long instrumentId, User user);

  /** Soft-deletes the instrument template. */
  ApiInstrumentTemplate markInstrumentTemplateAsDeleted(Long templateId, User user);

  /** Restores a previously soft-deleted instrument. */
  ApiInstrument restoreDeletedInstrument(Long instrumentId, User user);

  /** Restores a previously soft-deleted instrument template. */
  ApiInstrumentTemplate restoreDeletedInstrumentTemplate(Long templateId, User user);

  /** Changes the owner of the instrument. */
  ApiInstrument changeApiInstrumentOwner(ApiInstrument apiInstrument, User user);

  /** Changes the owner of the instrument template. */
  ApiInstrumentTemplate changeApiInstrumentTemplateOwner(
      ApiInstrumentTemplate apiTemplate, User user);

  /** Creates a copy of the instrument. */
  ApiInstrument duplicateInstrument(Long instrumentId, User user);

  /** Creates a copy of the instrument template. */
  ApiInstrumentTemplate duplicateInstrumentTemplate(Long templateId, User user);

  /**
   * Re-syncs an instrument with the latest version of its template, copying any added template
   * fields onto the instrument. No-op if already on the latest version.
   */
  ApiInstrument updateInstrumentToLatestTemplateVersion(Long instrumentId, User user);

  /**
   * Returns instruments owned by the given user that were created from the given template at an
   * older version than the template's current version.
   */
  List<ApiInventoryRecordInfo> getInstrumentsLinkingOldTemplateVersion(Long templateId, User user);

  /** Persists a new icon id on the given instrument template. */
  InstrumentTemplate saveIconId(InstrumentTemplate template, Long iconId);

  /** Returns true if an instrument with the given name already exists for the user. */
  boolean nameExistsForUser(String name, User user);

  /** Returns true if an instrument template with the given name already exists for the user. */
  boolean templateNameExistsForUser(String name, User user);

  Instrument assertUserCanEditInstrument(Long dbId, User user);

  Instrument assertUserCanReadInstrument(Long dbId, User user);

  Instrument assertUserCanDeleteInstrument(Long dbId, User user);

  Instrument assertUserCanTransferInstrument(Long dbId, User user);

  InstrumentTemplate assertUserCanEditInstrumentTemplate(Long dbId, User user);

  InstrumentTemplate assertUserCanReadInstrumentTemplate(Long dbId, User user);

  /**
   * Like {@link #assertUserCanReadInstrumentTemplate(Long, User)} but eagerly initialises the
   * template's fields collection within the transaction, so that the returned (detached) template
   * can be safely passed to downstream code (e.g. controllers, validators, the record factory)
   * without triggering a {@link org.hibernate.LazyInitializationException}.
   */
  InstrumentTemplate assertUserCanReadInstrumentTemplateWithPopulatedFields(Long dbId, User user);

  InstrumentTemplate assertUserCanDeleteInstrumentTemplate(Long dbId, User user);

  InstrumentTemplate assertUserCanTransferInstrumentTemplate(Long dbId, User user);

  InventoryRecord assertUserCanEditInventoryEntityField(Long id, User user);

  InventoryRecord assertUserCanReadInventoryEntityField(Long id, User user);
}
