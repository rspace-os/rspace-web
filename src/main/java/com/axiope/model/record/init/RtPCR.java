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
import java.util.Locale;

public class RtPCR extends BuiltinContent implements IBuiltinContent {

  public RtPCR(IBuiltInPersistor initializer) {
    super(initializer);
  }

  public RtPCR(IBuiltInPersistor initializer, Locale locale) {
    super(initializer, locale);
  }

  public RtPCR() {}

  @Override
  protected String getFormName() {
    return getMessage("form.rtpcr.name");
  }

  @Override
  public RSForm createForm(User createdBy) {
    String[] textFieldNames = {
      getMessage("form.rtpcr.template"),
      getMessage("form.rtpcr.masterMix"),
      getMessage("form.rtpcr.cyclingParameters"),
      getMessage("form.rtpcr.primers"),
      getMessage("form.rtpcr.results"),
      getMessage("form.rtpcr.positives"),
      getMessage("form.rtpcr.discussion"),
      getMessage("form.rtpcr.toDo"),
    };

    RSForm form =
        new RSForm(getMessage("form.rtpcr.name"), getMessage("form.rtpcr.description"), createdBy);
    form.setCurrent(true);

    int colindex = 1;
    FieldForm dateField = new DateFieldForm(getMessage("form.rtpcr.date"));
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
    return "PCR 32.png";
  }

  @Override
  public List<StructuredDocument> createTemplates(User createdBy) {
    return new ArrayList<>();
  }

  @Override
  public List<StructuredDocument> createExamples(User createdBy, UserFolderSetup folderSetup) {
    ArrayList<StructuredDocument> examples = new ArrayList<>();
    if (m_form == null) {
      log.warn("Can't create example from form " + getFormName() + " - does not exist!");
      return examples;
    }

    StructuredDocument example =
        recordFactory.createStructuredDocument(getMessage("form.rtpcrE1.name"), createdBy, m_form);

    // persist so images can be added, they need a database id
    m_initializer.saveRecord(example);
    example.getField(getMessage("form.rtpcr.date")).setFieldData(getMessage("form.rtpcrE1.date"));
    example
        .getField(getMessage("form.rtpcr.template"))
        .setFieldData(getStartupHTMLData(getMessage("form.rtpcrE1.template")));
    example
        .getField(getMessage("form.rtpcr.masterMix"))
        .setFieldData(getStartupHTMLData(getMessage("form.rtpcrE1.masterMix")));
    example
        .getField(getMessage("form.rtpcr.cyclingParameters"))
        .setFieldData(getStartupHTMLData(getMessage("form.rtpcrE1.cyclingParameters")));
    example
        .getField(getMessage("form.rtpcr.primers"))
        .setFieldData(getStartupHTMLData(getMessage("form.rtpcrE1.primers")));
    TextField method = (TextField) example.getField(getMessage("form.rtpcr.results"));
    String first = getStartupHTMLData(getMessage("form.rtpcrE1.results"));
    String second =
        m_initializer.loadImageReturnTextFieldLink(
            createdBy,
            "StartUpData/" + getMessage("form.rtpcrE1.resultsImage"),
            "E1_Picture1.png",
            method.getId(),
            folderSetup,
            0,
            0); // full size
    method.setFieldData(first + second);
    example
        .getField(getMessage("form.rtpcr.positives"))
        .setFieldData(getStartupHTMLData(getMessage("form.rtpcrE1.positives")));
    example
        .getField(getMessage("form.rtpcr.discussion"))
        .setFieldData(getStartupHTMLData(getMessage("form.rtpcrE1.discussion")));
    example
        .getField(getMessage("form.rtpcr.toDo"))
        .setFieldData(getStartupHTMLData(getMessage("form.rtpcrE1.toDo")));
    m_initializer.saveRecord(example);
    examples.add(example);

    return examples;
  }
}
