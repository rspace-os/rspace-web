package com.researchspace.service.chemistry;

import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.dtos.chemistry.ChemElementDataDto;
import com.researchspace.model.dtos.chemistry.ChemicalExportFormat;
import com.researchspace.model.dtos.chemistry.ChemicalSearchResultsDTO;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public interface ChemistryProvider {

  byte[] exportToImage(String chemicalString, ChemicalExportFormat format);

  RSChemElement save(RSChemElement rsChemElement);

  String convert(String chemical);

  String convert(File chemical) throws IOException;

  Optional<ElementalAnalysisDTO> getProperties(RSChemElement chemicalElement);

  ChemicalSearchResultsDTO search(String chemQuery, String chemicalSearchType);

  List<String> getSupportedFileTypes();

  ChemElementDataDto getUpdatableChemicals(
      EcatChemistryFile chemistryFile, List<RSChemElement> chemicalElements);

  ChemElementDataDto getChemicalsForFile(EcatChemistryFile chemistryFile);
}
