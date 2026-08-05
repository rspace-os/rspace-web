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
import java.util.Locale;
import java.util.ResourceBundle;

public class LabProtocol extends BuiltinContent implements IBuiltinContent {

  public LabProtocol(IBuiltInPersistor initializer) {
    super(initializer);
  }

  public LabProtocol(IBuiltInPersistor initializer, Locale locale) {
    super(initializer, locale);
  }

  public LabProtocol() {}

  @Override
  protected String getFormName() {
    ResourceBundle resources = getResourceBundle();
    return resources.getString("form.labProtocol.name");
  }

  @Override
  public RSForm createForm(User createdBy) {
    String[] textFieldKeys = {
      "form.labProtocol.safety", "form.labProtocol.method", "form.labProtocol.materials"
    };
    String[] dateFieldKeys = {"form.labProtocol.dateCreated", "form.labProtocol.dateReviewed"};
    ResourceBundle resources = getResourceBundle();

    RSForm form =
        new RSForm(
            resources.getString("form.labProtocol.name"),
            resources.getString("form.labProtocol.description"),
            createdBy);
    form.setCurrent(true);

    // text fields first
    int colindex = 1;
    for (String resourceKey : textFieldKeys) {
      String fieldName = resources.getString(resourceKey);
      FieldForm field = new TextFieldForm(fieldName);
      field.setColumnIndex(colindex++);
      form.addFieldForm(field);
    }

    // then date fields
    for (String resourceKey : dateFieldKeys) {
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
    ArrayList<StructuredDocument> templates = new ArrayList<>();

    StructuredDocument template =
        recordFactory.createStructuredDocument(
            resources.getString("form.labProtocolT1.name"), createdBy, m_form);

    template
        .getField(resources.getString("form.labProtocol.safety"))
        .setFieldData(resources.getString("form.labProtocolT1.safetyFieldValue"));
    template
        .getField(resources.getString("form.labProtocol.materials"))
        .setFieldData(resources.getString("form.labProtocolT1.materialsFieldValue"));
    template
        .getField(resources.getString("form.labProtocol.method"))
        .setFieldData(resources.getString("form.labProtocolT1.methodFieldValue"));

    template.addType(RecordType.TEMPLATE);
    templates.add(template);
    markAsTemplate(template);
    return templates;
  }

  @Override
  public List<StructuredDocument> createExamples(User createdBy, UserFolderSetup folderSetup) {
    ResourceBundle resources = getResourceBundle();
    ArrayList<StructuredDocument> examples = new ArrayList<>();
    if (m_form == null) {
      log.warn("Can't create example from form " + getFormName() + " - does not exist!");
      return examples;
    }

    StructuredDocument example =
        recordFactory.createStructuredDocument(
            resources.getString("form.labProtocolE1.name"), createdBy, m_form);
    example
        .getField(resources.getString("form.labProtocol.safety"))
        .setFieldData(getStartupHTMLData(resources.getString("form.labProtocolE1.safety")));
    example
        .getField(resources.getString("form.labProtocol.method"))
        .setFieldData(getStartupHTMLData(resources.getString("form.labProtocolE1.method")));
    example
        .getField(resources.getString("form.labProtocol.dateCreated"))
        .setFieldData(resources.getString("form.labProtocolE1.dateCreated"));
    example
        .getField(resources.getString("form.labProtocol.dateReviewed"))
        .setFieldData(resources.getString("form.labProtocolE1.dateReviewed"));
    m_initializer.saveRecord(example);
    examples.add(example);

    return examples;
  }

  @Override
  public String getFormIconName() {
    return "Labprotocol32.png";
  }
}
