package com.axiope.model.record.init;

import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.field.DateFieldForm;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.FormState;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.UserFolderSetup;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class LabProtocol extends BuiltinContent implements IBuiltinContent {

  public LabProtocol(IBuiltInPersistor initializer) {
    super(initializer);
  }

  public LabProtocol() {}

  @Override
  protected String getFormName() {
    ResourceBundle resources = getResourceBundle();
    return resources.getString("form.labprotocol.name");
  }

  @Override
  public RSForm createForm(User createdBy) {
    String[] textFieldKeys = new String[] {"Safety", "Method", "Materials"};
    String[] dateFieldKeys = new String[] {"DateCreated", "DateReviewed"};
    ResourceBundle resources = getResourceBundle();

    RSForm form =
        new RSForm(
            resources.getString("form.labprotocol.name"),
            resources.getString("form.labprotocol.description"),
            createdBy);
    form.setCurrent(true);

    // text fields first
    int colindex = 1;
    for (String fieldKey : textFieldKeys) {
      String resourceKey = "form.labprotocol." + fieldKey;
      String fieldName = resources.getString(resourceKey);
      FieldForm field = new TextFieldForm(fieldName);
      field.setColumnIndex(colindex++);
      form.addFieldForm(field);
    }

    // then date fields
    for (String fieldKey : dateFieldKeys) {
      String resourceKey = "form.labprotocol." + fieldKey;
      String fieldName = resources.getString(resourceKey);
      FieldForm field = new DateFieldForm(fieldName);
      field.setColumnIndex(colindex++);
      form.addFieldForm(field);
    }

    form.setPublishingState(FormState.PUBLISHED);
    form.getAccessControl().setWorldPermissionType(PermissionType.READ);
    m_initializer.saveForm(form);
    m_form = form;
    return form;
  }

  public List<StructuredDocument> createTemplates(User createdBy) {
    ResourceBundle resources = getResourceBundle();
    ArrayList<StructuredDocument> templates = new ArrayList<StructuredDocument>();

    StructuredDocument template =
        recordFactory.createStructuredDocument(
            resources.getString("form.labprotocolT1.name"), createdBy, m_form);

    template
        .getField(resources.getString("form.labprotocol.Safety"))
        .setFieldData(resources.getString("form.labprotocolT1.Safetyfieldvalue"));
    template
        .getField(resources.getString("form.labprotocol.Materials"))
        .setFieldData(resources.getString("form.labprotocolT1.Materialsfieldvalue"));
    template
        .getField(resources.getString("form.labprotocol.Method"))
        .setFieldData(resources.getString("form.labprotocolT1.Methodfieldvalue"));

    template.addType(RecordType.TEMPLATE);
    templates.add(template);
    markAsTemplate(template);
    return templates;
  }

  @Override
  public List<StructuredDocument> createExamples(User createdBy, UserFolderSetup folderSetup) {
    ResourceBundle resources = getResourceBundle();
    ArrayList<StructuredDocument> examples = new ArrayList<StructuredDocument>();
    if (m_form == null) {
      log.warn("Can't create example from form " + getFormName() + " - does not exist!");
      return examples;
    }

    StructuredDocument example =
        recordFactory.createStructuredDocument(
            resources.getString("form.labprotocolE1.name"), createdBy, m_form);
    example
        .getField(resources.getString("form.labprotocol.Safety"))
        .setFieldData(getStartupHTMLData(resources.getString("form.labprotocolE1.Safety")));
    example
        .getField(resources.getString("form.labprotocol.Method"))
        .setFieldData(getStartupHTMLData(resources.getString("form.labprotocolE1.Method")));
    example
        .getField(resources.getString("form.labprotocol.DateCreated"))
        .setFieldData(resources.getString("form.labprotocolE1.DateCreated"));
    example
        .getField(resources.getString("form.labprotocol.DateReviewed"))
        .setFieldData(resources.getString("form.labprotocolE1.DateReviewed"));
    m_initializer.saveRecord(example);
    examples.add(example);

    return examples;
  }

  @Override
  public String getFormIconName() {
    return "Labprotocol32.png";
  }
}
