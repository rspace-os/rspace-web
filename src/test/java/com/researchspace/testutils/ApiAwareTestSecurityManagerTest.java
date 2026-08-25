package com.researchspace.testutils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.auth.ApiAwareWebSecurityManager;
import java.util.List;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.support.DelegatingSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the Shiro 3 session-fixation behaviour for the non-web subjects used by test wiring: a
 * stateless API login must leave the thread-bound session untouched, otherwise MockMvc apiKey
 * requests rotate the test thread's session id and strand session-keyed state such as record edit
 * locks.
 */
public class ApiAwareTestSecurityManagerTest {

  private ApiAwareTestSecurityManager securityManager;
  private Session session;

  @BeforeEach
  public void setUp() {
    securityManager = new ApiAwareTestSecurityManager();
    session = mock(Session.class);
  }

  private DelegatingSubject subjectWithSession() {
    return new DelegatingSubject(
        new SimplePrincipalCollection("user1a", "realm"), true, null, session, securityManager);
  }

  @Test
  public void statelessApiLoginLeavesSessionUntouched() {
    ApiAwareWebSecurityManager.doStatelessLogin(
        () -> securityManager.beforeSuccessfulLogin(subjectWithSession()));

    verifyNoInteractions(session);
  }

  @Test
  public void ordinaryLoginStillRotatesSession() {
    when(session.getAttributeKeys()).thenReturn(List.of());

    securityManager.beforeSuccessfulLogin(subjectWithSession());

    verify(session).stop();
  }
}
