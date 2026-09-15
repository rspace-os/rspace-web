package com.researchspace.service.archive;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.archive.ArchivalGalleryMetaDataParserRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GalleryParserRefTest {
  ArchivalGalleryMetaDataParserRef parserRef;

  @BeforeEach
  public void setUp() throws Exception {
    parserRef = new ArchivalGalleryMetaDataParserRef();
  }

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
