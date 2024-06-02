package com.researchspace.webapp.controller;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.license.InactiveLicenseTestService;
import com.researchspace.licensews.LicenseExpiredException;
import com.researchspace.service.impl.license.NoCheckLicenseService;
import com.researchspace.testutils.SpringTransactionalTest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;

public class ServiceLoggerAspectTest extends SpringTransactionalTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Mock JoinPoint joinpoint;
  @Mock Signature signature;
  @Autowired private ServiceLoggerAspct aspect;

  @Before
  public void setUp() throws Exception {}

  @After
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

  @Test(expected = LicenseExpiredException.class)
  public void testInvalidLicenseThrowsException() {
    aspect.setLicenseService(new InactiveLicenseTestService());
    when(joinpoint.getSignature()).thenReturn(signature);
    aspect.assertValidLicense(joinpoint);
  }
}
