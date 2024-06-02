package com.researchspace.service.impl;

import com.axiope.model.record.init.IBuiltInPersistor;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.UserFolderSetup;

class BuiltInPersistorFacade implements IBuiltInPersistor {

  private AbstractContentInitializer delegate;

  public BuiltInPersistorFacade(AbstractContentInitializer contentInitializerManager) {
    this.delegate = contentInitializerManager;
  }

  @Override
  public void saveRecord(StructuredDocument record) {
    delegate.saveRecord(record);
  }

  @Override
  public String loadImageReturnTextFieldLink(
      User user,
      String path,
      String name,
      Long fieldId,
      UserFolderSetup folders,
      int width,
      int height) {
    return delegate.loadImageReturnTextFieldLink(user, path, name, fieldId, folders, width, height);
  }

  @Override
  public RSForm findFormByName(String name) {
    return delegate.findFormByName(name);
  }

  @Override
  public void saveForm(RSForm form) {
    delegate.saveForm(form);
  }

  @Override
  public void saveSampleTemplate(Sample template) {
    delegate.saveSampleTemplate(template);
  }
}
