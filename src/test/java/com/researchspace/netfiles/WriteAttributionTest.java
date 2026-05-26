package com.researchspace.netfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WriteAttributionTest {

  @Test
  void metadataForRecord_withRecordIdAndName_includesAllThreeKeys() {
    WriteAttribution a = new WriteAttribution("alice", Map.of(123L, "My Image"));

    Map<String, String> m = a.metadataForRecord(123L);

    assertEquals("alice", m.get("rspace-user"));
    assertEquals("123", m.get("rspace-record-id"));
    assertEquals("My Image", m.get("rspace-record-name"));
    assertEquals(3, m.size());
  }

  @Test
  void metadataForRecord_nullRecordNames_omitsRecordName() {
    WriteAttribution a = new WriteAttribution("alice", null);

    Map<String, String> m = a.metadataForRecord(123L);

    assertEquals("alice", m.get("rspace-user"));
    assertEquals("123", m.get("rspace-record-id"));
    assertFalse(m.containsKey("rspace-record-name"));
    assertEquals(2, m.size());
  }

  @Test
  void metadataForRecord_nullRecordId_omitsRecordIdAndName() {
    WriteAttribution a = new WriteAttribution("alice", null);

    Map<String, String> m = a.metadataForRecord(null);

    assertEquals("alice", m.get("rspace-user"));
    assertFalse(m.containsKey("rspace-record-id"));
    assertFalse(m.containsKey("rspace-record-name"));
    assertEquals(1, m.size());
  }
}
