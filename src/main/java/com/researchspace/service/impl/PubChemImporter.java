package com.researchspace.service.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchResult;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchType;
import com.researchspace.model.dtos.chemistry.PubChemResponse;
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
    String url =
        String.format(
            "%s/%s/%s/property/Title,SMILES,MolecularFormula/json",
            PUBCHEM_BASE_URL, searchType.name(), searchTerm);
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

      String url =
          String.format(
              "%s/cid/%s/property/Title,SMILES,MolecularFormula/json", PUBCHEM_BASE_URL, cid);
      List<ChemicalImportSearchResult> results = makeApiRequestAndParseResponse(url, cid);

      if (results.isEmpty()) {
        throw new ChemicalImportException(
            String.format("No chemical found for PubChem CID: %s", cid));
      }

      log.info("Successfully imported chemical with PubChem CID: {}", cid);
      // todo: import via RSChemService
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

  private List<ChemicalImportSearchResult> parseResponseToResults(String responseBody)
      throws ChemicalImportException {
    try {
      PubChemResponse response = objectMapper.readValue(responseBody, PubChemResponse.class);

      List<ChemicalImportSearchResult> results = new ArrayList<>();

      if (response.getPropertyTable() != null
          && response.getPropertyTable().getProperties() != null) {
        for (PubChemResponse.ChemicalProperty property :
            response.getPropertyTable().getProperties()) {
          ChemicalImportSearchResult result = convertToSearchResult(property);
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

  private ChemicalImportSearchResult convertToSearchResult(
      PubChemResponse.ChemicalProperty property) {
    try {
      if (property.getCid() == null) {
        return null;
      }

      String cid = String.valueOf(property.getCid());

      return ChemicalImportSearchResult.builder()
          .pubchemId(cid)
          .name(property.getTitle())
          .formula(property.getMolecularFormula())
          .smiles(property.getSmiles())
          .pngImage(
              String.format("https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=%s&t=l", cid))
          .pubchemUrl(String.format("https://pubchem.ncbi.nlm.nih.gov/compound/%s", cid))
          .build();

    } catch (Exception e) {
      log.warn("Error converting property to search result: {}", e.getMessage());
      return null;
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
