package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.client.BioPortalLinks;
import com.researchspace.client.BioPortalOntologiesClient;
import com.researchspace.client.BioPortalSearchResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class BioPortalOntologiesServiceTest {

  @Mock private BioPortalOntologiesClient bioOntologiesClientMock;
  @InjectMocks private BioPortalOntologiesService testee;

  @Test
  void shouldSwallowProviderRestClientExceptions() {
    doThrow(new RestClientException("boom"))
        .when(bioOntologiesClientMock)
        .search(any(String.class));
    assertEquals(0, testee.getBioOntologyDataForQuery("abc").size());
  }

  @Test
  void shouldSwallowUnexpectedMappingExceptions() {
    doThrow(RuntimeException.class).when(bioOntologiesClientMock).search(any(String.class));
    assertEquals(0, testee.getBioOntologyDataForQuery("abc").size());
  }

  @Test
  void shouldReturnEmptyListWhenNoFilterTerm() {
    assertEquals(0, testee.getBioOntologyDataForQuery("").size());
    verify(bioOntologiesClientMock, never()).search(anyString());
  }

  @Test
  void shouldReturnEmptyListWhenFilterTermIsTwoCharsOrFewer() {
    assertEquals(0, testee.getBioOntologyDataForQuery("a").size());
    assertEquals(0, testee.getBioOntologyDataForQuery("aa").size());
    verify(bioOntologiesClientMock, never()).search(anyString());
  }

  @Test
  void shouldCallClientOnceFilterTermIsThreeCharsOrMore() {
    when(bioOntologiesClientMock.search(anyString())).thenReturn(List.of());
    testee.getBioOntologyDataForQuery("aaa");
    verify(bioOntologiesClientMock).search("aaa");
  }

  @Test
  void shouldNormalizeSurroundingWhitespaceBeforeThresholdAndSearch() {
    when(bioOntologiesClientMock.search(anyString())).thenReturn(List.of());
    testee.getBioOntologyDataForQuery("  aaa  ");
    verify(bioOntologiesClientMock).search("aaa");
  }

  @Test
  void shouldReturnFormattedValuesForValidResults() {
    when(bioOntologiesClientMock.search("cen"))
        .thenReturn(
            List.of(
                new BioPortalSearchResult(
                    "http://purl.bioontology.org/ontology/NCBITAXON/480744",
                    "Ctenotus hanloni",
                    new BioPortalLinks("https://data.bioontology.org/ontologies/NCBITAXON")),
                new BioPortalSearchResult(
                    "http://purl.obolibrary.org/obo/VTO_0018361",
                    "Ctenotus hanloni",
                    new BioPortalLinks("https://data.bioontology.org/ontologies/VTO"))));
    when(bioOntologiesClientMock.getBioportalBaseUrl())
        .thenReturn("https://bioportal.bioontology.org");

    List<String> data = testee.getBioOntologyDataForQuery("cen");

    assertEquals(2, data.size());
    assertTrue(
        data.contains(
            "Ctenotus"
                + " hanloni__RSP_EXTONT_URL_DELIM__http://purl.bioontology.org/ontology/NCBITAXON/480744__RSP_EXTONT_NAME_DELIM__NCBITAXON__RSP_EXTONT_VERSION_DELIM__https://bioportal.bioontology.org/ontologies/NCBITAXON"
                + "  on: "
                + getTodayDateFormatted()),
        "unexpected: " + Arrays.toString(data.toArray()));
    assertTrue(
        data.contains(
            "Ctenotus"
                + " hanloni__RSP_EXTONT_URL_DELIM__http://purl.obolibrary.org/obo/VTO_0018361__RSP_EXTONT_NAME_DELIM__VTO__RSP_EXTONT_VERSION_DELIM__https://bioportal.bioontology.org/ontologies/VTO"
                + "  on: "
                + getTodayDateFormatted()),
        "unexpected: " + Arrays.toString(data.toArray()));
  }

  @Test
  void shouldKeepDuplicateLabelsFromDifferentOntologiesDistinguishable() {
    when(bioOntologiesClientMock.search("Toluene"))
        .thenReturn(
            List.of(
                new BioPortalSearchResult(
                    "http://purl.obolibrary.org/obo/CHEAR_1",
                    "Toluene",
                    new BioPortalLinks("https://data.bioontology.org/ontologies/CHEAR")),
                new BioPortalSearchResult(
                    "http://purl.obolibrary.org/obo/HHEAR_1",
                    "Toluene",
                    new BioPortalLinks("https://data.bioontology.org/ontologies/HHEAR"))));
    when(bioOntologiesClientMock.getBioportalBaseUrl())
        .thenReturn("https://bioportal.bioontology.org");

    List<String> data = testee.getBioOntologyDataForQuery("Toluene");

    assertEquals(2, data.size());
    assertTrue(data.get(0).contains("__RSP_EXTONT_NAME_DELIM__CHEAR__"));
    assertTrue(data.get(1).contains("__RSP_EXTONT_NAME_DELIM__HHEAR__"));
  }

  @Test
  void shouldSkipMalformedResultsButKeepValidOnes() {
    when(bioOntologiesClientMock.search("abc"))
        .thenReturn(
            List.of(
                new BioPortalSearchResult("some-uri", "Something", new BioPortalLinks(null)),
                new BioPortalSearchResult(
                    "http://purl.obolibrary.org/obo/GAZ_00593210",
                    "Lev Tolstoy",
                    new BioPortalLinks("https://data.bioontology.org/ontologies/GAZ"))));
    when(bioOntologiesClientMock.getBioportalBaseUrl())
        .thenReturn("https://bioportal.bioontology.org");

    List<String> data = testee.getBioOntologyDataForQuery("abc");

    assertEquals(1, data.size());
    assertTrue(data.get(0).startsWith("Lev Tolstoy__RSP_EXTONT_URL_DELIM__"));
  }

  @Test
  void shouldSkipNullResultButKeepValidOnesBeforeAndAfterIt() {

    when(bioOntologiesClientMock.search("abc"))
        .thenReturn(
            Arrays.asList(
                new BioPortalSearchResult(
                    "http://purl.obolibrary.org/obo/GAZ_00593210",
                    "Lev Tolstoy",
                    new BioPortalLinks("https://data.bioontology.org/ontologies/GAZ")),
                null,
                new BioPortalSearchResult(
                    "http://purl.obolibrary.org/obo/GAZ_00245556",
                    "Town of Tolstoy",
                    new BioPortalLinks("https://data.bioontology.org/ontologies/GAZ"))));
    when(bioOntologiesClientMock.getBioportalBaseUrl())
        .thenReturn("https://bioportal.bioontology.org");

    List<String> data = testee.getBioOntologyDataForQuery("abc");

    assertEquals(2, data.size());
    assertTrue(data.get(0).startsWith("Lev Tolstoy__RSP_EXTONT_URL_DELIM__"));
    assertTrue(data.get(1).startsWith("Town of Tolstoy__RSP_EXTONT_URL_DELIM__"));
  }

  @Test
  void shouldSkipResultWithBlankPrefLabel() {
    when(bioOntologiesClientMock.search("abc"))
        .thenReturn(
            List.of(
                new BioPortalSearchResult(
                    "http://purl.obolibrary.org/obo/GAZ_00593210",
                    " ",
                    new BioPortalLinks("https://data.bioontology.org/ontologies/GAZ"))));

    assertEquals(0, testee.getBioOntologyDataForQuery("abc").size());
  }

  @Test
  void shouldSkipResultWithBlankId() {
    when(bioOntologiesClientMock.search("abc"))
        .thenReturn(
            List.of(
                new BioPortalSearchResult(
                    " ",
                    "Lev Tolstoy",
                    new BioPortalLinks("https://data.bioontology.org/ontologies/GAZ"))));

    assertEquals(0, testee.getBioOntologyDataForQuery("abc").size());
  }

  @Test
  void shouldSkipResultWithNullLinksObject() {
    when(bioOntologiesClientMock.search("abc"))
        .thenReturn(
            List.of(
                new BioPortalSearchResult(
                    "http://purl.obolibrary.org/obo/GAZ_00593210", "Lev Tolstoy", null)));

    assertEquals(0, testee.getBioOntologyDataForQuery("abc").size());
  }

  private String getTodayDateFormatted() {
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    LocalDateTime now = LocalDateTime.now();
    return dtf.format(now);
  }
}
