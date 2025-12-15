package com.researchspace.service;

import com.researchspace.model.dtos.chemistry.ChemicalImportSearchResult;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchType;
import java.util.List;

public interface ChemicalSearcher {

  List<ChemicalImportSearchResult> searchChemicals(
      ChemicalImportSearchType searchType, String searchTerm) throws ChemicalImportException;
}
