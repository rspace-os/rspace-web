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
import java.util.ResourceBundle;

public class Experiment extends BuiltinContent implements IBuiltinContent {

  // used for identifying this document in jmeter tests
  static final String EXAMPLE_TAG = "exampleExperimentTag";

  public Experiment(IBuiltInPersistor initializer) {
    super(initializer);
  }

  public Experiment() {}

  @Override
  protected String getFormName() {
    ResourceBundle resources = getResourceBundle();
    return resources.getString("form.experiment.name");
  }

  public RSForm createForm(User createdBy) {
    String[] textFieldKeys = {
      "form.experiment.objectiveFieldName",
      "form.experiment.methodFieldName",
      "form.experiment.resultsFieldName",
      "form.experiment.conclusionFieldName"
    };
    ResourceBundle resources = getResourceBundle();

    RSForm form =
        new RSForm(
            resources.getString("form.experiment.name"),
            resources.getString("form.experiment.description"),
            createdBy);
    form.setCurrent(true);

    int colindex = 1;
    FieldForm dateField = new DateFieldForm(resources.getString("form.experiment.dateFieldName"));
    dateField.setColumnIndex(colindex++);
    form.addFieldForm(dateField);

    // then text fields
    for (String resourceKey : textFieldKeys) {
      String fieldName = resources.getString(resourceKey);
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
    ResourceBundle resources = getResourceBundle();
    ArrayList<StructuredDocument> templates = new ArrayList<>();

    StructuredDocument template =
        recordFactory.createStructuredDocument(
            resources.getString("form.experimentT1.name"), createdBy, m_form);

    template
        .getField(resources.getString("form.experiment.dateFieldName"))
        .setFieldData(resources.getString("form.experimentT1.dateFieldValue"));
    template
        .getField(resources.getString("form.experiment.methodFieldName"))
        .setFieldData(
            getStartupHTMLData(resources.getString("form.experimentT1.methodFieldValue")));
    template
        .getField(resources.getString("form.experiment.objectiveFieldName"))
        .setFieldData(resources.getString("form.experimentT1.objectiveFieldValue"));
    template
        .getField(resources.getString("form.experiment.resultsFieldName"))
        .setFieldData(resources.getString("form.experimentT1.resultsFieldValue"));
    template
        .getField(resources.getString("form.experiment.conclusionFieldName"))
        .setFieldData(resources.getString("form.experimentT1.conclusionFieldValue"));
    m_initializer.saveRecord(template);
    markAsTemplate(template);
    templates.add(template);

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
            resources.getString("form.experimentE2.name"), createdBy, m_form);
    example.addType(RecordType.NORMAL);
    example.setDocTag(EXAMPLE_TAG);

    m_initializer.saveRecord(example);
    example
        .getField(resources.getString("form.experiment.dateFieldName"))
        .setFieldData(resources.getString("form.experimentE2.dateFieldValue"));
    TextField method =
        (TextField) example.getField(resources.getString("form.experiment.methodFieldName"));
    String first = getStartupHTMLData(resources.getString("form.experimentE2.methodFieldValueA"));
    String second =
        m_initializer.loadImageReturnTextFieldLink(
            createdBy,
            "StartUpData/" + resources.getString("form.experimentE2.methodFieldValueB"),
            "E2_Picture1.png",
            method.getId(),
            folderSetup,
            251,
            324);
    String third = getStartupHTMLData(resources.getString("form.experimentE2.methodFieldValueC"));
    method.setFieldData(first + second + third);
    example
        .getField(resources.getString("form.experiment.objectiveFieldName"))
        .setFieldData(resources.getString("form.experimentE2.objectiveFieldValue"));
    TextField results =
        (TextField) example.getField(resources.getString("form.experiment.resultsFieldName"));
    first = getStartupHTMLData(resources.getString("form.experimentE2.resultsFieldValueA"));
    second =
        m_initializer.loadImageReturnTextFieldLink(
            createdBy,
            "StartUpData/" + resources.getString("form.experimentE2.resultsFieldValueB"),
            "E2_Picture2.png",
            results.getId(),
            folderSetup,
            475,
            322);
    third = getStartupHTMLData(resources.getString("form.experimentE2.resultsFieldValueC"));
    results.setFieldData(first + second + third);
    example
        .getField(resources.getString("form.experiment.conclusionFieldName"))
        .setFieldData(resources.getString("form.experimentE2.conclusionFieldValue"));
    m_initializer.saveRecord(example);
    examples.add(example);

    return examples;
  }
}
