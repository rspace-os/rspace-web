package com.researchspace.service.inventory.csvimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryImportParseResult;
import com.researchspace.api.v1.model.ApiInventoryImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportSampleImportResult;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.model.User;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import org.apache.tika.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;

public class InventoryItemCsvImporterTest extends SpringTransactionalTest {

  private final String DOI_1 = "10.12345/nico-test1";
  private final String DOI_2 = "10.12345/nico-test2";

  @Autowired private CsvContainerImporter containerCsvImporter;
  @Autowired private CsvSampleImporter sampleCsvImporter;
  @Autowired private CsvSubSampleImporter subSampleCsvImporter;
  @Mock private InventoryIdentifierApiManager mockIdenitfierManager;
  @Mock private ApiAvailabilityHandler mockApiHandler;

  private User user;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    user = createAndSaveRandomUser();
    containerCsvImporter.setInventoryIdentifierManager(mockIdenitfierManager);
    containerCsvImporter.setApiHandler(mockApiHandler);

    sampleCsvImporter.setInventoryIdentifierManager(mockIdenitfierManager);
    sampleCsvImporter.setApiHandler(mockApiHandler);

    subSampleCsvImporter.setInventoryIdentifierManager(mockIdenitfierManager);
    subSampleCsvImporter.setApiHandler(mockApiHandler);

