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
import java.util.Locale;
import java.util.StringJoiner;

public class Elisa extends BuiltinContent implements IBuiltinContent {

  public Elisa(IBuiltInPersistor initializer) {
    super(initializer);
  }

  public Elisa(IBuiltInPersistor initializer, Locale locale) {
    super(initializer, locale);
  }

  public Elisa() {}

  @Override
  protected String getFormName() {
    return getMessage("form.elisa.name");
  }

  public RSForm createForm(User createdBy) {
    String[] textFieldNames = {
      getMessage("form.elisa.volumesAntibodyUsed"),
      getMessage("form.elisa.platePlan"),
      getMessage("form.elisa.protocol"),
      getMessage("form.elisa.changes"),
      getMessage("form.elisa.results")
    };

    RSForm form =
        new RSForm(getMessage("form.elisa.name"), getMessage("form.elisa.description"), createdBy);
    form.setCurrent(true);

    int colindex = 1;
    FieldForm field = new TextFieldForm(getMessage("inventory:recordTypes.sample.plural"));
    field.setColumnIndex(colindex++);
    form.addFieldForm(field);

    ChoiceFieldForm choicefield = new ChoiceFieldForm(getMessage("form.elisa.cytokines.label"));
    String[] cytokineChoices = {
      getMessage("form.elisa.cytokines.choice1"),
      getMessage("form.elisa.cytokines.choice2"),
      getMessage("form.elisa.cytokines.choice3"),
      getMessage("form.elisa.cytokines.choice4"),
      getMessage("form.elisa.cytokines.choice5"),
      getMessage("form.elisa.cytokines.choice6"),
      getMessage("form.elisa.cytokines.choice7"),
      getMessage("form.elisa.cytokines.choice8"),
      getMessage("form.elisa.cytokines.choice9"),
      getMessage("form.elisa.cytokines.choice10"),
      getMessage("form.elisa.cytokines.choice11"),
      getMessage("form.elisa.cytokines.choice12"),
      getMessage("form.elisa.cytokines.choice13"),
      getMessage("form.elisa.cytokines.choice14")
    };
    StringJoiner choices = new StringJoiner("&");
    for (String choice : cytokineChoices) {
      choices.add("fieldChoices=" + choice);
    }
    choicefield.setChoiceOptions(choices.toString());
    choicefield.setColumnIndex(colindex++);
    form.addFieldForm(choicefield);

    // then rest of text fields
    for (String fieldName : textFieldNames) {
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
    ArrayList<StructuredDocument> templates = new ArrayList<>();

    StructuredDocument template =
        recordFactory.createStructuredDocument(getMessage("form.elisaT1.name"), createdBy, m_form);

    template
        .getField(getMessage("form.elisa.volumesAntibodyUsed"))
        .setFieldData(getMessage("form.elisaT1.volumesAntibodyUsedFieldValue"));
    template
        .getField(getMessage("form.elisa.protocol"))
        .setFieldData(getMessage("form.elisaT1.protocolFieldValue"));

    markAsTemplate(template);
    templates.add(template);

    return templates;
  }

  @Override
  public List<StructuredDocument> createExamples(User createdBy, UserFolderSetup folderSetup) {
    List<StructuredDocument> examples = new ArrayList<>();
    if (m_form == null) {
      log.warn("Can't create example from form {} - does not exist!", getFormName());
      return examples;
    }
    StructuredDocument example =
        recordFactory.createStructuredDocument(getMessage("form.elisaE1.name"), createdBy, m_form);

    m_initializer.saveRecord(example);
    example
        .getField(getMessage("inventory:recordTypes.sample.plural"))
        .setFieldData(getStartupHTMLData(getMessage("form.elisaE1.samples")));
    ChoiceField field = (ChoiceField) example.getField(getMessage("form.elisa.cytokines.label"));
    // code up weird string required to set a choice field
    String val =
        "fieldSelectedChoicesFinal_" + field.getId() + "=" + getMessage("form.elisaE1.cytokines");
    field.setFieldData(val);
    example
        .getField(getMessage("form.elisa.volumesAntibodyUsed"))
        .setFieldData(getStartupHTMLData(getMessage("form.elisaE1.volumesAntibodyUsed")));
    example
        .getField(getMessage("form.elisa.platePlan"))
        .setFieldData(getStartupHTMLData(getMessage("form.elisaE1.platePlan")));
    example
        .getField(getMessage("form.elisa.protocol"))
        .setFieldData(getStartupHTMLData(getMessage("form.elisaE1.protocol")));
    example
        .getField(getMessage("form.elisa.changes"))
        .setFieldData(getStartupHTMLData(getMessage("form.elisaE1.changes")));
    example
        .getField(getMessage("form.elisa.results"))
        .setFieldData(getStartupHTMLData(getMessage("form.elisaE1.results")));
    m_initializer.saveRecord(example);
    examples.add(example);

    return examples;
  }
}
