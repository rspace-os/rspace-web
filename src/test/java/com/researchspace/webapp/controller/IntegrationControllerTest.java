package com.researchspace.webapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

@ExtendWith(MockitoExtension.class)
public class IntegrationControllerTest {
  @InjectMocks IntegrationController integrationCtrller;
  MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());
  @Mock IntegrationsHandler handler;
  @Mock UserManager userMgr;
  User subject;
  Principal principal;

  private final int INTEGRATIONS_AMOUNT = 28;

  @BeforeEach
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
    assertThat(infos.getData()).hasSize(INTEGRATIONS_AMOUNT);
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
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(subject);
    when(handler.getIntegration(Mockito.eq(subject), Mockito.anyString()))
        .thenReturn(new IntegrationInfo());
    AjaxReturnObject<Map<String, IntegrationInfo>> infos =
        integrationCtrller.getAllIntegrationsInfo(principal);
    assertNotNull(infos.getData());
  }

  @Test
  public void egnyteDomainMustBeWebUrlButIsNotFetched() {
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(subject);
    for (String rejected :
        new String[] {
          "javascript:alert(1)//",
          "file:///etc/hosts",
          "ftp://acme.egnyte.com",
          "acme.egnyte.com",
          "https://"
        }) {
      AjaxReturnObject<IntegrationInfo> rc =
          integrationCtrller.updateIntegration(egnyteInfo(rejected), new MockHttpSession());
      assertNotNull(rc.getErrorMsg(), rejected + " should be rejected");
      assertEquals(
          messages.getMessage("apps.egnyte.errors.invalidDomainUrl"),
          rc.getErrorMsg().getErrorMessages().get(0));
    }
    verify(handler, never()).updateIntegrationInfo(Mockito.any(), Mockito.any());

    for (String accepted :
        new String[] {
          "https://acme.egnyte.com",
          "https://files.acme.com",
          "https://acme.egnyte.com/",
          "http://localhost:9099",
          "http://acme.egnyte.com",
          ""
        }) {
      integrationCtrller.updateIntegration(egnyteInfo(accepted), new MockHttpSession());
    }
    verify(handler, times(6)).updateIntegrationInfo(Mockito.eq(subject), Mockito.any());
  }

  @Test
  public void egnyteDomainIsCheckedWhenSavedAsAppOptions() {
    for (String appName : new String[] {IntegrationsHandler.EGNYTE_APP_NAME, "SLACK"}) {
      AjaxReturnObject<IntegrationInfo> rc =
          integrationCtrller.saveAppOptions(
              null,
              appName,
              Map.of(IntegrationsHandler.EGNYTE_DOMAIN_SETTING, "javascript:alert(1)//"));
      assertEquals(
          messages.getMessage("apps.egnyte.errors.invalidDomainUrl"),
          rc.getErrorMsg().getErrorMessages().get(0));
    }
    verify(handler, never())
        .saveAppOptions(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.any());
  }

  private IntegrationInfo egnyteInfo(String domainUrl) {
    IntegrationInfo info = new IntegrationInfo();
    info.setName(IntegrationsHandler.EGNYTE_APP_NAME);
    Map<String, Object> options = new HashMap<>();
    options.put(IntegrationsHandler.EGNYTE_DOMAIN_SETTING, domainUrl);
    info.setOptions(options);
    return info;
  }

  @Test
  public void testEnableIntegration() {
    IntegrationInfo info = new IntegrationInfo();
    info.setAvailable(false);
    info.setEnabled(true);
  }
}
