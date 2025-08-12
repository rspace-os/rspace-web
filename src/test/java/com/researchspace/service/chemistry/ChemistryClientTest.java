package com.researchspace.service.chemistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.researchspace.model.RSChemElement;
import com.researchspace.model.dtos.chemistry.ChemicalExportFormat;
import com.researchspace.model.dtos.chemistry.ChemicalExportType;
import com.researchspace.model.dtos.chemistry.ChemicalSearchResultsDTO;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class ChemistryClientTest {

  public static final String DUMMY_URL = "http://some.url";

  public static final String EMPTY_INPUT_FORMAT = "";

  public static final ChemicalExportFormat OUTPUT_FORMAT =
      ChemicalExportFormat.builder().exportType(ChemicalExportType.PNG).build();

  @Mock RestTemplate restTemplate;

  @InjectMocks ChemistryClient chemistryClient;

  @BeforeEach
  public void setup() {
    // set the @Value field to test error messages are generated correctly
    ReflectionTestUtils.setField(chemistryClient, "chemistryServiceUrl", DUMMY_URL);
  }

  @Test
  public void whenSuccessfulConvert_thenConvertedChemicalReturned() {
    String converted = "CCCC";
    when(restTemplate.postForEntity(anyString(), any(), any()))
        .thenReturn(new ResponseEntity<>(converted, HttpStatus.OK));

    String actual = chemistryClient.convert("some chemical", EMPTY_INPUT_FORMAT, "some format");

    assertEquals(converted, actual);
  }

  @ParameterizedTest
  @EnumSource(
      value = HttpStatus.class,
      names = {"BAD_REQUEST", "INTERNAL_SERVER_ERROR"})
  public void whenConvertResponseHasErrorStatus_thenThrowException(HttpStatus errorStatus) {
    when(restTemplate.postForEntity(anyString(), any(), any()))
        .thenReturn(new ResponseEntity<>("", errorStatus));

    ChemistryClientException exception =
        assertThrows(
            ChemistryClientException.class,
            () -> chemistryClient.convert("some chemical", EMPTY_INPUT_FORMAT, "some format"));

    String expectedErrorMsg =
        String.format(
            "Unsuccessful conversion request to the chemistry service, status code: %d.",
            errorStatus.value());
    assertEquals(expectedErrorMsg, exception.getMessage());
  }

  @Test
  public void whenConvertRequestThrowsException_thenThrowChemistryException() {
    when(restTemplate.postForEntity(anyString(), any(), any()))
        .thenThrow(new RestClientException(""));

    ChemistryClientException exception =
        assertThrows(
            ChemistryClientException.class,
            () -> chemistryClient.convert("some chemical", EMPTY_INPUT_FORMAT, "some format"));

    String expectedErrorMsg = "Chemistry service couldn't convert the chemical.";
    assertEquals(expectedErrorMsg, exception.getMessage());
  }

  @Test
  public void whenSuccessfulExtract_thenExtractionInfoReturned() {
    ElementalAnalysisDTO extractionInfo = new ElementalAnalysisDTO();
    when(restTemplate.postForEntity(anyString(), any(), any()))
        .thenReturn(new ResponseEntity<>(extractionInfo, HttpStatus.OK));

    Optional<ElementalAnalysisDTO> actual = chemistryClient.extract("CCC");

    assertEquals(Optional.of(extractionInfo), actual);
  }

  @ParameterizedTest
  @EnumSource(
      value = HttpStatus.class,
      names = {"BAD_REQUEST", "INTERNAL_SERVER_ERROR"})
  public void whenExtractionResponseHasErrorStatus_thenReturnEmptyOptional(HttpStatus errorStatus) {
    when(restTemplate.postForEntity(anyString(), any(), any()))
        .thenReturn(new ResponseEntity<>("", errorStatus));

    Optional<ElementalAnalysisDTO> actual = chemistryClient.extract("CCC");
    assertEquals(Optional.empty(), actual);
  }

  @Test
  public void whenExtractRequestThrowsException_thenReturnEmptyOptional() {
    when(restTemplate.postForEntity(anyString(), any(), any()))
        .thenThrow(new RestClientException(""));

    Optional<ElementalAnalysisDTO> actual = chemistryClient.extract("CCC");
    assertEquals(Optional.empty(), actual);
  }

  @Test
  public void whenSuccessfulImageExport_thenImageBytesReturned() {
    byte[] someBytes = "some bytes".getBytes(StandardCharsets.UTF_8);
    when(restTemplate.postForEntity(anyString(), any(), any()))
        .thenReturn(new ResponseEntity<>(someBytes, HttpStatus.OK));

    byte[] bytes = chemistryClient.exportImage("CC", "smiles", OUTPUT_FORMAT);

    assertEquals(someBytes, bytes);
  }

  @ParameterizedTest
  @EnumSource(
      value = HttpStatus.class,
      names = {"BAD_REQUEST", "INTERNAL_SERVER_ERROR"})
  void whenImageExportHasErrorStatus_thenThrowException(HttpStatus errorStatus) {
    when(restTemplate.postForEntity(anyString(), any(), any()))
        .thenReturn(new ResponseEntity<>("", errorStatus));

    ChemistryClientException exception =
        assertThrows(
            ChemistryClientException.class,
            () -> chemistryClient.exportImage("CC", "smiles", OUTPUT_FORMAT));

    String expectedErrorMsg =
        String.format(
            "Unsuccessful image export request to the chemistry service, status code: %d.",
            errorStatus.value());
    assertEquals(expectedErrorMsg, exception.getMessage());
  }

  @Test
  void whenImageExportThrowsException_thenThrowChemistryException() {
    when(restTemplate.postForEntity(anyString(), any(), any()))
        .thenThrow(new RestClientException(""));

    ChemistryClientException exception =
        assertThrows(
            ChemistryClientException.class,
            () -> chemistryClient.exportImage("CC", "smiles", OUTPUT_FORMAT));

    String expectedErrorMsg = "Chemistry service couldn't generate the image for the chemical.";
    assertEquals(expectedErrorMsg, exception.getMessage());
  }

  @ParameterizedTest
  @EnumSource(
      value = HttpStatus.class,
      names = {"BAD_REQUEST", "INTERNAL_SERVER_ERROR"})
  public void whenSaveResponseHasErrorStatus_thenThrowException(HttpStatus errorStatus) {
    when(restTemplate.postForEntity(anyString(), any(), any()))
        .thenReturn(new ResponseEntity<>("", errorStatus));

    RSChemElement chemElement = new RSChemElement();
    chemElement.setChemElements("CC");
    chemElement.setId(123L);
    ChemistryClientException exception =
        assertThrows(ChemistryClientException.class, () -> chemistryClient.save(chemElement));

    String expectedErrorMsg =
        String.format(
            "Unsuccessful save request to the chemistry service, status code: %d.",
            errorStatus.value());
    assertEquals(expectedErrorMsg, exception.getMessage());
  }

  @Test
  public void whenSaveRequestThrowsException_thenThrowChemistryException() {
    when(restTemplate.postForEntity(anyString(), any(), any()))
        .thenThrow(new RestClientException(""));

    RSChemElement chemElement = new RSChemElement();
    chemElement.setChemElements("CC");
    chemElement.setId(123L);
    ChemistryClientException exception =
        assertThrows(ChemistryClientException.class, () -> chemistryClient.save(chemElement));

    String expectedErrorMsg = "Chemistry service couldn't save the chemical.";
    assertEquals(expectedErrorMsg, exception.getMessage());
  }

  @Test
  public void whenSuccessfulSearch_thenReturnSearchResults() {
    String expectedId = "1234";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    String query = "CC";
    Map<String, Object> body = new HashMap<>();
    body.put("chemicalSearchTerm", query);
    body.put("searchType", "SUBSTRUCTURE");
    body.put("searchTermFormat", "smiles");
    HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
    when(restTemplate.exchange(
            DUMMY_URL + "/chemistry/search",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<List<String>>() {}))
        .thenReturn(new ResponseEntity<>(List.of(expectedId), HttpStatus.OK));

    ChemicalSearchResultsDTO actual = chemistryClient.search("CC", "SUBSTRUCTURE");

    List<Long> expectedIdMatches = List.of(Long.valueOf(expectedId));

    assertEquals(expectedIdMatches, actual.getChemicalHits());
  }

  @ParameterizedTest
  @EnumSource(
      value = HttpStatus.class,
      names = {"BAD_REQUEST", "INTERNAL_SERVER_ERROR"})
  public void whenSearchResponseHasErrorStatus_thenThrowException(HttpStatus errorStatus) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    String query = "CC";
    Map<String, Object> body = new HashMap<>();
    body.put("chemicalSearchTerm", query);
    body.put("searchType", "SUBSTRUCTURE");
    body.put("searchTermFormat", "smiles");
    HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
    when(restTemplate.exchange(
            DUMMY_URL + "/chemistry/search",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<List<String>>() {}))
        .thenReturn(new ResponseEntity<>(Collections.emptyList(), errorStatus));

    ChemistryClientException exception =
        assertThrows(
            ChemistryClientException.class, () -> chemistryClient.search("CC", "SUBSTRUCTURE"));

    String expectedErrorMsg =
        String.format(
            "Unsuccessful search request to the chemistry service, status code: %d.",
            errorStatus.value());
    assertEquals(expectedErrorMsg, exception.getMessage());
  }

  @Test
  public void whenSearchThrowsException_thenThrowChemistryException() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    String query = "CC";
    Map<String, Object> body = new HashMap<>();
    body.put("chemicalSearchTerm", query);
    body.put("searchType", "SUBSTRUCTURE");
    body.put("searchTermFormat", "smiles");
    HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
    when(restTemplate.exchange(
            DUMMY_URL + "/chemistry/search",
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<List<String>>() {}))
        .thenThrow(new RestClientException(""));

    ChemistryClientException exception =
        assertThrows(
            ChemistryClientException.class, () -> chemistryClient.search("CC", "SUBSTRUCTURE"));

    String expectedErrorMsg = "Chemistry service unable to search for the chemical.";
    assertEquals(expectedErrorMsg, exception.getMessage());
  }
}
