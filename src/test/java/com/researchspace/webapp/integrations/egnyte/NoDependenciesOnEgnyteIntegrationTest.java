package com.researchspace.webapp.integrations.egnyte;

import static org.junit.Assert.assertTrue;

import com.researchspace.core.testutil.AbstractJDependTest;
import org.junit.Test;

public class NoDependenciesOnEgnyteIntegrationTest extends AbstractJDependTest {

  @Test
  public void testOnlyConfigPackageDependOnEgnyteIntegration() {
    assertTrue(
        assertLessThanOrEqNDependenciesOnPackage(
            "com.researchspace.webapp.integrations.egnyte", 1));
  }
}
