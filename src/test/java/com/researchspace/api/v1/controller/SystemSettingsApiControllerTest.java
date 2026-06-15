package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.researchspace.api.v1.model.ApiInventorySystemSettings;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.IdentifierSettings;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

public class SystemSettingsApiControllerTest extends SpringTransactionalTest {

  private @Autowired SystemSettingsApiController settingsController;
  private @Autowired SystemPropertyManager sysPropertyMgr;

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

    IdentifierSettings initialIgsn =
        initialSettings.getIdentifiersSettings().get(InventorySettingType.IGSN);
    assertNotNull(initialIgsn);
    assertEquals(IdentifierType.IGSN_DATACITE, initialIgsn.getProvider());
    assertNotNull(initialSettings.getIdentifiersSettings().get(InventorySettingType.PDINST));

    // capture the current PDINST username, so we can prove the IGSN update leaves it untouched
    String initialPdinstUsername =
        initialSettings.getIdentifiersSettings().get(InventorySettingType.PDINST).getUsername();

    // a single IGSN-provider object updates only the IGSN config
    IdentifierSettings igsnUpdate = new IdentifierSettings();
    igsnUpdate.setProvider(IdentifierType.IGSN_DATACITE);
    igsnUpdate.setUsername("igsnUserUpdated");
    ApiInventorySystemSettings updatedSettings =
        settingsController.updateInventorySettings(
            request, igsnUpdate, mockBindingResult, sysadmin);
    assertNotNull(updatedSettings);
    assertEquals(
        "igsnUserUpdated",
        updatedSettings.getIdentifiersSettings().get(InventorySettingType.IGSN).getUsername());
    assertEquals(
        initialPdinstUsername,
        updatedSettings.getIdentifiersSettings().get(InventorySettingType.PDINST).getUsername());

    // a single PDINST-provider object updates only the PDINST config
    IdentifierSettings pdinstUpdate = new IdentifierSettings();
    pdinstUpdate.setProvider(IdentifierType.PDINST_DATACITE);
    pdinstUpdate.setUsername("pdinstUserUpdated");
    updatedSettings =
        settingsController.updateInventorySettings(
            request, pdinstUpdate, mockBindingResult, sysadmin);
    assertNotNull(updatedSettings);
    assertEquals(
        "pdinstUserUpdated",
        updatedSettings.getIdentifiersSettings().get(InventorySettingType.PDINST).getUsername());
    assertEquals(
        "igsnUserUpdated",
        updatedSettings.getIdentifiersSettings().get(InventorySettingType.IGSN).getUsername());

    ApiInventorySystemSettings reloadedSettings =
        settingsController.getInventorySettings(request, sysadmin);
    assertNotNull(reloadedSettings);
    assertEquals(
        "igsnUserUpdated",
        reloadedSettings.getIdentifiersSettings().get(InventorySettingType.IGSN).getUsername());
    assertEquals(
        "pdinstUserUpdated",
        reloadedSettings.getIdentifiersSettings().get(InventorySettingType.PDINST).getUsername());
  }

  @Test
  public void pdinstProviderPersistedFromSingleObject() throws BindException {
    User sysadmin = logoutAndLoginAsSysAdmin();

    // a PDINST_B2INST provider routes to the PDINST config and persists the provider
    IdentifierSettings update = new IdentifierSettings();
    update.setProvider(IdentifierType.PDINST_B2INST);
    ApiInventorySystemSettings updatedSettings =
        settingsController.updateInventorySettings(request, update, mockBindingResult, sysadmin);
    assertEquals(
        IdentifierType.PDINST_B2INST,
        updatedSettings.getIdentifiersSettings().get(InventorySettingType.PDINST).getProvider());

    ApiInventorySystemSettings reloadedSettings =
        settingsController.getInventorySettings(request, sysadmin);
    assertEquals(
        IdentifierType.PDINST_B2INST,
        reloadedSettings.getIdentifiersSettings().get(InventorySettingType.PDINST).getProvider());
  }

  @Test
  public void updateWithoutProviderRejected() {
    User sysadmin = logoutAndLoginAsSysAdmin();

    IdentifierSettings noProvider = new IdentifierSettings();
    noProvider.setUsername("whatever");
    assertThrows(
        BindException.class,
        () ->
            settingsController.updateInventorySettings(
                request,
                noProvider,
                new BeanPropertyBindingResult(noProvider, "identifierSettings"),
                sysadmin));
  }

  @Test
  public void pdinstSystemPropertiesAreSeeded() {
    // asserts the RSDEV-1175 changeset seeded the six pdinst.datacite.* properties; values are
    // mutable sysadmin config (so not asserted here)
    Map<String, SystemPropertyValue> propertiesMap = sysPropertyMgr.getAllSysadminPropertiesAsMap();

    assertPropertyPresent(propertiesMap, "pdinst.datacite.provider");
    assertPropertyPresent(propertiesMap, "pdinst.datacite.enabled");
    assertPropertyPresent(propertiesMap, "pdinst.datacite.server.url");
    assertPropertyPresent(propertiesMap, "pdinst.datacite.username");
    assertPropertyPresent(propertiesMap, "pdinst.datacite.password");
    assertPropertyPresent(propertiesMap, "pdinst.datacite.repositoryPrefix");
  }

  private void assertPropertyPresent(
      Map<String, SystemPropertyValue> propertiesMap, String propertyName) {
    assertNotNull(propertiesMap.get(propertyName), "missing system property: " + propertyName);
  }
}
