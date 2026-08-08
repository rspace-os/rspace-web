package com.researchspace.api.v2.auth;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import com.researchspace.session.SessionAttributeUtils;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiV2BrowserSessionAuthenticatorTest {

  private final UserManager userManager = mock(UserManager.class);
  private final ApiV2BrowserSessionAuthenticator authenticator =
      new ApiV2BrowserSessionAuthenticator(userManager);

  @AfterEach
  void clearSubject() {
    ThreadContext.remove();
  }

  @Test
  void doesNotCreateAMissingSession() {
    MockHttpServletRequest request = new MockHttpServletRequest();

    assertTrue(authenticator.authenticateIfPresent(request).isEmpty());
    assertTrue(request.getSession(false) == null);
    verifyNoInteractions(userManager);
  }

  @Test
  void returnsTheUserFromAnAuthenticatedExistingSession() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.getSession(true);
    Subject subject = mock(Subject.class);
    Session session = mock(Session.class);
    User user = mock(User.class);
    when(subject.isAuthenticated()).thenReturn(true);
    when(subject.getSession(false)).thenReturn(session);
    when(session.getAttribute(SessionAttributeUtils.USER)).thenReturn(user);
    ThreadContext.bind(subject);

    assertSame(user, authenticator.authenticateIfPresent(request).orElseThrow());
    verifyNoInteractions(userManager);
  }
}
