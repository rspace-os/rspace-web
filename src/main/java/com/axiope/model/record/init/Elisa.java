package com.axiope.model.record.init;

import com.researchspace.model.User;
import com.researchspace.model.field.ChoiceField;
import com.researchspace.model.field.ChoiceFieldForm;
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
import java.util.StringJoiner;

public class Elisa extends BuiltinContent implements IBuiltinContent {

  public Elisa(IBuiltInPersistor initializer) {
    super(initializer);
  }

  public Elisa() {}

  @Override
  protected String getFormName() {
    ResourceBundle resources = getResourceBundle();
    return resources.getString("form.elisa.name");
  }

  public RSForm createForm(User createdBy) {
    String[] textFieldKeys = {
      "form.elisa.volumesAntibodyUsed",
      "form.elisa.platePlan",
      "form.elisa.protocol",
      "form.elisa.changes",
      "form.elisa.results"
    };

    ResourceBundle resources = getResourceBundle();

    RSForm form =
        new RSForm(
            resources.getString("form.elisa.name"),
            resources.getString("form.elisa.description"),
            createdBy);
    form.setCurrent(true);

    int colindex = 1;
    FieldForm field = new TextFieldForm(resources.getString("inventory:recordTypes.sample.plural"));
    field.setColumnIndex(colindex++);
    form.addFieldForm(field);

    ChoiceFieldForm choicefield =
        new ChoiceFieldForm(resources.getString("form.elisa.cytokines.label"));
    String[] cytokineChoiceKeys = {
      "form.elisa.cytokines.choice1",
      "form.elisa.cytokines.choice2",
      "form.elisa.cytokines.choice3",
      "form.elisa.cytokines.choice4",
      "form.elisa.cytokines.choice5",
      "form.elisa.cytokines.choice6",
      "form.elisa.cytokines.choice7",
      "form.elisa.cytokines.choice8",
      "form.elisa.cytokines.choice9",
      "form.elisa.cytokines.choice10",
      "form.elisa.cytokines.choice11",
      "form.elisa.cytokines.choice12",
      "form.elisa.cytokines.choice13",
      "form.elisa.cytokines.choice14"
    };
    StringJoiner choices = new StringJoiner("&");
    for (String choiceKey : cytokineChoiceKeys) {
      choices.add("fieldChoices=" + resources.getString(choiceKey));
    }
    choicefield.setChoiceOptions(choices.toString());
    choicefield.setColumnIndex(colindex++);
    form.addFieldForm(choicefield);

    // then rest of text fields
    for (String resourceKey : textFieldKeys) {
      String fieldName = resources.getString(resourceKey);
      field = new TextFieldForm(fieldName);
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
    return "ELISA32.png";
  }

  public List<StructuredDocument> createTemplates(User createdBy) {
    ResourceBundle resources = getResourceBundle();
    ArrayList<StructuredDocument> templates = new ArrayList<>();

    StructuredDocument template =
        recordFactory.createStructuredDocument(
            resources.getString("form.elisaT1.name"), createdBy, m_form);

    template
        .getField(resources.getString("form.elisa.volumesAntibodyUsed"))
        .setFieldData(resources.getString("form.elisaT1.volumesAntibodyUsedFieldValue"));
    template
        .getField(resources.getString("form.elisa.protocol"))
        .setFieldData(resources.getString("form.elisaT1.protocolFieldValue"));

    markAsTemplate(template);
    templates.add(template);

    return templates;
  }

  @Override
  public List<StructuredDocument> createExamples(User createdBy, UserFolderSetup folderSetup) {
    ResourceBundle resources = getResourceBundle();
    List<StructuredDocument> examples = new ArrayList<>();
    if (m_form == null) {
      log.warn("Can't create example from form {} - does not exist!", getFormName());
      return examples;
    }
    StructuredDocument example =
        recordFactory.createStructuredDocument(
            resources.getString("form.elisaE1.name"), createdBy, m_form);

    m_initializer.saveRecord(example);
    example
        .getField(resources.getString("inventory:recordTypes.sample.plural"))
        .setFieldData(getStartupHTMLData(resources.getString("form.elisaE1.samples")));
    ChoiceField field =
        (ChoiceField) example.getField(resources.getString("form.elisa.cytokines.label"));
    // code up weird string required to set a choice field
    String val =
        "fieldSelectedChoicesFinal_"
            + field.getId()
            + "="
            + resources.getString("form.elisaE1.cytokines");
    field.setFieldData(val);
    example
        .getField(resources.getString("form.elisa.volumesAntibodyUsed"))
        .setFieldData(getStartupHTMLData(resources.getString("form.elisaE1.volumesAntibodyUsed")));
    example
        .getField(resources.getString("form.elisa.platePlan"))
        .setFieldData(getStartupHTMLData(resources.getString("form.elisaE1.platePlan")));
    example
        .getField(resources.getString("form.elisa.protocol"))
        .setFieldData(getStartupHTMLData(resources.getString("form.elisaE1.protocol")));
    example
        .getField(resources.getString("form.elisa.changes"))
        .setFieldData(getStartupHTMLData(resources.getString("form.elisaE1.changes")));
    example
        .getField(resources.getString("form.elisa.results"))
        .setFieldData(getStartupHTMLData(resources.getString("form.elisaE1.results")));
    m_initializer.saveRecord(example);
    examples.add(example);

    return examples;
  }
}
