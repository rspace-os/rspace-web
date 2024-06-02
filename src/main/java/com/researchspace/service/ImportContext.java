package com.researchspace.service;

/** Context for import actions on documents. */
public class ImportContext extends AbstractRecordContext implements RecordContext {

  public ImportContext() {
    super(false);
  }

  public ImportContext(boolean recursiveFolderOperation) {
    super(recursiveFolderOperation);
  }

  /**
   * When importing, there is a situation that an exported record was created from a Form that has
   * subsequently been unpublished.
   *
   * <p>When we import, we create a new record based on the form, and populate it with field data
   * from the archive. We want to ignore unpublished forms, which normally result in an exception if
   * we try to create a record from them.<br>
   * Also templates may be directly added to template folder
   */
  @Override
  public boolean ignoreUnpublishedForms() {
    return true;
  }

  @Override
  public boolean enableDirectTemplateCreationInTemplateFolder() {
    return true;
  }
}
