package com.axiope.model.record.init;

import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.field.DateFieldForm;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.TextField;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.FormState;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.UserFolderSetup;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Experiment extends BuiltinContent implements IBuiltinContent {

  // used for identifying this document in jmeter tests
  static final String EXAMPLE_TAG = "exampleExperimentTag";

  public Experiment(IBuiltInPersistor initializer) {
    super(initializer);
  }

  public Experiment(IBuiltInPersistor initializer, Locale locale) {
    super(initializer, locale);
  }

  public Experiment() {}

  @Override
  protected String getFormName() {
    return getMessage("form.experiment.name");
  }

  public RSForm createForm(User createdBy) {
    String[] textFieldNames = {
      getMessage("form.experiment.objectiveFieldName"),
      getMessage("form.experiment.methodFieldName"),
      getMessage("form.experiment.resultsFieldName"),
      getMessage("form.experiment.conclusionFieldName")
    };

    RSForm form =
        new RSForm(
            getMessage("form.experiment.name"),
            getMessage("form.experiment.description"),
            createdBy);
    form.setCurrent(true);

    int colindex = 1;
    FieldForm dateField = new DateFieldForm(getMessage("form.experiment.dateFieldName"));
    dateField.setColumnIndex(colindex++);
    form.addFieldForm(dateField);

    // then text fields
    for (String fieldName : textFieldNames) {
      FieldForm field = new TextFieldForm(fieldName);
      field.setColumnIndex(colindex++);
      form.addFieldForm(field);
    }

    form.setPublishingState(FormState.PUBLISHED);
    form.getAccessControl().setWorldPermissionType(PermissionType.READ);
    m_initializer.saveForm(form);
    m_form = form;
    return form;
  }

  @Override
  public String getFormIconName() {
    return "Experiment32.png";
  }

  public List<StructuredDocument> createTemplates(User createdBy) {
    ArrayList<StructuredDocument> templates = new ArrayList<>();

    StructuredDocument template =
        recordFactory.createStructuredDocument(
            getMessage("form.experimentT1.name"), createdBy, m_form);

    template
        .getField(getMessage("form.experiment.dateFieldName"))
        .setFieldData(getMessage("form.experimentT1.dateFieldValue"));
    template
        .getField(getMessage("form.experiment.methodFieldName"))
        .setFieldData(getStartupHTMLData(getMessage("form.experimentT1.methodFieldValue")));
    template
        .getField(getMessage("form.experiment.objectiveFieldName"))
        .setFieldData(getMessage("form.experimentT1.objectiveFieldValue"));
    template
        .getField(getMessage("form.experiment.resultsFieldName"))
        .setFieldData(getMessage("form.experimentT1.resultsFieldValue"));
    template
        .getField(getMessage("form.experiment.conclusionFieldName"))
        .setFieldData(getMessage("form.experimentT1.conclusionFieldValue"));
    m_initializer.saveRecord(template);
    markAsTemplate(template);
    templates.add(template);

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
            getMessage("form.experimentE2.name"), createdBy, m_form);
    example.addType(RecordType.NORMAL);
    example.setDocTag(EXAMPLE_TAG);

    m_initializer.saveRecord(example);
    example
        .getField(getMessage("form.experiment.dateFieldName"))
        .setFieldData(getMessage("form.experimentE2.dateFieldValue"));
    TextField method = (TextField) example.getField(getMessage("form.experiment.methodFieldName"));
    String first = getStartupHTMLData(getMessage("form.experimentE2.methodFieldValueA"));
    String second =
        m_initializer.loadImageReturnTextFieldLink(
            createdBy,
            "StartUpData/" + getMessage("form.experimentE2.methodFieldValueB"),
            "E2_Picture1.png",
            method.getId(),
            folderSetup,
            251,
            324);
    String third = getStartupHTMLData(getMessage("form.experimentE2.methodFieldValueC"));
    method.setFieldData(first + second + third);
    example
        .getField(getMessage("form.experiment.objectiveFieldName"))
        .setFieldData(getMessage("form.experimentE2.objectiveFieldValue"));
    TextField results =
        (TextField) example.getField(getMessage("form.experiment.resultsFieldName"));
    first = getStartupHTMLData(getMessage("form.experimentE2.resultsFieldValueA"));
    second =
        m_initializer.loadImageReturnTextFieldLink(
            createdBy,
            "StartUpData/" + getMessage("form.experimentE2.resultsFieldValueB"),
            "E2_Picture2.png",
            results.getId(),
            folderSetup,
            475,
            322);
    third = getStartupHTMLData(getMessage("form.experimentE2.resultsFieldValueC"));
    results.setFieldData(first + second + third);
    example
        .getField(getMessage("form.experiment.conclusionFieldName"))
        .setFieldData(getMessage("form.experimentE2.conclusionFieldValue"));
    m_initializer.saveRecord(example);
    examples.add(example);

    return examples;
  }
}
