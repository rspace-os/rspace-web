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
    String[] textFieldKeys =
        new String[] {
          "Objectivefieldname",
          // "Materialsfieldname",
          "Methodfieldname",
          "Resultsfieldname",
          "Conclusionfieldname"
        };
    String[] dateFieldKeys = new String[] {"Datefieldname"};
    ResourceBundle resources = getResourceBundle();

    RSForm form =
        new RSForm(
            resources.getString("form.experiment.name"),
            resources.getString("form.experiment.description"),
            createdBy);
    form.setCurrent(true);

    // date fields first
    int colindex = 1;
    for (String fieldKey : dateFieldKeys) {
      String resourceKey = "form.experiment." + fieldKey;
      String fieldName = resources.getString(resourceKey);
      FieldForm field = new DateFieldForm(fieldName);
      field.setColumnIndex(colindex++);
      form.addFieldForm(field);
    }

    // then text fields
    for (String fieldKey : textFieldKeys) {
      String resourceKey = "form.experiment." + fieldKey;
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
    ArrayList<StructuredDocument> templates = new ArrayList<StructuredDocument>();

    StructuredDocument template =
        recordFactory.createStructuredDocument(
            resources.getString("form.experimentT1.name"), createdBy, m_form);

    template
        .getField(resources.getString("form.experiment.Datefieldname"))
        .setFieldData(resources.getString("form.experimentT1.Datefieldvalue"));
    template
        .getField(resources.getString("form.experiment.Methodfieldname"))
        .setFieldData(
            getStartupHTMLData(resources.getString("form.experimentT1.Methodfieldvalue")));
    // template.getField(resources.getString("form.experiment.Materialsfieldname")).setFieldData(resources.getString("form.experimentT1.Materialsfieldvalue"));
    template
        .getField(resources.getString("form.experiment.Objectivefieldname"))
        .setFieldData(resources.getString("form.experimentT1.Objectivefieldvalue"));
    template
        .getField(resources.getString("form.experiment.Resultsfieldname"))
        .setFieldData(resources.getString("form.experimentT1.Resultsfieldvalue"));
    template
        .getField(resources.getString("form.experiment.Conclusionfieldname"))
        .setFieldData(resources.getString("form.experimentT1.Conclusionfieldvalue"));
    m_initializer.saveRecord(template);
    markAsTemplate(template);
    templates.add(template);

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
    /*
     * StructuredDocument example = recordFactory.createStructuredDocument(
     * resources.getString("form.experimentE1.name"),createdBy, m_form);
     * m_initializer.saveRecord(example);
     * example.getField(resources.getString("form.experiment.Datefieldname"
     * )).setFieldData(resources.getString(
     * "form.experimentE1.Datefieldvalue")); example.getField
     * (resources.getString("form.experiment.Methodfieldname")).setFieldData
     * (getStartupHTMLData
     * (resources.getString("form.experimentE1.Methodfieldvalue")));
     * example.getField(resources.getString
     * ("form.experiment.Objectivefieldname")).setFieldData(resources
     * .getString("form.experimentE1.Objectivefieldvalue"));
     * example.getField(resources.getString
     * ("form.experiment.Resultsfieldname")).setFieldData(resources
     * .getString("form.experimentE1.Resultsfieldvalue"));
     * example.getField(resources.getString(
     * "form.experiment.Conclusionfieldname"
     * )).setFieldData(resources.getString(
     * "form.experimentE1.Conclusionfieldvalue"));
     * m_initializer.saveRecord(example); examples.add(example);
     */
    StructuredDocument example =
        recordFactory.createStructuredDocument(
            resources.getString("form.experimentE2.name"), createdBy, m_form);
    example.addType(RecordType.NORMAL);
    example.setDocTag(EXAMPLE_TAG);

    m_initializer.saveRecord(example);
    example
        .getField(resources.getString("form.experiment.Datefieldname"))
        .setFieldData(resources.getString("form.experimentE2.Datefieldvalue"));
    TextField method =
        (TextField) example.getField(resources.getString("form.experiment.Methodfieldname"));
    String first = getStartupHTMLData(resources.getString("form.experimentE2.MethodfieldvalueA"));
    String second =
        m_initializer.loadImageReturnTextFieldLink(
            createdBy,
            "StartUpData/" + resources.getString("form.experimentE2.MethodfieldvalueB"),
            "E2_Picture1.png",
            method.getId(),
            folderSetup,
            251,
            324);
    String third = getStartupHTMLData(resources.getString("form.experimentE2.MethodfieldvalueC"));
    method.setFieldData(first + second + third);
    example
        .getField(resources.getString("form.experiment.Objectivefieldname"))
        .setFieldData(resources.getString("form.experimentE2.Objectivefieldvalue"));
    TextField results =
        (TextField) example.getField(resources.getString("form.experiment.Resultsfieldname"));
    first = getStartupHTMLData(resources.getString("form.experimentE2.ResultsfieldvalueA"));
    second =
        m_initializer.loadImageReturnTextFieldLink(
            createdBy,
            "StartUpData/" + resources.getString("form.experimentE2.ResultsfieldvalueB"),
            "E2_Picture2.png",
            results.getId(),
            folderSetup,
            475,
            322);
    third = getStartupHTMLData(resources.getString("form.experimentE2.ResultsfieldvalueC"));
    results.setFieldData(first + second + third);
    example
        .getField(resources.getString("form.experiment.Conclusionfieldname"))
        .setFieldData(resources.getString("form.experimentE2.Conclusionfieldvalue"));
    m_initializer.saveRecord(example);
    examples.add(example);

    return examples;
  }
}
