package com.researchspace.model.dtos;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class GalleryFilterTest {
  GalleryFilterCriteria filter;

  @Before
  public void setUp() throws Exception {
    filter = new GalleryFilterCriteria();
  }

  @Test
  public void testIsEnabled() {
    assertFalse("filter was enabled even though is empty name", filter.isEnabled());
    filter.setName("");
    assertFalse("filter was enabled even though is empty name", filter.isEnabled());
    filter.setName("any");
    assertTrue("filter not enabled even though name is set", filter.isEnabled());
  }
}
