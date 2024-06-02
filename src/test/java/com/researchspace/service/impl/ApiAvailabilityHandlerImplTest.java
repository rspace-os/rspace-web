package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.SystemPropertyPermissionManager;
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

  private String invAvailable = "inventory.available";
  private String apiAvailable = "api.available";
  private ApiAvailabilityHandlerImpl handler;

  @BeforeEach
  void setUp() {
    anyUser = TestFactory.createAnyUser("any");
    httpRequest = new MockHttpServletRequest();
    handler = new ApiAvailabilityHandlerImpl();
    handler.setSystemPropertyManager(mockSysPropMgr);
  }

  @Test
  @DisplayName("Case 1: api enabled, inventory enabled, Inventory api request")
  void isAvailableCase1() {
    enableApi(apiAvailable, Boolean.TRUE);
    enableApi(invAvailable, Boolean.TRUE);
    httpRequest.setRequestURI(API_INVENTORY_V_1_WORKBENCHES);
    assertApiEnabled();
  }

  @Test
  @DisplayName("Case 2: api enabled, inventory disabled, Inventory api request")
  void isAvailableCase2() {
    enableApi(apiAvailable, Boolean.TRUE);
    enableApi(invAvailable, Boolean.FALSE);
    httpRequest.setRequestURI(API_INVENTORY_V_1_WORKBENCHES);
    assertApiDisabled();
  }

  @Test
  @DisplayName("Case 3: api enabled, ELN api request")
  void isAvailableCase3() {
    enableApi(apiAvailable, Boolean.TRUE);
    httpRequest.setRequestURI(API_V_1_DOCUMENTS);
    assertApiEnabled();
  }

  @Test
  @DisplayName("Case 4: api disabled, eln api request")
  void isAvailableCase4() {
    enableApi(apiAvailable, Boolean.FALSE);
    httpRequest.setRequestURI(API_V_1_DOCUMENTS);
    assertApiDisabled();
  }

  @Test
  @DisplayName("Case 4b: api disabled, inv api request")
  void isAvailableCase4b() {
    enableApi(apiAvailable, Boolean.FALSE);
    httpRequest.setRequestURI(API_INVENTORY_V_1_WORKBENCHES);
    assertApiDisabled();
  }

  private void assertApiDisabled() {
    assertFalse(handler.isAvailable(anyUser, httpRequest).isSucceeded());
  }

  private void enableApi(String apiAvailable, Boolean aTrue) {
    Mockito.lenient()
        .when(mockSysPropMgr.isPropertyAllowed(anyUser, apiAvailable))
        .thenReturn(aTrue);
  }

  private void assertApiEnabled() {
    assertTrue(handler.isAvailable(anyUser, httpRequest).isSucceeded());
  }
}
