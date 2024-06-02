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
    String[] textFieldKeys =
        new String[] {"VolumesAntibodyUsed", "PlatePlan", "Protocol", "Changes", "Results"};

    ResourceBundle resources = getResourceBundle();

    RSForm form =
        new RSForm(
            resources.getString("form.elisa.name"),
            resources.getString("form.elisa.description"),
            createdBy);
    form.setCurrent(true);

    int colindex = 1;
    FieldForm field = null;

    field = new TextFieldForm(resources.getString("form.elisa.Samples"));
    field.setColumnIndex(colindex++);
    form.addFieldForm(field);

    ChoiceFieldForm choicefield = new ChoiceFieldForm(resources.getString("form.elisa.Cytokines"));
    String choices = "fieldChoices=" + resources.getString("form.elisa.Cytokines.choice1");
    for (int i = 2; i < 15; i++) {
      choices += "&fieldChoices=" + resources.getString("form.elisa.Cytokines.choice" + i);
    }
    choicefield.setChoiceOptions(choices);
    choicefield.setColumnIndex(colindex++);
    form.addFieldForm(choicefield);

    // then rest of text fields
    for (String fieldKey : textFieldKeys) {
      String resourceKey = "form.elisa." + fieldKey;
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
    ArrayList<StructuredDocument> templates = new ArrayList<StructuredDocument>();

    StructuredDocument template =
        recordFactory.createStructuredDocument(
            resources.getString("form.elisaT1.name"), createdBy, m_form);

    template
        .getField(resources.getString("form.elisa.VolumesAntibodyUsed"))
        .setFieldData(resources.getString("form.elisaT1.VolumesAntibodyUsedfieldvalue"));
    template
        .getField(resources.getString("form.elisa.Protocol"))
        .setFieldData(resources.getString("form.elisaT1.Protocolfieldvalue"));

    markAsTemplate(template);
    templates.add(template);

    return templates;
  }

  @Override
  public List<StructuredDocument> createExamples(User createdBy, UserFolderSetup folderSetup) {
    ResourceBundle resources = getResourceBundle();
    List<StructuredDocument> examples = new ArrayList<StructuredDocument>();
    if (m_form == null) {
      log.warn("Can't create example from form {} - does not exist!", getFormName());
      return examples;
    }
    StructuredDocument example =
        recordFactory.createStructuredDocument(
            resources.getString("form.elisaE1.name"), createdBy, m_form);

    m_initializer.saveRecord(example);
    example
        .getField(resources.getString("form.elisa.Samples"))
        .setFieldData(getStartupHTMLData(resources.getString("form.elisaE1.Samples")));
    ChoiceField field = (ChoiceField) example.getField(resources.getString("form.elisa.Cytokines"));
    // code up weird string required to set a choice field
    String val =
        "fieldSelectedChoicesFinal_"
            + field.getId()
            + "="
            + resources.getString("form.elisaE1.Cytokines");
    field.setFieldData(val);
    example
        .getField(resources.getString("form.elisa.VolumesAntibodyUsed"))
        .setFieldData(getStartupHTMLData(resources.getString("form.elisaE1.VolumesAntibodyUsed")));
    example
        .getField(resources.getString("form.elisa.PlatePlan"))
        .setFieldData(getStartupHTMLData(resources.getString("form.elisaE1.PlatePlan")));
    example
        .getField(resources.getString("form.elisa.Protocol"))
        .setFieldData(getStartupHTMLData(resources.getString("form.elisaE1.Protocol")));
    example
        .getField(resources.getString("form.elisa.Changes"))
        .setFieldData(getStartupHTMLData(resources.getString("form.elisaE1.Changes")));
    example
        .getField(resources.getString("form.elisa.Results"))
        .setFieldData(getStartupHTMLData(resources.getString("form.elisaE1.Results")));
    m_initializer.saveRecord(example);
    examples.add(example);

    return examples;
  }
}
