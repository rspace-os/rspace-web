package com.researchspace.service.inventory.csvexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.archive.ExportScope;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.field.ExtraNumberField;
import com.researchspace.model.inventory.field.ExtraTextField;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class CsvSampleExporterTest extends SpringTransactionalTest {

  @Autowired
  @Qualifier("csvSampleExporter")
  private CsvSampleExporter sampleExporter;

  private User testUser = new User("testUser");

  @Test
  public void exportMockSampleToCsv() throws IOException {

    // create mock test sample
    Sample testSample = new Sample();
    testSample.setId(5L);
    testSample.setName("testName");
    testSample.setTags("testTags");
    ExtraTextField textField = new ExtraTextField();
    textField.setName("test extra field");
    textField.setData("test data");
    testSample.addExtraField(textField);
    ExtraNumberField numberField = new ExtraNumberField();
    numberField.setName("Test Numeric");
    numberField.setData("515.15");
    testSample.addExtraField(numberField);

    // full-data export
    CsvExportMode exportMode = CsvExportMode.FULL;
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    List<String> csvColumnNames =
        sampleExporter.writeSampleCsvHeaderIntoOutput(
            List.of(testSample), exportMode, null, outputStream);
    assertEquals(14, csvColumnNames.size());
    String csvHeaderLineForSubSamples = outputStream.toString();
    String expectedColumnNamesLine =
        "Global ID,Name,Tags,Owner,Description,"
            + "Parent Template (Global ID),Parent Template (name),Total Quantity,"
            + "Expiry Date,Sample Source,Storage Temperature (min),Storage Temperature (max),"
            + "\"test extra field (TEXT, SA5)\",\"Test Numeric (NUMBER, SA5)\"\n";
    assertEquals(expectedColumnNamesLine, csvHeaderLineForSubSamples);

    outputStream = new ByteArrayOutputStream();
    sampleExporter.writeSampleCsvDetailsIntoOutput(
        testSample, csvColumnNames, exportMode, null, outputStream);
    String csvLineForSample = outputStream.toString();
    String expectedSampleLine = "SA5,testName,testTags,,,,,,,LAB_CREATED,,,test data,515.15\n";
    assertEquals(expectedSampleLine, csvLineForSample);

    String csvFragmentForSubSamples =
        sampleExporter.getCsvFragmentForSamples(List.of(testSample), exportMode).toString();
    assertEquals(expectedColumnNamesLine + expectedSampleLine, csvFragmentForSubSamples);
  }

  @Test
  public void exportDefaultUserSamplesToCsv() throws IOException {

    User testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("sampleExport"));
    initialiseContentWithExampleContent(testUser);
    ISearchResults<Sample> exampleSamples =
        sampleDao.getSamplesForUser(null, null, null, null, testUser);

    // full-data export
    CsvExportMode exportMode = CsvExportMode.FULL;
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    List<String> csvColumnNames =
        sampleExporter.writeSampleCsvHeaderIntoOutput(
            exampleSamples.getResults(), exportMode, null, outputStream);
    assertEquals(22, csvColumnNames.size());
    String csvHeaderLineForSamples = outputStream.toString();
    String expectedHeaderLineStart =
        "Global ID,Name,Tags,Owner,Description,Parent Template (Global ID),Parent Template"
            + " (name),Total Quantity,Expiry Date,Sample Source,Storage Temperature (min),Storage"
            + " Temperature (max),\"MyNumber (NUMBER, IT";
    assertTrue(
        csvHeaderLineForSamples.startsWith(expectedHeaderLineStart), csvHeaderLineForSamples);

    String csvFragmentForSamples =
        sampleExporter.getCsvFragmentForSamples(exampleSamples.getResults(), exportMode).toString();
    assertTrue(csvFragmentForSamples.startsWith(expectedHeaderLineStart), csvFragmentForSamples);
    assertTrue(csvFragmentForSamples.contains(",Basic Sample,"), csvFragmentForSamples);
    assertTrue(csvFragmentForSamples.contains(",Complex Sample #1,"), csvFragmentForSamples);
  }

  @Test
  public void checkSampleExportComment() throws IOException {
    User user = TestFactory.createAnyUser("testExportUser");
    String csvComment =
        sampleExporter
            .getCsvCommentFragmentForSamples(ExportScope.USER, CsvExportMode.COMPACT, user)
            .toString();
    assertTrue(csvComment.startsWith("# " + sampleExporter.CSV_COMMENT_HEADER), csvComment);
    assertTrue(csvComment.contains("# Exported content: SAMPLES"), csvComment);
    assertTrue(csvComment.contains("# Export scope: USER"), csvComment);
    assertTrue(csvComment.contains("# Export mode: COMPACT"), csvComment);
  }
}
