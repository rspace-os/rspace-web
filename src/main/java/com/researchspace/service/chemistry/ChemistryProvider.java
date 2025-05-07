package com.researchspace.service.chemistry;

import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.dtos.chemistry.ChemicalExportFormat;
import com.researchspace.model.dtos.chemistry.ChemicalSearchResultsDTO;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;

@Service
public interface ChemistryProvider {

  byte[] exportToImage(String chemicalString, String inputFormat, ChemicalExportFormat format);

  RSChemElement save(RSChemElement rsChemElement);

  String convertToDefaultFormat(String chemical);

  /*
   * inputFormat only applies to IndigoChemistryProvider, so default here for other impls is to call the original convert
   * method. This interface may be tidied up once indigo is the only engine.
   */
  default String convertToDefaultFormat(String chemicalInput, String inputFormat) {
    return convertToDefaultFormat(chemicalInput);
  }

  String convert(String chemical, String outputFormat);

  /*
  as above method convertToDefaultFormat
   */
  default String convert(String chemical, String inputFormat, String outputFormat) {
    return convert(chemical, outputFormat);
  }

  Optional<ElementalAnalysisDTO> getProperties(RSChemElement chemicalElement);

  ChemicalSearchResultsDTO search(String chemQuery, String chemicalSearchType);

  List<String> getSupportedFileTypes();

  // format for saving chemical elements created in chemical editor
  default ChemElementsFormat graphicFormat() {
    return ChemElementsFormat.MOL;
  }

  // format for saving all other usages of chemical elements
  default ChemElementsFormat defaultFormat() {
    return ChemElementsFormat.MOL;
  }

  /* This method could be moved to e.g. RSChemElementManager */
  default String convert(File file) throws IOException {
    String extension = FilenameUtils.getExtension(file.getName());
    String chemString;
    if (extension.equalsIgnoreCase("cdx")) {
      chemString =
          new String(
              Base64.encodeBase64(FileUtils.readFileToByteArray(file)), StandardCharsets.UTF_8);
    } else {
      chemString = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    }
    return chemString;
  }
}
