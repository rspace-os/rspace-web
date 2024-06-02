package com.researchspace.service.chemistry;

import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.dtos.chemistry.ChemElementDataDto;
import com.researchspace.model.dtos.chemistry.ChemicalExportFormat;
import com.researchspace.model.dtos.chemistry.ChemicalSearchResultsDTO;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/***
 * Dummy implementation with limited functionality. To be replaced with open-source chemistry module
 * implementation.
 */
@Service
public class DefaultChemistryProvider implements ChemistryProvider {

  @Override
  public byte[] exportToImage(String chemicalString, ChemicalExportFormat chemicalExportFormat) {
    return new byte[0];
  }

  @Override
  public RSChemElement save(RSChemElement rsChemElement) {
    return rsChemElement;
  }

  @Override
  public String convert(String chemical) {
    return "";
  }

  @Override
  public String convert(File chemical) throws IOException {
    return "";
  }

  @Override
  public Optional<ElementalAnalysisDTO> getProperties(RSChemElement chemicalElement) {
    return Optional.of(new ElementalAnalysisDTO());
  }

  @Override
  public ChemicalSearchResultsDTO search(String chemQuery, String chemicalSearchType) {
    return new ChemicalSearchResultsDTO();
  }

  @Override
  public List<String> getSupportedFileTypes() {
    return Collections.emptyList();
  }

  @Override
  public ChemElementDataDto getUpdatableChemicals(
      EcatChemistryFile chemistryFile, List<RSChemElement> chemicalElements) {
    return new ChemElementDataDto();
  }

  @Override
  public ChemElementDataDto getChemicalsForFile(EcatChemistryFile chemistryFile) {
    return new ChemElementDataDto();
  }
}
