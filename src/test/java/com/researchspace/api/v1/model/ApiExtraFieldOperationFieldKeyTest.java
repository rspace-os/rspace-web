package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * {@code operationFieldKey} is the wire identity an operation request uses to match a wizard-built
 * extra field to the definition entry that declared it (DevDocs/adr/0007). Resolved field names
 * interpolate user input and are localized, so the key travels explicitly; it is request-only and
 * never persisted or echoed back.
 */
class ApiExtraFieldOperationFieldKeyTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void bindsFromTheRequest() throws Exception {
    ApiExtraField field =
        mapper.readValue(
            "{\"name\":\"Aliquot of X\",\"type\":\"link\",\"newFieldRequest\":true,"
                + "\"operationFieldKey\":\"operations.aliquot.linkFieldName\"}",
            ApiExtraField.class);
    assertEquals("operations.aliquot.linkFieldName", field.getOperationFieldKey());
  }

  @Test
  void isNeverSerialisedInAResponse() throws Exception {
    ApiExtraField field = new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.TEXT);
    field.setName("Cryomedium");
    field.setOperationFieldKey("operations.cryopreserve.cryomediumField");
    assertFalse(mapper.writeValueAsString(field).contains("operationFieldKey"));
  }
}
