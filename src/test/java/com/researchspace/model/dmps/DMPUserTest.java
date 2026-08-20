package com.researchspace.model.dmps;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.model.record.TestFactory.createAnyUser;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class DMPUserTest {

  @Test
  void timestampAutoCreated() {
    DMPUser dmpUser = new DMPUser(createAnyUser(getRandomName(10)),
        new DmpDto("dmpID", "Title", DMPSource.UNKNOWN, "doiLink", "dmpLink"));
    assertNotNull(dmpUser.getUser());
    assertNull(dmpUser.getId());
    assertNotNull(dmpUser.getDmpId());
    assertNotNull(dmpUser.getTimestamp());
    assertNotNull(dmpUser.getDmpLink());
    assertNotNull(dmpUser.getDoiLink());
    assertNotNull(dmpUser.getSource());
  }
}