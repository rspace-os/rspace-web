package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.researchspace.api.v1.model.ApiInventorySystemSettings;
import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

public class SystemSettingsApiControllerTest extends SpringTransactionalTest {

  private @Autowired SystemSettingsApiController settingsController;

  private MockHttpServletRequest request;
  private BindingResult mockBindingResult = mock(BindingResult.class);

  @Before
  public void setUp() throws Exception {
    request = new MockHttpServletRequest();
  }

  @Test
  public void readUpdateSystemSettings() throws BindException {

    User sysadmin = logoutAndLoginAsSysAdmin();
    ApiInventorySystemSettings initialSettings =
        settingsController.getInventorySettings(request, sysadmin);
    assertNotNull(initialSettings);
    assertNotNull(initialSettings.getDatacite());
    assertEquals("https://api.datacite.org", initialSettings.getDatacite().getServerUrl());
    assertEquals("", initialSettings.getDatacite().getUsername());
    assertEquals("", initialSettings.getDatacite().getPassword());
    assertEquals("", initialSettings.getDatacite().getRepositoryPrefix());
    assertEquals("false", initialSettings.getDatacite().getEnabled());

    ApiInventorySystemSettings update = new ApiInventorySystemSettings();
    update.getDatacite().setUsername("updated");
    ApiInventorySystemSettings updatedSettings =
        settingsController.updateInventorySettings(request, update, mockBindingResult, sysadmin);
    assertNotNull(updatedSettings);
    assertEquals("updated", updatedSettings.getDatacite().getUsername());

    ApiInventorySystemSettings reloadedSettings =
        settingsController.getInventorySettings(request, sysadmin);
    assertNotNull(reloadedSettings);
    assertEquals("updated", reloadedSettings.getDatacite().getUsername());
  }
}
