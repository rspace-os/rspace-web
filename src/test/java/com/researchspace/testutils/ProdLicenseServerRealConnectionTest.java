package com.researchspace.testutils;

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
    service.isLicenseActive();
  }
}
