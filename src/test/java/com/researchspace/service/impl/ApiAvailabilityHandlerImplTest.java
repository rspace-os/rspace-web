package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.webapp.integrations.datacite.DataCiteConnectorDummy;
import com.researchspace.webapp.integrations.datacite.DataCiteConnectorDummyError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class ApiAvailabilityHandlerImplTest {

  private static final String API_V_1_DOCUMENTS = "/api/v1/documents";
  private static final String API_INVENTORY_V_1_WORKBENCHES = "/api/inventory/v1/workbenches";
  @Mock private SystemPropertyPermissionManager mockSysPropMgr;
  private User anyUser = null;
  private MockHttpServletRequest httpRequest = null;
  private ApiAvailabilityHandlerImpl handler;

  @BeforeEach
  void setUp() {
    anyUser = TestFactory.createAnyUser("any");
    httpRequest = new MockHttpServletRequest();
    handler = new ApiAvailabilityHandlerImpl();
    handler.setSystemPropertyManager(mockSysPropMgr);
    handler.setDataCiteConnector(new DataCiteConnectorDummy());
  }

  @Test
  @DisplayName("Case 1: api enabled, inventory enabled, Inventory api request")
  void isAvailableCase1() {
    setSystemProperty(SystemPropertyName.API_AVAILABLE, Boolean.TRUE);
    setSystemProperty(SystemPropertyName.INVENTORY_AVAILABLE, Boolean.TRUE);
    httpRequest.setRequestURI(API_INVENTORY_V_1_WORKBENCHES);
    assertApiEnabled();
  }

  @Test
  @DisplayName("Case 2: api enabled, inventory disabled, Inventory api request")
  void isAvailableCase2() {
    setSystemProperty(SystemPropertyName.API_AVAILABLE, Boolean.TRUE);
    setSystemProperty(SystemPropertyName.INVENTORY_AVAILABLE, Boolean.FALSE);
    httpRequest.setRequestURI(API_INVENTORY_V_1_WORKBENCHES);
    assertApiDisabled();
  }

  @Test
  @DisplayName("Case 3: api enabled, ELN api request")
  void isAvailableCase3() {
    setSystemProperty(SystemPropertyName.API_AVAILABLE, Boolean.TRUE);
    httpRequest.setRequestURI(API_V_1_DOCUMENTS);
    assertApiEnabled();
  }

  @Test
  @DisplayName("Case 4: api disabled, eln api request")
  void isAvailableCase4() {
    setSystemProperty(SystemPropertyName.API_AVAILABLE, Boolean.FALSE);
    httpRequest.setRequestURI(API_V_1_DOCUMENTS);
    assertApiDisabled();
  }

  @Test
  @DisplayName("Case 4b: api disabled, inv api request")
  void isAvailableCase4b() {
    setSystemProperty(SystemPropertyName.API_AVAILABLE, Boolean.FALSE);
    httpRequest.setRequestURI(API_INVENTORY_V_1_WORKBENCHES);
    assertApiDisabled();
  }

  @Test
  void testInventoryAndDataciteEnabled() {
    setSystemProperty(SystemPropertyName.DATACITE_ENABLED, Boolean.TRUE);
    setSystemProperty(SystemPropertyName.INVENTORY_AVAILABLE, Boolean.TRUE);
    assertTrue(handler.isInventoryAndDataciteEnabled(anyUser));
    handler.assertInventoryAndDataciteEnabled(anyUser);
  }

  @Test
  void testDataciteDisabled() {
    handler.setDataCiteConnector(new DataCiteConnectorDummyError());
    setSystemProperty(SystemPropertyName.INVENTORY_AVAILABLE, Boolean.TRUE);
    assertFalse(handler.isInventoryAndDataciteEnabled(anyUser));

    Exception exception =
        assertThrows(
            UnsupportedOperationException.class,
            () -> handler.assertInventoryAndDataciteEnabled(anyUser));
    assertEquals(
        "IGSN integration is not enabled on this RSpace instance.", exception.getMessage());
  }

  @Test
  void testInventoryAndDataciteDisabled() {
    handler.setDataCiteConnector(new DataCiteConnectorDummyError());
    setSystemProperty(SystemPropertyName.INVENTORY_AVAILABLE, Boolean.FALSE);
    assertFalse(handler.isInventoryAndDataciteEnabled(anyUser));

    Exception exception =
        assertThrows(
            UnsupportedOperationException.class,
            () -> handler.assertInventoryAndDataciteEnabled(anyUser));
    assertEquals("Inventory is not enabled on this RSpace instance.", exception.getMessage());
  }

  private void assertApiDisabled() {
    assertFalse(handler.isAvailable(anyUser, httpRequest).isSucceeded());
  }

  private void setSystemProperty(SystemPropertyName apiAvailable, Boolean aTrue) {
    Mockito.lenient()
        .when(mockSysPropMgr.isPropertyAllowed(anyUser, apiAvailable))
        .thenReturn(aTrue);
  }

  private void assertApiEnabled() {
    assertTrue(handler.isAvailable(anyUser, httpRequest).isSucceeded());
  }
}
