package com.researchspace.service.inventory.csvexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.archive.ExportScope;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.SampleDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.field.ExtraNumberField;
import com.researchspace.model.inventory.field.ExtraTextField;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class CsvSampleExporterTest extends SpringTransactionalTest {

  @Autowired
  @Qualifier("csvSampleExporter")
  private CsvSampleExporter sampleExporter;

  @Autowired private SampleDao sampleDao;
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
    assertThat(csvColumnNames).hasSize(14);
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
    assertThat(csvColumnNames).hasSize(23);
    String csvHeaderLineForSamples = outputStream.toString();
    String expectedHeaderLineStart =
        "Global ID,Name,Tags,Owner,Description,Parent Template (Global ID),Parent Template"
            + " (name),Total Quantity,Expiry Date,Sample Source,Storage Temperature (min),Storage"
            + " Temperature (max),\"MyNumber (NUMBER, IT";
    assertThat(csvHeaderLineForSamples)
        .as(csvHeaderLineForSamples)
        .startsWith(expectedHeaderLineStart);

    String csvFragmentForSamples =
        sampleExporter.getCsvFragmentForSamples(exampleSamples.getResults(), exportMode).toString();
    assertThat(csvFragmentForSamples).as(csvFragmentForSamples).startsWith(expectedHeaderLineStart);
    assertThat(csvFragmentForSamples).as(csvFragmentForSamples).contains(",Basic Sample,");
    assertThat(csvFragmentForSamples).as(csvFragmentForSamples).contains(",Complex Sample #1,");
  }

  @Test
  public void checkSampleExportComment() throws IOException {
    User user = TestFactory.createAnyUser("testExportUser");
    String csvComment =
        sampleExporter
            .getCsvCommentFragmentForSamples(ExportScope.USER, CsvExportMode.COMPACT, user)
            .toString();
    assertThat(csvComment).as(csvComment).startsWith("# " + sampleExporter.getCsvCommentHeader());
    assertThat(csvComment).as(csvComment).contains("# Exported content: SAMPLES");
    assertThat(csvComment).as(csvComment).contains("# Export scope: USER");
    assertThat(csvComment).as(csvComment).contains("# Export mode: COMPACT");
  }
}
