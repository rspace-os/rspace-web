package com.axiope.model.record.init;

import com.researchspace.model.User;
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

public class RtPCR extends BuiltinContent implements IBuiltinContent {

  public RtPCR(IBuiltInPersistor initializer) {
    super(initializer);
  }

  public RtPCR() {}

  @Override
  protected String getFormName() {
    ResourceBundle resources = getResourceBundle();
    return resources.getString("form.rtpcr.name");
  }

  @Override
  public RSForm createForm(User createdBy) {
    String[] textFieldKeys = {
      "form.rtpcr.template",
      "form.rtpcr.masterMix",
      "form.rtpcr.cyclingParameters",
      "form.rtpcr.primers",
      "form.rtpcr.results",
      "form.rtpcr.positives",
      "form.rtpcr.discussion",
      "form.rtpcr.toDo",
    };

    ResourceBundle resources = getResourceBundle();

    RSForm form =
        new RSForm(
            resources.getString("form.rtpcr.name"),
            resources.getString("form.rtpcr.description"),
            createdBy);
    form.setCurrent(true);

    int colindex = 1;
    FieldForm dateField = new DateFieldForm(resources.getString("form.rtpcr.date"));
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
    return "PCR 32.png";
  }

  @Override
  public List<StructuredDocument> createTemplates(User createdBy) {
    return new ArrayList<>();
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
            resources.getString("form.rtpcrE1.name"), createdBy, m_form);

    // persist so images can be added, they need a database id
    m_initializer.saveRecord(example);
    example
        .getField(resources.getString("form.rtpcr.date"))
        .setFieldData(resources.getString("form.rtpcrE1.date"));
    example
        .getField(resources.getString("form.rtpcr.template"))
        .setFieldData(getStartupHTMLData(resources.getString("form.rtpcrE1.template")));
    example
        .getField(resources.getString("form.rtpcr.masterMix"))
        .setFieldData(getStartupHTMLData(resources.getString("form.rtpcrE1.masterMix")));
    example
        .getField(resources.getString("form.rtpcr.cyclingParameters"))
        .setFieldData(getStartupHTMLData(resources.getString("form.rtpcrE1.cyclingParameters")));
    example
        .getField(resources.getString("form.rtpcr.primers"))
        .setFieldData(getStartupHTMLData(resources.getString("form.rtpcrE1.primers")));
    TextField method = (TextField) example.getField(resources.getString("form.rtpcr.results"));
    String first = getStartupHTMLData(resources.getString("form.rtpcrE1.results"));
    String second =
        m_initializer.loadImageReturnTextFieldLink(
            createdBy,
            "StartUpData/" + resources.getString("form.rtpcrE1.resultsImage"),
            "E1_Picture1.png",
            method.getId(),
            folderSetup,
            0,
            0); // full size
    method.setFieldData(first + second);
    example
        .getField(resources.getString("form.rtpcr.positives"))
        .setFieldData(getStartupHTMLData(resources.getString("form.rtpcrE1.positives")));
    example
        .getField(resources.getString("form.rtpcr.discussion"))
        .setFieldData(getStartupHTMLData(resources.getString("form.rtpcrE1.discussion")));
    example
        .getField(resources.getString("form.rtpcr.toDo"))
        .setFieldData(getStartupHTMLData(resources.getString("form.rtpcrE1.toDo")));
    m_initializer.saveRecord(example);
    examples.add(example);

    return examples;
  }
}
