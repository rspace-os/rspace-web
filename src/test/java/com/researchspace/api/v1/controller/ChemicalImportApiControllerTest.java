package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchResult;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchType;
import com.researchspace.model.dtos.chemistry.ChemicalSearchRequest;
import com.researchspace.service.ChemicalImportException;
import com.researchspace.service.ChemicalImporter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

@ExtendWith(MockitoExtension.class)
class ChemicalImportApiControllerTest {

  @Mock private ChemicalImporter chemicalImporter;

  @InjectMocks private ChemicalImportApiController controller;

  @Test
  void whenValidSearchRequest_thenReturnSuccessfulResponse() throws Exception {
    ChemicalSearchRequest request = new ChemicalSearchRequest(ChemicalImportSearchType.NAME, "aspirin");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    List<ChemicalImportSearchResult> mockResults =
        Arrays.asList(
            ChemicalImportSearchResult.builder()
                .pubchemId("2244")
                .name("aspirin")
                .formula("C9H8O4")
                .smiles("CC(=O)OC1=CC=CC=C1C(=O)O")
                .cas("50-78-2")
                .build());

    when(chemicalImporter.searchChemicals(ChemicalImportSearchType.NAME, "aspirin")).thenReturn(mockResults);

    ResponseEntity<?> response = controller.searchChemicals(request, bindingResult);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertInstanceOf(List.class, response.getBody());

    @SuppressWarnings("unchecked")
    List<ChemicalImportSearchResult> responseBody =
        (List<ChemicalImportSearchResult>) response.getBody();
    assertEquals(1, responseBody.size());
    assertEquals("2244", responseBody.get(0).getPubchemId());
    assertEquals("aspirin", responseBody.get(0).getName());
  }

  @Test
  void whenValidationErrors_thenThrowBindException() throws Exception {
    ChemicalSearchRequest request = new ChemicalSearchRequest(null, "aspirin");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");
    bindingResult.rejectValue(
        "searchType", "invalid", "Search type must be one of: cas, name, smiles");

    ResponseEntity<?> response = controller.searchChemicals(request, bindingResult);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertNotNull(response.getBody());
  }

  @Test
  void whenRateLimitExceeded_thenReturnTooManyRequests() throws Exception {
    ChemicalSearchRequest request = new ChemicalSearchRequest(ChemicalImportSearchType.NAME, "aspirin");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    when(chemicalImporter.searchChemicals(ChemicalImportSearchType.NAME, "aspirin"))
        .thenThrow(new ChemicalImportException("Rate limit exceeded"));

    ResponseEntity<?> response = controller.searchChemicals(request, bindingResult);

    assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
  }

  @Test
  void whenTimeoutError_thenReturnBadGateway() throws Exception {
    ChemicalSearchRequest request = new ChemicalSearchRequest(ChemicalImportSearchType.NAME, "aspirin");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    when(chemicalImporter.searchChemicals(ChemicalImportSearchType.NAME, "aspirin"))
        .thenThrow(new ChemicalImportException("Connection timeout"));

    ResponseEntity<?> response = controller.searchChemicals(request, bindingResult);

    assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
  }

  @Test
  void whenUnexpectedError_thenReturnInternalServerError() throws Exception {
    ChemicalSearchRequest request = new ChemicalSearchRequest(ChemicalImportSearchType.NAME, "aspirin");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    when(chemicalImporter.searchChemicals(ChemicalImportSearchType.NAME, "aspirin"))
        .thenThrow(new ChemicalImportException("Unexpected error"));

    ResponseEntity<?> response = controller.searchChemicals(request, bindingResult);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
  }

  @Test
  void whenRuntimeException_thenReturnInternalServerError() throws Exception {
    ChemicalSearchRequest request = new ChemicalSearchRequest(ChemicalImportSearchType.NAME, "aspirin");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    when(chemicalImporter.searchChemicals(ChemicalImportSearchType.NAME, "aspirin"))
        .thenThrow(new RuntimeException("Unexpected runtime error"));

    ResponseEntity<?> response = controller.searchChemicals(request, bindingResult);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
  }

  @Test
  void whenNoResultsFound_thenReturnEmptyList() throws Exception {
    ChemicalSearchRequest request = new ChemicalSearchRequest(ChemicalImportSearchType.NAME, "nonexistent");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    when(chemicalImporter.searchChemicals(ChemicalImportSearchType.NAME, "nonexistent"))
        .thenReturn(Collections.emptyList());

    ResponseEntity<?> response = controller.searchChemicals(request, bindingResult);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertTrue(response.getBody() instanceof List);

    @SuppressWarnings("unchecked")
    List<ChemicalImportSearchResult> responseBody =
        (List<ChemicalImportSearchResult>) response.getBody();
    assertTrue(responseBody.isEmpty());
  }

  @Test
  void whenValidCasSearch_thenReturnResults() throws Exception {
    ChemicalSearchRequest request = new ChemicalSearchRequest(ChemicalImportSearchType.CAS, "50-78-2");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    List<ChemicalImportSearchResult> mockResults =
        Arrays.asList(
            ChemicalImportSearchResult.builder().pubchemId("2244").cas("50-78-2").build());

    when(chemicalImporter.searchChemicals(ChemicalImportSearchType.CAS, "50-78-2")).thenReturn(mockResults);

    ResponseEntity<?> response = controller.searchChemicals(request, bindingResult);

    assertEquals(HttpStatus.OK, response.getStatusCode());

    @SuppressWarnings("unchecked")
    List<ChemicalImportSearchResult> responseBody =
        (List<ChemicalImportSearchResult>) response.getBody();
    assertEquals(1, responseBody.size());
    assertEquals("50-78-2", responseBody.get(0).getCas());
  }

