package com.researchspace.service.impl;

import static com.researchspace.service.impl.OntologyDocManager.RSPACE_EXTONTOLOGY_NAME_DELIM;
import static com.researchspace.service.impl.OntologyDocManager.RSPACE_EXTONTOLOGY_URL_DELIMITER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.client.BioPortalOntologiesClient;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;

@EnabledIfSystemProperty(named = "nightly", matches = "(|true)")
public class BioPortalOntologiesServiceNightlyTest {

  private BioPortalOntologiesClient bioportalClient = new BioPortalOntologiesClient();
  private BioPortalOntologiesService service = new BioPortalOntologiesService(bioportalClient);

  @SneakyThrows
  @BeforeEach
  public void setUp() {
    ReflectionTestUtils.setField(
        bioportalClient, "bioportalBaseUrl", "https://bioportal.bioontology.org");
    ReflectionTestUtils.setField(
        bioportalClient, "bioportalApiBaseUrl", "https://data.bioontology.org");
    ReflectionTestUtils.setField(
        bioportalClient, "bioportalApiKey", System.getProperty("bioportal.api.key"));
  }

  @SneakyThrows
  @Test
  public void testListResults() {
    List<String> results = service.getBioOntologyDataForQuery("Tolstoy");

    assertFalse(results.isEmpty());
    assertTrue(
        results.stream()
            .anyMatch(
                r ->
                    r.startsWith("Lev Tolstoy" + RSPACE_EXTONTOLOGY_URL_DELIMITER)
                        && r.contains(RSPACE_EXTONTOLOGY_NAME_DELIM + "GAZ")),
        "expected a 'Lev Tolstoy' result from the GAZ ontology, got: " + results);
  }
}
