package com.researchspace.testutils;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.service.LicenseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

@ProductionProfileTestConfiguration
@WithSpringContext
@EnabledIfSystemProperty(named = "licenseServer.realConnectionTests", matches = "true")
public class ProdLicenseServerRealConnectionTest {

  @Autowired
  @Qualifier("RemoteTestLicenseServiceImpl")
  private LicenseService service;

  @Test
  public void licenseServerIsActive() {
    assertTrue(service.isLicenseActive(), "Expected an active license from the license server");
  }
}
