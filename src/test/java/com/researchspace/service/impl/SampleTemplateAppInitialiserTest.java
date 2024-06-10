package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.testutil.StringAppenderForTestLogging;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.SystemPropertyPermissionManager;
import java.util.concurrent.Callable;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.subject.Subject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class SampleTemplateAppInitialiserTest {

  public @Rule MockitoRule mockito = MockitoJUnit.rule();
  StringAppenderForTestLogging strgLogger;
  private @Mock SampleTemplateInitializer sampleTemplateCreator;
  private @Mock UserDao userdao;
  private @Mock SystemPropertyPermissionManager sysPropertyPermissions;
  private @Mock Subject subject;

  static class SampleTemplateAppInitialiserTSS extends SampleTemplateAppInitialiser {
    Subject subject = Mockito.mock(Subject.class);

    Subject getSubject() {
      return subject;
    }
  }

  @InjectMocks private SampleTemplateAppInitialiserTSS sampleTemplateAppInitialiser;

  User user = TestFactory.createAnyUser(Constants.SYSADMIN_UNAME);

  @Before
  public void setUp() {
    strgLogger = CoreTestUtils.configureStringLogger(AbstractAppInitializor.log);
  }

  @Test
  public void onAppStartupSuccess() {
    mockGetUserByUsername();
    when(subject.execute(any(Callable.class))).thenReturn(Boolean.TRUE);
    sampleTemplateAppInitialiser.onAppStartup(null);
    verifyLoginAndLogout();
    assertTrue(strgLogger.logContents.isEmpty());
  }

  @Test
  public void onAppStartupFailure() {
    mockGetUserByUsername();
    when(subject.execute(any(Callable.class))).thenReturn(Boolean.FALSE);
    sampleTemplateAppInitialiser.onAppStartup(null);
    verifyLoginAndLogout();
    assertTrue(strgLogger.logContents.contains("Fatal error"));
  }

  private void verifyLoginAndLogout() {
    verify(subject).login(any(AuthenticationToken.class));
    verify(subject, Mockito.atLeastOnce()).logout();
  }

  private void mockGetUserByUsername() {
    when(userdao.getUserByUsername(Constants.SYSADMIN_UNAME)).thenReturn(user);
  }
}
