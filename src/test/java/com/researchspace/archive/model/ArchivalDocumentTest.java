package com.researchspace.archive.model;

import static org.junit.Assert.assertTrue;

import com.researchspace.archive.ArchivalDocument;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.ArchiveTestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ArchivalDocumentTest {
  ArchiveModelFactory fac;

  @Before
  public void setUp() throws Exception {
    fac = new ArchiveModelFactory();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testArchivalDocumentToFromXMLRoundTrip() throws Exception {
    StructuredDocument sd = TestFactory.createWiredFolderAndDocument();
    ArchivalDocument original = fac.createArchivalDocument(sd);
    ArchivalDocument fromXML =
        ArchiveTestUtils.writeToXMLAndReadFromXML(original, ArchivalDocument.class);
    assertTrue(
        "Original and from XML have different properties",
        ArchiveTestUtils.areEquals(original, fromXML));
  }
}
