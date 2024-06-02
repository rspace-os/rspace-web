package com.researchspace.archive.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DefaultArchiveReportTest {

  DefaultArchiveExportReport dar;

  @Before
  public void setUp() throws Exception {
    dar = new DefaultArchiveExportReport(false, new Date());
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testIsArchivalCompleted() {
    // check that once completed, does not allow new messages to be added
    assertFalse(dar.isArchivalCompleted());
    dar.addMessage("SSS");
    assertTrue(dar.toString().contains("SSS"));

    dar.setArchivalCompleted(true);
    dar.addMessage("NOO"); // not added
    assertFalse(dar.toString().contains("NOO"));
  }
}
