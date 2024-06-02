package com.researchspace.webapp.integrations.github;

import static org.junit.Assert.assertTrue;

import com.researchspace.core.testutil.AbstractJDependTest;
import org.junit.Test;

public class NoDependenciesOnGitHubIntegration extends AbstractJDependTest {

  @Test
  public void testNoPackageDependOnGitHubIntegration() {
    assertTrue(
        assertLessThanOrEqNDependenciesOnPackage(
            "com.researchspace.webapp.integrations.github", 0));
  }
}
