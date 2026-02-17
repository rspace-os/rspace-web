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

  private String chemString = "";
  private String convertedChemString = "";
  private ElementalAnalysisDTO elementalAnalysisDTO = new ElementalAnalysisDTO();

  /**
   * This value will be returned for all 'convert' operations. This allows integration tests to
   * create RSChemElements with actual data.
   */
  public void setChemString(String chemString) {
    this.chemString = chemString;
  }

  /**
   * This value will be returned for all 'convert' operations. This allows integration tests to
   * create RSChemElements with actual data.
   */
  public void setConvertedChemString(String convertedChemString) {
    this.convertedChemString = convertedChemString;
  }

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
    return convertedChemString;
  }

  @Override
  public String convert(String chemical, String outputFormat) {
    return chemString;
  }

  @Override
  public String convert(File chemical) throws IOException {
    return chemString;
  }

  @Override
  public Optional<ElementalAnalysisDTO> getProperties(String chemical) {
    return Optional.of(this.elementalAnalysisDTO);
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
  public Optional<ElementalAnalysisDTO> getStoichiometry(RSChemElement chemicalElement) {
    return Optional.of(this.elementalAnalysisDTO);
  }

  @Override
  public ChemElementsFormat defaultFormat() {
    return ChemElementsFormat.MOL;
  }

  /** Set this to allow Stoichiometries to be created that will have molecules */
  public void setElementalAnalysisDTO(ElementalAnalysisDTO elementalAnalysisDTO) {
    this.elementalAnalysisDTO = elementalAnalysisDTO;
  }
}
