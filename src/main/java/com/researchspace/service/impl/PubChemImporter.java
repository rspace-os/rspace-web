package com.researchspace.service.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchResult;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchType;
import com.researchspace.service.ChemicalImportException;
import com.researchspace.service.ChemicalImporter;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  @Autowired
  public PubChemImporter(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
    this.objectMapper = new ObjectMapper();
  }

  @Override
  @Cacheable(value = "pubchemResults", key = "#searchType + ':' + #searchTerm")
  public List<ChemicalImportSearchResult> searchChemicals(
      ChemicalImportSearchType searchType, String searchTerm) throws ChemicalImportException {
    validateSearchParameters(searchType, searchTerm);
    String url = buildPubChemUrl(searchType, searchTerm);
    return makeApiRequestAndParseResponse(url, searchTerm);
  }

  @Override
  public void importChemicals(List<String> cids) throws ChemicalImportException {
    if (cids == null || cids.isEmpty()) {
      throw new ChemicalImportException("At least one PubChem CID is required");
    }

    for (String cid : cids) {
      if (isBlank(cid)) {
        throw new ChemicalImportException("PubChem CID cannot be blank");
      }

      try {
        String url = buildPubChemUrlByCid(cid.trim());
        List<ChemicalImportSearchResult> results = makeApiRequestAndParseResponse(url, cid);

        if (results.isEmpty()) {
          throw new ChemicalImportException(
              String.format("No chemical found for PubChem CID: %s", cid));
        }

        // import into system
        log.info("Successfully imported chemical with PubChem CID: {}", cid);
      } catch (ChemicalImportException e) {
        log.error("Failed to import chemical with PubChem CID {}: {}", cid, e.getMessage());
        throw e;
      }
    }
  }

  private void validateSearchParameters(ChemicalImportSearchType searchType, String searchTerm)
      throws ChemicalImportException {
    if (searchType == null) {
      throw new ChemicalImportException("Unknown search type: " + null);
    }
    if (isBlank(searchTerm)) {
      throw new ChemicalImportException("Search type and term are required");
    }
  }

  private String buildPubChemUrl(ChemicalImportSearchType searchType, String searchTerm)
      throws ChemicalImportException {
    return String.format("%s/%s/%s/json", PUBCHEM_BASE_URL, searchType.name(), searchTerm);
  }

  private String buildPubChemUrlByCid(String cid) throws ChemicalImportException {
    return String.format("%s/cid/%s/json", PUBCHEM_BASE_URL, cid);
  }

  private String generatePngImageUrl(String cid) {
    // PubChem image service URL for PNG format
    // Format: https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid={CID}&t=l
    // t=l means large size, other options: s=small, m=medium
    return String.format("https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=%s&t=l", cid);
  }

  private String generatePubChemUrl(String cid) {
    // PubChem compound URL format
    return String.format("https://pubchem.ncbi.nlm.nih.gov/compound/%s", cid);
  }

  private List<ChemicalImportSearchResult> parseResponseToResults(String responseBody)
      throws ChemicalImportException {
    try {
      JsonNode rootNode = objectMapper.readTree(responseBody);
      JsonNode compoundsNode = rootNode.path("PC_Compounds");

      List<ChemicalImportSearchResult> results = new ArrayList<>();

      if (compoundsNode.isArray()) {
        for (JsonNode compoundNode : compoundsNode) {
          ChemicalImportSearchResult result = extractCompoundData(compoundNode);
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

  private ChemicalImportSearchResult extractCompoundData(JsonNode compoundNode) {
    try {
      JsonNode idNode = compoundNode.path("id").path("id").path("cid");
      String cid = idNode.isNumber() ? String.valueOf(idNode.asLong()) : null;

      if (cid == null) {
        return null;
      }

      ChemicalImportSearchResult.ChemicalImportSearchResultBuilder builder =
          ChemicalImportSearchResult.builder().pubchemId(cid);

      // Generate PNG image URL using PubChem's image service
      String pngImageUrl = generatePngImageUrl(cid);
      builder.pngImage(pngImageUrl);

      // Generate PubChem compound URL
      String pubchemUrl = generatePubChemUrl(cid);
      builder.pubchemUrl(pubchemUrl);

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
      JsonNode propNode, ChemicalImportSearchResult.ChemicalImportSearchResultBuilder builder) {

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
      case "Canonical SMILES":
        if (valueNode.path("sval").isTextual()) {
          builder.smiles(valueNode.path("sval").asText());
        }
        break;
    }
  }

  private List<ChemicalImportSearchResult> makeApiRequestAndParseResponse(
      String url, String searchTerm) throws ChemicalImportException {
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
}
