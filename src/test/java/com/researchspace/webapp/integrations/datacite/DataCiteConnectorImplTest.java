package com.researchspace.webapp.integrations.datacite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.model.system.SystemProperty;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.SystemPropertyManager;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataCiteConnectorImplTest {

  @Mock private SystemPropertyManager mockSysPropMgr;
  @InjectMocks private DataCiteConnectorImpl connector;

  private Map<String, SystemPropertyValue> propertiesMap;

  @BeforeEach
  void setUp() {
    propertiesMap = new HashMap<>();
    addProperty("datacite.enabled", "true");
    addProperty("datacite.server.url", "https://api.test.datacite.org");
    addProperty("datacite.username", "igsnUser");
    addProperty("datacite.password", "igsnPassword");
    addProperty("datacite.repositoryPrefix", "IGSNPREFIX");
    addProperty("pdinst.datacite.enabled", "true");
    addProperty("pdinst.datacite.server.url", "https://api.test.datacite.org");
    addProperty("pdinst.datacite.username", "pdinstUser");
    addProperty("pdinst.datacite.password", "pdinstPassword");
    addProperty("pdinst.datacite.repositoryPrefix", "PDINSTPREFIX");
    when(mockSysPropMgr.getAllSysadminPropertiesAsMap()).thenReturn(propertiesMap);
  }

  private void addProperty(String name, String value) {
    propertiesMap.put(name, new SystemPropertyValue(new SystemProperty(null), value));
  }

  @Test
  void reloadBuildsBothClientsWhenBothConfigured() {
    connector.reloadDataCiteClient();

    assertTrue(connector.isDataCiteConfiguredAndEnabled(InventorySettingType.IGSN));
    assertTrue(connector.isDataCiteConfiguredAndEnabled(InventorySettingType.PDINST));
  }

  @Test
  void incompletePdinstConfigSkipsPdinstClientOnly() {
    addProperty("pdinst.datacite.username", "");
    connector.reloadDataCiteClient();

    assertTrue(connector.isDataCiteConfiguredAndEnabled(InventorySettingType.IGSN));
    assertFalse(connector.isDataCiteConfiguredAndEnabled(InventorySettingType.PDINST));
  }

  @Test
  void incompleteIgsnConfigSkipsIgsnClientOnly() {
    addProperty("datacite.password", "");
    connector.reloadDataCiteClient();

    assertFalse(connector.isDataCiteConfiguredAndEnabled(InventorySettingType.IGSN));
    assertTrue(connector.isDataCiteConfiguredAndEnabled(InventorySettingType.PDINST));
  }

  @Test
  void disabledTypeIsNotEnabledEvenIfConfigured() {
    addProperty("pdinst.datacite.enabled", "false");
    connector.reloadDataCiteClient();

    assertTrue(connector.isDataCiteConfiguredAndEnabled(InventorySettingType.IGSN));
    assertFalse(connector.isDataCiteConfiguredAndEnabled(InventorySettingType.PDINST));
  }

  @Test
  void missingPdinstPropertiesAreToleratedAtReload() {
    propertiesMap.keySet().removeIf(name -> name.startsWith("pdinst.datacite."));
    connector.reloadDataCiteClient();

    assertTrue(connector.isDataCiteConfiguredAndEnabled(InventorySettingType.IGSN));
    assertFalse(connector.isDataCiteConfiguredAndEnabled(InventorySettingType.PDINST));
  }

  @Test
  void noArgMethodsDelegateToIgsn() {
    addProperty("pdinst.datacite.enabled", "false");
    connector.reloadDataCiteClient();

    assertEquals(
        connector.isDataCiteConfiguredAndEnabled(InventorySettingType.IGSN),
        connector.isDataCiteConfiguredAndEnabled());
    assertTrue(connector.isDataCiteConfiguredAndEnabled());
  }
}
