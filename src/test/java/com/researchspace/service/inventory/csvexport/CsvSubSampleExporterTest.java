package com.researchspace.service.inventory.csvexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.archive.ExportScope;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.SubSampleNote;
import com.researchspace.model.inventory.field.ExtraNumberField;
import com.researchspace.model.inventory.field.ExtraTextField;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

public class CsvSubSampleExporterTest extends SpringTransactionalTest {

  @Autowired private CsvSubSampleExporter subSampleExporter;

  private User testUser = new User("testUser");

  @Test
  public void exportSubSamplesToCsv() throws IOException {

    // create test subsample
    SubSample testSubSample = new SubSample();
    testSubSample.setId(5L);
    testSubSample.setName("testName");
    testSubSample.setTags("testTags");
    testSubSample.setQuantity(QuantityInfo.of(5, RSUnitDef.MILLI_GRAM));
    testSubSample.setSample(new Sample());
    testSubSample.getSample().setOwner(new User("testOwner"));
    ExtraTextField textField = new ExtraTextField();
    textField.setName("test extra field");
    textField.setData("test data");
    testSubSample.addExtraField(textField);
    ExtraNumberField numberField = new ExtraNumberField();
    numberField.setName("Test Numeric");
    numberField.setData("515.15");
    testSubSample.addExtraField(numberField);

    // create another subsample, with one extra field repeated with 1st subsample
    SubSample testSubSample2 = new SubSample();
    testSubSample2.setId(6L);
    testSubSample2.setName("testName2");
    testSubSample2.setQuantity(QuantityInfo.of(25, RSUnitDef.GRAM));
    testSubSample2.setSample(new Sample());
    testSubSample2.getSample().setOwner(new User("testOwner2"));
    ExtraTextField textField2 = new ExtraTextField();
    textField2.setName("test extra field 2");
    textField2.setData("test data 2");
    testSubSample2.addExtraField(textField2);
    ExtraNumberField numberField2 = new ExtraNumberField();
    numberField2.setName("Test Numeric");
    numberField2.setData("313.13");
    testSubSample2.addExtraField(numberField2);

    // full-data export
    CsvExportMode exportMode = CsvExportMode.FULL;
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    List<String> csvColumnNames =
        subSampleExporter.writeSubSampleCsvHeaderIntoOutput(
            List.of(testSubSample, testSubSample2), exportMode, null, outputStream);
    assertEquals(13, csvColumnNames.size());
    String csvHeaderLineForSubSamples = outputStream.toString();
    String expectedColumnNamesLine =
        "Global ID,Name,Tags,Owner,Description,"
            + "Parent Sample (Global ID),Parent Container (Global ID),Quantity,Notes,"
            + "\"test extra field (TEXT, SS5)\",\"Test Numeric (NUMBER, SS5)\","
            + "\"test extra field 2 (TEXT, SS6)\",\"Test Numeric (NUMBER, SS6)\"\n";
    assertEquals(expectedColumnNamesLine, csvHeaderLineForSubSamples);

    outputStream = new ByteArrayOutputStream();
    subSampleExporter.writeSubSampleCsvDetailsIntoOutput(
        testSubSample, csvColumnNames, exportMode, null, outputStream);
    String csvLineForSubSample = outputStream.toString();
    String expectedSubSampleLine =
        "SS5,testName,testTags,testOwner,,,,5 mg,,test data,515.15,#N/A,#N/A\n";
    assertEquals(expectedSubSampleLine, csvLineForSubSample);

    outputStream = new ByteArrayOutputStream();
    subSampleExporter.writeSubSampleCsvDetailsIntoOutput(
        testSubSample2, csvColumnNames, exportMode, null, outputStream);
    csvLineForSubSample = outputStream.toString();
    String expectedSubSample2Line =
        "SS6,testName2,,testOwner2,,,,25 g,,#N/A,#N/A,test data 2,313.13\n";
    assertEquals(expectedSubSample2Line, csvLineForSubSample);

    String csvFragmentForSubSamples =
        subSampleExporter
            .getCsvFragmentForSubSamples(List.of(testSubSample, testSubSample2), exportMode)
            .toString();
    assertEquals(
        expectedColumnNamesLine + expectedSubSampleLine + expectedSubSample2Line,
        csvFragmentForSubSamples);

    // compact-mode export
    exportMode = CsvExportMode.COMPACT;
    outputStream = new ByteArrayOutputStream();
    csvColumnNames =
        subSampleExporter.writeSubSampleCsvHeaderIntoOutput(
            List.of(testSubSample, testSubSample2), exportMode, null, outputStream);
    assertEquals(9, csvColumnNames.size());
    csvHeaderLineForSubSamples = outputStream.toString();
    expectedColumnNamesLine =
        "Global ID,Name,Tags,Owner,Description,Parent Sample (Global ID),Parent Container (Global"
            + " ID),Quantity,Notes\n";
    assertEquals(expectedColumnNamesLine, csvHeaderLineForSubSamples);

    outputStream = new ByteArrayOutputStream();
    subSampleExporter.writeSubSampleCsvDetailsIntoOutput(
        testSubSample, csvColumnNames, exportMode, null, outputStream);
    csvLineForSubSample = outputStream.toString();
    expectedSubSampleLine = "SS5,testName,testTags,testOwner,,,,5 mg,\n";
    assertEquals(expectedSubSampleLine, csvLineForSubSample);

    outputStream = new ByteArrayOutputStream();
    subSampleExporter.writeSubSampleCsvDetailsIntoOutput(
        testSubSample2, csvColumnNames, exportMode, null, outputStream);
    csvLineForSubSample = outputStream.toString();
    expectedSubSample2Line = "SS6,testName2,,testOwner2,,,,25 g,\n";
    assertEquals(expectedSubSample2Line, csvLineForSubSample);

    csvFragmentForSubSamples =
        subSampleExporter
            .getCsvFragmentForSubSamples(List.of(testSubSample, testSubSample2), exportMode)
            .toString();
    assertEquals(
        expectedColumnNamesLine + expectedSubSampleLine + expectedSubSample2Line,
        csvFragmentForSubSamples);
  }

