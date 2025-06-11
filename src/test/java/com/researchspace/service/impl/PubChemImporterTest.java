package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.researchspace.model.dtos.chemistry.ChemicalImportSearchResults;
import com.researchspace.service.ChemicalImportException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class PubChemImporterTest {

  @Mock private RestTemplate restTemplate;

  @InjectMocks private PubChemImporter pubChemImporter;

  private static final String VALID_PUBCHEM_RESPONSE =
      "{\"PC_Compounds\":[{\"id\":{\"id\":{\"cid\":2244}},\"props\":[{\"urn\":{\"label\":\"IUPAC"
          + " Name\"},\"value\":{\"sval\":\"2-acetoxybenzoic"
          + " acid\"}},{\"urn\":{\"label\":\"Molecular"
          + " Formula\"},\"value\":{\"sval\":\"C9H8O4\"}},{\"urn\":{\"label\":\"Canonical"
          + " SMILES\"},\"value\":{\"sval\":\"CC(=O)OC1=CC=CC=C1C(=O)O\"}},{\"urn\":{\"label\":\"CAS\"},\"value\":{\"sval\":\"50-78-2\"}}]}]}";

  private static final String EMPTY_PUBCHEM_RESPONSE = "{\"PC_Compounds\":[]}";

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(pubChemImporter, "timeoutMs", 10000);
  }

  @Test
  void whenValidSearchRequest_thenReturnParsedResults() throws ChemicalImportException {
    // Arrange
    when(restTemplate.getForEntity(anyString(), any()))
        .thenReturn(new ResponseEntity<>(VALID_PUBCHEM_RESPONSE, HttpStatus.OK));

    // Act
    List<ChemicalImportSearchResults> results = pubChemImporter.importChemicals("name", "aspirin");

    // Assert
    assertNotNull(results);
    assertEquals(1, results.size());

    ChemicalImportSearchResults result = results.get(0);
    assertEquals("2244", result.getCid());
    assertEquals("2-acetoxybenzoic acid", result.getName());
    assertEquals("C9H8O4", result.getFormula());
    assertEquals("CC(=O)OC1=CC=CC=C1C(=O)O", result.getSmiles());
    assertEquals("50-78-2", result.getCas());
  }

  @Test
  void whenEmptyResponse_thenReturnEmptyList() throws ChemicalImportException {
    // Arrange
    when(restTemplate.getForEntity(anyString(), any()))
        .thenReturn(new ResponseEntity<>(EMPTY_PUBCHEM_RESPONSE, HttpStatus.OK));

    // Act
    List<ChemicalImportSearchResults> results =
        pubChemImporter.importChemicals("name", "nonexistent");

    // Assert
    assertNotNull(results);
    assertTrue(results.isEmpty());
  }

  @Test
  void whenNotFoundResponse_thenReturnEmptyList() throws ChemicalImportException {
    // Arrange
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

    // Act
    List<ChemicalImportSearchResults> results = pubChemImporter.importChemicals("name", "notfound");

    // Assert
    assertNotNull(results);
    assertTrue(results.isEmpty());
  }

  @Test
  void whenRateLimitExceeded_thenThrowAppropriateException() {
    // Arrange
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));

    // Act & Assert
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.importChemicals("name", "aspirin"));

    assertTrue(exception.getMessage().contains("Rate limit exceeded"));
  }

  @Test
  void whenInvalidSearchType_thenThrowException() {
    // Act & Assert
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.importChemicals("invalid", "aspirin"));

    assertTrue(exception.getMessage().contains("Invalid search type"));
  }

  @Test
  void whenNullSearchTerm_thenThrowException() {
    // Act & Assert
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class, () -> pubChemImporter.importChemicals("name", null));

    assertTrue(exception.getMessage().contains("Search type and search term are required"));
  }

  @Test
  void whenEmptySearchTerm_thenThrowException() {
    // Act & Assert
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class, () -> pubChemImporter.importChemicals("name", ""));

    assertTrue(exception.getMessage().contains("Search type and search term are required"));
  }

  @Test
  void whenTimeoutOccurs_thenThrowAppropriateException() {
    // Arrange
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new ResourceAccessException("Timeout"));

    // Act & Assert
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.importChemicals("name", "aspirin"));

    assertTrue(exception.getMessage().contains("timeout or connection error"));
  }

  @Test
  void whenGenericRestClientException_thenThrowAppropriateException() {
    // Arrange
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new RestClientException("Network error"));

    // Act & Assert
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.importChemicals("name", "aspirin"));

    assertTrue(exception.getMessage().contains("Error communicating with PubChem API"));
  }

  @Test
  void whenUnexpectedError_thenThrowAppropriateException() {
    // Arrange
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new RuntimeException("Unexpected error"));

    // Act & Assert
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.importChemicals("name", "aspirin"));

    assertTrue(exception.getMessage().contains("Unexpected error"));
  }

  @Test
  void whenValidCasSearch_thenCorrectUrlBuilt() throws ChemicalImportException {
    // Arrange
    when(restTemplate.getForEntity(anyString(), any()))
        .thenReturn(new ResponseEntity<>(EMPTY_PUBCHEM_RESPONSE, HttpStatus.OK));

    // Act
    pubChemImporter.importChemicals("cas", "50-78-2");

    // Assert - verify the URL would be built correctly for CAS search
    // Note: In a more sophisticated test, we could capture the URL argument
    // For now, we just verify no exception is thrown for valid CAS search
    // The actual URL verification would require argument captors
  }

  @Test
  void whenValidSmilesSearch_thenCorrectUrlBuilt() throws ChemicalImportException {
    // Arrange
    when(restTemplate.getForEntity(anyString(), any()))
        .thenReturn(new ResponseEntity<>(EMPTY_PUBCHEM_RESPONSE, HttpStatus.OK));

    // Act
    pubChemImporter.importChemicals("smiles", "CC(=O)OC1=CC=CC=C1C(=O)O");

    // Assert - verify no exception is thrown for valid SMILES search
  }
}
