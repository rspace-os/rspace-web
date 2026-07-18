package com.researchspace.webapp.controller;

import static com.researchspace.testutils.RSpaceTestUtils.assertAuthExceptionThrown;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.licenseserver.model.License;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.LicenseService;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.EmailContentGenerator;
import com.researchspace.testutils.TestFactory;
import java.util.List;
import org.junit.After;
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

  @InjectMocks SysAdminSupportController controller;
  User sysadmin;

  @Before
  public void setUp() throws Exception {
    sysadmin = TestFactory.createAnyUser("sysadmin");
    sysadmin.addRole(Role.SYSTEM_ROLE);
    Mockito.when(usrMgr.getAuthenticatedUserInSession()).thenReturn(sysadmin);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testOnlySysAdminCanReloadLicense() throws Exception {
    demoteSysadmin();
    assertAuthExceptionThrown(() -> controller.forceRefreshLicense());
  }

  private void demoteSysadmin() {
    sysadmin.addRole(Role.USER_ROLE);
    sysadmin.removeRole(Role.SYSTEM_ROLE);
  }

  @Test
  public void testOnlySysAdminCanViewLicense() throws Exception {
    demoteSysadmin();
    assertAuthExceptionThrown(() -> controller.getLicense());
  }

  @Test
  public void testGetLicense() {
    License license = new License();
    license.setId(23L);
    Mockito.when(licenseService.getLicense()).thenReturn(license);
    AjaxReturnObject<License> returned = controller.getLicense();
    assertEquals(23, returned.getData().getId().intValue());
  }

  @Test
  public void testReloadLicense() {
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
        .generatePlainTextAndHtmlContent(
            Mockito.eq("system.support.serverlogs.supportEmailTitle"),
            Mockito.any(),
            Mockito.eq("supportLogFiles.vm"),
            Mockito.argThat(
                model ->
                    List.of("Map&lt;String, Object&gt; failed").equals(model.get("logLines"))));
  }
}
