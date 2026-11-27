package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.service.LicenseService;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

@ExtendWith(MockitoExtension.class)
class InventoryLicenseInterceptorTest {

  @Mock private LicenseService licenseService;

  private final InventoryLicenseInterceptor interceptor = new InventoryLicenseInterceptor();

  @BeforeEach
  void setUp() {
    interceptor.setLicenseService(licenseService);
  }

  private static HandlerMethod handler(Class<?> controller, String methodName) {
    Method method =
        Arrays.stream(controller.getMethods())
            .filter(m -> m.getName().equals(methodName) && !m.isBridge())
            .findFirst()
            .orElseThrow();
    return new HandlerMethod(mock(controller), method);
  }

  private boolean preHandle(String httpMethod, Object handler) {
    return interceptor.preHandle(
        new MockHttpServletRequest(httpMethod, "/api/inventory/v1/any"),
        new MockHttpServletResponse(),
        handler);
  }

  @Test
  void expiredLicenseRejectsWrites() {
    when(licenseService.isLicenseActive()).thenReturn(false);
    Object[][] writes = {
      {"POST", handler(SamplesApiController.class, "createNewSample")},
      {"PUT", handler(SamplesApiController.class, "updateSample")},
      {"DELETE", handler(SamplesApiController.class, "deleteSample")},
      {"POST", handler(InventoryEditLocksController.class, "lockItemForEdit")},
      {"PUT", handler(SystemSettingsApiController.class, "updateInventorySettings")},
      {"POST", null},
    };
    for (Object[] write : writes) {
      assertThrows(InventoryReadOnlyException.class, () -> preHandle((String) write[0], write[1]));
    }
  }

  @Test
  void readsExportAndLockReleaseNeverCheckTheLicense() {
    assertTrue(preHandle("GET", handler(SamplesApiController.class, "getSampleById")));
    assertTrue(preHandle("HEAD", null));
    assertTrue(preHandle("OPTIONS", null));
    assertTrue(preHandle("POST", handler(InventoryExportApiController.class, "exportToCsv")));
    assertTrue(
        preHandle("DELETE", handler(InventoryEditLocksController.class, "unlockItemAfterEdit")));
    verifyNoInteractions(licenseService);
  }

  @Test
  void activeLicenseAllowsWrites() {
    when(licenseService.isLicenseActive()).thenReturn(true);
    assertTrue(preHandle("POST", handler(SamplesApiController.class, "createNewSample")));
  }
}
