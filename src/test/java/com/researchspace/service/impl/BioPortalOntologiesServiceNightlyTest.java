package com.researchspace.service.impl;

import static com.researchspace.service.impl.OntologyDocManager.RSPACE_EXTONTOLOGY_NAME_DELIM;
import static com.researchspace.service.impl.OntologyDocManager.RSPACE_EXTONTOLOGY_URL_DELIMITER;
import static com.researchspace.service.impl.OntologyDocManager.RSPACE_EXTONTOLOGY_VERSION_DELIM;
import static org.junit.Assert.assertEquals;

import com.researchspace.client.BioPortalOntologiesClient;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

public class BioPortalOntologiesServiceNightlyTest {
  private BioPortalOntologiesClient bioportalClient = new BioPortalOntologiesClient();
  private BioPortalOntologiesService service = new BioPortalOntologiesService(bioportalClient);

  @SneakyThrows
  @Test
  @Disabled("BioPortal tags retrieval currently doesn't work, see rspace-os/rspace-web issue #319")
  @EnabledIfSystemProperty(named = "nightly", matches = "true")
  public void testListResults() {
    List<String> results = service.getBioOntologyDataForQuery("Tolstoy");
    String town = results.get(0);
    String man = results.get(1);
    assertEquals("Town of Tolstoy", town.split(RSPACE_EXTONTOLOGY_URL_DELIMITER)[0]);
    assertEquals(
        "http://purl.obolibrary.org/obo/GAZ_00245556",
        town.split(RSPACE_EXTONTOLOGY_URL_DELIMITER)[1].split(RSPACE_EXTONTOLOGY_NAME_DELIM)[0]);
    assertEquals(
        "GAZ",
        town.split(RSPACE_EXTONTOLOGY_NAME_DELIM)[1].split(RSPACE_EXTONTOLOGY_VERSION_DELIM)[0]);
    assertEquals("Lev Tolstoy", man.split(RSPACE_EXTONTOLOGY_URL_DELIMITER)[0]);
    assertEquals(
        "http://purl.obolibrary.org/obo/GAZ_00593210",
        man.split(RSPACE_EXTONTOLOGY_URL_DELIMITER)[1].split(RSPACE_EXTONTOLOGY_NAME_DELIM)[0]);
    assertEquals(
        "GAZ",
        man.split(RSPACE_EXTONTOLOGY_NAME_DELIM)[1].split(RSPACE_EXTONTOLOGY_VERSION_DELIM)[0]);
  }
}
