package com.researchspace.service.archive;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ImportArchiveReportTest {

  @BeforeEach
  public void setUp() throws Exception {
    report = new ImportArchiveReport();
  }

  ImportArchiveReport report;

  @Test
  public void testInitialState() {
    assertFalse(report.isComplete());
    assertFalse(report.isValidationComplete());
    assertFalse(report.isSuccessful());
    assertFalse(report.isValidationSuccessful());
    assertTrue(report.getImportedRecords().isEmpty());
    assertFalse(report.getErrorList().hasErrorMessages());
  }

  @Test
  public void testValidation() {
    // iniital state
    assertFalse(report.isValidationSuccessful());
    report.setValidationComplete(true);
    assertTrue(report.isValidationComplete());
    // no failed validations means success
    assertTrue(report.isValidationSuccessful());
    // once complete, further sets have no effect
    report.setValidationComplete(false);
    assertTrue(report.isValidationComplete());
  }

  @Test
  public void testValidationFAilsIfAtLeastOneFAilure() {
    report.setValidationResult(ImportValidationRule.MANIFEST_FILE_PRESENT, false);
    report.setValidationComplete(true);
    assertFalse(report.isValidationSuccessful());
    // no longer has any effect, as is set as complete
    report.setValidationResult(ImportValidationRule.MANIFEST_FILE_PRESENT, true);
    assertFalse(report.isValidationSuccessful());
  }

  @Test
  public void testImportFailure() {
    report.setComplete(true);
    report.setValidationComplete(true);
    assertTrue(report.isSuccessful());

    report = new ImportArchiveReport();
    report.getErrorList().addErrorMsg("error");
    report.setComplete(true);
    report.setValidationComplete(true);
    assertFalse(report.isSuccessful());
  }
}
