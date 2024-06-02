package com.researchspace.webapp.filter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class ShiroSslFilterMavenAgnosticTest {

  ShiroSslFilterMavenAgnostic filter;

  @Before
  public void setUp() {
    filter = new ShiroSslFilterMavenAgnostic();
  }

  @Test
  public void testGetSetEnabledOverride() {
    filter.setEnabledOverride("false");
    assertFalse(filter.isEnabled());

    filter.setEnabledOverride("true");
    assertTrue(filter.isEnabled());

    filter.setEnabledOverride("${unresolvedVar}");
    assertFalse(filter.isEnabled());
  }
}
