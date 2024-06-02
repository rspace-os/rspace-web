package com.axiope.model.record.init;

import com.researchspace.model.User;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.UserFolderSetup;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class Welcome extends BuiltinContent implements IBuiltinContent {

  public Welcome(IBuiltInPersistor initializer) {
    super(initializer);
  }

  public Welcome() {}

  public void setForm(RSForm form) {
    m_form = form;
  }

  public RSForm createForm(User createdBy) {
    logDebug("createForm method should not be called for Welcome");
    return null;
  }

  public ArrayList<StructuredDocument> createTemplates(User createdBy) {
    logDebug("createTemplates method should not be called for Welcome");
    return null;
  }

  public String getFormIconName() {
    logDebug("getFormIconName method should not be called for Welcome");
    return null;
  }

  @Override
  public List<StructuredDocument> createExamples(User createdBy, UserFolderSetup folders) {
    ResourceBundle resources = getResourceBundle();
    List<StructuredDocument> examples = new ArrayList<StructuredDocument>();

    StructuredDocument example =
        recordFactory.createStructuredDocument(
            resources.getString("form.welcome.name"), createdBy, m_form);

    example
        .getField("Data")
        .setFieldData(getStartupHTMLData(resources.getString("form.welcome.welcome1")));
    m_initializer.saveRecord(example);
    examples.add(example);

    return examples;
  }

  @Override
  protected String getFormName() {
    return null;
  }
}
