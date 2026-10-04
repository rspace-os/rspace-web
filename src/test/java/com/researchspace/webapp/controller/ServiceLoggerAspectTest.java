package com.researchspace.webapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.license.InactiveLicenseTestService;
import com.researchspace.licensews.LicenseExpiredException;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.impl.license.NoCheckLicenseService;
import java.lang.reflect.Method;
import java.util.Map;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ServiceLoggerAspectTest {
  @Mock JoinPoint joinpoint;
  @Mock Signature signature;
  private ServiceLoggerAspct aspect;

  @BeforeEach
  public void setUp() {
    aspect = new ServiceLoggerAspct();
    aspect.setLicenseService(new NoCheckLicenseService());
  }

  @Test
  public void testGetTRuncatedArgumentDoesnotThrowNPE() {
    ServiceLoggerAspct sla = new ServiceLoggerAspct();
    sla.getTruncatedArgumentString(5, null);
    sla.getTruncatedArgumentString(5, new Object[] {null});
    assertThat(sla.getTruncatedArgumentString(5, new Object[] {"LongerThanLimit"}).length())
        .isLessThan("LongerThanLimit".length());
  }

  @Test
  public void hidesCredentialsWhenLoggingNewAppConfigConnections() throws Exception {
    String accessToken = "synthetic-access-token";
    String secret = "synthetic-webhook-secret";
    Method method =
        UserConnectionManager.class.getMethod(
            "saveWithNewAppConfigElementSet",
            Map.class,
            String.class,
            String.class,
            String.class,
            boolean.class,
            com.researchspace.model.User.class);
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    MethodSignature methodSignature = mock(MethodSignature.class);
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(methodSignature.getDeclaringTypeName()).thenReturn(UserConnectionManager.class.getName());

    String logMessage = aspect.methodInfo(joinPoint, method.getName());
    verify(joinPoint, never()).getArgs();

    assertThat(logMessage)
        .contains("(args hidden)")
        .doesNotContain(accessToken)
        .doesNotContain(secret);
  }

  @Test
  public void testInvalidLicenseThrowsException() {
    aspect.setLicenseService(new InactiveLicenseTestService());
    when(joinpoint.getSignature()).thenReturn(signature);
    assertThrows(LicenseExpiredException.class, () -> aspect.assertValidLicense(joinpoint));
  }
}
