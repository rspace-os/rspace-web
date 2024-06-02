package com.researchspace.service.inventory.csvimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInventoryImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportSampleImportResult;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import org.apache.tika.io.IOUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class InventoryItemCsvImporterTest extends SpringTransactionalTest {

  @Autowired private CsvContainerImporter containerCsvImporter;
  @Autowired private CsvSampleImporter sampleCsvImporter;
  @Autowired private CsvSubSampleImporter subSampleCsvImporter;

  @Test
  public void testSampleCsvImportWithTopLevelErrors() throws IOException {
    // blank csv file
    HashMap<String, String> nameColumnMapping = new HashMap<>();
    nameColumnMapping.put("Name", "name");
    ApiSampleTemplate templateInfo = new ApiSampleTemplate();
    templateInfo.setName("TestTemplate");
    ApiInventoryImportSampleImportResult sampleProcessingResult =
        new ApiInventoryImportSampleImportResult();
    sampleProcessingResult.addCreatedTemplateResult(templateInfo);
    ApiInventoryImportResult processingResult = new ApiInventoryImportResult();
    processingResult.setSampleResult(sampleProcessingResult);

    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                sampleCsvImporter.readCsvIntoImportResult(
                    IOUtils.toInputStream(" "), nameColumnMapping, processingResult));
    assertTrue(iae.getMessage().contains("CSV file seems to be empty"), iae.getMessage());

    // invalid mapping for csv file ('Name' column not found)
    InputStream oneSampleCsvIS = IOUtils.toInputStream("Sample Name\nTestSample");
    iae =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                sampleCsvImporter.readCsvIntoImportResult(
                    oneSampleCsvIS, nameColumnMapping, processingResult));
    assertTrue(
        iae.getMessage().contains("Couldn't find 'Name' among columns in CSV file"),
        iae.getMessage());

    // duplicated column name
    InputStream duplicateColumnNamesIS = IOUtils.toInputStream("Name,Data,Data\nTestSample,,");
    iae =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                sampleCsvImporter.readCsvIntoImportResult(
                    duplicateColumnNamesIS, nameColumnMapping, processingResult));
    assertTrue(iae.getMessage().contains("CSV file has repeating column names"), iae.getMessage());

    // verify happy case works
    InputStream anotherThreeSampleCsvIS =
        IOUtils.toInputStream("Name\nTestSample\nTestSample2\nTestSample3");
    sampleCsvImporter.readCsvIntoImportResult(
        anotherThreeSampleCsvIS, nameColumnMapping, processingResult);
    assertEquals(3, sampleProcessingResult.getSuccessCount());
  }

  @Test
  public void testCsvFileSizeLimits() throws IOException {

    IllegalArgumentException iae;
    HashMap<String, String> nameColumnMapping = new HashMap<>();
    nameColumnMapping.put("Name", "name");
    ApiInventoryImportResult processingResult = new ApiInventoryImportResult();

    // let's reduce the limit of samples to import and try then
    Integer defaultLinesLimit = sampleCsvImporter.getCsvLinesLimit();
    try {
      sampleCsvImporter.setCsvLinesLimit(2);
      InputStream threeSampleCsvIS =
          IOUtils.toInputStream("Name\nTestSample\nTestSample2\nTestSample3");
      iae =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  sampleCsvImporter.readCsvIntoImportResult(
                      threeSampleCsvIS, nameColumnMapping, processingResult));
      assertTrue(
          iae.getMessage().contains("CSV file is too long, import limit is set to 2 samples."),
          iae.getMessage());
    } finally {
      sampleCsvImporter.setCsvLinesLimit(defaultLinesLimit);
    }

    // subsamples
    defaultLinesLimit = subSampleCsvImporter.getCsvLinesLimit();
    try {
      subSampleCsvImporter.setCsvLinesLimit(2);
      InputStream threeSampleCsvIS =
          IOUtils.toInputStream("Name\nTestSample\nTestSample2\nTestSample3");
      iae =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  subSampleCsvImporter.readCsvIntoImportResult(
                      threeSampleCsvIS, nameColumnMapping, processingResult));
      assertTrue(
          iae.getMessage().contains("CSV file is too long, import limit is set to 2 subsamples."),
          iae.getMessage());
    } finally {
      subSampleCsvImporter.setCsvLinesLimit(defaultLinesLimit);
    }

    // containers
    defaultLinesLimit = containerCsvImporter.getCsvLinesLimit();
    try {
      containerCsvImporter.setCsvLinesLimit(2);
      InputStream threeSampleCsvIS =
          IOUtils.toInputStream("Name\nTestSample\nTestSample2\nTestSample3");
      iae =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  containerCsvImporter.readCsvIntoImportResult(
                      threeSampleCsvIS, nameColumnMapping, processingResult));
      assertTrue(
          iae.getMessage().contains("CSV file is too long, import limit is set to 2 containers."),
          iae.getMessage());
    } finally {
      containerCsvImporter.setCsvLinesLimit(defaultLinesLimit);
    }
  }

  @Test
  public void testCommentsBeforeColumnNamesLineIgnored() throws IOException {

    HashMap<String, String> nameColumnMapping = new HashMap<>();
    nameColumnMapping.put("Name", "name");
    ApiInventoryImportResult processingResult = new ApiInventoryImportResult();

    // confirm simplest import works
    InputStream containerIS = IOUtils.toInputStream("Name\nTestContainer");
    containerCsvImporter.readCsvIntoImportResult(containerIS, nameColumnMapping, processingResult);
    assertEquals(1, processingResult.getContainerResult().getSuccessCount());

    // try with comments
    InputStream containerWithCommentIS =
        IOUtils.toInputStream("#Test Comment\n#Another\nName\n#Test Comment\nTestContainer");
    containerCsvImporter.readCsvIntoImportResult(
        containerWithCommentIS, nameColumnMapping, processingResult);
    assertEquals(2, processingResult.getContainerResult().getSuccessCount());
  }
}
