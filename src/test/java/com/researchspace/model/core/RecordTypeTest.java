package com.researchspace.model.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RecordTypeTest {

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testAllTypesReturnAPrefix() {
    EnumSet<RecordType> allTypes = EnumSet.allOf(RecordType.class);
    for (RecordType type : allTypes) {
      assertNotNull(RecordType.getGlobalIdFromType(type.name()));
    }
  }

  @Test
  public void testAllTypesInConcatenatedStringReturnAPrefix() {
    EnumSet<RecordType> allTypes = EnumSet.allOf(RecordType.class);
    String allTypesJoined = StringUtils.join(allTypes, ":");
    // this is just returned first
    assertEquals(GlobalIdPrefix.ST, RecordType.getGlobalIdFromType(allTypesJoined));
  }

  @Test
  public void testPrecedenceRules() {
    assertEquals(GlobalIdPrefix.NB, RecordType.getGlobalIdFromType("NOTEBOOK:FOLDER"));
    assertEquals(GlobalIdPrefix.GL, RecordType.getGlobalIdFromType("MEDIA_FILE:NORMAL"));
    assertEquals(GlobalIdPrefix.SD, RecordType.getGlobalIdFromType("TEMPLATE"));
    assertEquals(GlobalIdPrefix.FL, RecordType.getGlobalIdFromType("FOLDER:SYSTEM:TEMPLATE"));
    assertEquals(GlobalIdPrefix.ST, RecordType.getGlobalIdFromType(RecordType.SNIPPET.name()));
  }

  @Test
  public void testSnippet() {
    assertTrue(RecordType.isSnippet("SNIPPET"));
    assertTrue(RecordType.isSnippet(RecordType.SNIPPET.name()));
  }

  @Test
  public void testFolderNotebookRules() {
    assertTrue(RecordType.isFolder("FOLDER"));
    assertTrue(RecordType.isFolder("NOTEBOOK"));
    assertFalse(RecordType.isNotebook("FOLDER"));
    assertTrue(RecordType.isNotebook("NOTEBOOK"));
  }
}
