package com.researchspace.model.dmps;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.model.record.TestFactory.createAnyUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

class DMPUserTest {

  @Test
  void timestampAutoCreated() {
    DMPUser dmpUser =
        new DMPUser(
            createAnyUser(getRandomName(10)),
            new DmpDto("dmpID", "Title", DMPSource.UNKNOWN, "doiLink", "dmpLink"));
    assertNotNull(dmpUser.getUser());
    assertNull(dmpUser.getId());
    assertNotNull(dmpUser.getDmpId());
    assertNotNull(dmpUser.getTimestamp());
    assertNotNull(dmpUser.getDmpLink());
    assertNotNull(dmpUser.getDoiLink());
    assertNotNull(dmpUser.getSource());
  }

  @ParameterizedTest
  @NullSource
  @EnumSource(value = DMPSource.class, names = "UNKNOWN")
  void unknownSourceAdoptsImportingPlatform(DMPSource source) {
    DMPUser dmpUser = planFrom(source);
    dmpUser.adoptSourceIfUnknown(DMPSource.DMP_TOOL);
    assertEquals(DMPSource.DMP_TOOL, dmpUser.getSource());
  }

  @ParameterizedTest
  @EnumSource(value = DMPSource.class, names = "UNKNOWN", mode = EnumSource.Mode.EXCLUDE)
  void knownSourceIsKept(DMPSource source) {
    DMPUser dmpUser = planFrom(source);
    dmpUser.adoptSourceIfUnknown(
        source == DMPSource.DMP_TOOL ? DMPSource.ARGOS : DMPSource.DMP_TOOL);
    assertEquals(source, dmpUser.getSource());
  }

  @ParameterizedTest
  @CsvSource({", false", "UNKNOWN, false", "ARGOS, true"})
  void sourceIsKnownOnlyWhenSetToAPlatform(DMPSource source, boolean known) {
    assertEquals(known, planFrom(source).hasKnownSource());
  }

  private DMPUser planFrom(DMPSource source) {
    return new DMPUser(
        createAnyUser(getRandomName(10)), new DmpDto("dmpID", "Title", source, null, null));
  }
}
