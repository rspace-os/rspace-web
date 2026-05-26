package com.researchspace.dao;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import java.util.List;

/** For DAO operations specific to Inventory {@link Instrument} (not templates). */
public interface InstrumentDao extends InstrumentEntityDao<Instrument> {

  /**
   * Returns a paginated list of instruments visible to the given user, excluding instrument
   * templates.
   */
  ISearchResults<Instrument> getInstrumentsForUser(
      PaginationCriteria<Instrument> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      User user);

  /** Returns all instruments with the given name owned by the given user. */
  List<Instrument> findInstrumentsByName(String name, User user);
}
