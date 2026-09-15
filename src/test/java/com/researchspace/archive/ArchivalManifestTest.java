package com.researchspace.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.core.util.version.SemanticVersion;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ArchivalManifestTest {
  ArchiveManifest manifest;

  @BeforeEach
  public void setUp() throws Exception {
    manifest = new ArchiveManifest();
  }

  @Test
  public void testAddItem() {
    assertTrue(manifest.getItems().isEmpty());
    manifest.addItem("name", "value");
    assertEquals(1, manifest.getItems().size());
  }

  @Test
  public void testGetVersion() {
    // returns constant if not set
    assertEquals(SemanticVersion.UNKNOWN_VERSION, manifest.getDatabaseVersion());
    manifest.addItem(ArchiveManifest.DB_VERSIONKEY, "Not a number");
    assertEquals(SemanticVersion.UNKNOWN_VERSION, manifest.getDatabaseVersion());
    manifest.addItem(ArchiveManifest.DB_VERSIONKEY, "5");
    assertEquals(new SemanticVersion("5"), manifest.getDatabaseVersion());
  }

  @Test
  public void testGetAppVersion() {
    // returns constant if not set
    assertEquals(null, manifest.getRSpaceAppVersion());
    manifest.addItem(ArchiveManifest.RSPACE_APPLICATION_VERSION, "Not a version");
    assertEquals(null, manifest.getRSpaceAppVersion());
    manifest.addItem(ArchiveManifest.RSPACE_APPLICATION_VERSION, "5.3.5-RELEASE");
    assertEquals(new SemanticVersion("5.3.5-RELEASE"), manifest.getRSpaceAppVersion());
  }

  @Test
  public void getSource() {
    manifest.addItem(ArchiveManifest.SOURCE, ArchiveManifest.RSPACE_SOURCE);
    assertTrue(manifest.isRSpaceSource());
    manifest.addItem(ArchiveManifest.SOURCE, "cerf");
    assertFalse(manifest.isRSpaceSource());
  }

  @Test
  public void testStringify() {
    manifest.addItem("name", "value");
    assertEquals("name:value\n", manifest.stringify());
  }

  @Test
  public void testAddAll() {
    Map<String, String> toAdd = new HashMap<String, String>();
    toAdd.put("a", "b");
    toAdd.put("c", "d");
    manifest.addAll(toAdd);
    assertEquals(2, manifest.getItems().size());
  }
}
