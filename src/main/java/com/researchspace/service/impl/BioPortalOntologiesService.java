package com.researchspace.service.impl;

import static com.researchspace.service.impl.OntologyDocManager.RSPACE_EXTONTOLOGY_URL_DELIMITER;

import com.researchspace.client.BioPortalOntologiesClient;
import com.researchspace.client.BioPortalSearchResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BioPortalOntologiesService {

  /** Below this length, a search term is not eligible for a BioPortal lookup. */
  private static final int MIN_SEARCH_TERM_LENGTH = 2;

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

  private BioPortalOntologiesClient bioOntologiesClient;

  public BioPortalOntologiesService(BioPortalOntologiesClient bioOntologiesClient) {
    this.bioOntologiesClient = bioOntologiesClient;
  }

  public List<String> getBioOntologyDataForQuery(String filterTerm) {
    List<String> bioOntologyTerms = new ArrayList<>();
    String normalizedTerm = filterTerm == null ? "" : filterTerm.trim();
    if (normalizedTerm.length() > MIN_SEARCH_TERM_LENGTH) {
      try {
        String ontologyVersionSuffix = "  on: " + DATE_FORMAT.format(LocalDateTime.now());
        for (BioPortalSearchResult result : bioOntologiesClient.search(normalizedTerm)) {
          if (result == null) {
            log.debug("Skipping null BioPortal search result");
            continue;
          }
          String ontologyAcronym = extractOntologyAcronym(result);
          if (StringUtils.isBlank(result.getPrefLabel())
              || StringUtils.isBlank(result.getId())
              || ontologyAcronym == null) {
            log.debug("Skipping malformed BioPortal search result, missing required fields");
            continue;
          }
          String ontologyVersion =
              bioOntologiesClient.getBioportalBaseUrl()
                  + "/ontologies/"
                  + ontologyAcronym
                  + ontologyVersionSuffix;
          bioOntologyTerms.add(
              result.getPrefLabel()
                  + RSPACE_EXTONTOLOGY_URL_DELIMITER
                  + result.getId()
                  + OntologyDocManager.RSPACE_EXTONTOLOGY_NAME_DELIM
                  + ontologyAcronym
                  + OntologyDocManager.RSPACE_EXTONTOLOGY_VERSION_DELIM
                  + ontologyVersion);
        }
      } catch (Exception e) {
        log.warn("BioPortal ontology suggestions unavailable: {}", e.getClass().getSimpleName());
      }
    }
    return bioOntologyTerms;
  }

  private String extractOntologyAcronym(BioPortalSearchResult result) {
    if (result.getLinks() == null || StringUtils.isBlank(result.getLinks().getOntology())) {
      return null;
    }
    String ontologyUrl = result.getLinks().getOntology();
    int lastSlash = ontologyUrl.lastIndexOf('/');
    if (lastSlash < 0 || lastSlash == ontologyUrl.length() - 1) {
      return null;
    }
    return ontologyUrl.substring(lastSlash + 1);
  }
}
