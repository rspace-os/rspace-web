package com.researchspace.service.archive;

import jakarta.xml.bind.ValidationEvent;
import jakarta.xml.bind.ValidationEventHandler;

/**
 * Converts schema validation errors into a form for presentation in the {@link
 * ImportArchiveReport}.
 */
public class XMLImportSchemaValidator implements ValidationEventHandler {

  private ImportArchiveReport report;

  public XMLImportSchemaValidator(ImportArchiveReport report) {
    super();
    this.report = report;
  }

  @Override
  public boolean handleEvent(ValidationEvent event) {
    report.getErrorList().addErrorMsg(formatMsg(event));
    report.setValidationResult(ImportValidationRule.XMLSCHEMA, false);
    return true;
  }

  private String formatMsg(ValidationEvent event) {
    StringBuffer sb = new StringBuffer();
    sb.append("Severity:")
        .append(event.getSeverity())
        .append("Message:")
        .append(event.getMessage())
        .append(" at line ")
        .append(event.getLocator().getLineNumber() + "");
    return sb.toString();
  }
}
