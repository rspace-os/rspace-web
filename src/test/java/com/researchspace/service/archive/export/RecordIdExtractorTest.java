package com.researchspace.service.archive.export;

import static org.junit.Assert.assertEquals;

import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RecordIdExtractorTest {
  RecordIdExtractor processor;
  User user;
  Folder folder;
  StructuredDocument sd;

  @Before
  public void setUp() throws Exception {
    processor = new RecordIdExtractor();
    user = TestFactory.createAnyUser("any");
    folder = TestFactory.createAFolder("amy", user);
    sd = TestFactory.createAnySD();
    sd.setOwner(user);
    sd.setId(1L);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testProcess() {
    assertEquals(0, processor.getIds().size());
    // folders are ignored
    processor.process(folder);
    assertEquals(0, processor.getIds().size());

    processor.process(sd);
    assertEquals(1, processor.getIds().size());
    assertEquals(1, processor.getIds().iterator().next().getDbId().intValue());
  }

  @Test
  public void testProcessWithConfiguration() {
    processor = new RecordIdExtractor(true, false, false, null);
    processor.process(sd);
    assertEquals(1, processor.getIds().size());
    sd.setRecordDeleted(true);

    processor = new RecordIdExtractor(true, false, false, null);
    processor.process(sd);
    assertEquals(1, processor.getIds().size());
    // don't include deleted
    processor = new RecordIdExtractor(false, false, false, null);
    processor.process(sd);
    assertEquals(0, processor.getIds().size());

    // now set another owner, not added
    User other = TestFactory.createAnyUser("other");
    sd.setOwner(other);
    processor = new RecordIdExtractor(true, false, true, user);
    processor.process(sd);
    assertEquals(0, processor.getIds().size());

    // now configure so we don't care about the owner:
    processor = new RecordIdExtractor(true, false, false, null);
    processor.process(sd);
    assertEquals(1, processor.getIds().size());
  }
}
