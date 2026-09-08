package com.researchspace.dao;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.FileProperty;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InstrumentTemplate;
import java.util.List;
import java.util.Optional;

/** For DAO operations specific to Inventory {@link InstrumentTemplate} */
public interface InstrumentTemplateDao extends InstrumentEntityDao<InstrumentTemplate> {

  /**
   * Returns a paginated list of instrument templates visible to the given user. When {@code
   * searchTerm} is non-blank, results are filtered to records whose name contains the term
   * (case-insensitive).
   */
  ISearchResults<InstrumentTemplate> getTemplatesForUser(
      PaginationCriteria<InstrumentTemplate> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      String searchTerm,
      User user);

  /**
   * @return username of the user who owns the locked default instrument template (the oldest
   *     template whose {@code isEditable} flag is {@code false}), or {@code null} if no default
   *     template has been seeded yet. Every user may read (and duplicate) templates owned by this
   *     account.
   */
  String getDefaultTemplatesOwner();

  /**
   * The locked default template with this name (the seeded "Instrument (PIDINST 1.0)"), or empty
   * before the seeder has run. Locked means {@code isEditable=false}, which only the seeder writes,
   * so a same-named template a user created is never returned (ADR 0003).
   */
  Optional<InstrumentTemplate> findLockedTemplateByName(String name);

  /** Returns all instrument templates with the given name owned by the given user. */
  List<InstrumentTemplate> findInstrumentTemplatesByName(String name, User user);

  /** Returns all instrument templates using the given image file property. */
  List<InstrumentTemplate> getAllUsingImage(FileProperty fileProperty);

  /**
   * Persists a new InstrumentTemplate, explicitly saving transient Choice/Radio field defs first.
   */
  InstrumentTemplate persistInstrumentTemplate(InstrumentTemplate template);
}
