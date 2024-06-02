package com.researchspace.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.version.SemanticVersion;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ArchivalManifestTest {
  ArchiveManifest manifest;

  @Before
  public void setUp() throws Exception {
    manifest = new ArchiveManifest();
  }

  @After
  public void tearDown() throws Exception {}

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
