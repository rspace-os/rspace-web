package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.dtos.chemistry.ChemicalImportRequest;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchResults;
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
import org.springframework.validation.BindingResult;

@ExtendWith(MockitoExtension.class)
class ChemicalImportApiControllerTest {

  @Mock private ChemicalImporter chemicalImporter;

  @InjectMocks private ChemicalImportApiController controller;

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
  }

  @Test
  void whenValidRequest_thenReturnSuccessfulResponse() throws ChemicalImportException {
    // Arrange
    ChemicalImportRequest request = new ChemicalImportRequest("name", "aspirin");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    List<ChemicalImportSearchResults> mockResults =
        Arrays.asList(
            ChemicalImportSearchResults.builder()
                .cid("2244")
                .name("aspirin")
                .formula("C9H8O4")
                .smiles("CC(=O)OC1=CC=CC=C1C(=O)O")
                .cas("50-78-2")
                .build());

    when(chemicalImporter.importChemicals("name", "aspirin")).thenReturn(mockResults);

    // Act
    ResponseEntity<?> response = controller.importChemicals(request, bindingResult);

    // Assert
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertTrue(response.getBody() instanceof List);

    @SuppressWarnings("unchecked")
    List<ChemicalImportSearchResults> responseBody =
        (List<ChemicalImportSearchResults>) response.getBody();
    assertEquals(1, responseBody.size());
    assertEquals("2244", responseBody.get(0).getCid());
    assertEquals("aspirin", responseBody.get(0).getName());
  }

  @Test
  void whenValidationErrors_thenReturnBadRequest() throws ChemicalImportException {
    // Arrange
    ChemicalImportRequest request = new ChemicalImportRequest("invalid", "aspirin");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");
    bindingResult.rejectValue(
        "searchType", "invalid", "Search type must be one of: cas, name, smiles");

    // Act
    ResponseEntity<?> response = controller.importChemicals(request, bindingResult);

    // Assert
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertNotNull(response.getBody());
  }

  @Test
  void whenRateLimitExceeded_thenReturnTooManyRequests() throws ChemicalImportException {
    // Arrange
    ChemicalImportRequest request = new ChemicalImportRequest("name", "aspirin");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    when(chemicalImporter.importChemicals(anyString(), anyString()))
        .thenThrow(new ChemicalImportException("Rate limit exceeded"));

    // Act
    ResponseEntity<?> response = controller.importChemicals(request, bindingResult);

    // Assert
    assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
  }

  @Test
  void whenTimeoutError_thenReturnBadGateway() throws ChemicalImportException {
    // Arrange
    ChemicalImportRequest request = new ChemicalImportRequest("name", "aspirin");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    when(chemicalImporter.importChemicals(anyString(), anyString()))
        .thenThrow(new ChemicalImportException("Connection timeout"));

    // Act
    ResponseEntity<?> response = controller.importChemicals(request, bindingResult);

    // Assert
    assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
  }

  @Test
  void whenUnexpectedError_thenReturnInternalServerError() throws ChemicalImportException {
    // Arrange
    ChemicalImportRequest request = new ChemicalImportRequest("name", "aspirin");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    when(chemicalImporter.importChemicals(anyString(), anyString()))
        .thenThrow(new ChemicalImportException("Unexpected error"));

    // Act
    ResponseEntity<?> response = controller.importChemicals(request, bindingResult);

    // Assert
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
  }

  @Test
  void whenRuntimeException_thenReturnInternalServerError() throws ChemicalImportException {
    // Arrange
    ChemicalImportRequest request = new ChemicalImportRequest("name", "aspirin");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    when(chemicalImporter.importChemicals(anyString(), anyString()))
        .thenThrow(new RuntimeException("Unexpected runtime error"));

    // Act
    ResponseEntity<?> response = controller.importChemicals(request, bindingResult);

    // Assert
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
  }

  @Test
  void whenNoResultsFound_thenReturnEmptyList() throws ChemicalImportException {
    // Arrange
    ChemicalImportRequest request = new ChemicalImportRequest("name", "nonexistent");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    when(chemicalImporter.importChemicals("name", "nonexistent"))
        .thenReturn(Collections.emptyList());

    // Act
    ResponseEntity<?> response = controller.importChemicals(request, bindingResult);

    // Assert
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertTrue(response.getBody() instanceof List);

    @SuppressWarnings("unchecked")
    List<ChemicalImportSearchResults> responseBody =
        (List<ChemicalImportSearchResults>) response.getBody();
    assertTrue(responseBody.isEmpty());
  }

  @Test
  void whenValidCasSearch_thenReturnResults() throws ChemicalImportException {
    // Arrange
    ChemicalImportRequest request = new ChemicalImportRequest("cas", "50-78-2");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    List<ChemicalImportSearchResults> mockResults =
        Arrays.asList(ChemicalImportSearchResults.builder().cid("2244").cas("50-78-2").build());

    when(chemicalImporter.importChemicals("cas", "50-78-2")).thenReturn(mockResults);

    // Act
    ResponseEntity<?> response = controller.importChemicals(request, bindingResult);

    // Assert
    assertEquals(HttpStatus.OK, response.getStatusCode());

    @SuppressWarnings("unchecked")
    List<ChemicalImportSearchResults> responseBody =
        (List<ChemicalImportSearchResults>) response.getBody();
    assertEquals(1, responseBody.size());
    assertEquals("50-78-2", responseBody.get(0).getCas());
  }

  @Test
  void whenValidSmilesSearch_thenReturnResults() throws ChemicalImportException {
    // Arrange
    ChemicalImportRequest request = new ChemicalImportRequest("smiles", "CC(=O)OC1=CC=CC=C1C(=O)O");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    List<ChemicalImportSearchResults> mockResults =
        Arrays.asList(
            ChemicalImportSearchResults.builder()
                .cid("2244")
                .smiles("CC(=O)OC1=CC=CC=C1C(=O)O")
                .build());

    when(chemicalImporter.importChemicals("smiles", "CC(=O)OC1=CC=CC=C1C(=O)O"))
        .thenReturn(mockResults);

    // Act
    ResponseEntity<?> response = controller.importChemicals(request, bindingResult);

    // Assert
    assertEquals(HttpStatus.OK, response.getStatusCode());

    @SuppressWarnings("unchecked")
    List<ChemicalImportSearchResults> responseBody =
        (List<ChemicalImportSearchResults>) response.getBody();
    assertEquals(1, responseBody.size());
    assertEquals("CC(=O)OC1=CC=CC=C1C(=O)O", responseBody.get(0).getSmiles());
  }
}
