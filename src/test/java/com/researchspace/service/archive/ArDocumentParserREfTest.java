package com.researchspace.service.archive;

import static com.researchspace.core.util.TransformerUtils.toList;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.archive.ArchivalDocument;
import com.researchspace.archive.ArchivalDocumentParserRef;
import java.util.Date;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ArDocumentParserREfTest {
  ArchivalDocumentParserRef parserRef;

  @Before
  public void setUp() throws Exception {
    parserRef = new ArchivalDocumentParserRef();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testGetFileList() {
    assertNotNull(parserRef.getFileList());
  }

  @Test
  public void testIsMedia() {
    assertFalse(parserRef.isMedia());
    assertTrue(parserRef.isDocument());
  }

  @Test
  public void testSortByCreationDateAsc() {
    ArchivalDocumentParserRef parserRef2 = new ArchivalDocumentParserRef();
    // ad1 created before ad2
    ArchivalDocument ad1 = new ArchivalDocument();
    ad1.setCreationDate(new Date(now().minus(5, DAYS).toEpochMilli()));
    ArchivalDocument ad2 = new ArchivalDocument();
    ad2.setCreationDate(new Date(now().toEpochMilli()));
    parserRef.setArchivalDocument(ad1);
    parserRef2.setArchivalDocument(ad2);
    List<ArchivalDocumentParserRef> toSort = toList(parserRef2, parserRef);
    toSort.sort(ArchivalDocumentParserRef.SortArchivalDocumentParserRefByCreationDateAsc);
    assertEquals(parserRef, toSort.get(0));
    assertTrue(
        toSort
            .get(0)
            .getArchivalDocument()
            .getCreationDate()
            .before(toSort.get(1).getArchivalDocument().getCreationDate()));
  }
}
