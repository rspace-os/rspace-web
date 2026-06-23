package com.researchspace.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventorySystemSettings.IdentifierSettings;
import com.researchspace.auth.WhiteListIPChecker;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.model.system.SystemProperty;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.testutils.TestFactory;
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
    sysadmin = TestFactory.createAnyUserWithRole("sysadmin", Role.SYSTEM_ROLE.getName());
    when(mockIpChecker.isRequestWhitelisted(any(), any(), any())).thenReturn(true);
    when(mockSysPropMgr.getAllSysadminPropertiesAsMap()).thenReturn(defaultPropertiesMap());
  }

  private Map<String, SystemPropertyValue> defaultPropertiesMap() {
    Map<String, SystemPropertyValue> propertiesMap = new HashMap<>();
    propertiesMap.put("igsn.datacite.enabled", propertyValue("false"));
    propertiesMap.put("igsn.datacite.server.url", propertyValue("https://api.datacite.org"));
    propertiesMap.put("igsn.datacite.username", propertyValue(""));
    propertiesMap.put("igsn.datacite.password", propertyValue(""));
    propertiesMap.put("igsn.datacite.repositoryPrefix", propertyValue(""));
    propertiesMap.put("pidinst.datacite.enabled", propertyValue("false"));
    propertiesMap.put("pidinst.datacite.server.url", propertyValue("https://api.datacite.org"));
    propertiesMap.put("pidinst.datacite.username", propertyValue(""));
    propertiesMap.put("pidinst.datacite.password", propertyValue(""));
    propertiesMap.put("pidinst.datacite.repositoryPrefix", propertyValue(""));
    return propertiesMap;
  }

  private SystemPropertyValue propertyValue(String value) {
    return new SystemPropertyValue(new SystemProperty(null), value);
  }

  @Test
  void reloadCalledOnceWhenPidinstSettingChanged() throws Exception {
    IdentifierSettings update = new IdentifierSettings();
    update.setProvider(IdentifierType.PIDINST_DATACITE);
    update.setUsername("newPidinstUser");

    controller.updateInventorySettings(
        request, update, new BeanPropertyBindingResult(update, "identifierSettings"), sysadmin);

    verify(mockDataCiteConnector, times(1)).reloadDataCiteClient();
  }

  @Test
  void reloadNotCalledWhenNothingChanged() throws Exception {
    // values matching the current PIDINST defaults, so nothing changes
    IdentifierSettings update = new IdentifierSettings();
    update.setProvider(IdentifierType.PIDINST_DATACITE);
    update.setEnabled("false");
    update.setServerUrl("https://api.datacite.org");
    update.setUsername("");
    update.setPassword("");
    update.setRepositoryPrefix("");

    controller.updateInventorySettings(
        request, update, new BeanPropertyBindingResult(update, "identifierSettings"), sysadmin);

    verify(mockDataCiteConnector, never()).reloadDataCiteClient();
  }
}
