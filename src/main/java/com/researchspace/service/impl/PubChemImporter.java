package com.researchspace.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchResults;
import com.researchspace.service.ChemicalImportException;
import com.researchspace.service.ChemicalImporter;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class PubChemImporter implements ChemicalImporter {

  private static final String PUBCHEM_BASE_URL =
      "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound";
  private static final long RATE_LIMIT_DELAY_MS = 200; // 5 requests per second max

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  @Value("${chemical.import.pubchem.timeout:10000}")
  private int timeoutMs;

  private volatile long lastRequestTime = 0;

  @Autowired
  public PubChemImporter(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
    this.objectMapper = new ObjectMapper();
  }

  @Override
  @Cacheable(value = "pubchemResults", key = "#searchType + ':' + #searchTerm")
  public List<ChemicalImportSearchResults> importChemicals(String searchType, String searchTerm)
      throws ChemicalImportException {

    validateSearchParameters(searchType, searchTerm);
    applyRateLimit();

    String url = buildPubChemUrl(searchType, searchTerm);

    try {
      log.info("Making PubChem API request to: {}", url);
      ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

      if (!response.getStatusCode().is2xxSuccessful()) {
        throw new ChemicalImportException(
            String.format("PubChem API returned status: %s", response.getStatusCode()));
      }

      return parseResponseToResults(response.getBody());

    } catch (HttpClientErrorException e) {
      if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
        log.info("No results found for search term: {}", searchTerm);
        return new ArrayList<>();
      } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
        throw new ChemicalImportException("Rate limit exceeded. Please try again later.");
      }
      throw new ChemicalImportException(String.format("PubChem API error: %s", e.getMessage()), e);
    } catch (ResourceAccessException e) {
      throw new ChemicalImportException("PubChem API timeout or connection error", e);
    } catch (RestClientException e) {
      throw new ChemicalImportException("Error communicating with PubChem API", e);
    } catch (Exception e) {
      throw new ChemicalImportException("Unexpected error during chemical import", e);
    }
  }

  private void validateSearchParameters(String searchType, String searchTerm)
      throws ChemicalImportException {
    if (searchType == null || searchTerm == null || searchTerm.trim().isEmpty()) {
      throw new ChemicalImportException("Search type and search term are required");
    }

    if (!isValidSearchType(searchType)) {
      throw new ChemicalImportException("Invalid search type. Must be one of: cas, name, smiles");
    }
  }

  private boolean isValidSearchType(String searchType) {
    return "cas".equals(searchType) || "name".equals(searchType) || "smiles".equals(searchType);
  }

  private synchronized void applyRateLimit() {
    long currentTime = System.currentTimeMillis();
    long timeSinceLastRequest = currentTime - lastRequestTime;

    if (timeSinceLastRequest < RATE_LIMIT_DELAY_MS) {
      try {
        Thread.sleep(RATE_LIMIT_DELAY_MS - timeSinceLastRequest);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Rate limiting interrupted", e);
      }
    }

    lastRequestTime = System.currentTimeMillis();
  }

  private String buildPubChemUrl(String searchType, String searchTerm) {
    // Map our search types to PubChem endpoint names
    String pubchemSearchType = mapToPubChemSearchType(searchType);
    return String.format("%s/%s/%s/json", PUBCHEM_BASE_URL, pubchemSearchType, searchTerm);
  }

  private String mapToPubChemSearchType(String searchType) {
    switch (searchType) {
      case "cas":
        return "name"; // PubChem handles CAS numbers through the name endpoint
      case "name":
        return "name";
      case "smiles":
        return "smiles";
      default:
        throw new IllegalArgumentException("Unknown search type: " + searchType);
    }
  }

  private List<ChemicalImportSearchResults> parseResponseToResults(String responseBody)
      throws ChemicalImportException {
    try {
      JsonNode rootNode = objectMapper.readTree(responseBody);
      JsonNode compoundsNode = rootNode.path("PC_Compounds");

      List<ChemicalImportSearchResults> results = new ArrayList<>();

      if (compoundsNode.isArray()) {
        for (JsonNode compoundNode : compoundsNode) {
          ChemicalImportSearchResults result = extractCompoundData(compoundNode);
          if (result != null) {
            results.add(result);
          }
        }
      }

      return results;

    } catch (Exception e) {
      throw new ChemicalImportException("Error parsing PubChem response", e);
    }
  }

  private ChemicalImportSearchResults extractCompoundData(JsonNode compoundNode) {
    try {
      JsonNode idNode = compoundNode.path("id").path("id").path("cid");
      String cid = idNode.isNumber() ? String.valueOf(idNode.asLong()) : null;

      if (cid == null) {
        return null;
      }

      ChemicalImportSearchResults.ChemicalImportSearchResultsBuilder builder =
          ChemicalImportSearchResults.builder().cid(cid);

      // Extract properties from the compound data
      JsonNode propsNode = compoundNode.path("props");
      if (propsNode.isArray()) {
        for (JsonNode propNode : propsNode) {
          extractProperty(propNode, builder);
        }
      }

      return builder.build();

    } catch (Exception e) {
      log.warn("Error extracting compound data: {}", e.getMessage());
      return null;
    }
  }

  private void extractProperty(
      JsonNode propNode, ChemicalImportSearchResults.ChemicalImportSearchResultsBuilder builder) {

    JsonNode urnNode = propNode.path("urn");
    String label = urnNode.path("label").asText();

    JsonNode valueNode = propNode.path("value");

    switch (label) {
      case "IUPAC Name":
        if (valueNode.path("sval").isTextual()) {
          builder.name(valueNode.path("sval").asText());
        }
        break;
      case "Molecular Formula":
        if (valueNode.path("sval").isTextual()) {
          builder.formula(valueNode.path("sval").asText());
        }
        break;
      case "SMILES":
        if (valueNode.path("sval").isTextual()) {
          builder.smiles(valueNode.path("sval").asText());
        }
        break;
      case "CAS":
        if (valueNode.path("sval").isTextual()) {
          builder.cas(valueNode.path("sval").asText());
        }
        break;
    }
  }
}
