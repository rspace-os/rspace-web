package com.researchspace.service.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.researchspace.model.dtos.chemistry.ChemicalImportSearchResult;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchType;
import com.researchspace.model.dtos.chemistry.PubChemResponse;
import com.researchspace.model.dtos.chemistry.PubChemSynonymsResponse;
import com.researchspace.service.ChemicalImportException;
import com.researchspace.service.ChemicalSearcher;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class PubchemSearcher implements ChemicalSearcher {

  private static final String PUBCHEM_BASE_URL =
      "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound";

  private final RestTemplate restTemplate;

  @Autowired
  public PubchemSearcher(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
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

  private void validateSearchParameters(ChemicalImportSearchType searchType, String searchTerm)
      throws ChemicalImportException {
    if (searchType == null || isBlank(searchTerm)) {
      throw new ChemicalImportException(
          "Search type and term are required", HttpStatus.BAD_REQUEST);
    }
  }

  private List<ChemicalImportSearchResult> parseResponseToResults(PubChemResponse response)
      throws ChemicalImportException {
    try {
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
      throw new ChemicalImportException(
          "Error parsing PubChem response", e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  private ChemicalImportSearchResult convertToSearchResult(
      PubChemResponse.ChemicalProperty property) {
    try {
      if (property.getCid() == null) {
        return null;
      }

      String cid = String.valueOf(property.getCid());
      String cas = fetchCasNumberFromSynonyms(cid);

      return ChemicalImportSearchResult.builder()
          .pubchemId(cid)
          .name(property.getTitle())
          .formula(property.getMolecularFormula())
          .smiles(property.getSmiles())
          .cas(cas)
          .pngImage(
              String.format("https://pubchem.ncbi.nlm.nih.gov/image/imgsrv.fcgi?cid=%s&t=l", cid))
          .pubchemUrl(String.format("https://pubchem.ncbi.nlm.nih.gov/compound/%s", cid))
          .build();

    } catch (Exception e) {
      log.warn("Error converting property to search result: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Fetches the CAS number for a given PubChem CID by querying the synonym endpoint, as it's not
   * available from the same endpoint where we get other chemical information. A CAS Registry Number
   * includes up to 10 digits which are separated into 3 groups by hyphens. The first part of the
   * number, starting from the left, has 2 to 7 digits; the second part has 2 digits. The final part
   * consists of a single check digit. Since cas isn't critical, we don't throw an exception if
   * there's a problem and instead return an empty string.
   */
  private String fetchCasNumberFromSynonyms(String cid) {
    try {
      String url = String.format("%s/cid/%s/synonyms/JSON", PUBCHEM_BASE_URL, cid);
      log.info("Fetching synonyms from PubChem API: {}", url);

      ResponseEntity<PubChemSynonymsResponse> response =
          restTemplate.getForEntity(url, PubChemSynonymsResponse.class);

      if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
        log.warn("Failed to fetch synonyms for CID {}: {}", cid, response.getStatusCode());
        return "";
      }

      PubChemSynonymsResponse synonymsResponse = response.getBody();
      if (synonymsResponse.getInformationList() == null
          || synonymsResponse.getInformationList().getInformation() == null
          || synonymsResponse.getInformationList().getInformation().isEmpty()) {
        log.warn("No synonym information found for CID {}", cid);
        return "";
      }

      List<String> synonyms =
          synonymsResponse.getInformationList().getInformation().get(0).getSynonyms();
      if (synonyms == null || synonyms.isEmpty()) {
        log.warn("No synonyms found for CID {}", cid);
        return "";
      }

      // CAS Registry Numbers follow the format: numbers separated by hyphens (e.g., 50-78-2)
      Pattern casPattern = Pattern.compile("([0-9]{2,7}-[0-9]{2}-[0-9])");

      for (String synonym : synonyms) {
        if (casPattern.matcher(synonym).matches()) {
          log.info("Found CAS number {} for CID {}", synonym, cid);
          return synonym;
        }
      }

      log.warn("No CAS number found in synonyms for CID {}", cid);
      return "";

    } catch (HttpClientErrorException e) {
      log.warn("HTTP error fetching synonyms for CID {}: {}", cid, e.getMessage());
      return "";
    } catch (Exception e) {
      log.warn("Error fetching CAS number for CID {}: {}", cid, e.getMessage());
      return "";
    }
  }

  private List<ChemicalImportSearchResult> makeApiRequestAndParseResponse(
      String url, String searchTerm) throws ChemicalImportException {
    try {
      log.info("Making PubChem API request to: {}", url);
      ResponseEntity<PubChemResponse> response =
          restTemplate.getForEntity(url, PubChemResponse.class);

      if (!response.getStatusCode().is2xxSuccessful()) {
        throw new ChemicalImportException(
            String.format("PubChem API returned status: %s", response.getStatusCode()),
            response.getStatusCode());
      }

      return parseResponseToResults(response.getBody());

    } catch (HttpClientErrorException e) {
      if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
        log.info("No results found for search term: {}", searchTerm);
        return new ArrayList<>();
      } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
        throw new ChemicalImportException(
            "Rate limit exceeded. Please try again later.", HttpStatus.TOO_MANY_REQUESTS);
      } else if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
        throw new ChemicalImportException(
            String.format("Invalid request to PubChem API: %s", e.getMessage()),
            e,
            HttpStatus.BAD_REQUEST);
      }
      throw new ChemicalImportException(
          String.format("PubChem API error: %s", e.getMessage()), e, e.getStatusCode());
    } catch (RestClientException e) {
      throw new ChemicalImportException(
          "Error communicating with PubChem API", e, HttpStatus.BAD_GATEWAY);
    } catch (Exception e) {
      throw new ChemicalImportException(
          "Unexpected error during chemical import", e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
