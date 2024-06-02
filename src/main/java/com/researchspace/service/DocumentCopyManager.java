package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.RecordCopyResult;

/** Copying-related code refactored from {@link RecordManager} */
public interface DocumentCopyManager {

  /**
   * Copies document, field and associated elements
   *
   * @param original
   * @param newname
   * @param user
   * @param targetFolder
   * @return
   */
  RecordCopyResult copy(Record original, String newname, User user, Folder targetFolder);

  /**
   * Copies content into the specified {@link Field} in the given {@link StructuredDocument}
   *
   * @param destFieldId
   * @param destRecord
   * @param content
   * @param user
   * @return The modified content
   */
  String copyElementsInContent(Long destFieldId, Record destRecord, String content, User user);
}
