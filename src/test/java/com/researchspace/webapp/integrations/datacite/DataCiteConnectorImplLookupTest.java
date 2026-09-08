package com.researchspace.webapp.integrations.datacite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.datacite.client.DataCiteClient;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.datacite.model.DataCiteDoiSearchResult;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;

/**
 * The connector only routes to the PIDINST client and turns DataCite's 404 into an empty answer.
 */
class DataCiteConnectorImplLookupTest {

  private final DataCiteConnectorImpl connector = new DataCiteConnectorImpl();
  private final DataCiteClient pidinstClient = mock(DataCiteClient.class);

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    Map<InventorySettingType, DataCiteClient> clients =
        (Map<InventorySettingType, DataCiteClient>)
            ReflectionTestUtils.getField(connector, "dataCiteClients");
    clients.put(InventorySettingType.PIDINST, pidinstClient);
  }

  @Test
  void findDoiReturnsTheDoiOrEmptyOn404() {
    DataCiteDoi found = new DataCiteDoi();
    found.setId("10.15151/esrf-instr-gco8");
    when(pidinstClient.retrieveDoi("10.15151/esrf-instr-gco8")).thenReturn(found);
    when(pidinstClient.retrieveDoi("10.15151/nope"))
        .thenThrow(
            HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

    assertEquals(
        "10.15151/esrf-instr-gco8",
        connector.findDoi("10.15151/esrf-instr-gco8", InventorySettingType.PIDINST).get().getId());
    assertTrue(connector.findDoi("10.15151/nope", InventorySettingType.PIDINST).isEmpty());
  }

  @Test
  void findDoiReturnsEmptyForSomethingThatIsNotADoi() {
    when(pidinstClient.retrieveDoi("not-a-doi"))
        .thenThrow(
            new IllegalArgumentException("Not a DOI, so it will not be put in a request path"));

    assertTrue(connector.findDoi("not-a-doi", InventorySettingType.PIDINST).isEmpty());
  }

  @Test
  void searchInstrumentDoisRestrictsToTheInstrumentResourceType() {
    DataCiteDoiSearchResult page = new DataCiteDoiSearchResult();
    page.getMeta().setTotal(63);
    when(pidinstClient.searchDois("Zeiss", "instrument", 50)).thenReturn(page);

    assertEquals(
        63,
        connector
            .searchInstrumentDois("Zeiss", 50, InventorySettingType.PIDINST)
            .getMeta()
            .getTotal());
  }
}
