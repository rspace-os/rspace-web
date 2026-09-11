package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.inventory.field.ExtraTextField;
import org.junit.jupiter.api.Test;

/**
 * {@code operationFieldKey} is the identity an operation uses to recognise the field a PREVIOUS run
 * of that operation generated (DevDocs/adr/0007). Resolved field names interpolate user input and
 * are localized, so the key travels explicitly.
 *
 * <p>It used to be request-only and never echoed back. That is what forced the next run to match on
 * the localized name: the run reads the parent sample's fields over GET, so a key that never comes
 * back cannot identify the previous generation, and a second locale or a reworded translation made
 * the lookup miss and restart a Passage counter at 1 (RSDEV-1231, F6). It is now persisted and
 * returned on GET, and read-only on the API: only the server's request builder puts one on a field
 * (ApiInventoryOperationPostBindingTest, OperationFieldKeyPersistenceTest).
 */
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
    // Null is the normal case: every hand-created field, and every field predating the column.
    ExtraTextField entity = new ExtraTextField();
    entity.setId(2L);
    entity.setName("Batch");
    entity.setData("B1");

    assertNull(new ApiExtraField(entity).getOperationFieldKey());
  }

  @Test
  void aCopiedFieldKeepsItsKey() {
    // Provenance travels with a copy, so a sample created from a template that carries an
    // operation-generated field is recognised by a later run of that operation. This is also the
    // mechanism that made a forged key worth forging, which is why no request can set one
    // (OperationFieldKeyPersistenceTest).
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
