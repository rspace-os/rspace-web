package com.researchspace.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.archive.model.ArchiveExportConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ArchivalExportConfigTest {

  ArchiveExportConfig cfg;

  @BeforeEach
  public void setUp() throws Exception {
    cfg = new ArchiveExportConfig();
  }

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
