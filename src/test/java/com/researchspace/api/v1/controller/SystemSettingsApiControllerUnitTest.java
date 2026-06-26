package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventorySystemSettings;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.IdentifierSettings;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.auth.WhiteListIPChecker;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.model.system.SystemProperty;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.testutils.TestFactory;
import com.researchspace.webapp.integrations.b2inst.B2instConnector;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BeanPropertyBindingResult;

@ExtendWith(MockitoExtension.class)
class SystemSettingsApiControllerUnitTest {

  @Mock private SystemPropertyManager mockSysPropMgr;
  @Mock private DataCiteConnector mockDataCiteConnector;
  @Mock private B2instConnector mockB2instConnector;
  @Mock private WhiteListIPChecker mockIpChecker;

  private SystemSettingsApiController controller;
  private User sysadmin;
  private final MockHttpServletRequest request = new MockHttpServletRequest();

  @BeforeEach
  void setUp() {
    controller = new SystemSettingsApiController();
    ReflectionTestUtils.setField(controller, "sysPropertyMgr", mockSysPropMgr);
    ReflectionTestUtils.setField(controller, "ipWhiteListChecker", mockIpChecker);
    ReflectionTestUtils.setField(controller, "dataCiteConnector", mockDataCiteConnector);
    ReflectionTestUtils.setField(controller, "b2instConnector", mockB2instConnector);
    sysadmin = TestFactory.createAnyUserWithRole("sysadmin", Role.SYSTEM_ROLE.getName());
    when(mockIpChecker.isRequestWhitelisted(any(), any(), any())).thenReturn(true);
    when(mockSysPropMgr.getAllSysadminPropertiesAsMap()).thenReturn(defaultPropertiesMap());
  }

  private Map<String, SystemPropertyValue> defaultPropertiesMap() {
    Map<String, SystemPropertyValue> p = new HashMap<>();
    p.put("igsn.datacite.enabled", propertyValue("false"));
    p.put("igsn.datacite.server.url", propertyValue("https://api.datacite.org"));
    p.put("igsn.datacite.username", propertyValue(""));
    p.put("igsn.datacite.password", propertyValue(""));
    p.put("igsn.datacite.repositoryPrefix", propertyValue(""));
    p.put("pidinst.datacite.enabled", propertyValue("false"));
    p.put("pidinst.datacite.server.url", propertyValue("https://api.datacite.org"));
    p.put("pidinst.datacite.username", propertyValue(""));
    p.put("pidinst.datacite.password", propertyValue(""));
    p.put("pidinst.datacite.repositoryPrefix", propertyValue(""));
    p.put("pidinst.b2inst.enabled", propertyValue("false"));
    p.put("pidinst.b2inst.server.url", propertyValue("https://b2inst-test.gwdg.de"));
    p.put("pidinst.b2inst.community.id", propertyValue("comm-default"));
    p.put("pidinst.b2inst.token", propertyValue("tok-default"));
    return p;
  }

  private SystemPropertyValue propertyValue(String value) {
    return new SystemPropertyValue(new SystemProperty(null), value);
  }

  private BeanPropertyBindingResult errorsFor(IdentifierSettings s) {
    return new BeanPropertyBindingResult(s, "identifierSettings");
  }

  @Test
  void getReturnsBothPidinstProvidersWithB2instMappedFields() {
    ApiInventorySystemSettings settings = controller.getInventorySettings(request, sysadmin);

    assertEquals(2, settings.getIdentifiersSettings().get(InventorySettingType.PIDINST).size());
    IdentifierSettings b2inst =
        settings.findByProvider(IdentifierType.PIDINST_B2INST).orElseThrow();
    // B2INST reuses the IdentifierSettings shape: username <- community.id, password <- token
    assertEquals("comm-default", b2inst.getUsername());
    assertEquals("tok-default", b2inst.getPassword());
    assertEquals("https://b2inst-test.gwdg.de", b2inst.getServerUrl());
  }

  @Test
  void putB2instWritesCommunityAndTokenAndReloadsB2instClient() throws Exception {
    IdentifierSettings update = new IdentifierSettings();
    update.setProvider(IdentifierType.PIDINST_B2INST);
    update.setEnabled("true");
    update.setServerUrl("https://b2inst-test.gwdg.de"); // unchanged
    update.setUsername("comm123"); // community id
    update.setPassword("tok456"); // token

    controller.updateInventorySettings(request, update, errorsFor(update), sysadmin);

    verify(mockSysPropMgr)
        .save(eq(SystemPropertyName.PIDINST_B2INST_COMMUNITY_ID), eq("comm123"), eq(sysadmin));
    verify(mockSysPropMgr)
        .save(eq(SystemPropertyName.PIDINST_B2INST_TOKEN), eq("tok456"), eq(sysadmin));
    verify(mockSysPropMgr)
        .save(eq(SystemPropertyName.PIDINST_B2INST_ENABLED), eq("true"), eq(sysadmin));
    verify(mockB2instConnector, times(1)).reloadClient();
  }

  @Test
  void enablingB2instAutoDisablesPidinstDatacite() throws Exception {
    Map<String, SystemPropertyValue> props = defaultPropertiesMap();
    props.put("pidinst.datacite.enabled", propertyValue("true")); // datacite currently enabled
    when(mockSysPropMgr.getAllSysadminPropertiesAsMap()).thenReturn(props);

    IdentifierSettings update = new IdentifierSettings();
    update.setProvider(IdentifierType.PIDINST_B2INST);
    update.setEnabled("true");

    controller.updateInventorySettings(request, update, errorsFor(update), sysadmin);

    verify(mockSysPropMgr)
        .save(eq(SystemPropertyName.PIDINST_DATACITE_ENABLED), eq("false"), eq(sysadmin));
  }

  @Test
  void enablingPidinstDataciteAutoDisablesB2inst() throws Exception {
    Map<String, SystemPropertyValue> props = defaultPropertiesMap();
    props.put("pidinst.b2inst.enabled", propertyValue("true")); // b2inst currently enabled
    when(mockSysPropMgr.getAllSysadminPropertiesAsMap()).thenReturn(props);

    IdentifierSettings update = new IdentifierSettings();
    update.setProvider(IdentifierType.PIDINST_DATACITE);
    update.setEnabled("true");

    controller.updateInventorySettings(request, update, errorsFor(update), sysadmin);

    verify(mockSysPropMgr)
        .save(eq(SystemPropertyName.PIDINST_B2INST_ENABLED), eq("false"), eq(sysadmin));
  }

  @Test
  void reloadCalledWhenPidinstSettingChanged() throws Exception {
    IdentifierSettings update = new IdentifierSettings();
    update.setProvider(IdentifierType.PIDINST_DATACITE);
    update.setUsername("newPidinstUser");

    controller.updateInventorySettings(request, update, errorsFor(update), sysadmin);

    verify(mockDataCiteConnector, times(1)).reloadDataCiteClient();
  }

  @Test
  void reloadNotCalledWhenNothingChanged() throws Exception {
    IdentifierSettings update = new IdentifierSettings();
    update.setProvider(IdentifierType.PIDINST_DATACITE);
    update.setEnabled("false");
    update.setServerUrl("https://api.datacite.org");
    update.setUsername("");
    update.setPassword("");
    update.setRepositoryPrefix("");

    controller.updateInventorySettings(request, update, errorsFor(update), sysadmin);

    verify(mockDataCiteConnector, never()).reloadDataCiteClient();
    verify(mockB2instConnector, never()).reloadClient();
  }
}
