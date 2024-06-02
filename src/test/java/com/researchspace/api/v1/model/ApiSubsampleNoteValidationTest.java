package com.researchspace.api.v1.model;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

import com.researchspace.core.testutil.JavaxValidatorTest;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import org.junit.Test;

public class ApiSubsampleNoteValidationTest extends JavaxValidatorTest {

  User any = TestFactory.createAnyUser("any");

  @Test
  public void createApiNote() {
    ApiSubSampleNote note = new ApiSubSampleNote();
    assertNErrors(note, 2, true);

    note.setContent("content");
    assertNErrors(note, 0, true);

    note.setContent(randomAlphabetic(ApiSubSampleNote.MAX_CONTENT_LENGTH + 1));
    assertNErrors(note, 1, true);
  }
}
