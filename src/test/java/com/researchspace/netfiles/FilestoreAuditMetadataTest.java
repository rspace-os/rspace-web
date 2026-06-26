package com.researchspace.netfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FilestoreAuditMetadataTest {

  private static final Instant CUTOFF = Instant.parse("2026-06-18T10:00:00Z");

  @Test
  void from_parsesCreatedByAndCreatedAt() {
    FilestoreAuditMetadata a =
        FilestoreAuditMetadata.from(
            Map.of("rspace-created-by", "alice", "rspace-created-at", "2026-06-18T10:00:00Z"));

    assertEquals("alice", a.createdBy());
    assertEquals(Instant.parse("2026-06-18T10:00:00Z"), a.createdAt());
  }

  @Test
  void from_nullOrMissingValues_yieldNulls() {
    assertNull(FilestoreAuditMetadata.from(null).createdBy());
    assertNull(FilestoreAuditMetadata.from(null).createdAt());
    assertNull(FilestoreAuditMetadata.from(Map.of()).createdBy());
  }

  @Test
  void from_unparseableCreatedAt_yieldsNullInstant() {
    FilestoreAuditMetadata a =
        FilestoreAuditMetadata.from(
            Map.of("rspace-created-by", "alice", "rspace-created-at", "not-a-date"));

    assertEquals("alice", a.createdBy());
    assertNull(a.createdAt());
  }

  @Test
  void isCreatedBy_matchesOnlyExactUsername() {
    assertTrue(new FilestoreAuditMetadata("alice", null).isCreatedBy("alice"));
    assertFalse(new FilestoreAuditMetadata("alice", null).isCreatedBy("bob"));
    assertFalse(new FilestoreAuditMetadata(null, null).isCreatedBy("alice"));
  }

  @Test
  void hasTimestamp_reflectsPresence() {
    assertTrue(new FilestoreAuditMetadata("alice", CUTOFF).hasTimestamp());
    assertFalse(new FilestoreAuditMetadata("alice", null).hasTimestamp());
  }

  @Test
  void isWithin_isInclusiveOfCutoffAndExcludesEarlier() {
    assertTrue(new FilestoreAuditMetadata("alice", CUTOFF).isWithin(CUTOFF)); // exactly at cutoff
    assertTrue(new FilestoreAuditMetadata("alice", CUTOFF.plusSeconds(60)).isWithin(CUTOFF));
    assertFalse(new FilestoreAuditMetadata("alice", CUTOFF.minusSeconds(1)).isWithin(CUTOFF));
    assertFalse(new FilestoreAuditMetadata("alice", null).isWithin(CUTOFF));
  }
}