  @Test
  public void convertSubSampleNotesToStringCsv() throws IOException {
    // empty notes array
    List<SubSampleNote> notes = new ArrayList<>();
    String notesAsCsv = subSampleExporter.convertNotesToCsvStringValue(notes);
    assertEquals("", notesAsCsv);

    // single note
    SubSampleNote testNote1 = new SubSampleNote("note content", testUser);
    ReflectionTestUtils.setField(testNote1, "creationDateMillis", 1649317466100L);
    String expectedTestNote1AsCsv =
        "Note created by \"testUser\" at 2022-04-07T07:44:26.100Z: \"note content\"";

    notes.add(testNote1);
    notesAsCsv = subSampleExporter.convertNotesToCsvStringValue(notes);
    assertEquals(expectedTestNote1AsCsv, notesAsCsv);

    // two notes
    SubSampleNote testNote2 = new SubSampleNote("note content 2", testUser);
    ReflectionTestUtils.setField(testNote2, "creationDateMillis", 1649317467200L);
    String expectedTestNote2AsCsv =
        "Note created by \"testUser\" at 2022-04-07T07:44:27.200Z: \"note content 2\"";

    notes.add(testNote2);
    notesAsCsv = subSampleExporter.convertNotesToCsvStringValue(notes);
    assertEquals(
        expectedTestNote1AsCsv
            + InventoryItemCsvExporter.CSV_VALUE_LIST_SEPARATOR
            + expectedTestNote2AsCsv,
        notesAsCsv);
  }

  @Test
  public void checkSubSampleExportComment() throws IOException {
    String csvComment =
        subSampleExporter
            .getCsvCommentFragmentForSubSamples(ExportScope.SELECTION, CsvExportMode.FULL, testUser)
            .toString();
    assertTrue(csvComment.startsWith("# " + subSampleExporter.CSV_COMMENT_HEADER), csvComment);
    assertTrue(csvComment.contains("# Exported content: SUBSAMPLES"), csvComment);
    assertTrue(csvComment.contains("# Export scope: SELECTION"), csvComment);
    assertTrue(csvComment.contains("# Export mode: FULL"), csvComment);
    assertTrue(csvComment.contains("# RSpace URL: http"), csvComment);
  }
}
