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
    String[] textFieldKeys =
        new String[] {
          "Template",
          "MasterMix",
          "CyclingParameters",
          "Primers",
          "Results",
          "Positives",
          "Discussion",
          "To-do",
        };

    String[] dateFieldKeys = new String[] {"Date"};
    ResourceBundle resources = getResourceBundle();

    RSForm form =
        new RSForm(
            resources.getString("form.rtpcr.name"),
            resources.getString("form.rtpcr.description"),
            createdBy);
    form.setCurrent(true);

    // date field first
    int colindex = 1;
    for (String fieldKey : dateFieldKeys) {
      String resourceKey = "form.rtpcr." + fieldKey;
      String fieldName = resources.getString(resourceKey);
      FieldForm field = new DateFieldForm(fieldName);
      field.setColumnIndex(colindex++);
      form.addFieldForm(field);
    }

    // then text fields
    for (String fieldKey : textFieldKeys) {
      String resourceKey = "form.rtpcr." + fieldKey;
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
    // ResourceBundle resources = getResourceBundle();
    ArrayList<StructuredDocument> templates = new ArrayList<StructuredDocument>();
    /*
     * StructuredDocument template = recordFactory.createStructuredDocument(
     * resources.getString("form.rtpcrT1.name"),createdBy, m_form);
     *
     * m_initializer.saveRecord(template);
     * template.getField(resources.getString("form.rtpcr.Date"
     * )).setFieldData(resources.getString("form.rtpcrE1.Date"));
     * m_initializer.saveRecord(template); markAsTemplate(template);
     * templates.add(template);
     */
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
            resources.getString("form.rtpcrE1.name"), createdBy, m_form);

    // persist so images can be added, they need a database id
    m_initializer.saveRecord(example);
    example
        .getField(resources.getString("form.rtpcr.Date"))
        .setFieldData(resources.getString("form.rtpcrE1.Date"));
    example
        .getField(resources.getString("form.rtpcr.Template"))
        .setFieldData(getStartupHTMLData(resources.getString("form.rtpcrE1.Template")));
    example
        .getField(resources.getString("form.rtpcr.MasterMix"))
        .setFieldData(getStartupHTMLData(resources.getString("form.rtpcrE1.MasterMix")));
    example
        .getField(resources.getString("form.rtpcr.CyclingParameters"))
        .setFieldData(getStartupHTMLData(resources.getString("form.rtpcrE1.CyclingParameters")));
    example
        .getField(resources.getString("form.rtpcr.Primers"))
        .setFieldData(getStartupHTMLData(resources.getString("form.rtpcrE1.Primers")));
    TextField method = (TextField) example.getField(resources.getString("form.rtpcr.Results"));
    String first = getStartupHTMLData(resources.getString("form.rtpcrE1.Results"));
    String second =
        m_initializer.loadImageReturnTextFieldLink(
            createdBy,
            "StartUpData/" + resources.getString("form.rtpcrE1.ResultsImage"),
            "E1_Picture1.png",
            method.getId(),
            folderSetup,
            0,
            0); // full size
    method.setFieldData(first + second);
    example
        .getField(resources.getString("form.rtpcr.Positives"))
        .setFieldData(getStartupHTMLData(resources.getString("form.rtpcrE1.Positives")));
    example
        .getField(resources.getString("form.rtpcr.Discussion"))
        .setFieldData(getStartupHTMLData(resources.getString("form.rtpcrE1.Discussion")));
    example
        .getField(resources.getString("form.rtpcr.To-do"))
        .setFieldData(getStartupHTMLData(resources.getString("form.rtpcrE1.To-do")));
    m_initializer.saveRecord(example);
    examples.add(example);

    return examples;
  }
}
