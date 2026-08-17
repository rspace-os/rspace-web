package com.researchspace.dao.hibernate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.core.util.SortOrder;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.record.BaseRecord;
import org.junit.jupiter.api.Test;

public class RecordDaoHibernateTest {

  private PaginationCriteria<BaseRecord> pgCrit(String orderBy) {
    return PaginationCriteria.createForClass(
        BaseRecord.class, orderBy, SortOrder.ASC.toString(), 0L, 10);
  }

  @Test
  public void editInfoFieldsGetPathPrefix() {
    assertEquals(" order by br.editInfo.name ASC", RecordDaoHibernate.makeOrderBy(pgCrit("name")));
    assertEquals(
        " order by br.editInfo.creationDateMillis ASC",
        RecordDaoHibernate.makeOrderBy(pgCrit("creationDateMillis")));
    assertEquals(
        " order by br.editInfo.modificationDateMillis ASC",
        RecordDaoHibernate.makeOrderBy(pgCrit("modificationDateMillis")));
  }

  @Test
  public void otherFieldsArePassedThroughUnchanged() {
    assertEquals(" order by br.id ASC", RecordDaoHibernate.makeOrderBy(pgCrit("id")));
    assertEquals(
        " order by br.editInfo.name ASC", RecordDaoHibernate.makeOrderBy(pgCrit("editInfo.name")));
  }

  @Test
  public void missingOrderByDefaultsToLastModifiedDesc() {
    assertEquals(
        " order by br.editInfo.modificationDateMillis DESC",
        RecordDaoHibernate.makeOrderBy(pgCrit(null)));
  }
}
