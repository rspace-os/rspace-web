package com.researchspace.dao;

import com.researchspace.model.FieldAttachment;
import com.researchspace.model.field.Field;
import java.util.List;
import java.util.Optional;

public interface FieldDao extends GenericDao<Field, Long> {

  List<Field> getFieldFromStructuredDocument(long id);

  List<Field> getFieldByRecordIdFromColumnNumber(long recordId, int columnNumber);

  List<String> getFieldNamesForRecord(Long recordId);

  List<Long> getFieldIdsForRecord(Long recordId);

  List<Field> findByTextContent(String text);

  /**
   * Finds current text fields whose content contains <code>text</code>: fields of documents that
   * are neither deleted nor temporary, excluding autosave (temporary) fields.
   *
   * @param text literal text to search for; SQL LIKE wildcards in it are not escaped
   * @return matching field ids in ascending order
   */
  List<Long> getCurrentTextFieldIdsContaining(String text);

  /**
   * Loads a field with a pessimistic write lock (SELECT ... FOR UPDATE), held until the transaction
   * ends. Call it as the transaction's first database read: later reads then see what a concurrent
   * transaction holding the lock committed.
   *
   * @param fieldId a field id
   * @return the field, or empty if it doesn't exist
   */
  Optional<Field> getForUpdate(Long fieldId);

  /**
   * Whether a field is a current text field, by the same rules as {@link
   * #getCurrentTextFieldIdsContaining(String)}.
   *
   * @param fieldId a field id
   * @return <code>false</code> if the field doesn't exist, isn't a text field, is an autosave
   *     field, or belongs to a deleted or temporary document
   */
  boolean isCurrentTextField(Long fieldId);

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
