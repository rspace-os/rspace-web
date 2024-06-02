package com.axiope.model.record.init;

import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.UserFolderSetup;

/**
 * Abstraction of persistence mechanism to prevent dependencies of example record folder on service
 * implementation.
 */
public interface IBuiltInPersistor {

  void saveRecord(StructuredDocument record);

  String loadImageReturnTextFieldLink(
      User user,
      String path,
      String name,
      Long fieldId,
      UserFolderSetup folders,
      int width,
      int height);

  RSForm findFormByName(String name);

  void saveForm(RSForm form);

  void saveSampleTemplate(Sample template);
}
