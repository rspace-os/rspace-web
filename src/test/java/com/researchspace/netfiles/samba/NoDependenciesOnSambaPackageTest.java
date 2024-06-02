package com.researchspace.netfiles.samba;

import static org.junit.Assert.assertTrue;

import com.researchspace.core.testutil.AbstractJDependTest;
import org.junit.Test;

public class NoDependenciesOnSambaPackageTest extends AbstractJDependTest {

  @Test
  public void testNoPackageDependOnSamba() {
    assertTrue(assertLessThanOrEqNDependenciesOnPackage("com.researchspace.netfiles.samba", 1));
  }
}
