package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.controller.ApiControllerAdvice;
import com.researchspace.api.v1.controller.ContainersApiController;
import com.researchspace.api.v1.controller.SampleTemplatesApiController;
import com.researchspace.api.v1.controller.SamplesApiController;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.InventoryBulkOperationStatus;
import com.researchspace.api.v1.model.ApiInventoryImportPartialResult;
import com.researchspace.api.v1.model.ApiInventoryImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportSampleImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportSampleParseResult;
import com.researchspace.api.v1.model.ApiInventoryImportSubSampleImportResult;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleField;
import com.researchspace.api.v1.model.ApiSampleField.ApiInventoryFieldDef;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleSource;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
import com.researchspace.service.inventory.csvimport.CsvContainerImporter;
import com.researchspace.service.inventory.csvimport.CsvSampleImporter;
import com.researchspace.service.inventory.csvimport.CsvSubSampleImporter;
import com.researchspace.service.inventory.csvimport.exception.InventoryImportException;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tika.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;

public class InventoryImportManagerTest extends SpringTransactionalTest {

  private User testUser;

  // creating impl class here so we can test protected methods that are not part of interface
  private InventoryImportManagerImpl importMgr = new InventoryImportManagerImpl();

  @Autowired private SampleTemplatesApiController templatesController;
  @Autowired private SamplesApiController samplesController;
  @Autowired private ContainersApiController containersController;
  @Autowired private ContainerApiManager containerManager;
  @Autowired private SampleApiManager sampleManager;
  @Autowired private SubSampleApiManager subSampleManager;
  @Autowired private InventoryBulkOperationHandler bulkOperationHandler;
  @Autowired private ApiControllerAdvice apiControllerAdvice;
  @Autowired protected InventoryPermissionUtils invPermissions;
  @Autowired private CsvContainerImporter containerCsvImporter;
  @Autowired private CsvSampleImporter sampleCsvImporter;
  @Autowired private CsvSubSampleImporter subSampleCsvImporter;

  @Before
  public void setUp() {
    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("apiImport"));
    initialiseContentWithEmptyContent(testUser);
    assertTrue(testUser.isContentInitialized());

