package com.researchspace.model.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.core.RecordType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RecordDocView {

  RSpaceDocView docView;

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testGetGlobalIdentifier() {
    docView = new RSpaceDocView();
    docView.setType("NORMAL");
    docView.setId(23L);
    assertEquals("SD23", docView.getGlobalIdentifier());
  }

  @Test
  public void testIsMediaRecord() {
    docView = new RSpaceDocView();

    docView.setType(RecordType.MEDIA_FILE.name());
    assertTrue(docView.isMediaRecord());
    docView.setType(RecordType.NORMAL.name());
    assertFalse(docView.isMediaRecord());
  }

  @Test
  public void testIsStructuredDocument() {
    docView = new RSpaceDocView();
    docView.setType(RecordType.MEDIA_FILE.name());
    assertFalse(docView.isStructuredDocument());
    docView.setType(RecordType.NORMAL.name());
    assertTrue(docView.isStructuredDocument());
  }
}
