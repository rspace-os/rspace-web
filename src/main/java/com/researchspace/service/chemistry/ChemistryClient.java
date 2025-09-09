package com.researchspace.service.chemistry;

import com.researchspace.model.RSChemElement;
import com.researchspace.model.dtos.chemistry.ChemicalExportFormat;
import com.researchspace.model.dtos.chemistry.ChemicalExportType;
import com.researchspace.model.dtos.chemistry.ChemicalSearchResultsDTO;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class ChemistryClient {

  @Value("${chemistry.service.url}")
  private String chemistryServiceUrl;

  private final RestTemplate restTemplate;

  @Autowired
  public ChemistryClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  public String convert(String chemical, String inputFormat, String outputFormat) {
    Map<String, Object> body = new HashMap<>();
    body.put("input", chemical);
    body.put("inputFormat", inputFormat);
    body.put("outputFormat", outputFormat);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(body, headers);
    String url = chemistryServiceUrl + "/chemistry/convert";
    try {
      ResponseEntity<String> response = restTemplate.postForEntity(url, httpEntity, String.class);
      HttpStatus responseStatus = response.getStatusCode();
      if (!responseStatus.is2xxSuccessful()) {
        log.warn("Unsuccessful conversion with url: " + url + ", code " + responseStatus.value());
        throw new ChemistryClientException(
            String.format(
                "Unsuccessful conversion request to the chemistry service, status code: %d.",
                responseStatus.value()));
      }
      return response.getBody();

    } catch (RestClientException e) {
      log.warn(
          "Problem with chemistry service converting the chemical at {}: {}", url, e.getMessage());
      throw new ChemistryClientException("Chemistry service couldn't convert the chemical.", e);
    }
  }

  public Optional<ElementalAnalysisDTO> extract(String chem) {
    Map<String, Object> body = new HashMap<>();
    body.put("input", chem);
    String url = chemistryServiceUrl + "/chemistry/extract";

    return postForElementalAnalysis(body, url);
  }

  private Optional<ElementalAnalysisDTO> postForElementalAnalysis(
      Map<String, Object> body, String url) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(body, headers);

    try {
      ResponseEntity<ElementalAnalysisDTO> response =
          restTemplate.postForEntity(url, httpEntity, ElementalAnalysisDTO.class);
      if (response.getStatusCode().is2xxSuccessful()) {
        return Optional.ofNullable(response.getBody());
      } else {
        log.warn(
            "Unsuccessful request to: {}. Response code: {}", url, response.getStatusCodeValue());
        return Optional.empty();
      }
    } catch (RestClientException e) {
      log.warn("Error while making request to {}: {}", url, e.getMessage());
      return Optional.empty();
    }
  }

  public Optional<ElementalAnalysisDTO> extractStoichiometry(RSChemElement chemicalElement) {
    Map<String, Object> body = new HashMap<>();

    String chemString = chemicalElement.getChemElements();
    if (StringUtils.isNotEmpty(chemicalElement.getSmilesString())) {
      log.info(
          "rsChemElement "
              + chemicalElement.getId()
              + " has smiles representation, using it for stoichiometry");
      chemString = chemicalElement.getSmilesString();
    }
    body.put("input", chemString);
    String url = chemistryServiceUrl + "/chemistry/stoichiometry";

    return postForElementalAnalysis(body, url);
  }

  public byte[] exportImage(
      String chemical, String inputFormat, ChemicalExportFormat outputFormat) {
    String imageFormat =
        outputFormat.getExportType() == null
            ? ChemicalExportType.PNG.getType()
            : outputFormat.getExportType().getType();
    Map<String, Object> body = new HashMap<>();
    body.put("input", chemical);
    body.put("inputFormat", inputFormat);
    body.put("outputFormat", imageFormat);
    body.put("height", outputFormat.getHeight());
    body.put("width", outputFormat.getWidth());
    String url = chemistryServiceUrl + "/chemistry/image";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(body, headers);
    try {
      ResponseEntity<byte[]> response = restTemplate.postForEntity(url, httpEntity, byte[].class);
      HttpStatus responseStatus = response.getStatusCode();
      if (!responseStatus.is2xxSuccessful()) {
        log.warn("Unsuccessful image export with url: " + url + ", code " + responseStatus.value());
        throw new ChemistryClientException(
            String.format(
                "Unsuccessful image export request to the chemistry service, status code: %d.",
                responseStatus.value()));
      }
      return response.getBody();

    } catch (RestClientException e) {
      log.warn("Problem with chemistry service exporting image at {}: {}", url, e.getMessage());
      throw new ChemistryClientException(
          "Chemistry service couldn't generate the image for the chemical.", e);
    }
  }

  public void save(RSChemElement chemical) {
    save(chemical.getChemElements(), chemical.getId());
  }

  public void save(String chemical, Long chemicalId) {
    Map<String, Object> body = new HashMap<>();
    body.put("chemical", chemical);
    body.put("chemicalId", chemicalId);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(body, headers);
    String url = chemistryServiceUrl + "/chemistry/save";
    try {
      ResponseEntity<String> response = restTemplate.postForEntity(url, httpEntity, String.class);
      HttpStatus responseStatus = response.getStatusCode();
      if (!responseStatus.is2xxSuccessful()) {
        log.warn("Unsuccessful saving with url: " + url + ", code " + responseStatus.value());
        throw new ChemistryClientException(
            String.format(
                "Unsuccessful save request to the chemistry service, status code: %d.",
                responseStatus.value()));
      }
    } catch (RestClientException e) {
      log.warn(
          "Problem with chemistry service saving the chemical {} at {}: {} ",
          chemicalId,
          url,
          e.getMessage());
      throw new ChemistryClientException("Chemistry service couldn't save the chemical.", e);
    }
  }

  public ChemicalSearchResultsDTO search(String chemQuery, String chemicalSearchType) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    String url = chemistryServiceUrl + "/chemistry/search";
    Map<String, Object> body = new HashMap<>();
    body.put("chemicalSearchTerm", chemQuery);
    body.put("searchType", chemicalSearchType);
    body.put("searchTermFormat", "smiles");
    HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
    try {
      ResponseEntity<List<String>> response =
          restTemplate.exchange(
              url, HttpMethod.POST, request, new ParameterizedTypeReference<>() {});
      HttpStatus responseStatus = response.getStatusCode();
      if (!responseStatus.is2xxSuccessful()) {
        log.warn("Unsuccessful searching with url: " + url + ", code " + responseStatus.value());
        throw new ChemistryClientException(
            String.format(
                "Unsuccessful search request to the chemistry service, status code: %d.",
                responseStatus.value()));
      }
      return ChemicalSearchResultsDTO.builder()
          .chemicalHits(
              response.getBody().stream()
                  .map(
                      str -> {
                        try {
                          return Long.valueOf(str);
                        } catch (NumberFormatException e) {
                          return null;
                        }
                      })
                  .filter(Objects::nonNull)
                  .collect(Collectors.toList()))
          .totalHits(response.getBody() == null ? 0 : response.getBody().size())
          .build();

    } catch (RestClientException e) {
      log.warn(
          "Problem with chemistry service searching for the chemical at {}: {}",
          url,
          e.getMessage());
      throw new ChemistryClientException("Chemistry service unable to search for the chemical.", e);
    }
  }

  public void clearSearchIndexes() {
    String url = chemistryServiceUrl + "/chemistry/clearSearchIndexes";
    try {
      ResponseEntity<String> response =
          restTemplate.exchange(url, HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
      HttpStatus responseStatus = response.getStatusCode();
      if (!responseStatus.is2xxSuccessful()) {
        log.warn("Unsuccessful call with url: " + url + ", code " + responseStatus.value());
        throw new ChemistryClientException(
            String.format(
                "Unsuccessful clearSearchIndexes request to the chemistry service, status code:"
                    + " %d.",
                responseStatus.value()));
      }
    } catch (RestClientException e) {
      log.warn(
          "Problem with chemistry service clearing search indexes at {}: {} ", url, e.getMessage());
      throw new ChemistryClientException("Chemistry service couldn't clear search indexes.", e);
    }
  }

  public void callFastSearchIndexing() {
    String url = chemistryServiceUrl + "/chemistry/index";
    try {
      ResponseEntity<String> response =
          restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, String.class);
      HttpStatus responseStatus = response.getStatusCode();
      if (!responseStatus.is2xxSuccessful()) {
        log.warn("Unsuccessful call with url: " + url + ", code " + responseStatus.value());
        throw new ChemistryClientException(
            String.format(
                "Unsuccessful callFastSearchIndexing request to the chemistry service, status code:"
                    + " %d.",
                responseStatus.value()));
      }
    } catch (RestClientException e) {
      log.warn(
          "Problem with chemistry service  fast search indexing at {}: {} ", url, e.getMessage());
      throw new ChemistryClientException("Chemistry service couldn't clear search indexes.", e);
    }
  }
}
