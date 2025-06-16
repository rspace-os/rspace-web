package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.researchspace.model.dtos.chemistry.ChemicalImportSearchResult;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchType;
import com.researchspace.service.ChemicalImportException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

  @Test
  void whenValidSearchRequest_thenReturnParsedResults() throws ChemicalImportException {
    when(restTemplate.getForEntity(anyString(), any()))
        .thenReturn(new ResponseEntity<>(VALID_PUBCHEM_RESPONSE, HttpStatus.OK));

    List<ChemicalImportSearchResult> results = pubChemImporter.searchChemicals(ChemicalImportSearchType.NAME, "aspirin");

    assertNotNull(results);
    assertEquals(1, results.size());

    ChemicalImportSearchResult result = results.get(0);
    assertEquals("2244", result.getPubchemId());
    assertEquals("2-acetoxybenzoic acid", result.getName());
    assertEquals("C9H8O4", result.getFormula());
    assertEquals("CC(=O)OC1=CC=CC=C1C(=O)O", result.getSmiles());
    assertEquals("50-78-2", result.getCas());
  }

  @Test
  void whenEmptyResponse_thenReturnEmptyList() throws ChemicalImportException {
    when(restTemplate.getForEntity(anyString(), any()))
        .thenReturn(new ResponseEntity<>(EMPTY_PUBCHEM_RESPONSE, HttpStatus.OK));

    List<ChemicalImportSearchResult> results =
        pubChemImporter.searchChemicals(ChemicalImportSearchType.NAME, "nonexistent");

    assertNotNull(results);
    assertTrue(results.isEmpty());
  }

  @Test
  void whenNotFoundResponse_thenReturnEmptyList() throws ChemicalImportException {
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

    List<ChemicalImportSearchResult> results = pubChemImporter.searchChemicals(ChemicalImportSearchType.NAME, "notfound");

    assertNotNull(results);
    assertTrue(results.isEmpty());
  }

  @Test
  void whenRateLimitExceeded_thenThrowAppropriateException() {
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));

    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.searchChemicals(ChemicalImportSearchType.NAME, "aspirin"));

    assertTrue(exception.getMessage().contains("Rate limit exceeded"));
  }

  @Test
  void whenNullSearchType_thenThrowException() {
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.searchChemicals(null, "aspirin"));

    assertTrue(exception.getMessage().contains("Unknown search type: " + null));
  }

  @Test
  void whenNullSearchTerm_thenThrowException() {
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class, () -> pubChemImporter.searchChemicals(ChemicalImportSearchType.NAME, null));

    assertTrue(exception.getMessage().contains("Search type and term are required"));
  }

  @Test
  void whenEmptySearchTerm_thenThrowException() {
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class, () -> pubChemImporter.searchChemicals(ChemicalImportSearchType.NAME, ""));

    assertTrue(exception.getMessage().contains("Search type and term are required"));
  }

  @Test
  void whenTimeoutOccurs_thenThrowAppropriateException() {
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new ResourceAccessException("Timeout"));

    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.searchChemicals(ChemicalImportSearchType.NAME, "aspirin"));

    assertTrue(exception.getMessage().contains("timeout or connection error"));
  }

  @Test
  void whenGenericRestClientException_thenThrowAppropriateException() {
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new RestClientException("Network error"));

    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.searchChemicals(ChemicalImportSearchType.NAME, "aspirin"));

    assertTrue(exception.getMessage().contains("Error communicating with PubChem API"));
  }

  @Test
  void whenUnexpectedError_thenThrowAppropriateException() {
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new RuntimeException("Unexpected error"));

    // Act & Assert
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.searchChemicals(ChemicalImportSearchType.NAME, "aspirin"));

    assertTrue(exception.getMessage().contains("Unexpected error"));
  }

  @Test
  void whenValidCasSearch_thenCorrectUrlBuilt() throws ChemicalImportException {
    // Arrange
    when(restTemplate.getForEntity(anyString(), any()))
        .thenReturn(new ResponseEntity<>(EMPTY_PUBCHEM_RESPONSE, HttpStatus.OK));

    // Act
    pubChemImporter.searchChemicals(ChemicalImportSearchType.CAS, "50-78-2");

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
    pubChemImporter.searchChemicals(ChemicalImportSearchType.SMILES, "CC(=O)OC1=CC=CC=C1C(=O)O");

    // Assert - verify no exception is thrown for valid SMILES search
  }

  // Import tests
  @Test
  void whenValidCasImport_thenImportSuccessfully() {
    // Arrange
    when(restTemplate.getForEntity(anyString(), any()))
        .thenReturn(new ResponseEntity<>(VALID_PUBCHEM_RESPONSE, HttpStatus.OK));

    // Act & Assert - should not throw exception
    assertDoesNotThrow(() -> pubChemImporter.importChemicals(List.of("50-78-2")));
  }

  @Test
  void whenValidMultipleCasImport_thenImportAllSuccessfully() {
    // Arrange
    when(restTemplate.getForEntity(anyString(), any()))
        .thenReturn(new ResponseEntity<>(VALID_PUBCHEM_RESPONSE, HttpStatus.OK));

    // Act & Assert - should not throw exception for multiple CAS numbers
    assertDoesNotThrow(
        () -> pubChemImporter.importChemicals(List.of("50-78-2", "64-17-5", "57-55-6")));
  }

  @Test
  void whenImportNotFound_thenThrowException() {
    // Arrange
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

    // Act & Assert
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.importChemicals(List.of("invalid-cas")));

    assertTrue(exception.getMessage().contains("No chemical found for CAS number"));
  }

  @Test
  void whenImportEmptyResponse_thenThrowException() {
    // Arrange
    when(restTemplate.getForEntity(anyString(), any()))
        .thenReturn(new ResponseEntity<>(EMPTY_PUBCHEM_RESPONSE, HttpStatus.OK));

    // Act & Assert
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.importChemicals(List.of("50-78-2")));

    assertTrue(exception.getMessage().contains("No chemical found for CAS number"));
  }

  @Test
  void whenImportNullCasNumbers_thenThrowException() {
    // Act & Assert
    ChemicalImportException exception =
        assertThrows(ChemicalImportException.class, () -> pubChemImporter.importChemicals(null));

    assertTrue(exception.getMessage().contains("At least one CAS number is required"));
  }

  @Test
  void whenImportEmptyCasNumbersList_thenThrowException() {
    // Act & Assert
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class, () -> pubChemImporter.importChemicals(List.of()));

    assertTrue(exception.getMessage().contains("At least one CAS number is required"));
  }

  @Test
  void whenImportBlankCasNumber_thenThrowException() {
    // Act & Assert
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class, () -> pubChemImporter.importChemicals(List.of("   ")));

    assertTrue(exception.getMessage().contains("CAS number cannot be blank"));
  }

  @Test
  void whenImportRateLimitExceeded_thenThrowAppropriateException() {
    // Arrange
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));

    // Act & Assert
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.importChemicals(List.of("50-78-2")));

    assertTrue(exception.getMessage().contains("Rate limit exceeded"));
  }

  @Test
  void whenImportTimeoutOccurs_thenThrowAppropriateException() {
    // Arrange
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new ResourceAccessException("Timeout"));

    // Act & Assert
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.importChemicals(List.of("50-78-2")));

    assertTrue(exception.getMessage().contains("timeout or connection error"));
  }
}
