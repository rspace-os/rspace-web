package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.license.InactiveLicenseTestService;
import com.researchspace.licensews.LicenseExpiredException;
import com.researchspace.service.impl.license.NoCheckLicenseService;
import com.researchspace.testutils.SpringTransactionalTest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

@ExtendWith(MockitoExtension.class)
public class ServiceLoggerAspectTest extends SpringTransactionalTest {
  @Mock JoinPoint joinpoint;
  @Mock Signature signature;
  @Autowired private ServiceLoggerAspct aspect;

  @AfterEach
  public void tearDown() throws Exception {
    // return to default after test ends
    aspect.setLicenseService(new NoCheckLicenseService());
  }

  @Test
  public void testGetTRuncatedArgumentDoesnotThrowNPE() {
    ServiceLoggerAspct sla = new ServiceLoggerAspct();
    sla.getTruncatedArgumentString(5, null);
    sla.getTruncatedArgumentString(5, new Object[] {null});
    assertTrue(
        sla.getTruncatedArgumentString(5, new Object[] {"LongerThanLimit"}).length()
            < "LongerThanLimit".length());
  }

  @Test
  public void testInvalidLicenseThrowsException() {
    aspect.setLicenseService(new InactiveLicenseTestService());
    when(joinpoint.getSignature()).thenReturn(signature);
    assertThrows(LicenseExpiredException.class, () -> aspect.assertValidLicense(joinpoint));
  }
}
