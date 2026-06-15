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
    assertEquals(IdentifierType.DATACITE_IGSN, initialIgsn.getProvider());
    assertEquals("https://api.datacite.org", initialIgsn.getServerUrl());
    assertEquals("", initialIgsn.getUsername());
    assertEquals("", initialIgsn.getPassword());
    assertEquals("", initialIgsn.getRepositoryPrefix());
    assertEquals("false", initialIgsn.getEnabled());

    IdentifierSettings initialPdinst =
        initialSettings.getIdentifiersSettings().get(InventorySettingType.PDINST);
    assertNotNull(initialPdinst);
    assertEquals("https://api.datacite.org", initialPdinst.getServerUrl());
    assertEquals("", initialPdinst.getUsername());
    assertEquals("", initialPdinst.getPassword());
    assertEquals("", initialPdinst.getRepositoryPrefix());
    assertEquals("false", initialPdinst.getEnabled());

    // update with only an IGSN entry, partial fields
    ApiInventorySystemSettings igsnUpdate = new ApiInventorySystemSettings();
    igsnUpdate.getOrCreate(InventorySettingType.IGSN).setUsername("updated");
    ApiInventorySystemSettings updatedSettings =
        settingsController.updateInventorySettings(
            request, igsnUpdate, mockBindingResult, sysadmin);
    assertNotNull(updatedSettings);
    assertEquals(
        "updated",
        updatedSettings.getIdentifiersSettings().get(InventorySettingType.IGSN).getUsername());
    assertEquals(
        "",
        updatedSettings.getIdentifiersSettings().get(InventorySettingType.PDINST).getUsername());

    // update with only a PDINST entry, partial fields
    ApiInventorySystemSettings pdinstUpdate = new ApiInventorySystemSettings();
    pdinstUpdate.getOrCreate(InventorySettingType.PDINST).setUsername("pdinstUser");
    updatedSettings =
        settingsController.updateInventorySettings(
            request, pdinstUpdate, mockBindingResult, sysadmin);
    assertNotNull(updatedSettings);
    assertEquals(
        "pdinstUser",
        updatedSettings.getIdentifiersSettings().get(InventorySettingType.PDINST).getUsername());
    assertEquals(
        "updated",
        updatedSettings.getIdentifiersSettings().get(InventorySettingType.IGSN).getUsername());

    // empty update changes nothing and doesn't NPE
    ApiInventorySystemSettings emptyUpdate = new ApiInventorySystemSettings();
    updatedSettings =
        settingsController.updateInventorySettings(
            request, emptyUpdate, mockBindingResult, sysadmin);
    assertNotNull(updatedSettings);

    ApiInventorySystemSettings reloadedSettings =
        settingsController.getInventorySettings(request, sysadmin);
    assertNotNull(reloadedSettings);
    assertEquals(
        "updated",
        reloadedSettings.getIdentifiersSettings().get(InventorySettingType.IGSN).getUsername());
    assertEquals(
        "pdinstUser",
        reloadedSettings.getIdentifiersSettings().get(InventorySettingType.PDINST).getUsername());
  }

  @Test
  public void pdinstProviderExposedAndPersisted() throws BindException {
    User sysadmin = logoutAndLoginAsSysAdmin();

    ApiInventorySystemSettings initialSettings =
        settingsController.getInventorySettings(request, sysadmin);
    assertEquals(
        IdentifierType.DATACITE_PDINST,
        initialSettings.getIdentifiersSettings().get(InventorySettingType.PDINST).getProvider());

    // switch PDINST provider to B2INST_PDINST
    ApiInventorySystemSettings update = new ApiInventorySystemSettings();
    update.getOrCreate(InventorySettingType.PDINST).setProvider(IdentifierType.B2INST_PDINST);
    ApiInventorySystemSettings updatedSettings =
        settingsController.updateInventorySettings(request, update, mockBindingResult, sysadmin);
    assertEquals(
        IdentifierType.B2INST_PDINST,
        updatedSettings.getIdentifiersSettings().get(InventorySettingType.PDINST).getProvider());

    // reload from the database
    ApiInventorySystemSettings reloadedSettings =
        settingsController.getInventorySettings(request, sysadmin);
    assertEquals(
        IdentifierType.B2INST_PDINST,
        reloadedSettings.getIdentifiersSettings().get(InventorySettingType.PDINST).getProvider());
  }

  @Test
  public void invalidProviderForSettingTypeRejected() {
    User sysadmin = logoutAndLoginAsSysAdmin();

    ApiInventorySystemSettings igsnWithPdinstProvider = new ApiInventorySystemSettings();
    igsnWithPdinstProvider
        .getOrCreate(InventorySettingType.IGSN)
        .setProvider(IdentifierType.DATACITE_PDINST);
    assertThrows(
        BindException.class,
        () ->
            settingsController.updateInventorySettings(
                request,
                igsnWithPdinstProvider,
                new BeanPropertyBindingResult(igsnWithPdinstProvider, "systemSettings"),
                sysadmin));

    ApiInventorySystemSettings pdinstWithIgsnProvider = new ApiInventorySystemSettings();
    pdinstWithIgsnProvider
        .getOrCreate(InventorySettingType.PDINST)
        .setProvider(IdentifierType.DATACITE_IGSN);
    assertThrows(
        BindException.class,
        () ->
            settingsController.updateInventorySettings(
                request,
                pdinstWithIgsnProvider,
                new BeanPropertyBindingResult(pdinstWithIgsnProvider, "systemSettings"),
                sysadmin));
  }

  @Test
  public void pdinstSystemPropertiesHaveDefaults() {
    Map<String, SystemPropertyValue> propertiesMap = sysPropertyMgr.getAllSysadminPropertiesAsMap();

    assertPropertyValue(propertiesMap, "pdinst.datacite.provider", "DATACITE_PDINST");
    assertPropertyValue(propertiesMap, "pdinst.datacite.enabled", "false");
    assertPropertyValue(propertiesMap, "pdinst.datacite.server.url", "https://api.datacite.org");
    assertPropertyValue(propertiesMap, "pdinst.datacite.username", "");
    assertPropertyValue(propertiesMap, "pdinst.datacite.password", "");
    assertPropertyValue(propertiesMap, "pdinst.datacite.repositoryPrefix", "");
  }

  private void assertPropertyValue(
      Map<String, SystemPropertyValue> propertiesMap, String propertyName, String expectedValue) {
    assertNotNull(propertiesMap.get(propertyName), "missing system property: " + propertyName);
    assertEquals(expectedValue, propertiesMap.get(propertyName).getValue());
  }
}
