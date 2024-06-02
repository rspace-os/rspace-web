package com.researchspace.service.impl;

import static com.researchspace.service.impl.OntologyDocManager.RSPACE_EXTONTOLOGY_URL_DELIMITER;

import com.researchspace.client.BioPortalOntologiesClient;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Gets data from the BioPortal Ontologies Portal at and then formats the data to resemble our
 * locally stored tags with metadata
 */
@Slf4j
@Service
public class BioPortalOntologiesService {
  private BioPortalOntologiesClient bioOntologiesClient;

  public BioPortalOntologiesService(BioPortalOntologiesClient bioOntologiesClient) {
    this.bioOntologiesClient = bioOntologiesClient;
  }

  public List<String> getBioOntologyDataForQuery(String filterTerm) {
    List<String> bioOntologyTerms = new ArrayList<>();
    if (filterTerm.length() > 2) {
      try {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        LocalDateTime now = LocalDateTime.now();
        String bioOntologyData = bioOntologiesClient.getBioOntologyData(filterTerm);
        String[] bioOntTerms = bioOntologyData.split("~!~");
        for (String ontologyTerm : bioOntTerms) {
          StringBuilder parsedBioOntologyTerms = new StringBuilder();
          String[] ontologyTermParts = ontologyTerm.split("\\|\\|");
          String ontologyName = ontologyTermParts[1].split("\\|")[0];
          String ontologyVersion =
              "https://bioportal.bioontology.org/ontologies/"
                  + ontologyName
                  + "  on: "
                  + dtf.format(now);
          String[] termValueAndUrl = ontologyTermParts[0].split("\\|");
          parsedBioOntologyTerms.append(
              termValueAndUrl[0]
                  + RSPACE_EXTONTOLOGY_URL_DELIMITER
                  + termValueAndUrl[1]
                  + OntologyDocManager.RSPACE_EXTONTOLOGY_NAME_DELIM
                  + ontologyName
                  + OntologyDocManager.RSPACE_EXTONTOLOGY_VERSION_DELIM
                  + ontologyVersion);
          bioOntologyTerms.add(parsedBioOntologyTerms.toString());
        }
      } catch (Exception e) {
        // In case of any issues with BioPortal API,
        // deliberately swallow exceptions so that 'local' tagging still works
        log.error(e.getMessage());
      }
    }
    return bioOntologyTerms;
  }
}
