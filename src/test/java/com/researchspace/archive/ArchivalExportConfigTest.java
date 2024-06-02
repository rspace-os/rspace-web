package com.researchspace.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.archive.model.ArchiveExportConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ArchivalExportConfigTest {

  ArchiveExportConfig cfg;

  @Before
  public void setUp() throws Exception {
    cfg = new ArchiveExportConfig();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testGenerateDocumentExportFileName() {
    assertTrue(cfg.isArchive()); // default
    assertEquals("file.xml", cfg.generateDocumentExportFileName("file"));
    cfg.setArchiveType(ArchiveExportConfig.HTML);
    assertEquals("file.html", cfg.generateDocumentExportFileName("file"));
  }

  @Test
  public void testIsSelection() {
    assertFalse(cfg.isSelectionScope());
    cfg.setExportScope(ExportScope.SELECTION);
    assertTrue(cfg.isSelectionScope());
  }
}
