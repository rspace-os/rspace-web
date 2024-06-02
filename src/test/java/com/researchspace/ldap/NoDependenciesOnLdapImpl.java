package com.researchspace.ldap;

import static org.junit.Assert.assertTrue;

import com.researchspace.core.testutil.AbstractJDependTest;
import org.junit.Test;

public class NoDependenciesOnLdapImpl extends AbstractJDependTest {

  @Test
  public void testNoPackageDependOnLadpImpl() {
    assertTrue(assertLessThanOrEqNDependenciesOnPackage("com.researchspace.ldap.impl", 0));
  }
}
