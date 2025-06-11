package com.researchspace.service;

import com.researchspace.model.dtos.chemistry.ChemicalImportSearchResults;
import java.util.List;

/** Service interface for importing chemical data from external sources. */
public interface ChemicalImporter {

  /**
   * Imports chemical data based on the provided search criteria.
   *
   * @param searchType the type of search to perform (cas, name, smiles)
   * @param searchTerm the search term to use
   * @return a list of chemical search results
   * @throws ChemicalImportException if the import operation fails
   */
  List<ChemicalImportSearchResults> importChemicals(String searchType, String searchTerm)
      throws ChemicalImportException;
}
