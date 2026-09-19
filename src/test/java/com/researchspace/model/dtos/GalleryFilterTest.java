package com.researchspace.model.dtos;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GalleryFilterTest {
  GalleryFilterCriteria filter;

  @BeforeEach
  public void setUp() throws Exception {
    filter = new GalleryFilterCriteria();
  }

  @Test
  public void testIsEnabled() {
    assertFalse(filter.isEnabled(), "filter was enabled even though is empty name");
    filter.setName("");
    assertFalse(filter.isEnabled(), "filter was enabled even though is empty name");
    filter.setName("any");
    assertTrue(filter.isEnabled(), "filter not enabled even though name is set");
  }
}
