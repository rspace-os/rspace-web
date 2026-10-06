package com.researchspace.webapp.integrations.datacite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.datacite.client.DataCiteClient;
import com.researchspace.datacite.client.DataCiteClientImpl;
import com.researchspace.datacite.model.DataCiteConnectionException;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.datacite.model.DataCiteDoiSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;

/**
 * The connector routes lookups to the anonymous public-registry client and turns 404 into empty.
 */
class DataCiteConnectorImplLookupTest {

  private final DataCiteConnectorImpl connector = new DataCiteConnectorImpl();
  private final DataCiteClient lookupClient = mock(DataCiteClient.class);

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(connector, "lookupClient", lookupClient);
  }

  @Test
  void findPublicDoiReturnsTheDoiOrEmptyOn404() {
    DataCiteDoi found = new DataCiteDoi();
    found.setId("10.15151/esrf-instr-gco8");
    when(lookupClient.retrieveDoi("10.15151/esrf-instr-gco8")).thenReturn(found);
    when(lookupClient.retrieveDoi("10.15151/nope"))
        .thenThrow(
            HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

    assertEquals(
        "10.15151/esrf-instr-gco8",
        connector.findPublicDoi("10.15151/esrf-instr-gco8").get().getId());
    assertTrue(connector.findPublicDoi("10.15151/nope").isEmpty());
  }

  @Test
  void findPublicDoiReturnsEmptyForSomethingThatIsNotADoi() {
    when(lookupClient.retrieveDoi("not-a-doi"))
        .thenThrow(
            new IllegalArgumentException("Not a DOI, so it will not be put in a request path"));

    assertTrue(connector.findPublicDoi("not-a-doi").isEmpty());
  }

  @Test
  void searchPublicInstrumentDoisAsksForFindableInstrumentsNewestUpdateFirstOnTheOneBasedPage() {
    DataCiteDoiSearchResult page = new DataCiteDoiSearchResult();
    page.getMeta().setTotal(41);
    when(lookupClient.searchDois("Zeiss", "instrument", "findable", 50, 3, "-updated"))
        .thenReturn(page);

    assertEquals(41, connector.searchPublicInstrumentDois("Zeiss", 2, 50).getMeta().getTotal());
  }

  @Test
  void findPublicDoiStillReturnsWhateverStateTheRegistryHolds() {
    // the connector reports the state; refusing a non-findable DOI is the lookup manager's job
    DataCiteDoi draft = new DataCiteDoi();
    draft.setId("10.82316/dhhr-4396");
    draft.getAttributes().setState("draft");
    when(lookupClient.retrieveDoi("10.82316/dhhr-4396")).thenReturn(draft);

    assertEquals(
        "draft", connector.findPublicDoi("10.82316/dhhr-4396").get().getAttributes().getState());
  }

  @Test
  void aBlankLookupUrlLeavesNoClientAndFailsTheSearchWithAClearMessage() {
    ReflectionTestUtils.setField(connector, "lookupServerUrl", " ");
    connector.initLookupClient();

    DataCiteConnectionException thrown =
        assertThrows(
            DataCiteConnectionException.class,
            () -> connector.searchPublicInstrumentDois("Zeiss", 0, 50));

    assertTrue(thrown.getMessage().contains("pidinst.lookup.datacite.url"), thrown.getMessage());
  }

  @Test
  void theLookupClientIsBuiltFromTheDeploymentPropertyAndTheSupportEmail() {
    ReflectionTestUtils.setField(connector, "lookupServerUrl", "https://api.datacite.org");
    ReflectionTestUtils.setField(connector, "supportEmail", "support@example.org");
    connector.initLookupClient();

    Object built = ReflectionTestUtils.getField(connector, "lookupClient");
    assertInstanceOf(DataCiteClientImpl.class, built);
    assertEquals(
        "RSpace (mailto:support@example.org)", ReflectionTestUtils.getField(built, "userAgent"));
    assertNull(ReflectionTestUtils.getField(built, "basicAuthenticationHeader"));
  }
}
