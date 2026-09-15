package com.researchspace.core.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class CollectionFilterTest {

  @Test
  public void testFilterOfNoopsImplmentations() {
    assertTrue(CollectionFilter.NO_FILTER.filter(new Object()));
    assertFalse(CollectionFilter.NULLFILTER.filter(new Object()));
  }
}
