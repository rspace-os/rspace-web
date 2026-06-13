package com.researchspace.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventorySystemSettings;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.auth.WhiteListIPChecker;
import com.researchspace.model.Role;
import com.researchspace.model.User;
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
    propertiesMap.put("datacite.enabled", propertyValue("false"));
    propertiesMap.put("datacite.server.url", propertyValue("https://api.datacite.org"));
    propertiesMap.put("datacite.username", propertyValue(""));
    propertiesMap.put("datacite.password", propertyValue(""));
    propertiesMap.put("datacite.repositoryPrefix", propertyValue(""));
    propertiesMap.put("pdinst.provider", propertyValue("DATACITE_PDINST"));
    propertiesMap.put("pdinst.enabled", propertyValue("false"));
    propertiesMap.put("pdinst.server.url", propertyValue("https://api.datacite.org"));
    propertiesMap.put("pdinst.username", propertyValue(""));
    propertiesMap.put("pdinst.password", propertyValue(""));
    propertiesMap.put("pdinst.repositoryPrefix", propertyValue(""));
    return propertiesMap;
  }

  private SystemPropertyValue propertyValue(String value) {
    return new SystemPropertyValue(new SystemProperty(null), value);
  }

  @Test
  void reloadCalledOnceWhenPdinstSettingChanged() throws Exception {
    ApiInventorySystemSettings update = new ApiInventorySystemSettings();
    update.getOrCreate(InventorySettingType.PDINST).setUsername("newPdinstUser");

    controller.updateInventorySettings(
        request, update, new BeanPropertyBindingResult(update, "systemSettings"), sysadmin);

    verify(mockDataCiteConnector, times(1)).reloadDataCiteClient();
  }

  @Test
  void reloadNotCalledWhenNothingChanged() throws Exception {
    ApiInventorySystemSettings update = new ApiInventorySystemSettings();
    update.getOrCreate(InventorySettingType.PDINST).setUsername("");
    update.getOrCreate(InventorySettingType.IGSN).setEnabled("false");

    controller.updateInventorySettings(
        request, update, new BeanPropertyBindingResult(update, "systemSettings"), sysadmin);

    verify(mockDataCiteConnector, never()).reloadDataCiteClient();
  }
}
