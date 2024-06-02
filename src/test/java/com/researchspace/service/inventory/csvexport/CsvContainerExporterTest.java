package com.researchspace.service.inventory.csvexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.archive.ExportScope;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.field.ExtraNumberField;
import com.researchspace.model.inventory.field.ExtraTextField;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class CsvContainerExporterTest extends SpringTransactionalTest {

  @Autowired private CsvContainerExporter containerExporter;

  private User testUser = new User("testUser");

  @Test
  public void exportMockContainerToCsv() throws IOException {

    Container testContainer = new Container();
    testContainer.setId(5L);
    testContainer.setName("testName");
    testContainer.setTags("testTags");
    ExtraTextField textField = new ExtraTextField();
    textField.setName("test extra field");
    textField.setData("test data");
    testContainer.addExtraField(textField);
    ExtraNumberField numberField = new ExtraNumberField();
    numberField.setName("Test Numeric");
    numberField.setData("515.15");
    testContainer.addExtraField(numberField);

    // full-data export
    CsvExportMode exportMode = CsvExportMode.FULL;
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    List<String> csvColumnNames =
        containerExporter.writeContainerCsvHeaderIntoOutput(
            List.of(testContainer), exportMode, null, outputStream);
    assertEquals(13, csvColumnNames.size());
    String csvHeaderLineForSubSamples = outputStream.toString();
    String expectedColumnNamesLine =
        "Global ID,Name,Tags,Owner,Description,Parent Container (Global ID),"
            + "Container Type,Can Store Containers (Y/N),Can Store Subsamples (Y/N),"
            + "Number of Stored Containers,Number of Stored Subsamples,"
            + "\"test extra field (TEXT, IC5)\",\"Test Numeric (NUMBER, IC5)\"\n";
    assertEquals(expectedColumnNamesLine, csvHeaderLineForSubSamples);

    outputStream = new ByteArrayOutputStream();
    containerExporter.writeContainerCsvDetailsIntoOutput(
        testContainer, csvColumnNames, exportMode, null, outputStream);
    String csvLineForSample = outputStream.toString();
    String expectedSampleLine = "IC5,testName,testTags,,,,LIST,Y,Y,0,0,test data,515.15\n";
    assertEquals(expectedSampleLine, csvLineForSample);

    String csvFragmentForSubSamples =
        containerExporter
            .getCsvFragmentForContainers(List.of(testContainer), exportMode)
            .toString();
    assertEquals(expectedColumnNamesLine + expectedSampleLine, csvFragmentForSubSamples);
  }

  @Test
  public void exportDefaultUserContainersToCsv() throws IOException {

    User testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("sampleExport"));
    initialiseContentWithExampleContent(testUser);
    ISearchResults<Container> topContainers =
        containerDao.getTopContainersForUser(null, null, null, null, testUser);

    // full-data export
    CsvExportMode exportMode = CsvExportMode.FULL;
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    List<String> csvColumnNames =
        containerExporter.writeContainerCsvHeaderIntoOutput(
            topContainers.getResults(), exportMode, null, outputStream);
    assertEquals(11, csvColumnNames.size());
    String csvHeaderLineForSamples = outputStream.toString();
    String expectedHeaderLineStart =
        "Global ID,Name,Tags,Owner,Description,Parent Container (Global ID),"
            + "Container Type,Can Store Containers (Y/N),Can Store Subsamples (Y/N),"
            + "Number of Stored Containers,Number of Stored Subsamples\n";
    assertTrue(
        csvHeaderLineForSamples.startsWith(expectedHeaderLineStart), csvHeaderLineForSamples);

    String csvFragmentForSamples =
        containerExporter
            .getCsvFragmentForContainers(topContainers.getResults(), exportMode)
            .toString();
    assertTrue(csvFragmentForSamples.startsWith(expectedHeaderLineStart), csvFragmentForSamples);
    assertTrue(
        csvFragmentForSamples.contains(",storage shelf #1 (list container),"),
        csvFragmentForSamples);
    assertTrue(csvFragmentForSamples.contains(",LIST,Y,Y,3,0\n"), csvFragmentForSamples);
    assertTrue(
        csvFragmentForSamples.contains(",4-drawer storage unit (image container),"),
        csvFragmentForSamples);
    assertTrue(csvFragmentForSamples.contains(",IMAGE,Y,Y,0,0\n"), csvFragmentForSamples);
  }

  @Test
  public void checkContainerExportComment() throws IOException {
    User user = TestFactory.createAnyUser("testExportUser");
    String csvComment =
        containerExporter
            .getCsvCommentFragmentForContainers(ExportScope.USER, CsvExportMode.COMPACT, user)
            .toString();
    assertTrue(csvComment.startsWith("# " + containerExporter.CSV_COMMENT_HEADER), csvComment);
    assertTrue(csvComment.contains("# Exported content: CONTAINERS"), csvComment);
    assertTrue(csvComment.contains("# Export scope: USER"), csvComment);
    assertTrue(csvComment.contains("# Export mode: COMPACT"), csvComment);
  }
}
