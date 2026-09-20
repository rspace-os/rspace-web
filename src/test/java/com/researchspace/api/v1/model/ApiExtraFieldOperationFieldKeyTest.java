package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.inventory.field.ExtraTextField;
import org.junit.jupiter.api.Test;

class ApiExtraFieldOperationFieldKeyTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void isSerialisedInAResponseSoALaterRunCanMatchOnIt() throws Exception {
    ApiExtraField field = new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.TEXT);
    field.setName("Cryomedium");
    field.setOperationFieldKey("operations.cryopreserve.cryomediumField");
    assertTrue(
        mapper
            .writeValueAsString(field)
            .contains("\"operationFieldKey\":\"operations.cryopreserve.cryomediumField\""));
  }

  @Test
  void isCarriedFromThePersistedEntityOntoTheResponse() {
    ExtraTextField entity = new ExtraTextField();
    // An id, because the DTO constructor derives the field's global id from it.
    entity.setId(1L);
    entity.setName("Passage number");
    entity.setData("4");
    entity.setOperationFieldKey("operations.passage.numberField");

    assertEquals(
        "operations.passage.numberField", new ApiExtraField(entity).getOperationFieldKey());
  }

  @Test
  void isNullForAFieldNoOperationGenerated() {
    ExtraTextField entity = new ExtraTextField();
    entity.setId(2L);
    entity.setName("Batch");
    entity.setData("B1");

    assertNull(new ApiExtraField(entity).getOperationFieldKey());
  }

  @Test
  void aCopiedFieldKeepsItsKey() {
    ExtraTextField original = new ExtraTextField();
    original.setId(3L);
    original.setName("Passage number");
    original.setData("4");
    original.setOperationFieldKey("operations.passage.numberField");

    assertEquals("operations.passage.numberField", original.shallowCopy().getOperationFieldKey());
  }

  @Test
  void aCopiedFieldWithNoKeyStaysKeyless() {
    ExtraTextField original = new ExtraTextField();
    original.setId(4L);
    original.setName("Batch");
    original.setData("B1");

    assertNull(original.shallowCopy().getOperationFieldKey());
  }
}
