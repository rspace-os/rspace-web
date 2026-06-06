package com.researchspace.webapp.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.testutils.TestFactory;
import java.util.Date;
import org.junit.Test;
import org.springframework.http.HttpHeaders;

public class BaseControllerTest {
  // concrete class
  static class BaseControllerTSS extends BaseController {}

  BaseController bc = new BaseControllerTSS();

  @Test
  public void testSetCacheTimeInBrowser() {
    HttpHeaders hdrs = new HttpHeaders();
    Date date = new Date();
    bc.setCacheTimeInBrowser(1000, date, hdrs);
    assertTrue(hdrs.getCacheControl().contains("1000"));
    assertTrue(date.getTime() - hdrs.getLastModified() < 1000); // rounds to
    // second
    bc.setCacheTimeInBrowser(1000, null, hdrs); // null is ok
  }

  @Test
  public void isValidSettingsKey() {
    assertTrue(BaseController.isValidSettingsKey("abc1ADE"));
    assertFalse(BaseController.isValidSettingsKey("javascript:alert(1)"));
  }

  @Test
  public void isDMPEnabledChecksDmpAssistant() {
    BaseControllerTSS controller = new BaseControllerTSS();
    IntegrationsHandler handler = mock(IntegrationsHandler.class);
    controller.integrationsHandler = handler;
    User user = TestFactory.createAnyUser("any");

    // no DMP integration configured at all
    assertFalse(controller.isDMPEnabled(user));

    // DMP Assistant enabled and available is sufficient on its own
    IntegrationInfo dmpAssistant = mock(IntegrationInfo.class);
    when(dmpAssistant.isEnabled()).thenReturn(true);
    when(dmpAssistant.isAvailable()).thenReturn(true);
    when(handler.getIntegration(user, IntegrationsHandler.DMPASSISTANT_APP_NAME))
        .thenReturn(dmpAssistant);
    assertTrue(controller.isDMPEnabled(user));

    // enabled but sysadmin has made it unavailable
    when(dmpAssistant.isAvailable()).thenReturn(false);
    assertFalse(controller.isDMPEnabled(user));
  }
}
