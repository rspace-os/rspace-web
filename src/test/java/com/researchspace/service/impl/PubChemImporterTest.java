package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.researchspace.model.dtos.chemistry.ChemicalImportSearchResult;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchType;
import com.researchspace.model.dtos.chemistry.PubChemResponse;
import com.researchspace.model.dtos.chemistry.PubChemSynonymsResponse;
import com.researchspace.service.ChemicalImportException;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
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
public class PubChemImporterTest {

  @Mock private RestTemplate restTemplate;

  @InjectMocks private PubChemImporter pubChemImporter;

  private static PubChemResponse createValidPubChemResponse() {
    PubChemResponse.ChemicalProperty property = new PubChemResponse.ChemicalProperty();
    property.setCid(2244L);
    property.setTitle("2-acetoxybenzoic acid");
    property.setMolecularFormula("C9H8O4");
    property.setSmiles("CC(=O)OC1=CC=CC=C1C(=O)O");

    PubChemResponse.PropertyTable propertyTable = new PubChemResponse.PropertyTable();
    propertyTable.setProperties(List.of(property));

    PubChemResponse response = new PubChemResponse();
    response.setPropertyTable(propertyTable);
    return response;
  }

  private static PubChemSynonymsResponse createValidSynonymsResponse(Long cid) {
    PubChemSynonymsResponse.Information information = new PubChemSynonymsResponse.Information();
    information.setCid(cid);
    information.setSynonyms(
        List.of(
            "Aspirin",
            "Acetylsalicylic acid",
            "50-78-2",
            "2-acetoxybenzoic acid"));

    PubChemSynonymsResponse.InformationList informationList =
        new PubChemSynonymsResponse.InformationList();
    informationList.setInformation(List.of(information));

    PubChemSynonymsResponse response = new PubChemSynonymsResponse();
    response.setInformationList(informationList);
    return response;
  }

  private static PubChemResponse createEmptyPubChemResponse() {
    PubChemResponse.PropertyTable propertyTable = new PubChemResponse.PropertyTable();
    propertyTable.setProperties(List.of());

    PubChemResponse response = new PubChemResponse();
    response.setPropertyTable(propertyTable);
    return response;
  }

  @Test
  public void whenValidSearchRequest_thenReturnParsedResults() throws Exception {
    ResponseEntity<PubChemResponse> mainResponse =
        new ResponseEntity<>(createValidPubChemResponse(), HttpStatus.OK);

    ResponseEntity<PubChemSynonymsResponse> synonymsResponse =
        new ResponseEntity<>(createValidSynonymsResponse(2244L), HttpStatus.OK);

    when(restTemplate.getForEntity(anyString(), eq(PubChemResponse.class)))
        .thenReturn(mainResponse);

    when(restTemplate.getForEntity(anyString(), eq(PubChemSynonymsResponse.class)))
        .thenReturn(synonymsResponse);

    List<ChemicalImportSearchResult> results =
        pubChemImporter.searchChemicals(ChemicalImportSearchType.NAME, "aspirin");

    assertNotNull(results);
    assertEquals(1, results.size());

    ChemicalImportSearchResult result = results.get(0);
    assertEquals("2244", result.getPubchemId());
    assertEquals("2-acetoxybenzoic acid", result.getName());
    assertEquals("C9H8O4", result.getFormula());
    assertEquals("CC(=O)OC1=CC=CC=C1C(=O)O", result.getSmiles());
    assertEquals("50-78-2", result.getCas());
    assertEquals(
        "https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=2244&t=l", result.getPngImage());
    assertEquals("https://pubchem.ncbi.nlm.nih.gov/compound/2244", result.getPubchemUrl());
  }

  @Test
  public void whenSearchEmptyResponse_thenReturnEmptyList() throws Exception {
    ResponseEntity<PubChemResponse> emptyResponse =
        new ResponseEntity<>(createEmptyPubChemResponse(), HttpStatus.OK);

    when(restTemplate.getForEntity(anyString(), eq(PubChemResponse.class)))
        .thenReturn(emptyResponse);

    List<ChemicalImportSearchResult> results =
        pubChemImporter.searchChemicals(ChemicalImportSearchType.NAME, "nonexistent");

    assertNotNull(results);
    assertTrue(results.isEmpty());
  }

