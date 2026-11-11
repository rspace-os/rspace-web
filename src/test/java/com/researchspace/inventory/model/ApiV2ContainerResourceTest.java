package com.researchspace.inventory.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.WriteOperation;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiV2ContainerResourceTest {
  @Test
  void exposesOnlyTheLimitedReadFieldsWithoutWritesOrGlobalIdQueries() {
    var description = ApiV2ContainerResource.DESCRIPTION;
    assertEquals(
        List.of("id", "globalId", "name", "containerType", "deleted"),
        description.fields().stream().map(Field::name).toList());
    assertEquals(java.util.Set.of(), description.requireField("globalId").operators());
    assertFalse(description.requireField("globalId").sortable());
    for (WriteOperation operation : WriteOperation.values()) {
      assertEquals(java.util.Set.of(), description.writableFields(operation));
    }
  }
}
