package com.researchspace.service.chemistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.researchspace.model.dtos.chemistry.ChemicalExportFormat;
import com.researchspace.model.dtos.chemistry.ChemicalExportType;
import com.researchspace.model.dtos.chemistry.ChemicalSearchResultsDTO;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class IndigoChemistryProviderTest {

  @Mock ChemistryClient chemistryClient;

  @InjectMocks IndigoChemistryProvider indigoChemistryProvider;

  static final String CC = "CC";

  @Test
  public void whenSuccessfulImageConversion_thenBytesReturned() {
    byte[] expectedBytes = "some bytes".getBytes();
    ChemicalExportFormat outputFormat =
        ChemicalExportFormat.builder().exportType(ChemicalExportType.PNG).build();
    when(chemistryClient.exportImage(CC, "smiles", outputFormat)).thenReturn(expectedBytes);

    byte[] actual = indigoChemistryProvider.exportToImage(CC, "smiles", outputFormat);

    assertEquals(expectedBytes, actual);
  }

  @Test
  public void whenSuccessfulChemicalTypeConversion_thenOutputReturned() {
    String expectedConversion = "some converted chemistry..";
    when(chemistryClient.convert(CC, "", "mol")).thenReturn(expectedConversion);

    String actual = indigoChemistryProvider.convertToDefaultFormat(CC);

    assertEquals(expectedConversion, actual);
  }

  @Test
  public void whenSuccessfulGetProperties_thenPropertiesReturned() {
    Optional<ElementalAnalysisDTO> analysis = Optional.of(new ElementalAnalysisDTO());
    when(chemistryClient.extract(CC)).thenReturn(analysis);

    Optional<ElementalAnalysisDTO> actual = indigoChemistryProvider.getProperties(CC);

    assertEquals(analysis, actual);
  }

  @Test
  public void whenSuccessfulSearch_thenResultsReturned() {
    ChemicalSearchResultsDTO searchResults = new ChemicalSearchResultsDTO();
    when(chemistryClient.search(any(), any())).thenReturn(searchResults);

    ChemicalSearchResultsDTO actual = indigoChemistryProvider.search(CC, "search type");

    assertEquals(searchResults, actual);
  }
}
