package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.BaseManagerTestCaseBase.MockPrincipal;
import com.researchspace.testutils.TestFactory;
import java.security.Principal;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class IntegrationControllerTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @InjectMocks IntegrationController integrationCtrller;
  MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());
  @Mock IntegrationsHandler handler;
  @Mock UserManager userMgr;
  User subject;
  Principal principal;

  private final int INTEGRATIONS_AMOUNT = 29;

  @Before
  public void setup() {
    integrationCtrller.setMessageSource(messages);
    subject = TestFactory.createAnyUser("any");
    principal = new MockPrincipal(subject.getUsername());
  }

  @Test
  public void getAllIntegrations() {
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(subject);
    when(handler.getIntegration(Mockito.eq(subject), Mockito.anyString()))
        .thenReturn(new IntegrationInfo());
    AjaxReturnObject<Map<String, IntegrationInfo>> infos =
        integrationCtrller.getAllIntegrationsInfo(new MockPrincipal(subject.getUsername()));
    assertEquals(INTEGRATIONS_AMOUNT, infos.getData().size());
  }

  @Test
  public void testGetIntegrationInfo() {
    String valid = Preference.BOX.name();

    when(handler.isValidIntegration(valid)).thenReturn(true);
    when(handler.getIntegration(subject, valid)).thenReturn(new IntegrationInfo());
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(subject);
    AjaxReturnObject<IntegrationInfo> info = integrationCtrller.getIntegrationInfo(valid);
    assertNotNull(info.getData());
    assertNull(info.getErrorMsg());

    // now do when false
    String invalid = Preference.BOX.name() + "xxx";
    when(handler.isValidIntegration(invalid)).thenReturn(false);
    info = integrationCtrller.getIntegrationInfo(invalid);
    assertNull(info.getData());
    assertNotNull(info.getErrorMsg());
    assertEquals(
        messages.getMessage("errors.invalid", new Object[] {invalid}),
        info.getErrorMsg().getErrorMessages().get(0));
  }

  @Test
  public void testGetAllIntegrationsInfo() {
    when(handler.getIntegration(Mockito.eq(subject), Mockito.anyString()))
        .thenReturn(new IntegrationInfo());
    AjaxReturnObject<Map<String, IntegrationInfo>> infos =
        integrationCtrller.getAllIntegrationsInfo(principal);
    assertNotNull(infos.getData());
  }

  @Test
  public void testEnableIntegration() {
    IntegrationInfo info = new IntegrationInfo();
    info.setAvailable(false);
    info.setEnabled(true);
  }
}
