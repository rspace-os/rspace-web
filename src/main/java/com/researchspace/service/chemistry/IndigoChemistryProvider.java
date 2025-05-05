package com.researchspace.service.chemistry;

import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.dtos.chemistry.ChemicalExportFormat;
import com.researchspace.model.dtos.chemistry.ChemicalSearchResultsDTO;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class IndigoChemistryProvider implements ChemistryProvider {

  private final ChemistryClient chemistryClient;

  @Autowired
  public IndigoChemistryProvider(ChemistryClient chemistryClient) {
    this.chemistryClient = chemistryClient;
  }

  @Override
  public byte[] exportToImage(
      String chemicalString, String inputFormat, ChemicalExportFormat outputFormat) {
    return chemistryClient.exportImage(chemicalString, inputFormat, outputFormat);
  }

  @Override
  public String convertToDefaultFormat(String chemical) {
    String emptyInputFormat = "";
    return chemistryClient.convert(chemical, emptyInputFormat, defaultFormat().getLabel());
  }

  @Override
  public String convert(String chemical, String outputFormat) {
    String emptyInputFormat = "";
    return chemistryClient.convert(chemical, emptyInputFormat, outputFormat);
  }

  @Override
  public String convert(String chemical, String inputFormat, String outputFormat) {
    return chemistryClient.convert(chemical, inputFormat, outputFormat);
  }

  @Override
  public String convertToDefaultFormat(String chemical, String inputFormat) {
    if (StringUtils.isBlank(inputFormat)) {
      return convertToDefaultFormat(chemical);
    }
    return chemistryClient.convert(chemical, inputFormat, defaultFormat().getLabel());
  }

  @Override
  public Optional<ElementalAnalysisDTO> getProperties(RSChemElement chemicalElement) {
    return chemistryClient.extract(chemicalElement);
  }

  @Override
  public List<String> getSupportedFileTypes() {
    return List.of("smiles", "cdxml", "rxn", "rxnfile", "ket", "mol");
  }

  @Override
  public ChemElementsFormat graphicFormat() {
    return ChemElementsFormat.KET;
  }

  @Override
  public RSChemElement save(RSChemElement rsChemElement) {
    chemistryClient.save(rsChemElement);
    return rsChemElement;
  }

  @Override
  public ChemicalSearchResultsDTO search(String chemQuery, String chemicalSearchType) {
    return chemistryClient.search(chemQuery, chemicalSearchType);
  }
}
