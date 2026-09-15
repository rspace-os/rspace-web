package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.testutil.StringAppenderForTestLogging;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.testutils.TestFactory;
import java.util.concurrent.Callable;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SampleTemplateAppInitialiserTest {
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

  @BeforeEach
  public void setUp() {
    strgLogger = CoreTestUtils.configureStringLogger(AbstractAppInitializor.log);
  }

  @Test
  public void onAppStartupSuccess() {
    mockGetUserByUsername();
    when(subject.execute(any(Callable.class))).thenReturn(Boolean.TRUE);
    sampleTemplateAppInitialiser.onAppStartup(null);
    verifyLoginAndLogout();
    assertFalse(strgLogger.logContents.contains("error"));
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