    importMgr.setTemplatesController(templatesController);
    importMgr.setSamplesController(samplesController);
    importMgr.setContainersController(containersController);
    importMgr.setContainerManager(containerManager);
    importMgr.setSampleManager(sampleManager);
    importMgr.setSubSampleManager(subSampleManager);
    importMgr.setBulkOperationHandler(bulkOperationHandler);
    importMgr.setApiControllerAdvice(apiControllerAdvice);
    importMgr.setInvPermissions(invPermissions);
    importMgr.setContainerCsvImporter(containerCsvImporter);
    importMgr.setSampleCsvImporter(sampleCsvImporter);
    importMgr.setSubSampleCsvImporter(subSampleCsvImporter);
  }

  @Test
  public void testTemplateAndSampleCreationMethods() {

    // with a 3-field template (text/radio/choice fields)
    ApiSampleTemplatePost templatePost = new ApiSampleTemplatePost();
    templatePost.setName("3-field template");
    templatePost.setSampleSource(SampleSource.VENDOR_SUPPLIED);
    templatePost.setDefaultUnitId(RSUnitDef.GRAM.getId());
    ApiSampleField textField = createBasicApiSampleField("textField", ApiFieldType.TEXT, "");
    templatePost.getFields().add(textField);
    ApiSampleField radioField = createBasicApiSampleField("radioField", ApiFieldType.RADIO, "");
    ApiInventoryFieldDef radioDef = new ApiInventoryFieldDef();
    radioDef.setOptions(List.of("no", "yes"));
    radioField.setDefinition(radioDef);
    templatePost.getFields().add(radioField);
    ApiSampleField choiceField = createBasicApiSampleField("choiceField", ApiFieldType.CHOICE, "");
    ApiInventoryFieldDef def = new ApiInventoryFieldDef();
    def.setOptions(List.of("opt1", "opt2", "opt3"));
    choiceField.setDefinition(def);
    templatePost.getFields().add(choiceField);

    ApiInventoryImportSampleImportResult sampleProcessingResult =
        importMgr.createSampleResultWithRequestedTemplate(templatePost, testUser);
    // verify template created
    assertTrue(sampleProcessingResult.isTemplateCreated());
    ApiSampleTemplate createdTemplate =
        (ApiSampleTemplate) sampleProcessingResult.getTemplate().getRecord();
    assertNotNull(createdTemplate);
    assertTrue(createdTemplate.isTemplate());
    assertEquals("3-field template", createdTemplate.getName());
    assertEquals(SampleSource.VENDOR_SUPPLIED, createdTemplate.getSampleSource());
    assertEquals(RSUnitDef.GRAM.getId(), createdTemplate.getDefaultUnitId());

    // import lines pointing to 3-field template
    List<String[]> csvSampleLines = new ArrayList<>();
    csvSampleLines.add(new String[] {"testContent", "testName3", "yes", "opt1"});
    csvSampleLines.add(new String[] {"testContent2", "testName4", "no", ""});
    Map<Integer, String> colIndexToDefaultFieldMapping = new HashMap<>();
    colIndexToDefaultFieldMapping.put(1, "name");

    // run line conversion
    sampleCsvImporter.convertLinesToSamples(
        sampleProcessingResult,
        csvSampleLines,
        colIndexToDefaultFieldMapping,
        csvSampleLines.get(0).length);
    assertEquals(2, sampleProcessingResult.getSuccessCount());

    // run import
    ApiInventoryImportResult importResult = new ApiInventoryImportResult(testUser);
    importMgr.importSamples(
        importResult, new ApiInventoryImportResult(null, sampleProcessingResult, null, testUser));
    ApiInventoryImportSampleImportResult sampleImportResult = importResult.getSampleResult();
    assertEquals(2, sampleImportResult.getSuccessCount());

    ApiSampleWithFullSubSamples sample1 =
        (ApiSampleWithFullSubSamples) sampleImportResult.getResults().get(0).getRecord();
    assertEquals("testName3", sample1.getName());
    assertEquals(3, sample1.getFields().size());
    assertEquals("testContent", sample1.getFields().get(0).getContent());
    assertEquals(null, sample1.getFields().get(1).getContent());
    assertEquals(List.of("yes"), sample1.getFields().get(1).getSelectedOptions());
    assertEquals(null, sample1.getFields().get(2).getContent());
    assertEquals(List.of("opt1"), sample1.getFields().get(2).getSelectedOptions());

    // check defaults taken from template, or unset (RSINV-311)
    assertEquals(null, sample1.getDescription());
    assertEquals(SampleSource.VENDOR_SUPPLIED, sample1.getSampleSource());
    assertEquals(null, sample1.getExpiryDate());
    assertEquals(null, sample1.getStorageTempMin());
    assertEquals(null, sample1.getStorageTempMax());
    assertEquals("1 g", sample1.getQuantity().toQuantityInfo().toPlainString());

    ApiSampleWithFullSubSamples sample2 =
        (ApiSampleWithFullSubSamples) sampleImportResult.getResults().get(1).getRecord();
    assertEquals("testName4", sample2.getName());
    assertEquals(null, sample2.getDescription());
    assertEquals(3, sample2.getFields().size());
    assertEquals("testContent2", sample2.getFields().get(0).getContent());
    assertEquals(null, sample2.getFields().get(1).getContent());
    assertEquals(List.of("no"), sample2.getFields().get(1).getSelectedOptions());
    assertEquals(null, sample2.getFields().get(2).getContent());
    assertEquals(Collections.emptyList(), sample2.getFields().get(2).getSelectedOptions());

    // let's use same template, but longer lines and a different mapping
    sampleProcessingResult = new ApiInventoryImportSampleImportResult();
    sampleProcessingResult.addCreatedTemplateResult(createdTemplate);
    csvSampleLines.clear();
    csvSampleLines.add(
        new String[] {
          "testName5",
          "desc1",
          "toIgnore1",
          "testContent1",
          "yes",
          "",
          "OTHER",
          "",
          "2030-01-01",
          "200 g"
        });
    csvSampleLines.add(
        new String[] {
          "testName6",
          "desc2",
          "toIgnore2",
          "testContent2",
          "no",
          "tag1, tag2",
          "VENDOR_SUPPLIED",
          "opt2",
          "",
          "5.25μg"
        });
    colIndexToDefaultFieldMapping.clear();
    colIndexToDefaultFieldMapping.put(0, "name"); // save first column as name
    colIndexToDefaultFieldMapping.put(1, "description"); // save 2nd column as description
    colIndexToDefaultFieldMapping.put(2, null); // ignore 3rd column
    colIndexToDefaultFieldMapping.put(5, "tags"); // save 6th column as tags
    colIndexToDefaultFieldMapping.put(6, "source"); // save 7th column as source
    colIndexToDefaultFieldMapping.put(8, "expiry date"); // save 9th column as expiry date
    colIndexToDefaultFieldMapping.put(9, "quantity"); // save 10th column as quantity
    sampleCsvImporter.convertLinesToSamples(
        sampleProcessingResult,
        csvSampleLines,
        colIndexToDefaultFieldMapping,
        csvSampleLines.get(0).length);
    assertEquals(2, sampleProcessingResult.getSuccessCount());
    importMgr.importSamples(
        importResult, new ApiInventoryImportResult(null, sampleProcessingResult, null, testUser));
    sampleImportResult = importResult.getSampleResult();
    assertEquals(2, sampleImportResult.getSuccessCount());

    sample1 = (ApiSampleWithFullSubSamples) sampleImportResult.getResults().get(0).getRecord();
    assertEquals("testName5", sample1.getName());
    assertEquals("desc1", sample1.getDescription());
    assertTrue(sample1.getTags().isEmpty());
    assertEquals(LocalDate.of(2030, 1, 1), sample1.getExpiryDate());
    assertEquals(SampleSource.OTHER, sample1.getSampleSource());
    assertEquals("200 g", sample1.getQuantity().toQuantityInfo().toPlainString());
    assertEquals(3, sample1.getFields().size());
    assertEquals("testContent1", sample1.getFields().get(0).getContent());
    assertEquals(null, sample1.getFields().get(1).getContent());
    assertEquals(List.of("yes"), sample1.getFields().get(1).getSelectedOptions());
    assertEquals(null, sample1.getFields().get(2).getContent());
    assertEquals(Collections.emptyList(), sample1.getFields().get(2).getSelectedOptions());

    sample2 = (ApiSampleWithFullSubSamples) sampleImportResult.getResults().get(1).getRecord();
    assertEquals("testName6", sample2.getName());
    assertEquals("desc2", sample2.getDescription());
    assertEquals("tag1", sample2.getTags().get(0).toString());
    assertEquals("tag2", sample2.getTags().get(1).toString());
    assertEquals(null, sample2.getExpiryDate());
    assertEquals(SampleSource.VENDOR_SUPPLIED, sample2.getSampleSource());
    assertEquals("5.25 µg", sample2.getQuantity().toQuantityInfo().toPlainString());
    assertEquals(3, sample2.getFields().size());
    assertEquals("testContent2", sample2.getFields().get(0).getContent());
    assertEquals(null, sample2.getFields().get(1).getContent());
    assertEquals(List.of("no"), sample2.getFields().get(1).getSelectedOptions());
    assertEquals(null, sample2.getFields().get(2).getContent());
    assertEquals(List.of("opt2"), sample2.getFields().get(2).getSelectedOptions());
  }

  @Test
  public void testParsingValidSampleCSVFile() throws IOException {

    // 1. problem with one csv row having fewer values
    InputStream threeSampleCsvIS =
        IOUtils.toInputStream(
            "Name, Data, Quantity\n"
                + "TestSample1,TestData,\n"
                + "TestSample2,TestData, 12.5 mg\n"
                + "TestSample3,TestData, 0.211μg\n"
                + "TestSample4,TestData, 5 g\n");
    ApiInventoryImportSampleParseResult parseResult =
        importMgr.parseSamplesCsvFile("myFile", threeSampleCsvIS, testUser);

    // parsing should still succeed (it's for import action to show problems during prevalidation
    // phase)
    assertNotNull(parseResult.getTemplateInfo());
    // check suggested column names
    assertEquals(3, parseResult.getFieldNameForColumnName().size());
    assertEquals("_Name", parseResult.getFieldNameForColumnName().get("Name"));
    // check suggested radio options
    assertEquals(3, parseResult.getRadioOptionsForColumn().size());
    assertEquals(1, parseResult.getRadioOptionsForColumn().get("Data").size());
    assertEquals("TestData", parseResult.getRadioOptionsForColumn().get("Data").get(0));
    // check suggested quantity unit type for column
    assertEquals(1, parseResult.getQuantityUnitForColumn().size());
    assertEquals(
        RSUnitDef.MILLI_GRAM.getId(), parseResult.getQuantityUnitForColumn().get("Quantity"));
    // check non-blank columns
    assertEquals(List.of("Name", "Data"), parseResult.getColumnsWithoutBlankValue());
    // check rows count
    assertEquals(4, parseResult.getRowsCount());
  }

  @Test
  public void testParsingProblematicSampleCSVFile() throws IOException {

    // 1. problem with one csv row having fewer values
    InputStream threeSampleCsvIS =
        IOUtils.toInputStream(
            "Name, Data\nTestSample1,TestData\nTestSample2\nTestSample3,TestData");
    ApiInventoryImportSampleParseResult parseResult =
        importMgr.parseSamplesCsvFile("myFile", threeSampleCsvIS, testUser);

    // parsing should still succeed (it's for import action to show problems during prevalidation
    // phase)
    assertNotNull(parseResult.getTemplateInfo());
    assertEquals(3, parseResult.getRowsCount());

    // 2. empty file provided - parsing shouldn't succeed as empty file is an obvious data problem
    InputStream emptyFileCsvIS = IOUtils.toInputStream(" ");
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> importMgr.parseSamplesCsvFile("myFile", emptyFileCsvIS, testUser));
    assertTrue(iae.getMessage().contains("CSV file seems to be empty"), iae.getMessage());

    // 3. only one line provided - parsing shouldn't succeed as empty file is an obvious data
    // problem
    InputStream oneLineCsvIS = IOUtils.toInputStream("Test\n");
    iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> importMgr.parseSamplesCsvFile("myFile", oneLineCsvIS, testUser));
    assertTrue(iae.getMessage().contains("CSV file seems to have just one line"), iae.getMessage());
  }

  @Test
  public void testSampleCsvImportWithTemplateErrors() throws IOException {

    // csv file with one column and one sample
    byte[] oneSampleCsvBytes = "Name\nTestSample".getBytes();
    HashMap<String, String> nameColumnMapping = new HashMap<>();
    nameColumnMapping.put("Name", "name");

    // try import with invalid template (no name)
    ApiSampleTemplatePost templateInfo = new ApiSampleTemplatePost();
    InventoryImportException iie =
        assertThrows(
            InventoryImportException.class,
            () -> importMgr.createSampleResultWithRequestedTemplate(templateInfo, testUser));
    assertEquals("name: name is a required field.", iie.getMessage());
    ApiInventoryImportResult fullResult = iie.getResult();
    ApiInventoryImportSampleImportResult sampleProcessingResult = fullResult.getSampleResult();
    assertNotNull(sampleProcessingResult);

    // samples from csv lines shouldn't be processed
    assertEquals(0, sampleProcessingResult.getSuccessCount());
    assertEquals(0, sampleProcessingResult.getErrorCount());

    // template problem should be reported in template.error
    assertFalse(sampleProcessingResult.isTemplateCreated());
    assertNotNull(sampleProcessingResult.getTemplate());
    String templateErrorMsg = sampleProcessingResult.getTemplate().getError().getErrors().get(0);
    assertEquals("name: name is a required field.", templateErrorMsg);

    // try simplest valid template, one csv sample line
    templateInfo.setName("TestTemplate");
    sampleProcessingResult =
        importMgr.createSampleResultWithRequestedTemplate(templateInfo, testUser);
    ApiInventoryImportResult processingResult = new ApiInventoryImportResult(testUser);
    processingResult.setSampleResult(sampleProcessingResult);
    sampleCsvImporter.readCsvIntoImportResult(
        new ByteArrayInputStream(oneSampleCsvBytes), nameColumnMapping, processingResult);
    assertNotNull(sampleProcessingResult);

    // result should be success, with a template and a sample being created
    assertTrue(sampleProcessingResult.isTemplateCreated());
    assertEquals(1, sampleProcessingResult.getSuccessCount());
    assertEquals(0, sampleProcessingResult.getErrorCount());

    // check created template
    ApiSample createdTemplate = (ApiSample) sampleProcessingResult.getTemplate().getRecord();
    assertNotNull(createdTemplate);
    assertEquals("TestTemplate", createdTemplate.getName());

    // check created sample
    assertEquals("TestSample", sampleProcessingResult.getResults().get(0).getRecord().getName());
  }

  @Test
  public void testSampleCsvImportWithErrorsPrevalidated() throws IOException {

    // csv input with multiple problematic samples
    InputStream samplesCsvIS =
        IOUtils.toInputStream(
            "Expiry Date, Name, Quantity, Parent Container, Parent Container Global Id\n"
                + "2030-01-01, TestSample1, 200 ml,,\n"
                + "2030-01-01,,,,\n"
                + "2030-01-01,,,,,\n"
                + "TestData3, TestSample3,,,\n"
                + "2030-01-01, TestSample4,,,\n"
                + "2030-01-01, TestSample5, ml,,\n"
                + "2030-01-01, TestSample6, 500 g,,\n"
                + ", TestSample7,,containerX,\n"
                + ", TestSample8,,containerX, IC1\n");
    HashMap<String, String> nameDescriptionMapping = new HashMap<>();
    nameDescriptionMapping.put("Name", "name");
    nameDescriptionMapping.put("Expiry Date", "expiry date");
    nameDescriptionMapping.put("Quantity", "quantity");
    nameDescriptionMapping.put("Parent Container", "parent container import id");
    nameDescriptionMapping.put("Parent Container Global Id", "parent container global id");

    // use basic template
    ApiSampleTemplatePost templateInfo = new ApiSampleTemplatePost();
    templateInfo.setName("TestTemplate");
    ApiInventoryImportSampleImportResult sampleResult =
        importMgr.createSampleResultWithRequestedTemplate(templateInfo, testUser);
    ApiInventoryImportResult processingResult = new ApiInventoryImportResult(testUser);
    processingResult.setSampleResult(sampleResult);

    // try importing
    sampleCsvImporter.readCsvIntoImportResult(
        samplesCsvIS, nameDescriptionMapping, processingResult);
    importMgr.prevalidateSamples(processingResult);

    assertNotNull(sampleResult);

    // result object should list all errors
    assertTrue(sampleResult.isTemplateCreated());
    assertEquals(0, sampleResult.getSuccessCount());
    assertEquals(7, sampleResult.getErrorCount());
    assertEquals(1, sampleResult.getSuccessCountBeforeFirstError());
    assertEquals(InventoryBulkOperationStatus.PREVALIDATION_ERROR, sampleResult.getStatus());

    // check errors
    String sampleErrorMsg = sampleResult.getResults().get(1).getError().getErrors().get(0);
    assertEquals("name: name is a required field.", sampleErrorMsg);
    sampleErrorMsg = sampleResult.getResults().get(2).getError().getErrors().get(0);
    assertEquals("Unexpected number of values in CSV line, expected: 5, was: 6", sampleErrorMsg);
    sampleErrorMsg = sampleResult.getResults().get(3).getError().getErrors().get(0);
    assertEquals("Text 'TestData3' could not be parsed at index 0", sampleErrorMsg);
    sampleErrorMsg = sampleResult.getResults().get(5).getError().getErrors().get(0);
    assertEquals(
        "Cannot parse quantity string: Failed to parse number-literal 'ml'.", sampleErrorMsg);
    sampleErrorMsg = sampleResult.getResults().get(6).getError().getErrors().get(0);
    assertEquals(
        "quantity: Sample quantity unit 7 (GRAM) is incompatible with template quantity unit 3"
            + " (MILLI_LITRE)",
        sampleErrorMsg);
    sampleErrorMsg = sampleResult.getResults().get(7).getError().getErrors().get(0);
    assertEquals(
        "id: Parent container with import id 'containerX' could not be found", sampleErrorMsg);
    sampleErrorMsg = sampleResult.getResults().get(8).getError().getErrors().get(0);
    assertEquals(
        "id: Parent container should be set via either 'Parent Container Import ID' "
            + "or 'Parent Container Global ID', but not both at the same time",
        sampleErrorMsg);
  }

  @Test
  public void importPrevalidationForMandatoryFieldsTemplate() throws IOException {

    // csv input with multiple problematic samples
    InputStream samplesCsvIS =
        IOUtils.toInputStream(
            "Name, MyText1, MyText2, MyText3, MyRadio1, MyRadio2, MyRadio3\n"
                + ",3.14,,,,a,\n"
                + "TestSample2,,,,,,\n");

    HashMap<String, String> nameMapping = new HashMap<>();
    nameMapping.put("Name", "name");

    ApiSampleTemplate mandatoryFieldsTemplate = createSampleTemplateWithMandatoryFields(testUser);
    ApiSampleTemplatePost templateInfo = new ApiSampleTemplatePost();
    templateInfo.setId(mandatoryFieldsTemplate.getId());
    ApiInventoryImportSampleImportResult sampleResult =
        importMgr.createSampleResultWithRequestedTemplate(templateInfo, testUser);
    ApiInventoryImportResult processingResult = new ApiInventoryImportResult(testUser);
    processingResult.setSampleResult(sampleResult);

    // try importing
    sampleCsvImporter.readCsvIntoImportResult(samplesCsvIS, nameMapping, processingResult);
    importMgr.prevalidateSamples(processingResult);

    // result object should list all errors
    assertFalse(sampleResult.isTemplateCreated());
    assertEquals(0, sampleResult.getSuccessCount());
    assertEquals(2, sampleResult.getErrorCount());
    assertEquals(0, sampleResult.getSuccessCountBeforeFirstError());
    assertEquals(InventoryBulkOperationStatus.PREVALIDATION_ERROR, sampleResult.getStatus());

    // check errors
    List<String> firstLineErrors = sampleResult.getResults().get(0).getError().getErrors();
    assertEquals(3, firstLineErrors.size());
    assertEquals("name: name is a required field.", firstLineErrors.get(0));
    assertEquals(
        "fields: Field 'myText (mandatory - no default value)' is mandatory, but provided value was"
            + " empty",
        firstLineErrors.get(1));
    assertEquals(
        "fields: Field 'myRadio (mandatory - with default value)' is mandatory, but no option is"
            + " provided",
        firstLineErrors.get(2));
    List<String> secondLineErrors = sampleResult.getResults().get(1).getError().getErrors();
    assertEquals(4, secondLineErrors.size());
    assertEquals(
        "fields: Field 'myText (mandatory - with default value)' is mandatory, but provided value"
            + " was empty",
        secondLineErrors.get(0));
    assertEquals(
        "fields: Field 'myText (mandatory - no default value)' is mandatory, but provided value was"
            + " empty",
        secondLineErrors.get(1));
    assertEquals(
        "fields: Field 'myRadio (mandatory - with default value)' is mandatory, but no option is"
            + " provided",
        secondLineErrors.get(2));
    assertEquals(
        "fields: Field 'myRadio (mandatory - no default value)' is mandatory, but no option is"
            + " provided",
        secondLineErrors.get(3));
  }

  @Test
  public void testSampleCsvImportWithErrorsOnDbSave() throws IOException, BindException {

    // mock sample controller to throw error on save
    SamplesApiController spiedController = Mockito.spy(samplesController);
    Mockito.doThrow(new IllegalArgumentException("mocked create sample exception"))
        .when(spiedController)
        .createNewSample(Mockito.any(), Mockito.any(), Mockito.any());
    importMgr.setSamplesController(spiedController);

    // csv file with two columns and two samples
    InputStream samplesCsvIS =
        IOUtils.toInputStream(
            "Data, Name\n" + "TestData1, TestSample1\n" + "TestData2, TestSample2\n");
    HashMap<String, String> nameDescriptionMapping = new HashMap<>();
    nameDescriptionMapping.put("Name", "name");
    nameDescriptionMapping.put("Data", "description");

    // try simplest valid template, one csv sample line
    ApiSampleTemplate templateInfo = new ApiSampleTemplate();
    templateInfo.setName("TestTemplate");
    final ApiInventoryImportSampleImportResult sampleResult =
        new ApiInventoryImportSampleImportResult();
    sampleResult.addCreatedTemplateResult(templateInfo);
    ApiInventoryImportResult processingResult =
        new ApiInventoryImportResult(null, sampleResult, null, testUser);

    sampleCsvImporter.readCsvIntoImportResult(
        samplesCsvIS, nameDescriptionMapping, processingResult);

    ApiInventoryImportResult importResult = new ApiInventoryImportResult(testUser);
    InventoryImportException iie =
        assertThrows(
            InventoryImportException.class,
            () -> importMgr.importSamples(importResult, processingResult));
    assertEquals("mocked create sample exception", iie.getMessage());
    ApiInventoryImportResult fullResult = iie.getResult();
    ApiInventoryImportSampleImportResult sampleImportResult = fullResult.getSampleResult();
    assertNotNull(sampleImportResult);

    // result should be success template and then error on first sample creation
    assertTrue(sampleImportResult.isTemplateCreated());
    assertEquals(0, sampleImportResult.getSuccessCount());
    assertEquals(1, sampleImportResult.getErrorCount());
    assertEquals(0, sampleImportResult.getSuccessCountBeforeFirstError());

    // check created template
    ApiSample createdTemplate = (ApiSample) sampleImportResult.getTemplate().getRecord();
    assertNotNull(createdTemplate);
    assertEquals("TestTemplate", createdTemplate.getName());

    // check error on the sample
    String sampleErrorMsg = sampleImportResult.getResults().get(0).getError().getErrors().get(0);
    assertEquals("mocked create sample exception", sampleErrorMsg);
  }

  @Test
  public void testSampleCsvImportIntoPreexistingComplexTemplate()
      throws IOException, BindException {

    // csv file with 3 samples: columns mapped to name/expiry date/template fields/quantity
    InputStream samplesCsvIS =
        IOUtils.toInputStream(
            "Expiry Date, Name, MyNumber, MyDate, MyString, MyText, MyURL, MyRef, MyAtt, MyTime,"
                + " MyRadio, MyChoice, MyQuantity\n"
                + "2030-01-01, TestSample1,3.14,,,,,,,,,,\n"
                + "2030-01-01, TestSample2,,2021-11-23,,,,,,,,,5.25ml\n"
                + "2030-01-01, TestSample3,,, dummyText,,,,,,,,200 ml\n");
    HashMap<String, String> nameDescriptionMapping = new HashMap<>();
    nameDescriptionMapping.put("Name", "name");
    nameDescriptionMapping.put("Expiry Date", "expiry date");
    nameDescriptionMapping.put("MyQuantity", "quantity");

    // use complex template
    Sample complexTemplate = findComplexSampleTemplate(testUser);
    ApiInventoryImportSampleImportResult sampleResult = new ApiInventoryImportSampleImportResult();
    sampleResult.addExistingTemplateResult(new ApiSampleTemplate(complexTemplate));
    ApiInventoryImportResult processingResult = new ApiInventoryImportResult(testUser);
    processingResult.setSampleResult(sampleResult);

    sampleCsvImporter.readCsvIntoImportResult(
        samplesCsvIS, nameDescriptionMapping, processingResult);
    assertNotNull(sampleResult);

    // result object should list used template and created samples
    assertNotNull(sampleResult.getTemplate().getRecord());
    assertEquals("Complex Sample Template", sampleResult.getTemplate().getRecord().getName());
    assertFalse(sampleResult.isTemplateCreated());
    assertEquals(3, sampleResult.getSuccessCount());
    assertEquals(0, sampleResult.getErrorCount());
    assertEquals(3, sampleResult.getSuccessCountBeforeFirstError());

    assertEquals("TestSample1", sampleResult.getResults().get(0).getRecord().getName());
    assertEquals(
        "0 ml",
        sampleResult
            .getResults()
            .get(0)
            .getRecord()
            .getQuantity()
            .toQuantityInfo()
            .toPlainString());
    assertEquals("TestSample2", sampleResult.getResults().get(1).getRecord().getName());
    assertEquals(
        "5.25 ml",
        sampleResult
            .getResults()
            .get(1)
            .getRecord()
            .getQuantity()
            .toQuantityInfo()
            .toPlainString());
    assertEquals("TestSample3", sampleResult.getResults().get(2).getRecord().getName());
    assertEquals(
        "200 ml",
        sampleResult
            .getResults()
            .get(2)
            .getRecord()
            .getQuantity()
            .toQuantityInfo()
            .toPlainString());
  }

  @Test
  public void testSampleCsvImportIntoPreexistingTemplateErrors() throws IOException {

    // csv file with fewer columns than sample template fields
    InputStream samplesCsvIS =
        IOUtils.toInputStream(
            "Name, MyNumber, MyDate, MyString, MyText, MyURL, MyRef, MyAtt, MyRadio\n"
                + "TestSample1,3.14,,,,,,,\n");

    HashMap<String, String> nameMapping = new HashMap<>();
    nameMapping.put("Name", "name");

    Sample complexTemplate = findComplexSampleTemplate(testUser);
    ApiSampleTemplate complexApiTemplate = new ApiSampleTemplate(complexTemplate);

    // find complex template
    ApiInventoryImportSampleImportResult sampleResult = new ApiInventoryImportSampleImportResult();
    sampleResult.addCreatedTemplateResult(complexApiTemplate);
    ApiInventoryImportResult processingResult = new ApiInventoryImportResult(testUser);
    processingResult.setSampleResult(sampleResult);

    sampleCsvImporter.readCsvIntoImportResult(samplesCsvIS, nameMapping, processingResult);
    assertNotNull(sampleResult);

    // result object should list all errors
    assertFalse(sampleResult.isTemplateCreated());
    assertEquals(0, sampleResult.getSuccessCount());
    assertEquals(0, sampleResult.getErrorCount());
    assertEquals(0, sampleResult.getSuccessCountBeforeFirstError());
    assertEquals(InventoryBulkOperationStatus.PREVALIDATION_ERROR, sampleResult.getStatus());

    // check error
    String sampleErrorMsg = sampleResult.getTemplate().getError().getErrors().get(0);
    assertEquals(
        "Number of unmapped CSV columns is 8, but number of fields in sample template is 10. "
            + "The CSV file must exactly map all the template fields.",
        sampleErrorMsg);
  }

  @Test
  public void testSampleCsvImportIntoContainer() throws IOException, BindException {

    // csv file with 3 samples: columns mapped to name/expiry date/template fields/quantity
    InputStream samplesCsvIS =
        IOUtils.toInputStream(
            "Name, Parent Container\n"
                + "TestSample1, c1\n"
                + "TestSample2, \n"
                + "TestSample3, c2\n");
    HashMap<String, String> nameDescriptionMapping = new HashMap<>();
    nameDescriptionMapping.put("Name", "name");
    nameDescriptionMapping.put("Parent Container", "parent container import id");

    // simulate imported containers
    ApiInventoryImportPartialResult containerResult =
        getContainerProcessingResultWithTwoContainers();

    // use basic template
    ApiSampleTemplate templateInfo = new ApiSampleTemplate();
    templateInfo.setName("TestTemplate");
    ApiInventoryImportSampleImportResult sampleResult = new ApiInventoryImportSampleImportResult();
    sampleResult.addCreatedTemplateResult(templateInfo);
    ApiInventoryImportResult processingResult =
        new ApiInventoryImportResult(null, sampleResult, null, testUser);

    sampleCsvImporter.readCsvIntoImportResult(
        samplesCsvIS, nameDescriptionMapping, processingResult);
    assertNotNull(sampleResult);

    ApiInventoryImportResult importResult = new ApiInventoryImportResult(testUser);
    importResult.setContainerResult(containerResult);
    importMgr.importSamples(importResult, processingResult);
    ApiInventoryImportSampleImportResult sampleImportResult = importResult.getSampleResult();
    assertNotNull(sampleImportResult);

    // default workbench container should be created
    assertNotNull(importResult.getDefaultContainer());
    String defaultImportContainerName = importResult.getDefaultContainer().getName();
    assertTrue(defaultImportContainerName.startsWith("imported items"), defaultImportContainerName);

    // result object should list used template and created samples
    assertNotNull(sampleImportResult.getTemplate().getRecord());
    assertEquals("TestTemplate", sampleImportResult.getTemplate().getRecord().getName());
    assertTrue(sampleImportResult.isTemplateCreated());
    assertEquals(3, sampleImportResult.getSuccessCount());
    assertEquals(0, sampleImportResult.getErrorCount());
    assertEquals(3, sampleImportResult.getSuccessCountBeforeFirstError());

    ApiSampleWithFullSubSamples createdSample1 =
        (ApiSampleWithFullSubSamples) sampleImportResult.getResults().get(0).getRecord();
    assertEquals("TestSample1", createdSample1.getName());
    assertEquals("1 ml", createdSample1.getQuantity().toQuantityInfo().toPlainString());
    assertEquals(
        "testContainer1", createdSample1.getSubSamples().get(0).getParentContainer().getName());

    ApiSampleWithFullSubSamples createdSample2 =
        (ApiSampleWithFullSubSamples) sampleImportResult.getResults().get(1).getRecord();
    assertEquals("TestSample2", createdSample2.getName());
    assertEquals(
        defaultImportContainerName,
        createdSample2.getSubSamples().get(0).getParentContainer().getName());

    ApiSampleWithFullSubSamples createdSample3 =
        (ApiSampleWithFullSubSamples) sampleImportResult.getResults().get(2).getRecord();
    assertEquals("TestSample3", createdSample3.getName());
    assertEquals(
        "testContainer2", createdSample3.getSubSamples().get(0).getParentContainer().getName());
  }

  private ApiInventoryImportPartialResult getContainerProcessingResultWithTwoContainers() {
    ApiInventoryImportPartialResult containerResult =
        new ApiInventoryImportPartialResult(ApiInventoryRecordType.CONTAINER);
    ApiContainer container1 =
        containerManager.createNewApiContainer(
            new ApiContainer("testContainer1", Container.ContainerType.LIST), testUser);
    containerResult.addSuccessResult(container1);
    containerResult.addResultNumberWithImportId(0, "c1");
    ApiContainer container2 =
        containerManager.createNewApiContainer(
            new ApiContainer("testContainer2", Container.ContainerType.LIST), testUser);
    containerResult.addSuccessResult(container2);
    containerResult.addResultNumberWithImportId(1, "c2");
    return containerResult;
  }

  @Test
  public void testSubSampleCreationMethods() {

    List<String[]> csvSubSampleLines = new ArrayList<>();
    csvSubSampleLines.add(new String[] {"testSubSample1", "s1", "c1"});
    csvSubSampleLines.add(new String[] {"testSubSample2", "s2", ""});
    csvSubSampleLines.add(new String[] {"testSubSample3", "s2", "c2"});
    csvSubSampleLines.add(new String[] {"testSubSample4", "s1", ""});
    Map<Integer, String> colIndexToDefaultFieldMapping = new HashMap<>();
    colIndexToDefaultFieldMapping.put(0, "name");
    colIndexToDefaultFieldMapping.put(1, "parent sample import id");
    colIndexToDefaultFieldMapping.put(2, "parent container import id");

    // test lines conversion
    ApiInventoryImportSubSampleImportResult subSampleResult =
        new ApiInventoryImportSubSampleImportResult();
    subSampleCsvImporter.convertLinesToSubSamples(
        subSampleResult,
        csvSubSampleLines,
        colIndexToDefaultFieldMapping,
        csvSubSampleLines.get(0).length);
    assertEquals(4, subSampleResult.getSuccessCount());
    assertEquals(
        ApiInventoryRecordInfo.ApiInventoryRecordType.SUBSAMPLE,
        subSampleResult.getResults().get(0).getRecord().getType());
    assertEquals("testSubSample1", subSampleResult.getResults().get(0).getRecord().getName());
    assertNull(subSampleResult.getResults().get(0).getRecord().getId()); // not set yet
    assertEquals("testSubSample2", subSampleResult.getResults().get(1).getRecord().getName());
    assertEquals("testSubSample3", subSampleResult.getResults().get(2).getRecord().getName());
    assertEquals("testSubSample4", subSampleResult.getResults().get(3).getRecord().getName());
    // 2 records marked for putting into parent container during import process
    assertEquals(2, subSampleResult.getResultNumberToParentContainerImportIdMap().size());
    // 4 records marked for putting into parent sample
    assertEquals(4, subSampleResult.getResultNumberToParentSampleImportIdMap().size());

    // test updating subsample results after importing samples
    // simulate imported samples
    ApiInventoryImportSampleImportResult sampleImportResult =
        new ApiInventoryImportSampleImportResult();
    ApiSampleWithFullSubSamples sample1 = new ApiSampleWithFullSubSamples("importedSample1");
    sample1.getSubSamples().add(new ApiSubSample("importedSubSample1"));
    sample1.getSubSamples().add(new ApiSubSample("importedSubSample4"));
    ApiSampleWithFullSubSamples sample2 = new ApiSampleWithFullSubSamples("importedSample1");
    sample2.getSubSamples().add(new ApiSubSample("importedSubSample2"));
    sample2.getSubSamples().add(new ApiSubSample("importedSubSample3"));
    sampleImportResult.addResultNumberWithImportId(0, "s1");
    sampleImportResult.addSuccessResult(sample1);
    sampleImportResult.addResultNumberWithImportId(1, "s2");
    sampleImportResult.addSuccessResult(sample2);
    ApiInventoryImportResult importResult = new ApiInventoryImportResult(testUser);
    importResult.setSampleResult(sampleImportResult);

    // run subsample update and verify the results updated
    importMgr.populateSubSampleResultsWithImportedSubSamples(importResult, subSampleResult);
    subSampleResult = importResult.getSubSampleResult();
    assertEquals("importedSubSample1", subSampleResult.getResults().get(0).getRecord().getName());
    assertEquals("importedSubSample2", subSampleResult.getResults().get(1).getRecord().getName());
    assertEquals("importedSubSample3", subSampleResult.getResults().get(2).getRecord().getName());
    assertEquals("importedSubSample4", subSampleResult.getResults().get(3).getRecord().getName());
  }

  @Test
  public void importSampleWithSubSamplesInContainers() throws IOException {

    ApiInventoryImportResult processingResult = new ApiInventoryImportResult(testUser);

    // simulate imported containers
    ApiInventoryImportPartialResult containerProcessingResult =
        getContainerProcessingResultWithTwoContainers();
    processingResult.setContainerResult(containerProcessingResult);

    // csv file with 3 samples: columns mapped to name/import identifier
    InputStream samplesCsvIS =
        IOUtils.toInputStream(
            "Name, Import Identifier\n"
                + "TestSample1, s1\n"
                + "TestSample2, s2\n"
                + "TestSample3, s3\n");
    HashMap<String, String> sampleMapping = new HashMap<>();
    sampleMapping.put("Name", "name");
    sampleMapping.put("Import Identifier", "import identifier");

    // create simplest template & process samples
    ApiSampleTemplate templateInfo = new ApiSampleTemplate();
    templateInfo.setName("TestTemplate");
    ApiInventoryImportSampleImportResult sampleResult = new ApiInventoryImportSampleImportResult();
    sampleResult.addCreatedTemplateResult(templateInfo);
    processingResult.setSampleResult(sampleResult);

    sampleCsvImporter.readCsvIntoImportResult(samplesCsvIS, sampleMapping, processingResult);
    assertNotNull(processingResult.getSampleResult());

    // csv file with 4 subsamples pointing to two of the samples and containers
    InputStream subSamplesCsvIS =
        IOUtils.toInputStream(
            "Name, Parent Sample, Parent Container\n"
                + "TestSubSample1, s1, c1\n"
                + "TestSubSample2, s3, c1\n"
                + "TestSubSample3, s1, \n"
                + "TestSubSample4, s1, c2\n");
    HashMap<String, String> subSampleMapping = new HashMap<>();
    subSampleMapping.put("Name", "name");
    subSampleMapping.put("Parent Sample", "parent sample import id");
    subSampleMapping.put("Parent Container", "parent container import id");

    subSampleCsvImporter.readCsvIntoImportResult(
        subSamplesCsvIS, subSampleMapping, processingResult);
    ApiInventoryImportSubSampleImportResult subSampleProcessingResult =
        processingResult.getSubSampleResult();
    assertNotNull(subSampleProcessingResult);

    // populate subsamples with samples, put into containers & import
    importMgr.populateSamplesToImportWithSubSamplesToImport(processingResult);

    ApiInventoryImportResult importResult = new ApiInventoryImportResult(testUser);
    importMgr.importSamples(importResult, processingResult);
    importMgr.populateSubSampleResultsWithImportedSubSamples(
        importResult, subSampleProcessingResult);
    importResult.setContainerResult(containerProcessingResult);
    importMgr.moveImportedSubSamplesIntoRequestedContainers(processingResult, importResult);

    ApiInventoryImportSampleImportResult sampleImportResult = importResult.getSampleResult();
    assertNotNull(sampleImportResult);
    ApiInventoryImportSubSampleImportResult subSampleImportResult =
        importResult.getSubSampleResult();
    assertNotNull(subSampleImportResult);

    // result object should list used template and created samples
    assertNotNull(sampleImportResult.getTemplate().getRecord());
    assertEquals("TestTemplate", sampleImportResult.getTemplate().getRecord().getName());
    assertTrue(sampleImportResult.isTemplateCreated());
    assertEquals(3, sampleImportResult.getSuccessCount());
    assertEquals(0, sampleImportResult.getErrorCount());
    assertEquals(3, sampleImportResult.getSuccessCountBeforeFirstError());

    // default workbench container should be created
    assertNotNull(importResult.getDefaultContainer());
    String defaultImportContainerName = importResult.getDefaultContainer().getName();
    assertTrue(defaultImportContainerName.startsWith("imported items"), defaultImportContainerName);

    // first sample should have 3 non-default subsamples
    ApiSampleWithFullSubSamples createdSample1 =
        (ApiSampleWithFullSubSamples) sampleImportResult.getResults().get(0).getRecord();
    assertEquals("TestSample1", createdSample1.getName());
    assertEquals("3 ml", createdSample1.getQuantity().toQuantityInfo().toPlainString());
    assertEquals(3, createdSample1.getSubSamples().size());
    assertEquals("TestSubSample1", createdSample1.getSubSamples().get(0).getName());
    // known issue: samples results show workbench as a parent of non-default subsamples
    assertTrue(createdSample1.getSubSamples().get(0).getParentContainer().isWorkbench());
    assertEquals("TestSubSample3", createdSample1.getSubSamples().get(1).getName());
    assertEquals("TestSubSample4", createdSample1.getSubSamples().get(2).getName());

    // second sample should have one default subsample
    ApiSampleWithFullSubSamples createdSample2 =
        (ApiSampleWithFullSubSamples) sampleImportResult.getResults().get(1).getRecord();
    assertEquals("TestSample2", createdSample2.getName());
    assertEquals(1, createdSample2.getSubSamples().size());
    assertEquals("TestSample2.01", createdSample2.getSubSamples().get(0).getName());
    // parent of default sample shown correctly
    assertEquals(
        defaultImportContainerName,
        createdSample2.getSubSamples().get(0).getParentContainer().getName());

    // third sample should have one non-default subsample
    ApiSampleWithFullSubSamples createdSample3 =
        (ApiSampleWithFullSubSamples) sampleImportResult.getResults().get(2).getRecord();
    assertEquals("TestSample3", createdSample3.getName());
    assertEquals(1, createdSample3.getSubSamples().size());
    assertEquals("TestSubSample2", createdSample3.getSubSamples().get(0).getName());

    // subsample results show correct parent though parent
    assertEquals(
        "testContainer1",
        subSampleImportResult.getResults().get(0).getRecord().getParentContainer().getName());
    assertEquals(
        "testContainer1",
        subSampleImportResult.getResults().get(1).getRecord().getParentContainer().getName());
    assertEquals(
        defaultImportContainerName,
        subSampleImportResult.getResults().get(2).getRecord().getParentContainer().getName());
    assertEquals(
        "testContainer2",
        subSampleImportResult.getResults().get(3).getRecord().getParentContainer().getName());
  }

  @Test
  public void subSampleImportErrorsPrevalidated() throws IOException {

    ApiInventoryImportResult processingResult = new ApiInventoryImportResult(testUser);
    ApiContainer workbench = getWorkbenchForUser(testUser);
    ApiContainer gridContainer = createBasicGridContainerForUser(testUser, 2, 2);
    ApiSampleWithFullSubSamples preexistingSample = createBasicSampleForUser(testUser);
    assertEquals(RSUnitDef.GRAM.getId(), preexistingSample.getQuantity().getUnitId());

    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("apiImport"));
    initialiseContentWithEmptyContent(otherUser);
    ApiContainer otherUserWorkbench = getWorkbenchForUser(otherUser);
    ApiSampleWithFullSubSamples otherUserSample = createBasicSampleForUser(otherUser);

    // simulate imported containers
    ApiInventoryImportPartialResult containerProcessingResult =
        getContainerProcessingResultWithTwoContainers();
    processingResult.setContainerResult(containerProcessingResult);

    // csv input with a sample
    InputStream samplesCsvIS = IOUtils.toInputStream("Name, Import Identifier\nTestSample1, s1\n");
    HashMap<String, String> sampleMapping = new HashMap<>();
    sampleMapping.put("Name", "name");
    sampleMapping.put("Import Identifier", "import identifier");

    // process samples using simplest template
    ApiSampleTemplate templateInfo = new ApiSampleTemplate();
    templateInfo.setName("TestTemplate");
    templateInfo.setDefaultUnitId(RSUnitDef.MILLI_LITRE.getId());
    ApiInventoryImportSampleImportResult sampleResult = new ApiInventoryImportSampleImportResult();
    sampleResult.addCreatedTemplateResult(templateInfo);
    processingResult.setSampleResult(sampleResult);
    sampleCsvImporter.readCsvIntoImportResult(samplesCsvIS, sampleMapping, processingResult);

    // csv input with various subsamples subsamples (1st and 7th fine, other with various problems)
    InputStream subSamplesCsvIS =
        IOUtils.toInputStream(
            "Name, Quantity, Parent Sample, Parent Sample Global Id, Parent Container, Parent"
                + " Container Global Id\n"
                + "TestSubSample1,1 ml, s1,, c1,\n"
                + "TestSubSample2,, ,, c1, \n"
                + "TestSubSample3,, s2,, c3, \n"
                + "TestSubSample4,1 mg, s1,, c1, IC0\n"
                + "TestSubSample5,, s1,,, IC0\n"
                + "TestSubSample6,, s1,,, "
                + gridContainer.getGlobalId()
                + "\n"
                + "TestSubSample7,5 mg,, "
                + preexistingSample.getGlobalId()
                + ",, "
                + workbench.getGlobalId()
                + "\n"
                + "TestSubSample8,5 mg, s1,,, "
                + otherUserWorkbench.getGlobalId()
                + "\n"
                + "TestSubSample9,,, SA0,,\n"
                + "TestSubSample10,,, "
                + otherUserSample.getGlobalId()
                + ",,\n");
    HashMap<String, String> subSampleMapping = new HashMap<>();
    subSampleMapping.put("Name", "name");
    subSampleMapping.put("Quantity", "quantity");
    subSampleMapping.put("Parent Sample", "parent sample import id");
    subSampleMapping.put("Parent Sample Global Id", "parent sample global id");
    subSampleMapping.put("Parent Container", "parent container import id");
    subSampleMapping.put("Parent Container Global Id", "parent container global id");

    // run subsample processing and validation
    subSampleCsvImporter.readCsvIntoImportResult(
        subSamplesCsvIS, subSampleMapping, processingResult);
    ApiInventoryImportSubSampleImportResult subSampleResult = processingResult.getSubSampleResult();
    importMgr.prevalidateSubSamples(processingResult);
    assertNotNull(subSampleResult);
    assertEquals(InventoryBulkOperationStatus.PREVALIDATION_ERROR, subSampleResult.getStatus());
    assertEquals(0, subSampleResult.getSuccessCount());
    assertEquals(8, subSampleResult.getErrorCount());
    assertEquals(1, subSampleResult.getSuccessCountBeforeFirstError());

    // 1st and 7th subsample processed fine
    assertNotNull(subSampleResult.getResults().get(0).getRecord());
    assertEquals("TestSubSample1", subSampleResult.getResults().get(0).getRecord().getName());
    assertEquals("TestSubSample7", subSampleResult.getResults().get(6).getRecord().getName());

    // other have problems
    String errorMsg = subSampleResult.getResults().get(1).getError().getErrors().get(0);
    assertEquals(
        "id: Parent sample import id or global id must be set, but both were empty", errorMsg);
    errorMsg = subSampleResult.getResults().get(2).getError().getErrors().get(0);
    assertEquals("id: Parent sample with import id 's2' could not be found", errorMsg);
    errorMsg = subSampleResult.getResults().get(2).getError().getErrors().get(1);
    assertEquals("id: Parent container with import id 'c3' could not be found", errorMsg);
    errorMsg = subSampleResult.getResults().get(3).getError().getErrors().get(0);
    assertEquals(
        "quantity: Subsample quantity '1 mg' is incompatible with quantity unit used by parent"
            + " sample (MILLI_LITRE)",
        errorMsg);
    errorMsg = subSampleResult.getResults().get(3).getError().getErrors().get(1);
    assertEquals(
        "id: Parent container should be set via either 'Parent Container Import ID' "
            + "or 'Parent Container Global ID', but not both at the same time",
        errorMsg);
    errorMsg = subSampleResult.getResults().get(4).getError().getErrors().get(0);
    assertEquals(
        "id: Parent container with global id 'IC0' doesn't exist, "
            + "or user has no permission to move items into it",
        errorMsg);
    errorMsg = subSampleResult.getResults().get(5).getError().getErrors().get(0);
    assertTrue(
        errorMsg.contains(
            "is a grid container, but CSV import only supports import into list-type containers"),
        errorMsg);
    errorMsg = subSampleResult.getResults().get(7).getError().getErrors().get(0);
    assertEquals(
        "quantity: Subsample quantity '5 mg' is incompatible with quantity unit used by parent"
            + " sample (MILLI_LITRE)",
        errorMsg);
    errorMsg = subSampleResult.getResults().get(7).getError().getErrors().get(1);
    assertTrue(
        errorMsg.contains("doesn't exist, or user has no permission to move items into it"),
        errorMsg);
    errorMsg = subSampleResult.getResults().get(8).getError().getErrors().get(0);
    assertEquals(
        "id: Parent sample with global id 'SA0' doesn't exist, or user has no permission to add new"
            + " subsamples into it",
        errorMsg);
    errorMsg = subSampleResult.getResults().get(9).getError().getErrors().get(0);
    assertTrue(
        errorMsg.contains("doesn't exist, or user has no permission to add new subsamples into it"),
        errorMsg);
  }

  @Test
  public void testContainerCreationMethods() {

    String workbenchGlobalId = getWorkbenchForUser(testUser).getGlobalId();

    List<String[]> csvContainerLines = new ArrayList<>();
    csvContainerLines.add(new String[] {"testContent1", "testContainer1", "1", "", ""});
    csvContainerLines.add(new String[] {"testContent2", "testContainer2", "", "1", ""});
    csvContainerLines.add(new String[] {"testContent3", "testContainer3", "", "4", ""});
    csvContainerLines.add(new String[] {"testContent4", "testContainer4", "4", "1", ""});
    csvContainerLines.add(
        new String[] {"testContent4", "testContainer5", "", "", workbenchGlobalId});
    Map<Integer, String> colIndexToDefaultFieldMapping = new HashMap<>();
    colIndexToDefaultFieldMapping.put(1, "name");
    colIndexToDefaultFieldMapping.put(2, "import identifier");
    colIndexToDefaultFieldMapping.put(3, "parent container import id");
    colIndexToDefaultFieldMapping.put(4, "parent container global id");

    // test lines conversion
    ApiInventoryImportPartialResult processingResult = new ApiInventoryImportSampleImportResult();
    containerCsvImporter.convertLinesToContainers(
        processingResult,
        csvContainerLines,
        colIndexToDefaultFieldMapping,
        csvContainerLines.get(0).length);
    assertEquals(5, processingResult.getSuccessCount());
    assertEquals(
        ApiInventoryRecordType.CONTAINER,
        processingResult.getResults().get(0).getRecord().getType());
    assertEquals("testContainer1", processingResult.getResults().get(0).getRecord().getName());
    assertNull(processingResult.getResults().get(0).getRecord().getId()); // not set yet
    assertEquals("testContainer2", processingResult.getResults().get(1).getRecord().getName());
    assertEquals("testContainer3", processingResult.getResults().get(2).getRecord().getName());
    assertEquals("testContainer4", processingResult.getResults().get(3).getRecord().getName());
    assertEquals("testContainer5", processingResult.getResults().get(4).getRecord().getName());
    // 3 records marked for putting into parent container during import process
    assertEquals(3, processingResult.getResultNumberToParentContainerImportIdMap().size());
    assertEquals(1, processingResult.getResultNumberToParentContainerGlobalIdMap().size());

    // test import of converted lines
    ApiInventoryImportResult csvProcessingResult = new ApiInventoryImportResult(testUser);
    csvProcessingResult.setContainerResult(processingResult);
    ApiInventoryImportResult importResult = new ApiInventoryImportResult(testUser);
    importMgr.importContainers(importResult, csvProcessingResult);

    // default workbench container should be created
    assertNotNull(importResult.getDefaultContainer());
    String defaultImportContainerName = importResult.getDefaultContainer().getName();
    assertTrue(defaultImportContainerName.startsWith("imported items"), defaultImportContainerName);

    ApiInventoryImportPartialResult containerImportResult = importResult.getContainerResult();
    assertEquals(5, containerImportResult.getSuccessCount());
    // container1 created on workbench
    assertEquals("testContainer1", containerImportResult.getResults().get(0).getRecord().getName());
    assertNotNull(
        containerImportResult.getResults().get(0).getRecord().getId()); // should be set now
    assertEquals(
        defaultImportContainerName,
        containerImportResult.getResults().get(0).getRecord().getParentContainer().getName());
    // container2 created inside container1
    assertEquals("testContainer2", containerImportResult.getResults().get(1).getRecord().getName());
    assertEquals(
        "testContainer1",
        containerImportResult.getResults().get(1).getRecord().getParentContainer().getName());
    // container3 created inside container4
    assertEquals("testContainer3", containerImportResult.getResults().get(2).getRecord().getName());
    assertEquals(
        "testContainer4",
        containerImportResult.getResults().get(2).getRecord().getParentContainer().getName());
    // container4 created inside container1
    assertEquals("testContainer4", containerImportResult.getResults().get(3).getRecord().getName());
    assertEquals(
        "testContainer1",
        containerImportResult.getResults().get(3).getRecord().getParentContainer().getName());
    // container5 imporeted into workbench (by global id)
    assertEquals("testContainer5", containerImportResult.getResults().get(4).getRecord().getName());
    assertTrue(
        containerImportResult.getResults().get(4).getRecord().getParentContainer().isWorkbench());

    // 'import id' mappings populated in result object
    assertEquals(0, containerImportResult.getResultNumberForImportId("1"));
    assertEquals(
        "testContainer1", containerImportResult.getResultForImportId("1").getRecord().getName());
    assertNull(containerImportResult.getResultNumberForImportId("2"));
    assertNull(containerImportResult.getResultForImportId("2"));
  }

  @Test
  public void testContainerCsvImportWithErrorsPrevalidated() throws IOException {

    InputStream containersCsvIS =
        IOUtils.toInputStream(
            "Import Id, Name, Quantity, Parent, Parent Global Id\n"
                + "c1, TestContainer1, 200 ml,,\n"
                + "c1, TestContainer2,, c1,\n" // duplicated id
                + "c3, TestContainer3,, c2,\n" // parent  not identifiable
                + "c4, ,,,\n" // no name
                + "c5, TestContainer5,,\n" // too few columns
                + "c6, TestContainer6,,c1,IC1\n");
    HashMap<String, String> fieldMappings = new HashMap<>();
    fieldMappings.put("Name", "name");
    fieldMappings.put("Import Id", "import identifier");
    fieldMappings.put("Quantity", "description");
    fieldMappings.put("Parent", "parent container import id");
    fieldMappings.put("Parent Global Id", "parent container global id");

    ApiInventoryImportResult importResult = new ApiInventoryImportResult(testUser);
    containerCsvImporter.readCsvIntoImportResult(containersCsvIS, fieldMappings, importResult);
    ApiInventoryImportPartialResult partialResult = importResult.getContainerResult();
    importMgr.prevalidateContainers(importResult);
    assertNotNull(partialResult);
    assertEquals(ApiInventoryRecordType.CONTAINER, partialResult.getType());

    // result object should list all errors
    assertEquals(0, partialResult.getSuccessCount());
    assertEquals(5, partialResult.getErrorCount());
    assertEquals(1, partialResult.getSuccessCountBeforeFirstError());
    assertEquals(InventoryBulkOperationStatus.PREVALIDATION_ERROR, partialResult.getStatus());

    // check errors
    String errorMsg = partialResult.getResults().get(1).getError().getErrors().get(0);
    assertEquals("Import identifier 'c1' was already used in row 1", errorMsg);
    errorMsg = partialResult.getResults().get(2).getError().getErrors().get(0);
    assertEquals("id: Parent container with import id 'c2' could not be found", errorMsg);
    errorMsg = partialResult.getResults().get(3).getError().getErrors().get(0);
    assertEquals("name: name is a required field.", errorMsg);
    errorMsg = partialResult.getResults().get(4).getError().getErrors().get(0);
    assertEquals("Unexpected number of values in CSV line, expected: 5, was: 4", errorMsg);
    errorMsg = partialResult.getResults().get(5).getError().getErrors().get(0);
    assertEquals(
        "id: Parent container should be set via either 'Parent Container Import ID' "
            + "or 'Parent Container Global ID', but not both at the same time",
        errorMsg);
  }

  @Test
  public void checkImportSaveErrorIfInvalidParentContainerSomehowAllowedByPrevalidation()
      throws IOException {

    // verify error on saving to non-list container
    ApiContainer gridContainer = createBasicGridContainerForUser(testUser, 4, 4);
    assertNotNull(gridContainer.getId());

    InputStream containersIS =
        IOUtils.toInputStream(
            "Import Id, Name, Parent Global Id\n"
                + "c1, TestContainer1, "
                + gridContainer.getGlobalId());
    HashMap<String, String> fieldMappings = new HashMap<>();
    fieldMappings.put("Name", "name");
    fieldMappings.put("Import Id", "import identifier");
    fieldMappings.put("Parent Global Id", "parent container global id");
    ApiInventoryImportResult csvProcessingResult = new ApiInventoryImportResult();
    containerCsvImporter.readCsvIntoImportResult(containersIS, fieldMappings, csvProcessingResult);

    // attempt to import into grid container
    ApiInventoryImportResult importResult = new ApiInventoryImportResult(testUser);
    InventoryImportException iie =
        assertThrows(
            InventoryImportException.class,
            () -> importMgr.importContainers(importResult, csvProcessingResult));
    assertEquals("move.failure.no.target.location.for.grid.image.container", iie.getMessage());

    // verify error on saving to container belonging to unrelated user
    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("apiImport"));
    initialiseContentWithEmptyContent(otherUser);
    assertTrue(otherUser.isContentInitialized());
    ApiContainer otherUserWorkbench = getWorkbenchForUser(otherUser);
    assertNotNull(otherUserWorkbench.getId());

    containersIS =
        IOUtils.toInputStream(
            "Import Id, Name, Parent Global Id\n"
                + "c1, TestContainer1, "
                + otherUserWorkbench.getGlobalId());
    ApiInventoryImportResult csvProcessingResult2 = new ApiInventoryImportResult();
    containerCsvImporter.readCsvIntoImportResult(containersIS, fieldMappings, csvProcessingResult2);

    // attempt to import into other user's bench
    ApiInventoryImportResult importResult2 = new ApiInventoryImportResult(testUser);
    iie =
        assertThrows(
            InventoryImportException.class,
            () -> importMgr.importContainers(importResult2, csvProcessingResult2));
    assertEquals("move.failure.cannot.locate.target.container", iie.getMessage());
  }

  @Test
  public void checkImportSaveErrorIfInvalidParentSampleSomehowAllowedByPrevalidation()
      throws IOException {

    // verify error on saving subsample into sample belonging to unrelated user
    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("apiImport"));
    initialiseContentWithEmptyContent(otherUser);
    assertTrue(otherUser.isContentInitialized());
    ApiSampleWithFullSubSamples otherUserSample = createBasicSampleForUser(otherUser);
    assertNotNull(otherUserSample.getId());

    InputStream subSampleIS =
        IOUtils.toInputStream(
            "Name, Parent Sample Global Id\n" + "SubSampleA, " + otherUserSample.getGlobalId());

    HashMap<String, String> fieldMappings = new HashMap<>();
    fieldMappings.put("Name", "name");
    fieldMappings.put("Parent Sample Global Id", "parent sample global id");
    ApiInventoryImportResult csvProcessingResult = new ApiInventoryImportResult();
    subSampleCsvImporter.readCsvIntoImportResult(subSampleIS, fieldMappings, csvProcessingResult);

    // attempt to import into other user's bench
    ApiInventoryImportResult importResult = new ApiInventoryImportResult(testUser);
    importResult.setSubSampleResult(csvProcessingResult.getSubSampleResult());
    InventoryImportException iie =
        assertThrows(
            InventoryImportException.class,
            () ->
                importMgr.importSubSamplesIntoPreexistingSamples(
                    importResult, csvProcessingResult.getSubSampleResult()));
    assertTrue(
        iie.getMessage()
            .startsWith(
                "Inventory record with id ["
                    + otherUserSample.getId()
                    + "] could not be retrieved"),
        iie.getMessage());
  }
}
