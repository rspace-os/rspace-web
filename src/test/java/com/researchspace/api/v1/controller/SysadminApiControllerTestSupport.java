package com.researchspace.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.auth.WhiteListIPChecker;
import com.researchspace.model.User;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Shared Spring transactional test base for tests that exercise {@link SysadminApiController}.
 *
 * <p>Swaps the autowired {@link WhiteListIPChecker} on the controller with a permissive mock so
 * tests don't need to be invoked from a whitelisted IP. The original bean is restored in {@link
 * #tearDown()}.
 *
 * <p>Subclasses get a {@link MockHttpServletRequest} ({@link #request}) and a logged-in sysadmin
 * {@link User} ({@link #sysadmin}) ready to pass into controller calls.
 */
public abstract class SysadminApiControllerTestSupport extends SpringTransactionalTest {

  @Autowired protected SysadminApiController sysadminApiController;
  @Autowired private WhiteListIPChecker originalIpChecker;

  protected User sysadmin;
  protected MockHttpServletRequest request;

  @Before
  public void setUpSysadminController() throws Exception {
    super.setUp();
    request = new MockHttpServletRequest();
    sysadmin = logoutAndLoginAsSysAdmin();
    WhiteListIPChecker mockIpChecker = mock(WhiteListIPChecker.class);
    when(mockIpChecker.isRequestWhitelisted(any(), any(User.class), any(Logger.class)))
        .thenReturn(true);
    ReflectionTestUtils.setField(sysadminApiController, "ipWhiteListChecker", mockIpChecker);
  }

  @After
  public void tearDownSysadminController() throws Exception {
    ReflectionTestUtils.setField(sysadminApiController, "ipWhiteListChecker", originalIpChecker);
    super.tearDown();
  }
}
