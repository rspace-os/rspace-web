package com.researchspace.analytics.service.impl;

import static org.junit.Assert.assertTrue;

import com.researchspace.core.testutil.AbstractJDependTest;
import org.junit.BeforeClass;
import org.junit.Test;

public class NoDependenciesOnAnalyticsImpl extends AbstractJDependTest {

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {}

  @Test
  public void testNoPackageDependOnAnayticsImplementation() {
    assertTrue(
        assertLessThanOrEqNDependenciesOnPackage("com.researchspace.analytics.service.impl", 0));
  }
}
