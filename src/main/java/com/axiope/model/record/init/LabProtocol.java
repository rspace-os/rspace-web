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
    return getMessage("form.labProtocol.name");
  }

  @Override
  public RSForm createForm(User createdBy) {
    String[] textFieldNames = {
      getMessage("form.labProtocol.safety"),
      getMessage("form.labProtocol.method"),
      getMessage("form.labProtocol.materials")
    };
    String[] dateFieldNames = {
      getMessage("form.labProtocol.dateCreated"), getMessage("form.labProtocol.dateReviewed")
    };

    RSForm form =
        new RSForm(
            getMessage("form.labProtocol.name"),
            getMessage("form.labProtocol.description"),
            createdBy);
    form.setCurrent(true);

    // text fields first
    int colindex = 1;
    for (String fieldName : textFieldNames) {
      FieldForm field = new TextFieldForm(fieldName);
      field.setColumnIndex(colindex++);
      form.addFieldForm(field);
    }

    // then date fields
    for (String fieldName : dateFieldNames) {
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
    ArrayList<StructuredDocument> templates = new ArrayList<>();

    StructuredDocument template =
        recordFactory.createStructuredDocument(
            getMessage("form.labProtocolT1.name"), createdBy, m_form);

    template
        .getField(getMessage("form.labProtocol.safety"))
        .setFieldData(getMessage("form.labProtocolT1.safetyFieldValue"));
    template
        .getField(getMessage("form.labProtocol.materials"))
        .setFieldData(getMessage("form.labProtocolT1.materialsFieldValue"));
    template
        .getField(getMessage("form.labProtocol.method"))
        .setFieldData(getMessage("form.labProtocolT1.methodFieldValue"));

    template.addType(RecordType.TEMPLATE);
    templates.add(template);
    markAsTemplate(template);
    return templates;
  }

  @Override
  public List<StructuredDocument> createExamples(User createdBy, UserFolderSetup folderSetup) {
    ArrayList<StructuredDocument> examples = new ArrayList<>();
    if (m_form == null) {
      log.warn("Can't create example from form " + getFormName() + " - does not exist!");
      return examples;
    }

    StructuredDocument example =
        recordFactory.createStructuredDocument(
            getMessage("form.labProtocolE1.name"), createdBy, m_form);
    example
        .getField(getMessage("form.labProtocol.safety"))
        .setFieldData(getStartupHTMLData(getMessage("form.labProtocolE1.safety")));
    example
        .getField(getMessage("form.labProtocol.method"))
        .setFieldData(getStartupHTMLData(getMessage("form.labProtocolE1.method")));
    example
        .getField(getMessage("form.labProtocol.dateCreated"))
        .setFieldData(getMessage("form.labProtocolE1.dateCreated"));
    example
        .getField(getMessage("form.labProtocol.dateReviewed"))
        .setFieldData(getMessage("form.labProtocolE1.dateReviewed"));
    m_initializer.saveRecord(example);
    examples.add(example);

    return examples;
  }

  @Override
  public String getFormIconName() {
    return "Labprotocol32.png";
  }
}
