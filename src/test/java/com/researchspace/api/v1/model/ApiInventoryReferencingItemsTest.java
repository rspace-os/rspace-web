package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiInventoryReferencingItemsTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void emptyListSerializes() throws Exception {
    ApiInventoryReferencingItems wrapper = new ApiInventoryReferencingItems();
    wrapper.setReferencingItems(List.of());

    String json = mapper.writeValueAsString(wrapper);

    assertTrue(json.contains("\"referencingItems\":[]"));
  }

  @Test
  void rowSerializesAllFields() throws Exception {
    ApiInventoryReferencingItem row = new ApiInventoryReferencingItem();
    row.setSourceGlobalId("SA77");
    row.setSourceName("Calibration aliquot");
    row.setSourceType("SAMPLE");
    row.setRelationType("IsCalibratedBy");
    row.setVersionPin(null);
    row.setModifiedAtMillis(0L);

    ApiInventoryReferencingItems wrapper = new ApiInventoryReferencingItems();
    wrapper.setReferencingItems(List.of(row));

    String json = mapper.writeValueAsString(wrapper);

    assertTrue(json.contains("\"sourceGlobalId\":\"SA77\""));
    assertTrue(json.contains("\"relationType\":\"IsCalibratedBy\""));
    assertTrue(json.contains("\"sourceType\":\"SAMPLE\""));
    assertTrue(json.contains("\"modifiedAt\":\"1970-01-01"));
  }

  @Test
  void roundTripPreservesRow() throws Exception {
    String json =
        "{\"referencingItems\":[{\"sourceGlobalId\":\"SS9\",\"sourceName\":\"x\","
            + "\"sourceType\":\"SUBSAMPLE\",\"relationType\":\"References\","
            + "\"versionPin\":null,\"modifiedAt\":\"1970-01-01T00:00:00.000Z\"}]}";
    ApiInventoryReferencingItems parsed =
        mapper.readValue(json, ApiInventoryReferencingItems.class);
    assertEquals(1, parsed.getReferencingItems().size());
    assertEquals("SS9", parsed.getReferencingItems().get(0).getSourceGlobalId());
  }
}
