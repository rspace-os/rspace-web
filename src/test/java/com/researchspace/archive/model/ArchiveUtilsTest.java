package com.researchspace.archive.model;

import static org.junit.Assert.assertEquals;

import com.researchspace.archive.ArchiveUtils;
import org.junit.Test;

public class ArchiveUtilsTest {

  @Test
  public void testGetFolderNameForRecord() {
    String name = "a b-c_d  ?fg";
    assertEquals("a-b-c-d-fg", ArchiveUtils.filterArchiveNameString(name));
  }
}
