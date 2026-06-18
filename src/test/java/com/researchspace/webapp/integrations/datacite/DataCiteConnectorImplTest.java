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
    addProperty("igsn.datacite.enabled", "true");
    addProperty("igsn.datacite.server.url", "https://api.test.datacite.org");
    addProperty("igsn.datacite.username", "igsnUser");
    addProperty("igsn.datacite.password", "igsnPassword");
    addProperty("igsn.datacite.repositoryPrefix", "IGSNPREFIX");
    addProperty("pidinst.datacite.enabled", "true");
    addProperty("pidinst.datacite.server.url", "https://api.test.datacite.org");
    addProperty("pidinst.datacite.username", "pidinstUser");
    addProperty("pidinst.datacite.password", "pidinstPassword");
    addProperty("pidinst.datacite.repositoryPrefix", "PIDINSTPREFIX");
    when(mockSysPropMgr.getAllSysadminPropertiesAsMap()).thenReturn(propertiesMap);
  }

  private void addProperty(String name, String value) {
    propertiesMap.put(name, new SystemPropertyValue(new SystemProperty(null), value));
  }

  @Test
  void reloadBuildsBothClientsWhenBothConfigured() {
    connector.reloadDataCiteClient();

    assertTrue(connector.isDataCiteConfiguredAndEnabled(InventorySettingType.IGSN));
    assertTrue(connector.isDataCiteConfiguredAndEnabled(InventorySettingType.PIDINST));
  }

  @Test
  void incompletePidinstConfigSkipsPidinstClientOnly() {
    addProperty("pidinst.datacite.username", "");
    connector.reloadDataCiteClient();

    assertTrue(connector.isDataCiteConfiguredAndEnabled(InventorySettingType.IGSN));
    assertFalse(connector.isDataCiteConfiguredAndEnabled(InventorySettingType.PIDINST));
  }

  @Test
  void incompleteIgsnConfigSkipsIgsnClientOnly() {
    addProperty("igsn.datacite.password", "");
    connector.reloadDataCiteClient();

    assertFalse(connector.isDataCiteConfiguredAndEnabled(InventorySettingType.IGSN));
    assertTrue(connector.isDataCiteConfiguredAndEnabled(InventorySettingType.PIDINST));
  }

  @Test
  void disabledTypeIsNotEnabledEvenIfConfigured() {
    addProperty("pidinst.datacite.enabled", "false");
    connector.reloadDataCiteClient();

    assertTrue(connector.isDataCiteConfiguredAndEnabled(InventorySettingType.IGSN));
    assertFalse(connector.isDataCiteConfiguredAndEnabled(InventorySettingType.PIDINST));
  }

  @Test
  void missingPidinstPropertiesAreToleratedAtReload() {
    propertiesMap.keySet().removeIf(name -> name.startsWith("pidinst.datacite."));
    connector.reloadDataCiteClient();

    assertTrue(connector.isDataCiteConfiguredAndEnabled(InventorySettingType.IGSN));
    assertFalse(connector.isDataCiteConfiguredAndEnabled(InventorySettingType.PIDINST));
  }

  @Test
  void noArgMethodsDelegateToIgsn() {
    addProperty("pidinst.datacite.enabled", "false");
    connector.reloadDataCiteClient();

    assertEquals(
        connector.isDataCiteConfiguredAndEnabled(InventorySettingType.IGSN),
        connector.isDataCiteConfiguredAndEnabled());
    assertTrue(connector.isDataCiteConfiguredAndEnabled());
  }
}
