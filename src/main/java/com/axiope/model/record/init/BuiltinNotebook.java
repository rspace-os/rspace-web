package com.axiope.model.record.init;

import com.researchspace.model.User;
import com.researchspace.model.field.TextField;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.UserFolderSetup;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class BuiltinNotebook extends BuiltinContent implements IBuiltinContent {

  private static final String START_UP_DATA = "StartUpData/";

  public BuiltinNotebook(IBuiltInPersistor initializer) {
    super(initializer);
  }

  public BuiltinNotebook() {}

  public void setForm(RSForm form) {
    m_form = form;
  }

  public RSForm createForm(User createdBy) {
    logDebug("createForm method should not be called for BuiltinNotebook");
    return null;
  }

  @Override
  public ArrayList<StructuredDocument> createTemplates(User createdBy) {
    logDebug("createTemplates method should not be called for BuiltinNotebook");
    return null;
  }

  public String getFormIconName() {
    logDebug("getFormIconName method should not be called for BuiltinNotebook");
    return null;
  }

  @Override
  public List<StructuredDocument> createExamples(User createdBy, UserFolderSetup folders) {
    ResourceBundle resources = getResourceBundle();
    ArrayList<StructuredDocument> examples = new ArrayList<>();

    StructuredDocument example =
        recordFactory.createStructuredDocument(
            resources.getString("form.notebookE1.name"), createdBy, m_form);

    example
        .getField("Data")
        .setFieldData(getStartupHTMLData(resources.getString("form.notebookE1.Content")));
    m_initializer.saveRecord(example);
    examples.add(example);

    example =
        recordFactory.createStructuredDocument(
            resources.getString("form.notebookE2.name"), createdBy, m_form);
    m_initializer.saveRecord(example);
    TextField content = (TextField) example.getField("Data");
    String strA = getStartupHTMLData(resources.getString("form.notebookE2.ContentA"));
    String strB =
        m_initializer.loadImageReturnTextFieldLink(
            createdBy,
            START_UP_DATA + resources.getString("form.notebookE2.ContentB"),
            "Notebook_E2_Picture1.png",
            content.getId(),
            folders,
            0,
            0);
    String strC = getStartupHTMLData(resources.getString("form.notebookE2.ContentC"));
    String strD =
        m_initializer.loadImageReturnTextFieldLink(
            createdBy,
            START_UP_DATA + resources.getString("form.notebookE2.ContentD"),
            "Notebook_E2_Picture2.png",
            content.getId(),
            folders,
            600,
            0);
    String strE = getStartupHTMLData(resources.getString("form.notebookE2.ContentE"));
    String strF =
        m_initializer.loadImageReturnTextFieldLink(
            createdBy,
            START_UP_DATA + resources.getString("form.notebookE2.ContentF"),
            "Notebook_E2_Picture3.png",
            content.getId(),
            folders,
            500,
            0);
    String strG =
        m_initializer.loadImageReturnTextFieldLink(
            createdBy,
            START_UP_DATA + resources.getString("form.notebookE2.ContentG"),
            "Notebook_E2_Picture4.png",
            content.getId(),
            folders,
            600,
            0);
    String strH =
        m_initializer.loadImageReturnTextFieldLink(
            createdBy,
            START_UP_DATA + resources.getString("form.notebookE2.ContentH"),
            "Notebook_E2_Picture5.png",
            content.getId(),
            folders,
            600,
            0);
    content.setFieldData(
        strA + strB + strC + strD + strE + "<p>" + strF + "</p><p>" + strG + "</p><p>" + strH
            + "</p>");
    examples.add(example);

    return examples;
  }

  @Override
  protected String getFormName() {
    return null;
  }
}
