package com.researchspace.dao;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InstrumentTemplate;
import java.util.List;

/** For DAO operations specific to Inventory {@link InstrumentTemplate} */
public interface InstrumentTemplateDao extends InstrumentEntityDao<InstrumentTemplate> {

  /** Returns a paginated list of instrument templates visible to the given user. */
  ISearchResults<InstrumentTemplate> getTemplatesForUser(
      PaginationCriteria<InstrumentTemplate> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      User user);

  /** Returns all instrument templates with the given name owned by the given user. */
  List<InstrumentTemplate> findInstrumentTemplatesByName(String name, User user);

  /**
   * Persists a new InstrumentTemplate, explicitly saving transient Choice/Radio field defs first.
   */
  InstrumentTemplate persistInstrumentTemplate(InstrumentTemplate template);
}