  @Test
  public void whenSearchNotFoundResponse_thenReturnEmptyList() throws Exception {
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

    List<ChemicalImportSearchResult> results =
        pubChemImporter.searchChemicals(ChemicalImportSearchType.NAME, "notfound");

    assertNotNull(results);
    assertTrue(results.isEmpty());
  }

  @Test
  public void whenSearchRateLimitExceeded_thenThrowException() {
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));

    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.searchChemicals(ChemicalImportSearchType.NAME, "aspirin"));

    assertTrue(exception.getMessage().contains("Rate limit exceeded"));
  }

  @Test
  public void whenSearchBadRequest_thenThrowException() {
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.searchChemicals(ChemicalImportSearchType.NAME, "invalid-search"));

    assertTrue(exception.getMessage().contains("Invalid request to PubChem API"));
  }

  @Test
  public void whenNullSearchType_thenThrowException() {
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class, () -> pubChemImporter.searchChemicals(null, "aspirin"));

    assertTrue(exception.getMessage().contains("Search type and term are required"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  public void whenNullOrEmptySearchTerm_thenThrowException(String searchTerm) {
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.searchChemicals(ChemicalImportSearchType.NAME, searchTerm));

    assertTrue(exception.getMessage().contains("Search type and term are required"));
  }

  @Test
  public void whenSearchTimeoutOccurs_thenThrowException() {
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new ResourceAccessException("Timeout"));

    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.searchChemicals(ChemicalImportSearchType.NAME, "aspirin"));

    assertTrue(exception.getMessage().contains("timeout or connection error"));
  }

  @Test
  public void whenSearchRestClientException_thenThrowException() {
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new RestClientException("Network error"));

    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.searchChemicals(ChemicalImportSearchType.NAME, "aspirin"));

    assertTrue(exception.getMessage().contains("Error communicating with PubChem API"));
  }

  @Test
  public void whenSearchUnexpectedError_thenThrowException() {
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new RuntimeException("Unexpected error"));

    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.searchChemicals(ChemicalImportSearchType.NAME, "aspirin"));

    assertTrue(exception.getMessage().contains("Unexpected error"));
  }

  @Test
  public void whenValidSmilesSearch_thenCorrectUrlBuilt() {
    ResponseEntity<PubChemResponse> mainResponse =
        new ResponseEntity<>(createEmptyPubChemResponse(), HttpStatus.OK);

    when(restTemplate.getForEntity(anyString(), eq(PubChemResponse.class)))
        .thenReturn(mainResponse);

    assertDoesNotThrow(
        () ->
            pubChemImporter.searchChemicals(
                ChemicalImportSearchType.SMILES, "CC(=O)OC1=CC=CC=C1C(=O)O"));
  }

  @Test public void correctlyParseCasFromSynonyms() throws Exception {
    assertCasIsParsed("1234567-12-1", List.of("123-123-123", "1234567-12-1"));
    assertCasIsParsed("50-12-1", List.of("somehting", "somehting else", "31/12/1999", "50-12-1"));
    assertCasIsParsed(null, List.of("Aspirin", "Acetylsalicylic acid"));
    assertCasIsParsed(null, List.of());
    assertCasIsParsed(null, List.of("123", "5049383757586-12-1"));
  }

  private void assertCasIsParsed(String targetCas, List<String> candidates) throws ChemicalImportException {
    ResponseEntity<PubChemResponse> mainResponse =
            new ResponseEntity<>(createValidPubChemResponse(), HttpStatus.OK);

    PubChemSynonymsResponse synonymsResponse = getPubChemSynonymsResponseWithSynonyms(candidates); // No CAS number included

    ResponseEntity<PubChemSynonymsResponse> response =
            new ResponseEntity<>(synonymsResponse, HttpStatus.OK);

    when(restTemplate.getForEntity(anyString(), eq(PubChemResponse.class)))
            .thenReturn(mainResponse);

    when(restTemplate.getForEntity(anyString(), eq(PubChemSynonymsResponse.class)))
            .thenReturn(response);

    ChemicalImportSearchResult result =
            pubChemImporter.searchChemicals(ChemicalImportSearchType.NAME, "aspirin").get(0);

    assertEquals(targetCas, result.getCas());
  }

  private static PubChemSynonymsResponse getPubChemSynonymsResponseWithSynonyms(List<String> synonyms) {
    PubChemSynonymsResponse.InformationList informationList = new PubChemSynonymsResponse.InformationList();
    PubChemSynonymsResponse.Information information = new PubChemSynonymsResponse.Information();
    information.setSynonyms(synonyms);
    informationList.setInformation(List.of(information));
    PubChemSynonymsResponse synonymsResponse = new PubChemSynonymsResponse();
    synonymsResponse.setInformationList(informationList);
    return synonymsResponse;
  }

  @Test
  public void whenValidCidImport_thenImportSuccessfully() {
    ResponseEntity<PubChemResponse> mainResponse =
        new ResponseEntity<>(createValidPubChemResponse(), HttpStatus.OK);

    ResponseEntity<PubChemSynonymsResponse> synonymsResponse =
        new ResponseEntity<>(createValidSynonymsResponse(2244L), HttpStatus.OK);

    when(restTemplate.getForEntity(anyString(), eq(PubChemResponse.class)))
        .thenReturn(mainResponse);

    when(restTemplate.getForEntity(anyString(), eq(PubChemSynonymsResponse.class)))
        .thenReturn(synonymsResponse);

    assertDoesNotThrow(() -> pubChemImporter.importChemicals(List.of("2244")));
  }

  @Test
  public void whenValidMultipleCidImport_thenImportAllSuccessfully() {
    ResponseEntity<PubChemResponse> mainResponse =
        new ResponseEntity<>(createValidPubChemResponse(), HttpStatus.OK);

    ResponseEntity<PubChemSynonymsResponse> synonymsResponse =
        new ResponseEntity<>(createValidSynonymsResponse(2244L), HttpStatus.OK);

    when(restTemplate.getForEntity(anyString(), eq(PubChemResponse.class)))
        .thenReturn(mainResponse);

    when(restTemplate.getForEntity(anyString(), eq(PubChemSynonymsResponse.class)))
        .thenReturn(synonymsResponse);

    assertDoesNotThrow(() -> pubChemImporter.importChemicals(List.of("2244", "702", "5957")));
  }

  @Test
  public void whenImportNotFound_thenThrowException() {
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.importChemicals(List.of("invalid-cid")));

    assertTrue(exception.getMessage().contains("No chemical found for PubChem CID"));
  }

  @Test
  public void whenImportEmptyResponse_thenThrowException() {
    when(restTemplate.getForEntity(anyString(), any()))
        .thenReturn(new ResponseEntity<>(createEmptyPubChemResponse(), HttpStatus.OK));

    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class, () -> pubChemImporter.importChemicals(List.of("2244")));

    assertTrue(exception.getMessage().contains("No chemical found for PubChem CID"));
  }

  @Test
  public void whenImportNullCids_thenThrowException() {
    ChemicalImportException exception =
        assertThrows(ChemicalImportException.class, () -> pubChemImporter.importChemicals(null));

    assertTrue(exception.getMessage().contains("At least one PubChem CID is required"));
  }

  @Test
  public void whenImportEmptyCidsList_thenThrowException() {
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class, () -> pubChemImporter.importChemicals(List.of()));

    assertTrue(exception.getMessage().contains("At least one PubChem CID is required"));
  }

  @Test
  public void whenImportBlankCid_thenThrowException() {
    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class, () -> pubChemImporter.importChemicals(List.of("   ")));

    assertTrue(exception.getMessage().contains("PubChem CID cannot be blank"));
  }

  @Test
  public void whenImportRateLimitExceeded_thenThrowAppropriateException() {
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));

    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class, () -> pubChemImporter.importChemicals(List.of("2244")));

    assertTrue(exception.getMessage().contains("Rate limit exceeded"));
  }

  @Test
  public void whenImportBadRequest_thenThrowAppropriateException() {
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class,
            () -> pubChemImporter.importChemicals(List.of("invalid-cid")));

    assertTrue(exception.getMessage().contains("Invalid request to PubChem API"));
  }

  @Test
  public void whenImportTimeoutOccurs_thenThrowAppropriateException() {
    when(restTemplate.getForEntity(anyString(), any()))
        .thenThrow(new ResourceAccessException("Timeout"));

    ChemicalImportException exception =
        assertThrows(
            ChemicalImportException.class, () -> pubChemImporter.importChemicals(List.of("2244")));

    assertTrue(exception.getMessage().contains("timeout or connection error"));
  }
}
