package com.researchspace.webapp.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ShiroSslFilterMavenAgnosticTest {

  ShiroSslFilterMavenAgnostic filter;

  @BeforeEach
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
