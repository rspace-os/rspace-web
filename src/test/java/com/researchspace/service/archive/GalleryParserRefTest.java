package com.researchspace.service.archive;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.archive.ArchivalGalleryMetaDataParserRef;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GalleryParserRefTest {
  ArchivalGalleryMetaDataParserRef parserRef;

  @Before
  public void setUp() throws Exception {
    parserRef = new ArchivalGalleryMetaDataParserRef();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testGetFileList() {
    assertNotNull(parserRef.getFileList());
  }

  @Test
  public void testIsMedia() {
    assertTrue(parserRef.isMedia());
    assertFalse(parserRef.isDocument());
  }
}
