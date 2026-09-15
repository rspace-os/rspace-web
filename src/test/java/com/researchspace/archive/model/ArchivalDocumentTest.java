package com.researchspace.archive.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.archive.ArchivalDocument;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.ArchiveTestUtils;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ArchivalDocumentTest {
  ArchiveModelFactory fac;

  @BeforeEach
  public void setUp() throws Exception {
    fac = new ArchiveModelFactory();
  }

  @Test
  public void testArchivalDocumentToFromXMLRoundTrip() throws Exception {
    StructuredDocument sd = TestFactory.createWiredFolderAndDocument();
    ArchivalDocument original = fac.createArchivalDocument(sd);
    ArchivalDocument fromXML =
        ArchiveTestUtils.writeToXMLAndReadFromXML(original, ArchivalDocument.class);
    assertTrue(
        ArchiveTestUtils.areEquals(original, fromXML),
        "Original and from XML have different properties");
  }
}
