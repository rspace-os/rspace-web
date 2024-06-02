package com.researchspace.webapp.integrations.box;

import static org.junit.Assert.assertTrue;

import com.researchspace.core.testutil.AbstractJDependTest;
import org.junit.Test;

public class NoDependenciesOnBoxIntegration extends AbstractJDependTest {

  @Test
  public void testNoPackageDependOnBoxIntegration() {
    assertTrue(
        assertLessThanOrEqNDependenciesOnPackage("com.researchspace.webapp.integrations.box", 0));
  }
}
