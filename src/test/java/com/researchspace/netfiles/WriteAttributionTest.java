package com.researchspace.netfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WriteAttributionTest {

  @Test
  void metadataForRecord_withRecordId_includesAllThreeKeys() {
    WriteAttribution a = new WriteAttribution("alice", "copy");

    Map<String, String> m = a.metadataForRecord(123L);

    assertEquals("alice", m.get("rspace-user"));
    assertEquals("copy", m.get("rspace-op"));
    assertEquals("123", m.get("rspace-record-id"));
    assertEquals(3, m.size());
  }

  @Test
  void metadataForRecord_nullRecordId_omitsRecordIdKey() {
    WriteAttribution a = new WriteAttribution("alice", "transfer");

    Map<String, String> m = a.metadataForRecord(null);

    assertEquals("alice", m.get("rspace-user"));
    assertEquals("transfer", m.get("rspace-op"));
    assertFalse(m.containsKey("rspace-record-id"));
    assertEquals(2, m.size());
  }
}