  @Test
  void whenValidSmilesSearch_thenReturnResults() throws Exception {
    ChemicalSearchRequest request = new ChemicalSearchRequest(ChemicalImportSearchType.SMILES, "CC(=O)OC1=CC=CC=C1C(=O)O");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    List<ChemicalImportSearchResult> mockResults =
        Arrays.asList(
            ChemicalImportSearchResult.builder()
                .pubchemId("2244")
                .smiles("CC(=O)OC1=CC=CC=C1C(=O)O")
                .build());

    when(chemicalImporter.searchChemicals(ChemicalImportSearchType.SMILES, "CC(=O)OC1=CC=CC=C1C(=O)O"))
        .thenReturn(mockResults);

    ResponseEntity<?> response = controller.searchChemicals(request, bindingResult);

    assertEquals(HttpStatus.OK, response.getStatusCode());

    @SuppressWarnings("unchecked")
    List<ChemicalImportSearchResult> responseBody =
        (List<ChemicalImportSearchResult>) response.getBody();
    assertEquals(1, responseBody.size());
    assertEquals("CC(=O)OC1=CC=CC=C1C(=O)O", responseBody.get(0).getSmiles());
  }

  // Import tests
  @Test
  void whenValidCasNumberImport_thenReturnCreated() throws Exception {
    List<String> casNumbers = List.of("50-78-2");
    BindingResult bindingResult = new BeanPropertyBindingResult(casNumbers, "request");
    doNothing().when(chemicalImporter).importChemicals(casNumbers);

    ResponseEntity<?> response = controller.importChemicals(casNumbers, bindingResult);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNull(response.getBody());
  }

  @Test
  void whenValidMultipleCasNumbersImport_thenReturnCreated() throws Exception {
    List<String> casNumbers = List.of("50-78-2", "64-17-5", "57-55-6");
    BindingResult bindingResult = new BeanPropertyBindingResult(casNumbers, "request");
    doNothing().when(chemicalImporter).importChemicals(casNumbers);

    ResponseEntity<?> response = controller.importChemicals(casNumbers, bindingResult);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNull(response.getBody());
  }

  @Test
  void whenEmptyCasNumbersList_thenReturnBadRequest() throws Exception{
    List<String> casNumbers = Collections.emptyList();
    BindingResult bindingResult = new BeanPropertyBindingResult(casNumbers, "request");
    ResponseEntity<?> response = controller.importChemicals(casNumbers, bindingResult);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("At least one CAS number is required", response.getBody());
  }

  @Test
  void whenNullCasNumbersList_thenReturnBadRequest() throws Exception {
    BindingResult bindingResult = new BeanPropertyBindingResult(null, "request");
    ResponseEntity<?> response = controller.importChemicals(null, bindingResult);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("At least one CAS number is required", response.getBody());
  }

  @Test
  void whenImportRateLimitExceeded_thenReturnTooManyRequests() throws Exception {
    List<String> casNumbers = List.of("50-78-2");
    BindingResult bindingResult = new BeanPropertyBindingResult(casNumbers, "request");
    doThrow(new ChemicalImportException("Rate limit exceeded"))
        .when(chemicalImporter)
        .importChemicals(anyList());

    ResponseEntity<?> response = controller.importChemicals(casNumbers, bindingResult);

    assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
  }

  @Test
  void whenImportTimeoutError_thenReturnBadGateway() throws Exception {
    List<String> casNumbers = List.of("50-78-2");
    BindingResult bindingResult = new BeanPropertyBindingResult(casNumbers, "request");
    doThrow(new ChemicalImportException("Connection timeout"))
        .when(chemicalImporter)
        .importChemicals(anyList());

    ResponseEntity<?> response = controller.importChemicals(casNumbers, bindingResult);

    assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
  }

  @Test
  void whenImportUnexpectedError_thenReturnInternalServerError() throws Exception {
    List<String> casNumbers = List.of("50-78-2");
    BindingResult bindingResult = new BeanPropertyBindingResult(casNumbers, "request");
    doThrow(new ChemicalImportException("Unexpected error"))
        .when(chemicalImporter)
        .importChemicals(anyList());

    ResponseEntity<?> response = controller.importChemicals(casNumbers, bindingResult);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
  }

  @Test
  void whenImportRuntimeException_thenReturnInternalServerError() throws Exception {
    List<String> casNumbers = List.of("50-78-2");
    BindingResult bindingResult = new BeanPropertyBindingResult(casNumbers, "request");
    doThrow(new RuntimeException("Unexpected runtime error"))
        .when(chemicalImporter)
        .importChemicals(anyList());

    ResponseEntity<?> response = controller.importChemicals(casNumbers, bindingResult);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
  }

  @Test
  void whenChemicalNotFound_thenReturnInternalServerError() throws Exception {
    List<String> casNumbers = List.of("invalid-cas");
    BindingResult bindingResult = new BeanPropertyBindingResult(casNumbers, "request");
    doThrow(new ChemicalImportException("No chemical found for CAS number: invalid-cas"))
        .when(chemicalImporter)
        .importChemicals(anyList());

    ResponseEntity<?> response = controller.importChemicals(casNumbers, bindingResult);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
  }
}
