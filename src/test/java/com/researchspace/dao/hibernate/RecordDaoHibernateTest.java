package com.researchspace.dao.hibernate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.core.util.SortOrder;
import com.researchspace.model.sort.RecordSort;
import org.junit.jupiter.api.Test;

public class RecordDaoHibernateTest {

  @Test
  public void editInfoFieldsGetPathPrefix() {
    assertEquals(
        " order by br.editInfo.name ASC",
        RecordDaoHibernate.makeOrderBy(RecordSort.NAME, SortOrder.ASC));
    assertEquals(
        " order by br.editInfo.creationDateMillis ASC",
        RecordDaoHibernate.makeOrderBy(RecordSort.CREATION_DATE_MILLIS, SortOrder.ASC));
    assertEquals(
        " order by br.editInfo.modificationDateMillis ASC",
        RecordDaoHibernate.makeOrderBy(RecordSort.MODIFICATION_DATE_MILLIS, SortOrder.ASC));
  }

  @Test
  public void blankSortKeyMeansLastModified() {
    assertEquals(
        " order by br.editInfo.modificationDateMillis DESC",
        RecordDaoHibernate.makeOrderBy(RecordSort.fromRequest(null), SortOrder.DESC));
  }

  @Test
  public void templateSortHasNoColumnHereSoFallsBackToLastModified() {
    assertEquals(
        " order by br.editInfo.modificationDateMillis DESC",
        RecordDaoHibernate.makeOrderBy(RecordSort.TEMPLATE, SortOrder.ASC));
  }
}
