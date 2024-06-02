package com.researchspace.webapp.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.license.InactiveLicenseTestService;
import com.researchspace.licensews.LicenseExceededException;
import com.researchspace.model.User;
import com.researchspace.model.events.AccountEventType;
import com.researchspace.properties.PropertyHolder;
import com.researchspace.service.UserEnablementUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

public class SysAdminControllerSpringTest extends SpringTransactionalTest {

  @Autowired private SysAdminController sysCtrller;

  @Autowired protected PropertyHolder props;

  @Autowired protected UserEnablementUtils userEnablementUtils;

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void testDisableUserAccount() throws Exception {
    final User user = createAndSaveUserIfNotExists(getRandomAlphabeticString(null));
    assertTrue(user.isEnabled());
    assertEquals(0, userMgr.getAccountEventsForUser(user).size());
    logoutAndLoginAsSysAdmin();
    final MockPrincipal principal = new MockPrincipal(SYS_ADMIN_UNAME);
    final Model model = new ExtendedModelMap();
    sysCtrller.setUserAccountEnablement(user.getId(), false);

    User updated = userMgr.get(user.getId());
    assertFalse(updated.isEnabled());
    assertTrue(
        userMgr.getAccountEventsForUser(user).stream()
            .filter(event -> AccountEventType.DISABLED.equals(event.getAccountEventType()))
            .findAny()
            .isPresent());
    final Model model2 = new ExtendedModelMap();

    // now we'll mock the license being exceeded - should get exception thrown
    userEnablementUtils.setLicenseService(new InactiveLicenseTestService());

    // also ensure the message contains custom message. rspac-2118
    String defaultMessage = props.getLicenseExceededCustomMessage();
    String customMessage = "hello-from-custom-message";
    props.setLicenseExceededCustomMessage(customMessage);
    try {
      Matcher<String> matcher = containsString(customMessage);
      CoreTestUtils.assertExceptionThrown(
          () -> sysCtrller.setUserAccountEnablement(user.getId(), true),
          LicenseExceededException.class,
          matcher);
    } finally {
      sysCtrller.setLicenseService(licenseService);
      props.setLicenseExceededCustomMessage(defaultMessage);
    }
    // user should still be blocked.
    updated = userMgr.get(user.getId());
    assertFalse(updated.isEnabled());
  }

  @Test
  public void deleteUserMustBeSysadmin() throws Exception {
    User admin = createAndSaveAdminUser();
    User toremove = createAndSaveRandomUser();

    logoutAndLoginAs(admin);
    assertAuthorisationExceptionThrown(() -> sysCtrller.removeUserAccount(toremove.getId()));

    logoutAndLoginAsSysAdmin();
    ResponseEntity<Object> res = sysCtrller.removeUserAccount(toremove.getId());
    assertTrue(res.getStatusCode().is2xxSuccessful());
  }
}
