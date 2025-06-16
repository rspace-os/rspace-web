package com.researchspace.service;

import com.researchspace.model.dtos.chemistry.ChemicalImportSearchResult;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchType;

import java.util.List;

/** Import chemical data from external sources. */
public interface ChemicalImporter {

  /**
   * Imports chemical data based on the provided search criteria.
   *
   * @param searchType the type of search to perform (cas, name, smiles)
   * @param searchTerm the search term to use
   * @return a list of chemical search results
   * @throws ChemicalImportException if the import operation fails
   */
  List<ChemicalImportSearchResult> searchChemicals(ChemicalImportSearchType searchType, String searchTerm)
      throws ChemicalImportException;

  /**
   * Imports chemical compounds by CAS number(s).
   *
   * @param casNumbers the CAS number(s) of the chemical(s) to import
   * @throws ChemicalImportException if the import operation fails
   */
  void importChemicals(List<String> casNumbers) throws ChemicalImportException;
}