    when(mockApiHandler.isInventoryAndDataciteEnabled(eq(user))).thenReturn(true);
  }

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
                    IOUtils.toInputStream(" "), nameColumnMapping, processingResult, user));
    assertTrue(iae.getMessage().contains("CSV file seems to be empty"), iae.getMessage());

    // invalid mapping for csv file ('Name' column not found)
    InputStream oneSampleCsvIS = IOUtils.toInputStream("Sample Name\nTestSample");
    iae =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                sampleCsvImporter.readCsvIntoImportResult(
                    oneSampleCsvIS, nameColumnMapping, processingResult, user));
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
                    duplicateColumnNamesIS, nameColumnMapping, processingResult, user));
    assertTrue(iae.getMessage().contains("CSV file has repeating column names"), iae.getMessage());

    // verify happy case works
    InputStream twoLinesCsv = IOUtils.toInputStream("Name\nTestSample\nTestSample2\nTestSample3");
    sampleCsvImporter.readCsvIntoImportResult(
        twoLinesCsv, nameColumnMapping, processingResult, user);
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
                      threeSampleCsvIS, nameColumnMapping, processingResult, user));
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
                      threeSampleCsvIS, nameColumnMapping, processingResult, user));
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
                      threeSampleCsvIS, nameColumnMapping, processingResult, user));
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
    containerCsvImporter.readCsvIntoImportResult(
        containerIS, nameColumnMapping, processingResult, null);
    assertEquals(1, processingResult.getContainerResult().getSuccessCount());

    // try with comments
    InputStream containerWithCommentIS =
        IOUtils.toInputStream("#Test Comment\n#Another\nName\n#Test Comment\nTestContainer");
    containerCsvImporter.readCsvIntoImportResult(
        containerWithCommentIS, nameColumnMapping, processingResult, null);
    assertEquals(2, processingResult.getContainerResult().getSuccessCount());
  }

  @Test
  public void testFieldMappingsWhileParsingForIdentifier() throws IOException {

    // confirm field mapping for Igsn to identifier is DONE
    InputStream containerIS = IOUtils.toInputStream("Igsn\n10.12345/nico-test");
    ApiInventoryImportParseResult parseResult =
        containerCsvImporter.readCsvIntoParseResults(containerIS, user);
    assertEquals(1, parseResult.getFieldMappings().size());
    assertEquals("identifier", parseResult.getFieldMappings().get("Igsn"));

    // confirm field mapping for Igsn to identifier is NOT DONE
    containerIS = IOUtils.toInputStream("Igsn\n11.12345/nico-test");
    parseResult = containerCsvImporter.readCsvIntoParseResults(containerIS, user);
    assertTrue(parseResult.getFieldMappings().isEmpty());
  }

  @Test
  public void testFieldMappingIsCalledForSamples() throws Exception {
    HashMap<String, String> nameColumnMapping = new HashMap<>();
    nameColumnMapping.put("Igsn", "identifier");
    ApiSampleTemplate templateInfo = new ApiSampleTemplate();
    templateInfo.setName("TestTemplate");
    ApiInventoryImportSampleImportResult sampleProcessingResult =
        new ApiInventoryImportSampleImportResult();
    sampleProcessingResult.addCreatedTemplateResult(templateInfo);
    ApiInventoryImportResult processingResult = new ApiInventoryImportResult();
    processingResult.setSampleResult(sampleProcessingResult);

    ApiInventoryDOI idTest1 = new ApiInventoryDOI();
    idTest1.setDoi(DOI_1);
    ApiInventoryDOI idTest2 = new ApiInventoryDOI();
    idTest2.setDoi(DOI_2);

    InputStream twoLinesCsv =
        IOUtils.toInputStream("Igsn\n10.12345/nico-test1\n10.12345/nico-test2");
    when(mockIdenitfierManager.findIdentifiers(
            eq("draft"), eq(false), eq(DOI_1), eq(false), eq(user)))
        .thenReturn(List.of(idTest1));
    when(mockIdenitfierManager.findIdentifiers(
            eq("draft"), eq(false), eq(DOI_2), eq(false), eq(user)))
        .thenReturn(List.of(idTest2));

    sampleCsvImporter.readCsvIntoImportResult(
        twoLinesCsv, nameColumnMapping, processingResult, user);
    assertEquals(2, sampleProcessingResult.getSuccessCount());
    verify(mockApiHandler, times(2)).assertInventoryAndDataciteEnabled(any());
    verify(mockIdenitfierManager, times(2))
        .findIdentifiers(eq("draft"), eq(false), anyString(), eq(false), eq(user));

    assertEquals(1, sampleProcessingResult.getResults().get(0).getRecord().getIdentifiers().size());
    assertEquals(
        DOI_1,
        sampleProcessingResult.getResults().get(0).getRecord().getIdentifiers().get(0).getDoi());

    assertEquals(1, sampleProcessingResult.getResults().get(1).getRecord().getIdentifiers().size());
    assertEquals(
        DOI_2,
        sampleProcessingResult.getResults().get(1).getRecord().getIdentifiers().get(0).getDoi());
  }

  @Test
  public void testFieldMappingIsCalledForSubSamples() throws Exception {
    HashMap<String, String> nameColumnMapping = new HashMap<>();
    nameColumnMapping.put("Igsn", "identifier");
    ApiSampleTemplate templateInfo = new ApiSampleTemplate();
    templateInfo.setName("TestTemplate");
    ApiInventoryImportSampleImportResult sampleProcessingResult =
        new ApiInventoryImportSampleImportResult();
    sampleProcessingResult.addCreatedTemplateResult(templateInfo);
    ApiInventoryImportResult processingResult = new ApiInventoryImportResult();
    processingResult.setSampleResult(sampleProcessingResult);

    ApiInventoryDOI idTest1 = new ApiInventoryDOI();
    idTest1.setDoi(DOI_1);
    ApiInventoryDOI idTest2 = new ApiInventoryDOI();
    idTest2.setDoi(DOI_2);

    InputStream twoLinesCsv =
        IOUtils.toInputStream("Igsn\n10.12345/nico-test1\n10.12345/nico-test2");
    when(mockIdenitfierManager.findIdentifiers(
            eq("draft"), eq(false), eq(DOI_1), eq(false), eq(user)))
        .thenReturn(List.of(idTest1));
    when(mockIdenitfierManager.findIdentifiers(
            eq("draft"), eq(false), eq(DOI_2), eq(false), eq(user)))
        .thenReturn(List.of(idTest2));

    subSampleCsvImporter.readCsvIntoImportResult(
        twoLinesCsv, nameColumnMapping, processingResult, user);
    assertEquals(2, processingResult.getSubSampleResult().getSuccessCount());
    verify(mockApiHandler, times(2)).assertInventoryAndDataciteEnabled(any());
    verify(mockIdenitfierManager, times(2))
        .findIdentifiers(eq("draft"), eq(false), anyString(), eq(false), eq(user));

    assertEquals(
        1,
        processingResult
            .getSubSampleResult()
            .getResults()
            .get(0)
            .getRecord()
            .getIdentifiers()
            .size());
    assertEquals(
        DOI_1,
        processingResult
            .getSubSampleResult()
            .getResults()
            .get(0)
            .getRecord()
            .getIdentifiers()
            .get(0)
            .getDoi());

    assertEquals(
        1,
        processingResult
            .getSubSampleResult()
            .getResults()
            .get(1)
            .getRecord()
            .getIdentifiers()
            .size());
    assertEquals(
        DOI_2,
        processingResult
            .getSubSampleResult()
            .getResults()
            .get(1)
            .getRecord()
            .getIdentifiers()
            .get(0)
            .getDoi());
  }

  @Test
  public void testFieldMappingIsCalledForContainer() throws Exception {
    HashMap<String, String> nameColumnMapping = new HashMap<>();
    nameColumnMapping.put("Igsn", "identifier");
    ApiSampleTemplate templateInfo = new ApiSampleTemplate();
    templateInfo.setName("TestTemplate");
    ApiInventoryImportSampleImportResult sampleProcessingResult =
        new ApiInventoryImportSampleImportResult();
    sampleProcessingResult.addCreatedTemplateResult(templateInfo);
    ApiInventoryImportResult processingResult = new ApiInventoryImportResult();
    processingResult.setSampleResult(sampleProcessingResult);

    ApiInventoryDOI idTest1 = new ApiInventoryDOI();
    idTest1.setDoi(DOI_1);
    ApiInventoryDOI idTest2 = new ApiInventoryDOI();
    idTest2.setDoi(DOI_2);

    InputStream twoLinesCsv =
        IOUtils.toInputStream("Igsn\n10.12345/nico-test1\n10.12345/nico-test2");
    when(mockIdenitfierManager.findIdentifiers(
            eq("draft"), eq(false), eq(DOI_1), eq(false), eq(user)))
        .thenReturn(List.of(idTest1));
    when(mockIdenitfierManager.findIdentifiers(
            eq("draft"), eq(false), eq(DOI_2), eq(false), eq(user)))
        .thenReturn(List.of(idTest2));

    containerCsvImporter.readCsvIntoImportResult(
        twoLinesCsv, nameColumnMapping, processingResult, user);
    assertEquals(2, processingResult.getContainerResult().getSuccessCount());
    verify(mockApiHandler, times(2)).assertInventoryAndDataciteEnabled(any());
    verify(mockIdenitfierManager, times(2))
        .findIdentifiers(eq("draft"), eq(false), anyString(), eq(false), eq(user));

    assertEquals(
        1,
        processingResult
            .getContainerResult()
            .getResults()
            .get(0)
            .getRecord()
            .getIdentifiers()
            .size());
    assertEquals(
        DOI_1,
        processingResult
            .getContainerResult()
            .getResults()
            .get(0)
            .getRecord()
            .getIdentifiers()
            .get(0)
            .getDoi());

    assertEquals(
        1,
        processingResult
            .getContainerResult()
            .getResults()
            .get(1)
            .getRecord()
            .getIdentifiers()
            .size());
    assertEquals(
        DOI_2,
        processingResult
            .getContainerResult()
            .getResults()
            .get(1)
            .getRecord()
            .getIdentifiers()
            .get(0)
            .getDoi());
  }
}
