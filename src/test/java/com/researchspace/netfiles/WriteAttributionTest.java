package com.researchspace.netfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WriteAttributionTest {

  private static final Instant CREATED = Instant.parse("2026-06-18T10:00:00Z");

  @Test
  void metadataForRecord_withRecordIdAndName_includesAllKeys() {
    WriteAttribution a = new WriteAttribution("alice", Map.of(123L, "My Image"), CREATED);

    Map<String, String> m = a.metadataForRecord(123L);

    assertEquals("alice", m.get("rspace-created-by"));
    assertEquals("2026-06-18T10:00:00Z", m.get("rspace-created-at"));
    assertEquals("123", m.get("rspace-record-id"));
    assertEquals("My Image", m.get("rspace-record-name"));
    assertEquals(4, m.size());
  }

  @Test
  void metadataForRecord_nullRecordNames_omitsRecordName() {
    WriteAttribution a = new WriteAttribution("alice", null, CREATED);

    Map<String, String> m = a.metadataForRecord(123L);

    assertEquals("alice", m.get("rspace-created-by"));
    assertEquals("2026-06-18T10:00:00Z", m.get("rspace-created-at"));
    assertEquals("123", m.get("rspace-record-id"));
    assertFalse(m.containsKey("rspace-record-name"));
    assertEquals(3, m.size());
  }

  @Test
  void metadataForRecord_nullRecordId_includesOnlyCreatedByAndCreatedAt() {
    WriteAttribution a = new WriteAttribution("alice", null, CREATED);

    Map<String, String> m = a.metadataForRecord(null);

    assertEquals("alice", m.get("rspace-created-by"));
    assertEquals("2026-06-18T10:00:00Z", m.get("rspace-created-at"));
    assertFalse(m.containsKey("rspace-record-id"));
    assertFalse(m.containsKey("rspace-record-name"));
    assertEquals(2, m.size());
  }
}
