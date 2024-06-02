package com.researchspace.dao.hibernate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.Test;

public class UserDeletionQueryBuilderTest {

  private UserDeletionQueryBuilder queryBuilder = new UserDeletionQueryBuilder();

  @Test
  public void checkDeleteByRecordQueryWithSingleJoin() {
    // syntax check
    assertEquals(
        "delete TableToDel from TableToDel left join BaseRecord br on TableToDel.record_id = br.id"
            + " where br.owner_id=:id",
        queryBuilder.generateDeleteByRecordOwnerQuery("TableToDel", "record_id"));
    // real example
    assertEquals(
        "delete RecordAttachment from RecordAttachment left join BaseRecord br on"
            + " RecordAttachment.record_id = br.id where br.owner_id=:id",
        queryBuilder.generateDeleteByRecordOwnerQuery("RecordAttachment", "record_id"));
  }

  @Test
  public void checkDeleteByRecordQueryWithDoublewJoin() {
    // syntax check
    assertEquals(
        "delete TableToDel from TableToDel left join JoinTable on"
            + " TableToDel.tableToDel_joinTable_id = JoinTable.joinTable_id left join BaseRecord br"
            + " on JoinTable.record_id = br.id where br.owner_id=:id",
        queryBuilder.generateDeleteByRecordOwnerQuery(
            "TableToDel", "tableToDel_joinTable_id", "JoinTable", "joinTable_id", "record_id"));
    // real example
    assertEquals(
        "delete FieldAutosaveLog from FieldAutosaveLog left join Field on FieldAutosaveLog.fieldId"
            + " = Field.id left join BaseRecord br on Field.structuredDocument_id = br.id where"
            + " br.owner_id=:id",
        queryBuilder.generateDeleteByRecordOwnerQuery(
            "FieldAutosaveLog", "fieldId", "Field", "id", "structuredDocument_id"));
  }
}
