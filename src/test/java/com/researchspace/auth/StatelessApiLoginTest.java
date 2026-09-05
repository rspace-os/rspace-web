package com.researchspace.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StatelessApiLoginTest {

  private Subject subject;
  private AuthenticationToken token;

  @BeforeEach
  public void setUp() {
    subject = mock(Subject.class);
    token = mock(AuthenticationToken.class);
  }

  @Test
  public void markSpansOnlyTheLoginCall() {
    doAnswer(
            invocation -> {
              assertTrue(StatelessApiLogin.isInProgress());
              return null;
            })
        .when(subject)
        .login(token);

    assertFalse(StatelessApiLogin.isInProgress());
    StatelessApiLogin.login(subject, token);

    verify(subject).login(token);
    assertFalse(StatelessApiLogin.isInProgress());
  }

  @Test
  public void nestedLoginKeepsOuterMarkUntilOuterCallReturns() {
    Subject innerSubject = mock(Subject.class);
    doAnswer(
            invocation -> {
              StatelessApiLogin.login(innerSubject, token);
              assertTrue(StatelessApiLogin.isInProgress());
              return null;
            })
        .when(subject)
        .login(token);

    StatelessApiLogin.login(subject, token);

    verify(innerSubject).login(token);
    assertFalse(StatelessApiLogin.isInProgress());
  }

  @Test
  public void markIsClearedWhenTheLoginThrows() {
    doThrow(new AuthenticationException("bad api key")).when(subject).login(token);

    assertThrows(AuthenticationException.class, () -> StatelessApiLogin.login(subject, token));

    assertFalse(StatelessApiLogin.isInProgress());
  }
}
