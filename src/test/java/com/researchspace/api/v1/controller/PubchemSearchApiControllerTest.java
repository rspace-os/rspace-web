package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.model.dtos.chemistry.ChemicalImportSearchResult;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchType;
import com.researchspace.model.dtos.chemistry.ChemicalSearchRequest;
import com.researchspace.service.ChemicalImportException;
import com.researchspace.service.ChemicalSearcher;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
public class PubchemSearchApiControllerTest {

  @Mock private ChemicalSearcher chemicalSearcher;

  @InjectMocks private PubchemSearchApiController controller;

  @Test
  public void whenValidSearchRequest_thenReturnSuccessfulResponse() throws Exception {
    ChemicalSearchRequest request =
        new ChemicalSearchRequest(ChemicalImportSearchType.NAME, "aspirin");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    List<ChemicalImportSearchResult> mockResults =
        Arrays.asList(
            ChemicalImportSearchResult.builder()
                .pubchemId("2244")
                .name("aspirin")
                .formula("C9H8O4")
                .smiles("CC(=O)OC1=CC=CC=C1C(=O)O")
                .pngImage("https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=2244&t=l")
                .pubchemUrl("https://pubchem.ncbi.nlm.nih.gov/compound/2244")
                .build());

    when(chemicalSearcher.searchChemicals(ChemicalImportSearchType.NAME, "aspirin"))
        .thenReturn(mockResults);

    ResponseEntity<?> response = controller.searchChemicals(request, bindingResult);
    List<ChemicalImportSearchResult> responseBody =
        (List<ChemicalImportSearchResult>) response.getBody();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(mockResults, responseBody);
  }

  @Test
  public void whenValidationErrors_thenThrowBindException() {
    ChemicalSearchRequest request = new ChemicalSearchRequest(null, "aspirin");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");
    bindingResult.rejectValue("searchType", "invalid", "Search type must be one of: name, smiles");

    assertThrows(BindException.class, () -> controller.searchChemicals(request, bindingResult));
  }

  @Test
  public void whenRateLimitExceeded_thenReturnTooManyRequests() throws Exception {
    ChemicalSearchRequest request =
        new ChemicalSearchRequest(ChemicalImportSearchType.NAME, "aspirin");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    when(chemicalSearcher.searchChemicals(ChemicalImportSearchType.NAME, "aspirin"))
        .thenThrow(
            new ChemicalImportException("Rate limit exceeded", HttpStatus.TOO_MANY_REQUESTS));

    ResponseEntity<?> response = controller.searchChemicals(request, bindingResult);

    assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
  }

  @Test
  public void whenBadRequest_thenReturnBadRequest() throws Exception {
    ChemicalSearchRequest request =
        new ChemicalSearchRequest(ChemicalImportSearchType.NAME, "invalid-search");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    when(chemicalSearcher.searchChemicals(ChemicalImportSearchType.NAME, "invalid-search"))
        .thenThrow(
            new ChemicalImportException("Invalid request to PubChem API", HttpStatus.BAD_REQUEST));

    ResponseEntity<?> response = controller.searchChemicals(request, bindingResult);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
  }

  @Test
  public void whenTimeoutError_thenReturnBadGateway() throws Exception {
    ChemicalSearchRequest request =
        new ChemicalSearchRequest(ChemicalImportSearchType.NAME, "aspirin");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    when(chemicalSearcher.searchChemicals(ChemicalImportSearchType.NAME, "aspirin"))
        .thenThrow(new ChemicalImportException("Connection timeout", HttpStatus.BAD_GATEWAY));

    ResponseEntity<?> response = controller.searchChemicals(request, bindingResult);

    assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
  }

  @Test
  public void whenUnexpectedError_thenReturnInternalServerError() throws Exception {
    ChemicalSearchRequest request =
        new ChemicalSearchRequest(ChemicalImportSearchType.NAME, "aspirin");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    when(chemicalSearcher.searchChemicals(ChemicalImportSearchType.NAME, "aspirin"))
        .thenThrow(
            new ChemicalImportException("Unexpected error", HttpStatus.INTERNAL_SERVER_ERROR));

    ResponseEntity<?> response = controller.searchChemicals(request, bindingResult);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
  }

  @Test
  public void whenRuntimeException_thenReturnInternalServerError() throws Exception {
    ChemicalSearchRequest request =
        new ChemicalSearchRequest(ChemicalImportSearchType.NAME, "aspirin");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    when(chemicalSearcher.searchChemicals(ChemicalImportSearchType.NAME, "aspirin"))
        .thenThrow(new RuntimeException("Unexpected runtime error"));

    ResponseEntity<?> response = controller.searchChemicals(request, bindingResult);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
  }

  @Test
  public void whenNoResultsFound_thenReturnEmptyList() throws Exception {
    ChemicalSearchRequest request =
        new ChemicalSearchRequest(ChemicalImportSearchType.NAME, "nonexistent");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    when(chemicalSearcher.searchChemicals(ChemicalImportSearchType.NAME, "nonexistent"))
        .thenReturn(Collections.emptyList());

    ResponseEntity<?> response = controller.searchChemicals(request, bindingResult);

    List<ChemicalImportSearchResult> responseBody =
        (List<ChemicalImportSearchResult>) response.getBody();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertTrue(responseBody.isEmpty());
  }

  @Test
  public void whenValidCasSearch_thenReturnResults() throws Exception {
    ChemicalSearchRequest request =
        new ChemicalSearchRequest(ChemicalImportSearchType.NAME, "50-78-2");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    List<ChemicalImportSearchResult> mockResults =
        Arrays.asList(
            ChemicalImportSearchResult.builder()
                .pubchemId("2244")
                .pngImage("https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=2244&t=l")
                .pubchemUrl("https://pubchem.ncbi.nlm.nih.gov/compound/2244")
                .build());

    when(chemicalSearcher.searchChemicals(ChemicalImportSearchType.NAME, "50-78-2"))
        .thenReturn(mockResults);

    ResponseEntity<?> response = controller.searchChemicals(request, bindingResult);

    List<ChemicalImportSearchResult> responseBody =
        (List<ChemicalImportSearchResult>) response.getBody();
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(mockResults, responseBody);
  }

  @Test
  public void whenValidSmilesSearch_thenReturnResults() throws Exception {
    ChemicalSearchRequest request =
        new ChemicalSearchRequest(ChemicalImportSearchType.SMILES, "CC(=O)OC1=CC=CC=C1C(=O)O");
    BindingResult bindingResult = new BeanPropertyBindingResult(request, "request");

    List<ChemicalImportSearchResult> mockResults =
        Arrays.asList(
            ChemicalImportSearchResult.builder()
                .pubchemId("2244")
                .smiles("CC(=O)OC1=CC=CC=C1C(=O)O")
                .pngImage("https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=2244&t=l")
                .pubchemUrl("https://pubchem.ncbi.nlm.nih.gov/compound/2244")
                .build());

    when(chemicalSearcher.searchChemicals(
            ChemicalImportSearchType.SMILES, "CC(=O)OC1=CC=CC=C1C(=O)O"))
        .thenReturn(mockResults);

    ResponseEntity<?> response = controller.searchChemicals(request, bindingResult);

    List<ChemicalImportSearchResult> responseBody =
        (List<ChemicalImportSearchResult>) response.getBody();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(mockResults, responseBody);
  }
}
