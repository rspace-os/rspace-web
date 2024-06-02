package com.researchspace.service.archive;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Contains results and information about the results of importing an archive, including validation.
 */
public class ImportArchiveReport implements Serializable {

  private static final long serialVersionUID = -1635730918179494207L;

  class ValidationResult implements Serializable {

    private static final long serialVersionUID = -7771551445392793055L;

    public ValidationResult(ImportValidationRule rule, ValidationTestResult result) {
      this.rule = rule;
      this.passed = result;
    }

    public ValidationResult() {}

    public ImportValidationRule getRule() {
      return rule;
    }

    public ValidationTestResult getPassed() {
      return passed;
    }

    public String getDescription() {
      return rule.getDesc();
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + getOuterType().hashCode();
      result = prime * result + ((rule == null) ? 0 : rule.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      ValidationResult other = (ValidationResult) obj;
      if (!getOuterType().equals(other.getOuterType())) {
        return false;
      }
      if (rule != other.rule) {
        return false;
      }
      return true;
    }

    private ImportValidationRule rule;
    private ValidationTestResult passed = ValidationTestResult.UNTESTED;

    private ImportArchiveReport getOuterType() {
      return ImportArchiveReport.this;
    }
  }

  private ErrorList errorList;

  private ErrorList infoList;
  private Set<BaseRecord> importedRecords;
  private Set<EcatMediaFile> importedMediaItems;
  private Set<Notebook> importedNotebooks;

  private List<ValidationResult> results;

  private String expandedZipFolderPath;
  private boolean complete;
  private boolean validationComplete = false;

  public List<ValidationResult> getResults() {
    return results;
  }

  public ImportArchiveReport() {
    this.results = new ArrayList<ValidationResult>();
    for (ImportValidationRule rule : EnumSet.allOf(ImportValidationRule.class)) {
      results.add(new ValidationResult(rule, ValidationTestResult.UNTESTED));
    }
    this.errorList = new ErrorList();
    this.infoList = new ErrorList();
    this.complete = false;
    this.importedRecords = new HashSet<>();
    this.importedMediaItems = new HashSet<>();
    this.importedNotebooks = new HashSet<>();
  }

  public String getExpandedZipFolderPath() {
    return expandedZipFolderPath;
  }

  public void setExpandedZipFolderPath(String expandedZipFolderPath) {
    this.expandedZipFolderPath = expandedZipFolderPath;
  }

  /**
   * Boolean test for whether validation of the archive has completed.
   *
   * @return
   */
  public boolean isValidationComplete() {
    return validationComplete;
  }

  public void setValidationComplete(boolean validationComplete) {
    if (this.validationComplete) {
      return;
    }
    this.validationComplete = validationComplete;
  }

  /**
   * Boolean test for whether import process has completed
   *
   * @return
   */
  public boolean isComplete() {
    return complete;
  }

  /**
   * Sets import as complete. Once set as complete, further invocations have no effect.
   *
   * @param isComplete
   */
  public void setComplete(boolean isComplete) {
    if (this.complete) {
      return;
    }
    this.complete = isComplete;
  }

  /**
   * Marks import as successful if:
   *
   * <ul>
   *   <li>import is complete
   *   <li>Validation was successful
   *   <li>Import was successful
   * </ul>
   *
   * @return
   */
  public boolean isSuccessful() {
    if (!isComplete()) {
      return false;
    }
    return isValidationSuccessful() && !errorList.hasErrorMessages();
  }

  /**
   * Marks validation as successful if:
   *
   * <ul>
   *   <li>validation is complete
   *   <li>Validation was successful - no errors in mandatory {@link ImportValidationRule}s
   * </ul>
   *
   * @return
   */
  public boolean isValidationSuccessful() {
    if (!validationComplete) {
      return false;
    }
    for (ValidationResult result : results) {
      if (ValidationTestResult.FAIL.equals(result.passed) && result.rule.isMandatory()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Gets a set of imported records
   *
   * @return
   */
  @JsonIgnore
  // we access this dat from toRecordInfo
  public Set<BaseRecord> getImportedRecords() {
    return importedRecords;
  }

  public void addImportedRecord(StructuredDocument std) {
    importedRecords.add(std);
  }

  @JsonIgnore
  public Set<EcatMediaFile> getImportedMedia() {
    return importedMediaItems;
  }

  @JsonIgnore
  public Set<Notebook> getImportedNotebooks() {
    return importedNotebooks;
  }

  public void addImportedMedia(EcatMediaFile std) {
    importedMediaItems.add(std);
  }

  public void addImportedNotebook(Notebook nb) {
    importedNotebooks.add(nb);
  }

  public List<RecordInformation> getRecordInfo() {
    return importedRecords.stream().map(BaseRecord::toRecordInfo).collect(Collectors.toList());
  }

  /**
   * Sets a validation result, while {@link #isValidationComplete()} == <code>false</code>
   *
   * @param rule
   * @param isPassed
   */
  public void setValidationResult(ImportValidationRule rule, boolean isPassed) {
    if (isValidationComplete()) {
      return;
    }
    if (isPassed) {
      results.get(results.indexOf(new ValidationResult(rule, ValidationTestResult.PASS))).passed =
          ValidationTestResult.PASS;
    } else {
      results.get(results.indexOf(new ValidationResult(rule, ValidationTestResult.PASS))).passed =
          ValidationTestResult.FAIL;
    }
  }

  public ErrorList getErrorList() {
    return errorList;
  }

  public ErrorList getInfoList() {
    return infoList;
  }
}
