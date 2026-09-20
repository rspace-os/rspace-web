package com.researchspace.service.inventory.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.model.record.BaseRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class OperationFieldNamesTest {

  @Test
  void truncatesAComposedNameToLeaveRoomForItsUniquenessSuffix() {
    // A generated name interpolates the origin's own name, which may already be at the column
    // limit; without this the INSERT failed as a 500 after the origins were decremented.
    String atTheLimit = "x".repeat(BaseRecord.DEFAULT_VARCHAR_LENGTH);
    List<ApiExtraField> colliding =
        List.of(
            OperationFieldNames.link(atTheLimit, "operations.pool.linkFieldName", "HasPart", "SS1"),
            OperationFieldNames.link(
                atTheLimit, "operations.pool.linkFieldName", "HasPart", "SS2"));

    List<ApiExtraField> unique = OperationFieldNames.withUniqueFieldNames(colliding);

    assertEquals(
        List.of(BaseRecord.DEFAULT_VARCHAR_LENGTH, BaseRecord.DEFAULT_VARCHAR_LENGTH),
        unique.stream().map(field -> field.getName().length()).toList());
    assertEquals(2, unique.stream().map(ApiExtraField::getName).distinct().count());
  }
}
