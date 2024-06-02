package com.researchspace.service;

import com.researchspace.model.EcatMediaFile;
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
   * @param id
   * @param user , can be <code>null</code>
   * @return
   */
  Optional<Field> get(Long id, User user);

  Optional<Field> getWithLoadedMediaLinks(Long id, User user);

  /**
   * Saves field
   *
   * @param Field field to save
   * @param user , can be <code>null</code>
   * @return
   */
  Field save(Field field, User user);

  void delete(Long field, User user);

  List<Field> getFieldsByRecordId(long id, User user);

  List<Field> getFieldsByRecordIdFromColumnNumber(long recordId, int columnNumber, User user);

  List<String> getFieldNamesForRecord(Long recordId);

  List<Long> getFieldIdsForRecord(Long recordId);

  /**
   * Associates an EcatMediaFile with this field.
   *
   * @param ecatMediaFileId
   * @param subject
   * @param fieldId
   * @param ignorePermissions whether to ignore permissions check or not; most times this should be
   *     <code>false</code>.
   * @return The created {FieldAttachment} as an optional. Will be empty if <code>subject</code>
   *     does not have read permission on the mediaFile
   * @throws AuthorizationException if subject lacks edit permission on field's containing document.
   */
  Optional<FieldAttachment> addMediaFileLink(
      Long ecatMediaFileId, User subject, Long fieldId, Boolean ignorePermissions);

  /**
   * Removes an EcatMediaFile from this field.
   *
   * <p>From 1.40, we're just marking as deleted.
   *
   * @param ecatMediaFileId
   * @param subject
   * @param fieldId
   * @return The associated {@link EcatMediaFile}
   * @deprecated
   * @throws AuthorizationException if subject lacks edit permission on field's containing document.
   */
  EcatMediaFile removeMediaFileLink(Long ecatMediaFileId, User user, Long fieldId);

  /**
   * Gets list of fields whose content matches the search term in a database like '%term%' query.
   *
   * <p>This method is for use by internal operations, not for user-based search, which should use
   * the SearchManager interface.
   *
   * @param searchTerm
   * @return return possibly empty but non-null list of {@link Field}s
   */
  List<Field> findByTextContent(String searchTerm);
}
