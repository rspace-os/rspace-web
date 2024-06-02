package com.researchspace.dao;

import com.researchspace.model.FieldAttachment;
import com.researchspace.model.field.Field;
import java.util.List;

public interface FieldDao extends GenericDao<Field, Long> {

  List<Field> getFieldFromStructuredDocument(long id);

  List<Field> getFieldByRecordIdFromColumnNumber(long recordId, int columnNumber);

  List<String> getFieldNamesForRecord(Long recordId);

  List<Long> getFieldIdsForRecord(Long recordId);

  List<Field> findByTextContent(String text);

  /** Removes a field attachment from the database. */
  void deleteFieldAttachment(FieldAttachment removed);

  /**
   * Loads all {@link FieldAttachment}s of a particular field
   *
   * @param id a field ID
   * @return a possibly empty but non-null List of {@link FieldAttachment}
   */
  List<FieldAttachment> getFieldAttachments(Long id);

  /**
   * Logs current temp field contents. We don't need special entity for this table as rows won't be
   * updated and there are no FK relations
   *
   * @param temp
   * @param permanentField
   */
  int logAutosave(Field temp, Field permanentField);
}
