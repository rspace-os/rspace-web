package com.researchspace.service.chemistry;

import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.RSChemElement;
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
 * Dummy implementation of ChemistryProvider. Used as a default with no functionality when no other chemistry is used
 * and for integration tests.
 */
@Service
public class DefaultChemistryProvider implements ChemistryProvider {

  @Override
  public byte[] exportToImage(
      String chemicalString, String inputFormat, ChemicalExportFormat chemicalExportFormat) {
    return new byte[0];
  }

  @Override
  public RSChemElement save(RSChemElement rsChemElement) {
    return rsChemElement;
  }

  @Override
  public String convertToDefaultFormat(String chemical) {
    return "";
  }

  @Override
  public String convert(String chemical, String outputFormat) {
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
  public ChemElementsFormat defaultFormat() {
    return ChemElementsFormat.MOL;
  }
}
