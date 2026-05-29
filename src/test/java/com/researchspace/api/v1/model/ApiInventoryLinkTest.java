package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ApiInventoryLinkTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void serializesRelationTypeAndTarget() throws Exception {
    ApiInventoryLink link = new ApiInventoryLink();
    link.setRelationType("IsCalibratedBy");
    link.setTargetGlobalId("SA123");

    String json = mapper.writeValueAsString(link);

    assertEquals(
        "{\"relationType\":\"IsCalibratedBy\",\"targetGlobalId\":\"SA123\","
            + "\"versionPin\":null,\"targetSummary\":null}",
        json);
  }

  @Test
  void versionPinDerivedFromGlobalIdSuffix() {
    ApiInventoryLink link = new ApiInventoryLink();
    link.setTargetGlobalId("SA123v3");

    assertEquals(Long.valueOf(3), link.derivedVersionPin());
  }

  @Test
  void noVersionPinWhenGlobalIdHasNoSuffix() {
    ApiInventoryLink link = new ApiInventoryLink();
    link.setTargetGlobalId("SA123");

    assertNull(link.derivedVersionPin());
  }

  @Test
  void roundTripJsonPreservesValues() throws Exception {
    String json =
        "{\"relationType\":\"References\",\"targetGlobalId\":\"SS42v7\",\"versionPin\":7,\"targetSummary\":null}";
    ApiInventoryLink parsed = mapper.readValue(json, ApiInventoryLink.class);
    assertEquals("References", parsed.getRelationType());
    assertEquals("SS42v7", parsed.getTargetGlobalId());
    assertEquals(Long.valueOf(7), parsed.getVersionPin());
    assertNotNull(parsed);
  }
}
