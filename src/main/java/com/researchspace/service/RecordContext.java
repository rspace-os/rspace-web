package com.researchspace.service;

/**
 * Some record operations need to be performed differently depending on how they were started. E.g.,
 * a user-initiated action, import, batch or scripted operation, etc., <br>
 * This interface defines methods that provides different behaviour depending on the context.
 */
public interface RecordContext {

  /**
   * Boolean test as to whether to allow records to be created from unpublished forms.
   *
   * @return <code>true</code> if records can be created</true>, <code>false</code> otherwise.
   */
  boolean ignoreUnpublishedForms();

  /**
   * Boolean test to shortcircuit permissions check and allow direct creation of templates in
   * template folders.
   *
   * @return
   */
  boolean enableDirectTemplateCreationInTemplateFolder();

  /**
   * Whether or not there is a recursive operation traversing multiple folders. Actions that might
   * apply to a single document may need to be deferred if that action is part of a longer
   * operation, such as folder copy, or folder import.
   *
   * @return
   */
  boolean isRecursiveFolderOperation();
}
