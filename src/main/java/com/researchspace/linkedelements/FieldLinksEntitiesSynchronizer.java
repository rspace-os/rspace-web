package com.researchspace.linkedelements;

import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import java.util.List;

/** Updates FieldAttachment and InternalLink entities in response to text field edits */
public interface FieldLinksEntitiesSynchronizer {

  /**
   * Updates field/record/internal associations according to changes in field content. <br>
   *
   * @param persistentField the old (currently persisted {@link Field})
   * @param tempField the tempField (that stores autosaved content)
   * @param newFieldData the new (to be saved) field data;
   */
  FieldContentDelta syncFieldWithEntitiesOnautosave(
      Field persistentField, Field tempField, String newFieldData, User subject);

  /**
   * Undoes associations after cancelling.
   *
   * @param field
   * @param newFieldField
   * @return
   */
  FieldContentDelta revertSyncFieldWithEntitiesOnCancel(Field field, Field newFieldField);

  /**
   * Undos document-level associations from an autosave.
   *
   * @param doc
   * @param fieldChanges
   * @return
   */
  void revertSyncDocumentWithEntitiesOnCancel(
      StructuredDocument doc, List<FieldContentDelta> fieldChanges);
}
