package com.researchspace.service.archive.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RecordIdExtractorTest {
  RecordIdExtractor processor;
  User user;
  Folder folder;
  StructuredDocument sd;

  @BeforeEach
  public void setUp() throws Exception {
    processor = new RecordIdExtractor();
    user = TestFactory.createAnyUser("any");
    folder = TestFactory.createAFolder("amy", user);
    sd = TestFactory.createAnySD();
    sd.setOwner(user);
    sd.setId(1L);
  }

  @Test
  public void testProcess() {
    assertThat(processor.getIds()).isEmpty();
    // folders are ignored
    processor.process(folder);
    assertThat(processor.getIds()).isEmpty();

    processor.process(sd);
    assertThat(processor.getIds()).hasSize(1);
    assertEquals(1, processor.getIds().iterator().next().getDbId().intValue());
  }

  @Test
  public void testProcessWithConfiguration() {
    processor = new RecordIdExtractor(true, false, false, null);
    processor.process(sd);
    assertThat(processor.getIds()).hasSize(1);
    sd.setRecordDeleted(true);

    processor = new RecordIdExtractor(true, false, false, null);
    processor.process(sd);
    assertThat(processor.getIds()).hasSize(1);
    // don't include deleted
    processor = new RecordIdExtractor(false, false, false, null);
    processor.process(sd);
    assertThat(processor.getIds()).isEmpty();

    // now set another owner, not added
    User other = TestFactory.createAnyUser("other");
    sd.setOwner(other);
    processor = new RecordIdExtractor(true, false, true, user);
    processor.process(sd);
    assertThat(processor.getIds()).isEmpty();

    // now configure so we don't care about the owner:
    processor = new RecordIdExtractor(true, false, false, null);
    processor.process(sd);
    assertThat(processor.getIds()).hasSize(1);
  }
}
