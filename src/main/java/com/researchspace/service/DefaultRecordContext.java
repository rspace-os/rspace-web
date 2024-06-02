package com.researchspace.service;

/** Context for the default, user-initiated actions */
public class DefaultRecordContext extends AbstractRecordContext implements RecordContext {

  public DefaultRecordContext(boolean recursiveFolderOperation) {
    super(recursiveFolderOperation);
  }

  public DefaultRecordContext() {
    this(false);
  }

  @Override
  public boolean ignoreUnpublishedForms() {
    return false;
  }

  @Override
  public boolean enableDirectTemplateCreationInTemplateFolder() {
    return false;
  }
}
