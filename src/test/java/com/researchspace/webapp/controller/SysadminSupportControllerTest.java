package com.researchspace.webapp.controller;

import static com.researchspace.testutils.RSpaceTestUtils.assertAuthExceptionThrown;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.admin.service.SysAdminManager;
import com.researchspace.licenseserver.model.License;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.EmailContent;
import com.researchspace.service.LicenseService;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.EmailContentGenerator;
import com.researchspace.testutils.TestFactory;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class SysadminSupportControllerTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock LicenseService licenseService;
  @Mock UserManager usrMgr;
  @Mock MessageSourceUtils messages;
  @Mock EmailContentGenerator emailContentGenerator;
  @Mock IPropertyHolder properties;
  @Mock SysAdminManager sysMgr;
  @Mock EmailBroadcast emailSender;

  @InjectMocks SysAdminSupportController controller;
  User sysadmin;

  @Before
  public void setUp() {
    sysadmin = TestFactory.createAnyUser("sysadmin");
    sysadmin.addRole(Role.SYSTEM_ROLE);
  }

  @Test
  public void testOnlySysAdminCanReloadLicense() throws Exception {
    authenticateSysadmin();
    demoteSysadmin();
    assertAuthExceptionThrown(() -> controller.forceRefreshLicense());
  }

  private void demoteSysadmin() {
    sysadmin.addRole(Role.USER_ROLE);
    sysadmin.removeRole(Role.SYSTEM_ROLE);
  }

  @Test
  public void testOnlySysAdminCanViewLicense() throws Exception {
    authenticateSysadmin();
    demoteSysadmin();
    assertAuthExceptionThrown(() -> controller.getLicense());
  }

  @Test
  public void testGetLicense() {
    authenticateSysadmin();
    License license = new License();
    license.setId(23L);
    Mockito.when(licenseService.getLicense()).thenReturn(license);
    AjaxReturnObject<License> returned = controller.getLicense();
    assertEquals(23, returned.getData().getId().intValue());
  }

  @Test
  public void testReloadLicense() {
    authenticateSysadmin();
    Mockito.when(licenseService.forceRefreshLicense()).thenReturn(true, false);
    AjaxReturnObject<Boolean> rc = controller.forceRefreshLicense();
    assertTrue(rc.getData());
    // false
    rc = controller.forceRefreshLicense();
    assertFalse(rc.getData());
  }

  @Test
  public void escapesLogLinesBeforeRenderingEmail() {
    controller.generateEmailContent(sysadmin, List.of("Map<String, Object> failed"), null);

    Mockito.verify(emailContentGenerator)
        .render(
            Mockito.eq("system.support.serverlogs.supportEmailTitle"),
            Mockito.any(),
            Mockito.eq("supportLogFiles.vm"),
            Mockito.argThat(
                model ->
                    List.of("Map&lt;String, Object&gt; failed").equals(model.get("logLines"))));
  }

  @Test
  public void mailLogsAreSentToConfiguredSupportAddress() throws Exception {
    authenticateSysadmin();
    Mockito.when(properties.getRSpaceSupportEmail()).thenReturn("ops@example.com");
    Mockito.when(sysMgr.getLastNLinesLogs(Mockito.anyInt())).thenReturn(List.of("a log line"));
    Mockito.when(
            emailContentGenerator.render(
                Mockito.anyString(), Mockito.any(), Mockito.anyString(), Mockito.any()))
        .thenReturn(new EmailContent("subject", "<p>log</p>", "log"));

    controller.mailServerErrorLog("XXXX", 5);

    Mockito.verify(emailSender)
        .sendEmail(
            Mockito.any(EmailContent.class),
            Mockito.eq(List.of("ops@example.com")),
            Mockito.isNull());
  }

  private void authenticateSysadmin() {
    Mockito.when(usrMgr.getAuthenticatedUserInSession()).thenReturn(sysadmin);
  }
}
