package com.researchspace.webapp.integrations.orcid;

import static org.junit.Assert.assertTrue;

import com.researchspace.core.testutil.AbstractJDependTest;
import org.junit.Test;

public class NoDependenciesOnOrcidIntegrationTest extends AbstractJDependTest {

  @Test
  public void testNoPackageDependOnBoxIntegration() {
    assertTrue(
        assertLessThanOrEqNDependenciesOnPackage("com.researchspace.webapp.integrations.orcid", 0));
  }
}
