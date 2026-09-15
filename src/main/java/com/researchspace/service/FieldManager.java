package com.researchspace.service;

import com.researchspace.model.FieldAttachment;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import java.util.List;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;

/** Contains all the Field actions required by RSpace. */
public interface FieldManager {
  /**
   * Gets field by id
   *
   * @param user , can be <code>null</code>
   */
  Optional<Field> get(Long id, User user);

  Optional<Field> getWithLoadedMediaLinks(Long id, User user);

  /**
   * Saves field
   *
   * @param user , can be <code>null</code>
   */
  Field save(Field field, User user);

  void delete(Long field, User user);

  /**
   * Gets the fields of a structured document.
   *
   * @param id the record id
   * @param user the subject; must have READ permission on the record
   * @return the record's fields
   * @throws org.apache.shiro.authz.AuthorizationException if user is null or the anonymous
   *     published-view guest, the record does not exist, or the user lacks READ permission on it
   *     (RSDEV-1329)
   */
  List<Field> getFieldsByRecordId(long id, User user);

  /**
   * Gets the fields of a structured document for reading its autosaved (draft) content.
   *
   * <p>Requires WRITE rather than READ: a published record grants READ to every user, but the
   * unsaved editing buffer belongs to whoever is editing and is never published (RSDEV-1329).
   *
   * @param id the record id
   * @param user the subject; must have WRITE permission on the record
   * @return the record's fields, from which the caller derives the temporary (draft) fields
   * @throws org.apache.shiro.authz.AuthorizationException if user is null or the anonymous guest,
   *     the record does not exist, or the user lacks WRITE permission on it (RSDEV-1329)
   */
  List<Field> getAutoSavedFieldsByRecordId(long id, User user);

  List<String> getFieldNamesForRecord(Long recordId);

  List<Long> getFieldIdsForRecord(Long recordId);

  List<FieldAttachment> getFieldAttachments(Long id);

  /**
   * Associates an EcatMediaFile with this field.
   *
   * @param ignorePermissions whether to ignore permissions check or not; most times this should be
   *     <code>false</code>.
   * @return The created {FieldAttachment} as an optional. Will be empty if <code>subject</code>
   *     does not have read permission on the mediaFile
   * @throws AuthorizationException if subject lacks edit permission on field's containing document.
   */
  Optional<FieldAttachment> addMediaFileLink(
      Long ecatMediaFileId, User subject, Long fieldId, Boolean ignorePermissions);

  /**
   * Gets list of fields whose content matches the search term in a database like '%term%' query.
   *
   * <p>This method is for use by internal operations, not for user-based search, which should use
   * the SearchManager interface.
   *
   * @return return possibly empty but non-null list of {@link Field}s
   */
  List<Field> findByTextContent(String searchTerm);
}
