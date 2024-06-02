package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.IntegrationsHandlerImpl;
import java.security.Principal;
import java.util.Locale;
import java.util.Map;
import org.apache.struts.mock.MockPrincipal;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.context.support.StaticMessageSource;

public class IntegrationControllerTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @InjectMocks IntegrationController integrationCtrller;
  StaticMessageSource msgSource;
  @Mock IntegrationsHandler handler;
  @Mock UserManager userMgr;
  User subject;
  Principal principal;

  @Before
  public void setup() {
    msgSource = new StaticMessageSource();
    integrationCtrller.setMessageSource(new MessageSourceUtils(msgSource));
    subject = TestFactory.createAnyUser("any");
    principal = new MockPrincipal(subject.getUsername());
  }

  @Test
  public void getAllIntegrations() {
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(subject);
    when(handler.getIntegration(Mockito.eq(subject), Mockito.anyString()))
        .thenReturn(new IntegrationInfo());
    // +15 for all non-boolean integrations
    int integrationsNumber = IntegrationsHandlerImpl.booleanIntegrationPrefs.size() + 19;
    AjaxReturnObject<Map<String, IntegrationInfo>> infos =
        integrationCtrller.getAllIntegrationsInfo(new MockPrincipal(subject.getUsername()));
    assertEquals(integrationsNumber, infos.getData().size());
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
    msgSource.addMessage("errors.invalid", Locale.getDefault(), "invalid");
    info = integrationCtrller.getIntegrationInfo(invalid);
    assertNull(info.getData());
    assertNotNull(info.getErrorMsg());
    assertTrue(info.getErrorMsg().getErrorMessages().get(0).contains("invalid"));
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
