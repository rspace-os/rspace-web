package com.researchspace.dao.hibernate;

import com.researchspace.dao.FieldDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.FieldAttachment;
import com.researchspace.model.field.Field;
import java.time.Instant;
import java.util.List;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

@Repository("fieldDao")
@SuppressWarnings({"unchecked"})
public class FieldDaoHibernate extends GenericDaoHibernate<Field, Long> implements FieldDao {

  public FieldDaoHibernate() {
    super(Field.class);
  }

  @Override
  public List<Field> getFieldFromStructuredDocument(long id) {
    Query<Field> q =
        getSession()
            .createQuery(
                "from Field f where f.structuredDocument.id = :id order by f.columnIndex",
                Field.class);
    q.setParameter("id", id);
    return q.list();
  }

  @Override
  public List<Field> getFieldByRecordIdFromColumnNumber(long recordId, int columnNumber) {
    Query<Field> q =
        getSession()
            .createQuery(
                "from Field f where f.structuredDocument.id = :recid  and f.columnIndex >"
                    + " :colNumber order by f.columnIndex",
                Field.class);
    q.setParameter("recid", recordId);
    q.setParameter("colNumber", columnNumber);
    return q.list();
  }

  @Override
  public List<String> getFieldNamesForRecord(Long recordId) {
    Query<String> q =
        getSession()
            .createQuery(
                "select f.name from Field f where f.structuredDocument.id = :recordId order by"
                    + " f.columnIndex asc",
                String.class);
    q.setParameter("recordId", recordId);
    return q.list();
  }

  @Override
  public List<Long> getFieldIdsForRecord(Long recordId) {
    Query<Long> q =
        getSession()
            .createQuery(
                "select f.id from Field f where f.structuredDocument.id = :recordId order by"
                    + " f.columnIndex asc",
                Long.class);
    q.setParameter("recordId", recordId);
    return q.list();
  }

  @Override
  public List<Field> findByTextContent(String text) {
    return getSession()
        .createQuery("from Field where rtfData like :data")
        .setParameter("data", "%" + text + "%")
        .list();
  }

  @Override
  public void deleteFieldAttachment(FieldAttachment removed) {
    getSession().delete(removed);
  }

  @Override
  public List<FieldAttachment> getFieldAttachments(Long fieldId) {
    return getSession()
        .createQuery("From FieldAttachment fa where fa.field.id=:fieldId", FieldAttachment.class)
        .setParameter("fieldId", fieldId)
        .list();
  }

  @Override
  public int logAutosave(Field temp, Field permanent) {
    // we don't need entity management here, not yet anyway
    return getSession()
        .createNativeQuery(
            "insert into FieldAutosaveLog values (NULL, :tempField_id, :date, :fieldData,"
                + " :fieldId)")
        .setParameter("tempField_id", temp.getId())
        .setParameter("date", Instant.now())
        .setParameter("fieldData", temp.getFieldData())
        .setParameter("fieldId", permanent.getId())
        .executeUpdate();
  }
}
