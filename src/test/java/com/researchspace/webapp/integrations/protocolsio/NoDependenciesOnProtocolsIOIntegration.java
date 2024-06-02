package com.researchspace.webapp.integrations.protocolsio;

import static org.junit.Assert.assertTrue;

import com.researchspace.core.testutil.AbstractJDependTest;
import org.junit.Test;

public class NoDependenciesOnProtocolsIOIntegration extends AbstractJDependTest {

  @Test
  public void testNoPackageDependOnBoxIntegration() {
    assertTrue(
        assertLessThanOrEqNDependenciesOnPackage(
            "com.researchspace.webapp.integrations.protocolsio", 0));
  }
}
