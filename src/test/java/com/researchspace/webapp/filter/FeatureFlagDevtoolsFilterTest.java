package com.researchspace.webapp.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.FeatureFlagManagerImpl;
import com.researchspace.testutils.TestFactory;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class FeatureFlagDevtoolsFilterTest {
  @Mock Subject subject;
  @Mock UserManager users;

  @AfterEach
  void unbind() {
    ThreadContext.unbindSubject();
  }

  @Test
  void sysadminCanUseDevtools() throws Exception {
    User admin = TestFactory.createAnyUserWithRole("admin", Constants.SYSADMIN_ROLE);
    when(subject.isRunAs()).thenReturn(false);
    assertCapability(admin, false, true);
  }

  @Test
  void runAsSysadminCannotUseDevtools() throws Exception {
    User admin = TestFactory.createAnyUserWithRole("admin", Constants.SYSADMIN_ROLE);
    when(subject.isRunAs()).thenReturn(true);
    assertCapability(admin, false, false);
  }

  @Test
  void devModeAllowsOrdinaryUser() throws Exception {
    assertCapability(TestFactory.createAnyUser("ordinary"), true, true);
  }

  @Test
  void ordinaryUserOnProductionCannotUseDevtools() throws Exception {
    assertCapability(TestFactory.createAnyUser("ordinary"), false, false);
  }

  @Test
  void unauthenticatedSubjectCannotUseDevtoolsEvenInDevMode() throws Exception {
    ThreadContext.bind(subject);
    MockHttpServletRequest request = new MockHttpServletRequest();
    filter(true)
        .doFilter(
            request,
            new MockHttpServletResponse(),
            (req, res) -> assertEquals(false, req.getAttribute("canUseDevtools")));
    verifyNoInteractions(users);
  }

  @Test
  void publicDocumentGuestCannotUseDevtoolsEvenInDevMode() throws Exception {
    assertCapability(TestFactory.createAnyUser(RecordGroupSharing.ANONYMOUS_USER), true, false);
  }

  private FeatureFlagDevtoolsFilter filter(boolean dev) {
    return new FeatureFlagDevtoolsFilter(
        users,
        new FeatureFlagManagerImpl(
            null, null, null, null, Boolean.toString(dev), "false", dev ? "dev" : "prod"));
  }

  private void assertCapability(User user, boolean dev, boolean expected) throws Exception {
    ThreadContext.bind(subject);
    when(subject.isAuthenticated()).thenReturn(true);
    when(subject.getPrincipal()).thenReturn(user.getUsername());
    when(users.getUserByUsername(user.getUsername())).thenReturn(user);
    filter(dev)
        .doFilter(
            new MockHttpServletRequest(),
            new MockHttpServletResponse(),
            (req, res) -> assertEquals(expected, req.getAttribute("canUseDevtools")));
  }
}
