package com.researchspace.dao;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.FileProperty;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import java.util.List;

/** For DAO operations specific to Inventory {@link Instrument} (not templates). */
public interface InstrumentDao extends InstrumentEntityDao<Instrument> {

  /**
   * Returns a paginated list of instruments visible to the given user, excluding instrument
   * templates. When {@code searchTerm} is non-blank, results are filtered to records whose name
   * contains the term (case-insensitive).
   */
  ISearchResults<Instrument> getInstrumentsForUser(
      PaginationCriteria<Instrument> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      String searchTerm,
      User user);

  /** Returns all instruments whose image or thumbnail is the given file property. */
  List<Instrument> getAllUsingImage(FileProperty fileProperty);

  /** Returns all instruments with the given name owned by the given user. */
  List<Instrument> findInstrumentsByName(String name, User user);

  /**
   * Returns a paginated list of instruments visible to the given user that were created from the
   * given template.
   */
  ISearchResults<Instrument> getInstrumentsForTemplate(
      PaginationCriteria<Instrument> pgCrit,
      Long templateId,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      User user);

  /**
   * Returns instruments owned by the given user that were created from the given template and whose
   * tracked template version is older than {@code version}.
   */
  List<Instrument> getInstrumentsLinkingOlderTemplateVersionForUser(
      Long templateId, Long version, User user);
}
