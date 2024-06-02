package com.researchspace.webapp.integrations.slack;

import static org.junit.Assert.assertTrue;

import com.researchspace.core.testutil.AbstractJDependTest;
import org.junit.Test;

public class NoDependenciesOnSlackIntegrationTest extends AbstractJDependTest {

  @Test
  public void onlyBaseConfigDependOnSlackIntegration() {
    assertTrue(
        assertLessThanOrEqNDependenciesOnPackage("com.researchspace.webapp.integrations.slack", 1));
  }
}
