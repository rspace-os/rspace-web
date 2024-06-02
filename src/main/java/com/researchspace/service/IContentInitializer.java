package com.researchspace.service;

import com.researchspace.model.inventory.Sample;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import org.springframework.transaction.annotation.Transactional;

/**
 * Initializer interface to populate the application programmatically on startup or on user login
 * with example records, etc
 *
 * <p>AbstractContentInitializer provides a lot of re-usable code to create the standard user
 * folders, and should be considered as a base class for any new implementations of this interface.
 */
@Transactional
public interface IContentInitializer {

  /**
   * Initialise record system. This method will check to see if is already initialised.
   *
   * @param userId The id of the user
   * @return The user's root folder
   * @throws IllegalAddChildOperation
   */
  InitializedContent init(Long userId) throws IllegalAddChildOperation;

  void saveRecord(StructuredDocument record);

  void saveForm(RSForm form);

  void saveSampleTemplate(Sample sampleTemplate);

  /**
   * Optional configuration to disable the custom initialization of forms and records for testing.
   *
   * @param isActive
   * @return
   */
  void setCustomInitActive(boolean isActive);

  RSForm findFormByName(String name);

  void setUserFolderCreator(UserFolderCreator setup);
}
