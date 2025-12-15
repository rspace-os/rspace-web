package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.InventoryBulkOperationStatus;
import com.researchspace.api.v1.model.ApiInventoryImportPartialResult;
import com.researchspace.api.v1.model.ApiInventoryImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportSampleImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportSampleParseResult;
import com.researchspace.api.v1.model.ApiInventoryImportSubSampleImportResult;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.apiutils.ApiError;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.dao.DigitalObjectIdentifierDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleSource;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.csvimport.CsvSampleImporter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class InventoryImportApiControllerMVCIT extends API_MVC_InventoryTestBase {

  private static final String ANTIBODY_IMPORT_ALL_COLUMNS = "antibody_import_all_columns.csv";
  private static final String ANTIBODY_IMPORT_COMPLEX_TEMPLATE_CSV =
      "antibody_import_complex_template.csv";
  private static final String ANTIBODY_IMPORT_REAL_DATA_CSV = "antibody_import_real_data.csv";
  private static final String ANTIBODY_IMPORT_ERRORS_CSV = "antibody_import_with_errors.csv";

  private static final String CONTAINER_IMPORT_ALL_COLUMNS_CSV = "container_import_all_columns.csv";
  private static final String SAMPLE_IMPORT_INTO_CONTAINERS_CSV =
      "sample_import_into_containers.csv";
  private static final String SUBSAMPLE_IMPORT_INTO_CONTAINERS_CSV =
      "subsample_import_into_containers.csv";

  private User anyUser;
  private String apiKey;

  @Autowired private SamplesApiController samplesController;

  @Autowired private SampleApiManager sampleApiMgr;

  @Autowired private ContainerApiManager containerApiMgr;

  @Autowired private DigitalObjectIdentifierDao doiDao;
  @Autowired private CsvSampleImporter csvSampleImporter;
  @Mock private ApiAvailabilityHandler apiHandler;

  @Before
  public void setup() throws Exception {
    super.setUp();
    MockitoAnnotations.openMocks(this);
    anyUser = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(anyUser);
    csvSampleImporter.setApiHandler(apiHandler);

    when(apiHandler.isInventoryAndDataciteEnabled(anyUser)).thenReturn(true);
    when(apiHandler.isInventoryAvailable(anyUser)).thenReturn(true);
    when(apiHandler.isDataCiteConnectorEnabled()).thenReturn(true);
  }

  @Test
  public void parseAndImportSampleRealDataCsv() throws Exception {

    // let's parse antibodies example csv and get the suggested template
    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/parseFile"))
                    .file(getTestCsvFile("file", ANTIBODY_IMPORT_REAL_DATA_CSV))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("recordType", "SAMPLES")
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiInventoryImportSampleParseResult parseResult =
        getFromJsonResponseBody(result, ApiInventoryImportSampleParseResult.class);
    assertNotNull(parseResult);
    ApiSampleTemplatePost templateInfo = parseResult.getTemplateInfo();
    assertEquals("antibody_import_real_data", templateInfo.getName());
    assertTrue(templateInfo.isTemplate());
    assertEquals(17, templateInfo.getFields().size());
    assertEquals(ApiFieldType.STRING, templateInfo.getFields().get(0).getType());
    assertEquals("_Name", templateInfo.getFields().get(0).getName());
    assertEquals(ApiFieldType.RADIO, templateInfo.getFields().get(3).getType());
    assertEquals(
        List.of("mouse", "rabbit"), templateInfo.getFields().get(3).getDefinition().getOptions());
    assertEquals(17, parseResult.getFieldNameForColumnName().size());
    assertEquals("_Name", parseResult.getFieldNameForColumnName().get("Name"));
    assertEquals(17, parseResult.getRadioOptionsForColumn().size());
    assertEquals(
        List.of("1:100", "1:100, 1:250", "1:200; 1:500", "1:500"),
        parseResult.getRadioOptionsForColumn().get("Dilution"));
    assertTrue(parseResult.getQuantityUnitForColumn().isEmpty()); // no column matches quantity
    assertEquals(15, parseResult.getColumnsWithoutBlankValue().size());
    assertEquals(
        List.of(
            "Name",
            "Alternative name",
            "Clonality",
            "Raised in",
            "Antigen / Immunogen",
            "Reacts with",
            "Isotype",
            "Concentration",
            "Application",
            "Dilution",
            "WB band",
            "Company",
            "Catalog no.",
            "Lagerung",
            "Comment"),
        parseResult.getColumnsWithoutBlankValue());
    assertEquals(Integer.valueOf(4), parseResult.getRowsCount());

    /* correct the issue with LocalDateDeserialiser setting expiry date to
     * LocalDateDeserialiser.NULL_DATE when decoding response body */
    assertEquals("-999999999-01-01", templateInfo.getExpiryDate().toString());
    templateInfo.setExpiryDate(null);

    // remove first suggested template field that will be used for name
    templateInfo.getFields().remove(0);

    // let's import, with default parsed template, and with name column being 'Name'
    String settingsJson =
        "{ \"sampleSettings\": { \"fieldMappings\": { \"Name\": \"name\"}, \"templateInfo\": "
            + JacksonUtil.toJson(templateInfo)
            + "} }";
    result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(getTestCsvFile("samplesFile", ANTIBODY_IMPORT_REAL_DATA_CSV))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiInventoryImportResult importResult =
        getFromJsonResponseBody(result, ApiInventoryImportResult.class);
    assertNull(importResult.getContainerResult());
    assertEquals(InventoryBulkOperationStatus.COMPLETED, importResult.getStatus());
    ApiInventoryImportSampleImportResult sampleResults = importResult.getSampleResult();
    assertNotNull(sampleResults);
    assertTrue(sampleResults.isTemplateCreated());
    ApiInventoryRecordInfo createdTemplate = sampleResults.getTemplate().getRecord();
    assertEquals("antibody_import_real_data", createdTemplate.getName());
    assertEquals(4, sampleResults.getSuccessCount());
    assertEquals(InventoryBulkOperationStatus.COMPLETED, sampleResults.getStatus());

    // confirm 4 samples found when searching for new templatecreated for template
    ApiInventorySearchResult foundSamples =
        sampleApiMgr.getSamplesCreatedFromTemplate(
            createdTemplate.getId(), null, null, null, anyUser);
    assertEquals(4, foundSamples.getTotalHits().intValue());

    // now import file again, but reuse the template-existing template
    settingsJson =
        "{ \"sampleSettings\": { \"fieldMappings\": { \"Name\": \"name\"}, \"templateInfo\": {"
            + " \"id\": "
            + createdTemplate.getId()
            + " } } }";
    result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(getTestCsvFile("samplesFile", ANTIBODY_IMPORT_REAL_DATA_CSV))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());

    // confirm samples imported again, for the same template
    foundSamples =
        sampleApiMgr.getSamplesCreatedFromTemplate(
            createdTemplate.getId(), null, null, null, anyUser);
    assertEquals(8, foundSamples.getTotalHits().intValue());
  }

  @Test
  public void parseAndImportSampleAllColumnsCsv() throws Exception {
    // let's parse antibodies example csv and get the suggested template
    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/parseFile"))
                    .file(getTestCsvFile("file", ANTIBODY_IMPORT_ALL_COLUMNS))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("recordType", "SAMPLES")
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiInventoryImportSampleParseResult parseResult =
        getFromJsonResponseBody(result, ApiInventoryImportSampleParseResult.class);
    assertNotNull(parseResult);
    ApiSampleTemplatePost templateInfo = parseResult.getTemplateInfo();
    assertEquals("antibody_import_all_columns", templateInfo.getName());
    assertTrue(templateInfo.isTemplate());
    assertEquals(12, templateInfo.getFields().size());
    assertEquals(ApiFieldType.STRING, templateInfo.getFields().get(0).getType());
    assertEquals("_Name", templateInfo.getFields().get(0).getName());
    assertEquals(ApiFieldType.TEXT, templateInfo.getFields().get(1).getType());
    assertEquals(ApiFieldType.RADIO, templateInfo.getFields().get(2).getType());
    assertEquals(
        List.of("monoclonal", "polyclonal"),
        templateInfo.getFields().get(2).getDefinition().getOptions());
    assertEquals(ApiFieldType.NUMBER, templateInfo.getFields().get(3).getType());
    assertEquals(ApiFieldType.STRING, templateInfo.getFields().get(4).getType());
    assertEquals(ApiFieldType.DATE, templateInfo.getFields().get(5).getType());
    assertEquals(ApiFieldType.TIME, templateInfo.getFields().get(6).getType());
    assertEquals(ApiFieldType.DATE, templateInfo.getFields().get(7).getType());
    assertEquals(ApiFieldType.RADIO, templateInfo.getFields().get(8).getType());
    assertEquals(ApiFieldType.URI, templateInfo.getFields().get(9).getType());
    assertEquals(ApiFieldType.STRING, templateInfo.getFields().get(10).getType());
    assertEquals(ApiFieldType.URI, templateInfo.getFields().get(11).getType());
    assertEquals(11, parseResult.getRadioOptionsForColumn().size());
    assertEquals(
        List.of("14:00", "15:00"), parseResult.getRadioOptionsForColumn().get("Creation Time"));
    assertEquals(
        List.of("2030-01-04", "2030-12-22", "2031-12-23"),
        parseResult.getRadioOptionsForColumn().get("Best Before"));
    assertEquals(2, parseResult.getQuantityUnitForColumn().size());
    assertEquals(
        RSUnitDef.DIMENSIONLESS.getId(), parseResult.getQuantityUnitForColumn().get("Internal id"));
    assertEquals(
        RSUnitDef.MILLI_GRAM.getId(), parseResult.getQuantityUnitForColumn().get("Quantity"));
    assertEquals(Integer.valueOf(5), parseResult.getRowsCount());
    assertEquals("identifier", parseResult.getFieldMappings().get("Igsn"));

    // remove suggested template field that will be mapped to quantity
    templateInfo.getFields().remove(10);
    // remove suggested template field that will be mapped to source
    templateInfo.getFields().remove(8);
    // remove suggested template field that will be mapped to expiry date
    templateInfo.getFields().remove(7);
    // remove dummy data field
    templateInfo.getFields().remove(4);
    // remove suggested template field that will be mapped to name
    templateInfo.getFields().remove(0);
    // remove the igsn as a field since it is mapped
    templateInfo.getFields().remove(6);

    /* correct the issue with LocalDateDeserialiser setting expiry date to
     * LocalDateDeserialiser.NULL_DATE when decoding response body */
    assertEquals("-999999999-01-01", templateInfo.getExpiryDate().toString());
    templateInfo.setExpiryDate(null);

    /* chagne default template quantity unit to one suggested for 'Quantity' column */
    templateInfo.setDefaultUnitId(parseResult.getQuantityUnitForColumn().get("Quantity"));

    // create the DOI to assign
    DigitalObjectIdentifier doi1 = new DigitalObjectIdentifier("10.82316/hm02-fz20", null);
    DigitalObjectIdentifier doi2 = new DigitalObjectIdentifier("10.82316/hm02-fz21", null);
    DigitalObjectIdentifier doi3 = new DigitalObjectIdentifier("10.82316/pqv5-0v92", null);
    DigitalObjectIdentifier doi4 = new DigitalObjectIdentifier("10.82316/k8xy-6y85", null);
    doi1.setOwner(anyUser);
    doi2.setOwner(anyUser);
    doi3.setOwner(anyUser);
    doi4.setOwner(anyUser);
    doi1.setState("draft");
    doi2.setState("draft");
    doi3.setState("draft");
    doi4.setState("draft");
    openTransaction();
    doiDao.save(doi1);
    doiDao.save(doi2);
    doiDao.save(doi3);
    doiDao.save(doi4);
    commitTransaction();
    /*
     * let's import using default parsed template, with name column being 'Name',
     * with ignored 'Dummy Data' column and 'Best Before' put into 'Expiry date' column
     */
    String settingsJson =
        "{ \"sampleSettings\": { \"fieldMappings\": { \"Name\": \"name\", \"Dummy Data\": null,"
            + " \"Best Before\": \"expiry date\", \"Sample Source\": \"source\", \"Quantity\":"
            + " \"quantity\", \"Igsn\": \"identifier\"},  \"templateInfo\": "
            + JacksonUtil.toJson(templateInfo)
            + " } }";
    result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(getTestCsvFile("samplesFile", ANTIBODY_IMPORT_ALL_COLUMNS))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiInventoryImportResult importResult =
        getFromJsonResponseBody(result, ApiInventoryImportResult.class);
    assertNull(importResult.getContainerResult());
    assertEquals(InventoryBulkOperationStatus.COMPLETED, importResult.getStatus());
    ApiInventoryImportSampleImportResult sampleResults = importResult.getSampleResult();
    assertNotNull(sampleResults);
    assertTrue(sampleResults.isTemplateCreated());
    assertEquals(0, sampleResults.getErrorCount());
    assertEquals(5, sampleResults.getResults().size());
    ApiSampleWithFullSubSamples firstSample =
        (ApiSampleWithFullSubSamples) sampleResults.getResults().get(0).getRecord();
    assertEquals("Sample1", firstSample.getName());
    assertEquals("2030-12-22", firstSample.getExpiryDate().toString());
    assertEquals(SampleSource.VENDOR_SUPPLIED, firstSample.getSampleSource());
    assertEquals("5 mg", firstSample.getQuantity().toQuantityInfo().toPlainString());
    assertEquals(null, firstSample.getFields().get(1).getContent());
    assertEquals(List.of("monoclonal"), firstSample.getFields().get(1).getSelectedOptions());
    assertEquals("https://researchspace.com", firstSample.getFields().get(5).getContent());
    assertNotNull(firstSample.getIdentifiers());
    assertEquals(1, firstSample.getIdentifiers().size());
    assertEquals("10.82316/k8xy-6y85", firstSample.getIdentifiers().get(0).getDoi());
    assertEquals(InventoryBulkOperationStatus.COMPLETED, sampleResults.getStatus());
  }

  @Test
  public void importComplexTemplateCsvWithoutTemplateCreation() throws Exception {

    Sample complexTemplate = findComplexSampleTemplate(anyUser);

    /* let's import with name column being 'Name' */
    String settingsJson =
        "{ \"sampleSettings\": { \"fieldMappings\": { \"Name\": \"name\" }, "
            + " \"templateInfo\": { \"id\": "
            + complexTemplate.getId()
            + " } } }";
    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(getTestCsvFile("samplesFile", ANTIBODY_IMPORT_COMPLEX_TEMPLATE_CSV))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiInventoryImportResult importResult =
        getFromJsonResponseBody(result, ApiInventoryImportResult.class);
    assertNull(importResult.getContainerResult());
    ApiInventoryImportSampleImportResult sampleResults = importResult.getSampleResult();
    assertNotNull(sampleResults);
    assertNotNull(sampleResults.getTemplate().getRecord());
    assertEquals("Complex Sample Template", sampleResults.getTemplate().getRecord().getName());
    assertFalse(sampleResults.isTemplateCreated());
    assertEquals(InventoryBulkOperationStatus.COMPLETED, sampleResults.getStatus());

    assertEquals(9, sampleResults.getResults().size());
    ApiSampleWithFullSubSamples firstSample =
        (ApiSampleWithFullSubSamples) sampleResults.getResults().get(0).getRecord();
    assertEquals("TestSample1", firstSample.getName());
    assertEquals("3.14", firstSample.getFields().get(0).getContent());
    ApiSampleWithFullSubSamples ninthSample =
        (ApiSampleWithFullSubSamples) sampleResults.getResults().get(8).getRecord();
    assertEquals("TestSample9", ninthSample.getName());
    assertEquals(1, ninthSample.getFields().get(9).getSelectedOptions().size());
    assertEquals("optionB", ninthSample.getFields().get(9).getSelectedOptions().get(0));
  }

  @Test
  public void importSettingsValidation() throws Exception {
    // no importSettings
    String settingsJson = "{ \"sampleSettings\": { } }";
    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(getTestCsvFile("samplesFile", ANTIBODY_IMPORT_ALL_COLUMNS))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNotNull(result.getResolvedException());
    ApiError error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertEquals(2, error.getErrors().size());
    assertApiErrorContainsMessage(error, "'templateInfo' property must be provided");
    assertApiErrorContainsMessage(error, "'fieldMappings' property must be provided");

    // no fieldMappings
    settingsJson = "{ \"sampleSettings\": {  \"templateInfo\": { } } }";
    result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(getTestCsvFile("samplesFile", ANTIBODY_IMPORT_ALL_COLUMNS))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNotNull(result.getResolvedException());
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertEquals(1, error.getErrors().size());
    assertApiErrorContainsMessage(error, "'fieldMappings' property must be provided");

    // fieldMappings present but not pointing to "name" column
    settingsJson =
        "{ \"sampleSettings\": { \"fieldMappings\": { \"Alternative Name\": \"description\"},"
            + " \"templateInfo\": { } } }";
    result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(getTestCsvFile("samplesFile", ANTIBODY_IMPORT_ALL_COLUMNS))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNotNull(result.getResolvedException());
    error = getErrorFromJsonResponseBody(result, ApiError.class);
    assertEquals(1, error.getErrors().size());
    assertApiErrorContainsMessage(error, "'fieldMappings' property must be provided");
  }

  @Test
  public void importPreValidationFindsMultipleErrors() throws Exception {

    /* initial count of templates and samples */
    int initTemplatesCount = sampleApiMgr.getAllTemplates(anyUser).size();
    int initSamplesCount =
        sampleApiMgr.getSamplesForUser(null, null, null, anyUser).getTotalHits().intValue();

    /*
     * let's try import using basic template, using name from csv 'Name' column
     * and expiry date from 'Expiry date' column.
     */
    String settingsJson =
        "{ \"sampleSettings\": { \"fieldMappings\": { \"Name\": \"name\", \"Expiry Date\": \"expiry"
            + " date\" },  \"templateInfo\": { \"name\": \"Simple no-fields antibody\" } } }";
    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(getTestCsvFile("samplesFile", ANTIBODY_IMPORT_ERRORS_CSV))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiInventoryImportResult importResult =
        getFromJsonResponseBody(result, ApiInventoryImportResult.class);
    assertNull(importResult.getContainerResult());
    assertEquals(InventoryBulkOperationStatus.PREVALIDATION_ERROR, importResult.getStatus());
    ApiInventoryImportSampleImportResult samplesResult = importResult.getSampleResult();
    assertNotNull(samplesResult);
    assertEquals(InventoryBulkOperationStatus.PREVALIDATION_ERROR, samplesResult.getStatus());
    assertTrue(samplesResult.isTemplateCreated());
    assertEquals(4, samplesResult.getErrorCount());
    assertEquals(0, samplesResult.getSuccessCount());
    assertEquals(1, samplesResult.getSuccessCountBeforeFirstError());
    assertApiErrorContainsMessage(
        samplesResult.getResults().get(1).getError(),
        "Unexpected number of values in CSV line, expected: 2, was: 3");
    assertApiErrorContainsMessage(
        samplesResult.getResults().get(2).getError(),
        "Unexpected number of values in CSV line, expected: 2, was: 1");
    assertApiErrorContainsMessage(
        samplesResult.getResults().get(3).getError(), "name is a required field");
    assertApiErrorContainsMessage(
        samplesResult.getResults().get(4).getError(), "Text '-' could not be parsed");

    // assert no new templates/samples created
    int finalTemplatesCount = sampleApiMgr.getAllTemplates(anyUser).size();
    assertEquals(initTemplatesCount, finalTemplatesCount);
    int finalSamplesCount =
        sampleApiMgr.getSamplesForUser(null, null, null, anyUser).getTotalHits().intValue();
    assertEquals(initSamplesCount, finalSamplesCount);
  }

  @Test
  public void importFullyRolledBackOnError() throws Exception {

    /* initial count of containers, samples and templates */
    int initWorkbenchContainerCount =
        getWorkbenchForUser(anyUser).getContentSummary().getContainerCount();
    int initTemplatesCount = sampleApiMgr.getAllTemplates(anyUser).size();
    int initSamplesCount =
        sampleApiMgr.getSamplesForUser(null, null, null, anyUser).getTotalHits().intValue();

    // test with simplest no-fields template
    byte[] sampleCsvContentBytes = "Name\nTestSample1".getBytes();
    MockMultipartFile samplesMultipartFile =
        new MockMultipartFile("samplesFile", "testFile", "text/csv", sampleCsvContentBytes);
    byte[] containerCsvContentBytes = "Name\nTestContainer1".getBytes();
    MockMultipartFile containersMultipartFile =
        new MockMultipartFile("containersFile", "testFile", "text/csv", containerCsvContentBytes);
    String settingsJson =
        "{ \"sampleSettings\": { \"fieldMappings\": { \"Name\": \"name\" }, \"templateInfo\": {"
            + " \"name\": \"TestTemplate\" } }, \"containerSettings\": { \"fieldMappings\": {"
            + " \"Name\": \"name\" } } }";
    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(samplesMultipartFile)
                    .file(containersMultipartFile)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());
    ApiInventoryImportResult importResult =
        getFromJsonResponseBody(result, ApiInventoryImportResult.class);
    ApiInventoryImportPartialResult containerResults = importResult.getContainerResult();
    assertNotNull(importResult.getContainerResult());
    assertEquals(0, containerResults.getErrorCount());
    assertEquals(1, containerResults.getSuccessCount());
    ApiInventoryImportSampleImportResult sampleResults = importResult.getSampleResult();
    assertNotNull(sampleResults);
    assertEquals(InventoryBulkOperationStatus.COMPLETED, sampleResults.getStatus());
    assertTrue(sampleResults.isTemplateCreated());
    assertEquals(0, sampleResults.getErrorCount());
    assertEquals(1, sampleResults.getSuccessCount());

    // assert a new container, sample and template are created
    int newWorkbenchContainerCount =
        getWorkbenchForUser(anyUser).getContentSummary().getContainerCount();
    assertEquals(initWorkbenchContainerCount + 1, newWorkbenchContainerCount);
    int newTemplatesCount = sampleApiMgr.getAllTemplates(anyUser).size();
    assertEquals(initTemplatesCount + 1, newTemplatesCount);
    int newSamplesCount =
        sampleApiMgr.getSamplesForUser(null, null, null, anyUser).getTotalHits().intValue();
    assertEquals(initSamplesCount + 1, newSamplesCount);

    // now mock controller to throw an exception on save sample attempt
    SamplesApiController spiedController = Mockito.spy(samplesController);
    Mockito.doThrow(new IllegalArgumentException("mocked create sample exception"))
        .when(spiedController)
        .createNewSample(Mockito.any(), Mockito.any(), Mockito.any());
    importApiMgr.setSamplesController(spiedController);

    // try importing same csv content, but with samples controller throwing exception on sample
    // creation
    samplesMultipartFile =
        new MockMultipartFile("samplesFile", "testFile", "text/csv", sampleCsvContentBytes);
    containersMultipartFile =
        new MockMultipartFile("containersFile", "testFile", "text/csv", containerCsvContentBytes);
    result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(samplesMultipartFile)
                    .file(containersMultipartFile)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    importApiMgr.setSamplesController(samplesController);

    // check reported results
    assertNull(result.getResolvedException());
    importResult = getFromJsonResponseBody(result, ApiInventoryImportResult.class);
    assertEquals(InventoryBulkOperationStatus.REVERTED_ON_ERROR, importResult.getStatus());
    sampleResults = importResult.getSampleResult();
    assertEquals(InventoryBulkOperationStatus.REVERTED_ON_ERROR, sampleResults.getStatus());
    assertTrue(sampleResults.isTemplateCreated());
    assertEquals(1, sampleResults.getErrorCount());
    assertEquals(0, sampleResults.getSuccessCount());
    assertEquals(0, sampleResults.getSuccessCountBeforeFirstError());
    assertApiErrorContainsMessage(
        sampleResults.getResults().get(0).getError(), "mocked create sample exception");

    // assert container, sample and template not created
    int finalWorkbenchContainerCount =
        getWorkbenchForUser(anyUser).getContentSummary().getContainerCount();
    assertEquals(initWorkbenchContainerCount + 1, finalWorkbenchContainerCount);
    int finalTemplatesCount = sampleApiMgr.getAllTemplates(anyUser).size();
    assertEquals(initTemplatesCount + 1, finalTemplatesCount);
    int finalSamplesCount =
        sampleApiMgr.getSamplesForUser(null, null, null, anyUser).getTotalHits().intValue();
    assertEquals(initSamplesCount + 1, finalSamplesCount);
  }

  @Test
  public void parseAndImportContainerAllColumnsCsv() throws Exception {

    // let's parse containers example csv and check the results
    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/parseFile"))
                    .file(getTestCsvFile("file", CONTAINER_IMPORT_ALL_COLUMNS_CSV))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("recordType", "CONTAINERS")
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiInventoryImportSampleParseResult parseResult =
        getFromJsonResponseBody(result, ApiInventoryImportSampleParseResult.class);
    assertNotNull(parseResult);
    assertNull(parseResult.getTemplateInfo());
    assertEquals(
        List.of("Name", "Alternative name", "Import identifier", "Parent container"),
        parseResult.getColumnNames());
    assertEquals(List.of("Name", "Alternative name"), parseResult.getColumnsWithoutBlankValue());
    assertEquals(Integer.valueOf(5), parseResult.getRowsCount());

    /*
     * let's import mapping name/import identifier/parent container
     */
    String settingsJson =
        "{ \"containerSettings\": { \"fieldMappings\": { \"Name\": \"name\", \"Import identifier\":"
            + " \"Import identifier\", \"Parent container\": \"parent container import id\" } } }";
    result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(getTestCsvFile("containersFile", CONTAINER_IMPORT_ALL_COLUMNS_CSV))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiInventoryImportResult importResult =
        getFromJsonResponseBody(result, ApiInventoryImportResult.class);
    assertNotNull(importResult);

    ApiContainer defaultImportContainer = importResult.getDefaultContainer();
    assertNotNull(defaultImportContainer);
    String defaultImportContainerName = defaultImportContainer.getName();
    assertTrue(defaultImportContainerName.startsWith("imported items"), defaultImportContainerName);
    assertEquals(
        "Default container for items imported from CSV file(s): "
            + "<br> * container_import_all_columns.csv  ",
        defaultImportContainer.getDescription());
    assertEquals(2, defaultImportContainer.getContentSummary().getTotalCount());

    assertNull(importResult.getSampleResult());
    assertEquals(InventoryBulkOperationStatus.COMPLETED, importResult.getStatus());
    ApiInventoryBulkOperationResult containerResults = importResult.getContainerResult();
    assertNotNull(containerResults);
    assertEquals(0, containerResults.getErrorCount());
    assertEquals(5, containerResults.getResults().size());
    assertEquals(InventoryBulkOperationStatus.COMPLETED, containerResults.getStatus());
    // verify imported containers
    ApiContainer firstContainer = (ApiContainer) containerResults.getResults().get(0).getRecord();
    assertEquals("Container1", firstContainer.getName());
    assertEquals(defaultImportContainerName, firstContainer.getParentContainer().getName());
    assertEquals(1, firstContainer.getContentSummary().getTotalCount());
    ApiContainer secondContainer = (ApiContainer) containerResults.getResults().get(1).getRecord();
    assertEquals("Container2", secondContainer.getName());
    assertEquals("Container1", secondContainer.getParentContainer().getName());
    ApiContainer thirdContainer = (ApiContainer) containerResults.getResults().get(2).getRecord();
    assertEquals("Container3", thirdContainer.getName());
    assertEquals("Container5", thirdContainer.getParentContainer().getName());
  }

  @Test
  public void importSamplesIntoContainersCsvFiles() throws Exception {

    Sample sampleTemplate = findBasicSampleTemplate(anyUser);
    String samplesSettingsJson =
        "{ \"fieldMappings\": { \"Name\": \"name\", \"Description\": \"description\", \"Import"
            + " identifier\": \"import identifier\", \"Parent container\":\"parent container import"
            + " id\" }, \"templateInfo\": { \"id\": "
            + sampleTemplate.getId()
            + " } }";
    String containersSettingsJson =
        "{ \"fieldMappings\": { \"Name\": \"name\", \"Import identifier\": \"import identifier\","
            + " \"Parent container\": \"parent container import id\" } }";
    String settingsJson =
        " { \"sampleSettings\": "
            + samplesSettingsJson
            + ", \"containerSettings\": "
            + containersSettingsJson
            + " } ";

    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(getTestCsvFile("samplesFile", SAMPLE_IMPORT_INTO_CONTAINERS_CSV))
                    .file(getTestCsvFile("containersFile", CONTAINER_IMPORT_ALL_COLUMNS_CSV))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiInventoryImportResult importResult =
        getFromJsonResponseBody(result, ApiInventoryImportResult.class);
    assertNotNull(importResult);

    // default workbench container should be created
    ApiContainer defaultImportContainer = importResult.getDefaultContainer();
    assertNotNull(defaultImportContainer);
    String defaultImportContainerName = defaultImportContainer.getName();
    assertTrue(defaultImportContainerName.startsWith("imported items"), defaultImportContainerName);
    assertEquals(
        "Default container for items imported from CSV file(s): "
            + "<br> * container_import_all_columns.csv <br> * sample_import_into_containers.csv ",
        defaultImportContainer.getDescription());
    assertEquals(4, defaultImportContainer.getContentSummary().getTotalCount());

    ApiInventoryImportSampleImportResult sampleResults = importResult.getSampleResult();
    assertNotNull(sampleResults);
    assertEquals(InventoryBulkOperationStatus.COMPLETED, sampleResults.getStatus());
    assertEquals(4, sampleResults.getSuccessCount());
    assertFalse(sampleResults.isTemplateCreated());

    ApiSampleWithFullSubSamples createdSample1 =
        (ApiSampleWithFullSubSamples) sampleResults.getResults().get(0).getRecord();
    assertEquals(
        defaultImportContainerName,
        createdSample1.getSubSamples().get(0).getParentContainer().getName());
    ApiSampleWithFullSubSamples createdSample2 =
        (ApiSampleWithFullSubSamples) sampleResults.getResults().get(1).getRecord();
    assertEquals(
        "Container1", createdSample2.getSubSamples().get(0).getParentContainer().getName());
    ApiSampleWithFullSubSamples createdSample3 =
        (ApiSampleWithFullSubSamples) sampleResults.getResults().get(2).getRecord();
    assertEquals(
        "Container5", createdSample3.getSubSamples().get(0).getParentContainer().getName());
    ApiSampleWithFullSubSamples createdSample4 =
        (ApiSampleWithFullSubSamples) sampleResults.getResults().get(3).getRecord();
    assertEquals(
        defaultImportContainerName,
        createdSample4.getSubSamples().get(0).getParentContainer().getName());
  }

  @Test
  public void importSampleAndSubSampleCsvFiles() throws Exception {

    byte[] sampleCsvContentBytes = "Name, Import identifier\nTestSample1, s1".getBytes();
    MockMultipartFile samplesMultipartFile =
        new MockMultipartFile("samplesFile", "testFile", "text/csv", sampleCsvContentBytes);
    byte[] subSampleCsvContentBytes =
        "Name, Parent sample\nTestSubSample1, s1\nTestSubSample2, s1".getBytes();
    MockMultipartFile subSamplesMultipartFile =
        new MockMultipartFile("subSamplesFile", "testFile", "text/csv", subSampleCsvContentBytes);
    String settingsJson =
        "{ \"sampleSettings\": { \"fieldMappings\": { \"Name\": \"name\", \"Import identifier\":"
            + " \"import identifier\" },  \"templateInfo\": { \"name\": \"TestTemplate\" } },"
            + " \"subSampleSettings\": { \"fieldMappings\": { \"Name\": \"name\", \"Parent"
            + " sample\": \"parent sample import id\" } } }";
    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(samplesMultipartFile)
                    .file(subSamplesMultipartFile)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiInventoryImportResult importResult =
        getFromJsonResponseBody(result, ApiInventoryImportResult.class);
    ApiInventoryImportPartialResult containerResults = importResult.getContainerResult();
    assertNull(importResult.getContainerResult());
    ApiInventoryImportSampleImportResult sampleResults = importResult.getSampleResult();
    assertNotNull(sampleResults);
    assertEquals(InventoryBulkOperationStatus.COMPLETED, sampleResults.getStatus());
    assertTrue(sampleResults.isTemplateCreated());
    assertEquals(0, sampleResults.getErrorCount());
    assertEquals(1, sampleResults.getSuccessCount());
    ApiSampleWithFullSubSamples createdSample =
        (ApiSampleWithFullSubSamples) sampleResults.getResults().get(0).getRecord();
    assertNotNull(createdSample.getId());
    assertEquals(2, createdSample.getSubSamples().size());
    ApiInventoryImportSubSampleImportResult subSampleResults = importResult.getSubSampleResult();
    assertNotNull(subSampleResults);
    assertEquals(InventoryBulkOperationStatus.COMPLETED, subSampleResults.getStatus());
    assertEquals(0, subSampleResults.getErrorCount());
    assertEquals(2, subSampleResults.getSuccessCount());
    assertNotNull(subSampleResults.getResults().get(0).getRecord().getId());
  }

  @Test
  public void importSubSamplesCsvIntoPreexistingSamplesAndContainers() throws Exception {

    ApiContainer workbench = getWorkbenchForUser(anyUser);
    ApiSampleWithFullSubSamples preexistingSample = createBasicSampleForUser(anyUser, "mySample1");
    assertEquals(1, preexistingSample.getSubSamplesCount());

    byte[] subSampleCsvContentBytes =
        ("Name, Parent sample globalId, Parent container globalId"
                + "\nTestSubSample1, "
                + preexistingSample.getGlobalId()
                + ","
                + "\nTestSubSample2, "
                + preexistingSample.getGlobalId()
                + ","
                + workbench.getGlobalId())
            .getBytes();
    MockMultipartFile subSamplesMultipartFile =
        new MockMultipartFile("subSamplesFile", "testFile", "text/csv", subSampleCsvContentBytes);
    String settingsJson =
        "{ \"subSampleSettings\": { \"fieldMappings\": { \"Name\": \"name\", \"Parent sample"
            + " globalId\": \"parent sample global id\",\"Parent container globalId\": \"parent"
            + " container global id\" } } }";
    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(subSamplesMultipartFile)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiInventoryImportResult importResult =
        getFromJsonResponseBody(result, ApiInventoryImportResult.class);
    assertNull(importResult.getContainerResult());
    assertNull(importResult.getSampleResult());
    ApiInventoryImportSubSampleImportResult subSampleResults = importResult.getSubSampleResult();
    assertNotNull(subSampleResults);
    assertEquals(InventoryBulkOperationStatus.COMPLETED, subSampleResults.getStatus());
    assertEquals(0, subSampleResults.getErrorCount());
    assertEquals(2, subSampleResults.getSuccessCount());
    ApiSubSample createdSubSample1 =
        (ApiSubSample) subSampleResults.getResults().get(0).getRecord();
    assertNotNull(createdSubSample1.getId());
    assertEquals(preexistingSample.getGlobalId(), createdSubSample1.getSampleInfo().getGlobalId());
    assertTrue(
        createdSubSample1.getParentContainer().getName().startsWith("imported items"),
        createdSubSample1.getParentContainer().getName());
    ApiSubSample createdSubSample2 =
        (ApiSubSample) subSampleResults.getResults().get(1).getRecord();
    assertNotNull(createdSubSample2.getId());
    assertEquals(preexistingSample.getGlobalId(), createdSubSample2.getSampleInfo().getGlobalId());
    assertTrue(createdSubSample2.getParentContainer().isWorkbench());
  }

  @Test
  public void importMultipleCsvFiles() throws Exception {

    Sample basicTemplate = findBasicSampleTemplate(anyUser);
    String containersSettingsJson =
        "{ \"fieldMappings\": { \"Name\": \"name\", \"Import identifier\": \"import identifier\","
            + " \"Parent container\": \"parent container import id\" } }";
    String samplesSettingsJson =
        "{ \"fieldMappings\": { \"Name\": \"name\", \"Description\": \"description\", \"Import"
            + " identifier\": \"import identifier\",  \"Parent container\": \"parent container"
            + " import id\" }, \"templateInfo\": { \"id\": "
            + basicTemplate.getId()
            + " } }";
    String subSamplesSettingsJson =
        "{ \"fieldMappings\": { \"Name\": \"name\", \"Parent sample\": \"parent sample import id\","
            + "\"Parent container\": \"parent container import id\"  } }";

    String settingsJson =
        " { \"sampleSettings\": "
            + samplesSettingsJson
            + ", \"subSampleSettings\": "
            + subSamplesSettingsJson
            + ", \"containerSettings\": "
            + containersSettingsJson
            + " } ";
    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(getTestCsvFile("containersFile", CONTAINER_IMPORT_ALL_COLUMNS_CSV))
                    .file(getTestCsvFile("samplesFile", SAMPLE_IMPORT_INTO_CONTAINERS_CSV))
                    .file(getTestCsvFile("subSamplesFile", SUBSAMPLE_IMPORT_INTO_CONTAINERS_CSV))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiInventoryImportResult importResult =
        getFromJsonResponseBody(result, ApiInventoryImportResult.class);
    assertNotNull(importResult);
    assertEquals(InventoryBulkOperationStatus.COMPLETED, importResult.getStatus());

    ApiContainer defaultImportContainer = importResult.getDefaultContainer();
    assertNotNull(defaultImportContainer);
    String defaultImportContainerName = defaultImportContainer.getName();
    assertTrue(defaultImportContainerName.startsWith("imported items"), defaultImportContainerName);
    assertEquals(
        "Default container for items imported from CSV file(s): <br> *"
            + " container_import_all_columns.csv <br> * sample_import_into_containers.csv <br> *"
            + " subsample_import_into_containers.csv",
        defaultImportContainer.getDescription());
    assertEquals(3, defaultImportContainer.getContentSummary().getTotalCount());

    ApiInventoryImportSampleImportResult sampleResults = importResult.getSampleResult();
    assertNotNull(sampleResults);
    assertNotNull(sampleResults.getTemplate().getRecord());
    assertEquals("Sample", sampleResults.getTemplate().getRecord().getName());
    assertFalse(sampleResults.isTemplateCreated());
    assertEquals(InventoryBulkOperationStatus.COMPLETED, sampleResults.getStatus());
    assertEquals(4, sampleResults.getSuccessCount());

    ApiSampleWithFullSubSamples firstSample =
        (ApiSampleWithFullSubSamples) sampleResults.getResults().get(0).getRecord();
    assertEquals("Sample1", firstSample.getName());
    assertEquals(Integer.valueOf(3), firstSample.getSubSamplesCount());
    assertEquals("SubSample1", firstSample.getSubSamples().get(0).getName());
    assertEquals("Container1", firstSample.getSubSamples().get(0).getParentContainer().getName());
    assertEquals("SubSample2", firstSample.getSubSamples().get(1).getName());
    assertEquals("Container1", firstSample.getSubSamples().get(1).getParentContainer().getName());
    assertEquals("SubSample4", firstSample.getSubSamples().get(2).getName());
    assertEquals("Container5", firstSample.getSubSamples().get(2).getParentContainer().getName());

    ApiSampleWithFullSubSamples secondSample =
        (ApiSampleWithFullSubSamples) sampleResults.getResults().get(1).getRecord();
    assertEquals("Sample2", secondSample.getName());
    assertEquals(Integer.valueOf(2), secondSample.getSubSamplesCount());
    assertEquals("SubSample3", secondSample.getSubSamples().get(0).getName());
    assertEquals("Container5", secondSample.getSubSamples().get(0).getParentContainer().getName());
    assertEquals("SubSample5", secondSample.getSubSamples().get(1).getName());
    // 2nd subsample didn't specify container, so parent container specified for sample was applied
    assertEquals("Container1", secondSample.getSubSamples().get(1).getParentContainer().getName());

    ApiSampleWithFullSubSamples thirdSample =
        (ApiSampleWithFullSubSamples) sampleResults.getResults().get(2).getRecord();
    assertEquals("Sample3", thirdSample.getName());
    // only default subsample, moved into cont5 specified as parent for sample
    assertEquals(Integer.valueOf(1), thirdSample.getSubSamplesCount());
    assertEquals("Sample3.01", thirdSample.getSubSamples().get(0).getName());
    assertEquals("Container5", thirdSample.getSubSamples().get(0).getParentContainer().getName());

    ApiSampleWithFullSubSamples fourthSample =
        (ApiSampleWithFullSubSamples) sampleResults.getResults().get(3).getRecord();
    assertEquals("Sample4", fourthSample.getName());
    // only default subsample, no parent specified for sample nor subsample so stays on workbench
    assertEquals(Integer.valueOf(1), fourthSample.getSubSamplesCount());
    assertEquals("Sample4.01", fourthSample.getSubSamples().get(0).getName());
    assertEquals(
        defaultImportContainerName,
        fourthSample.getSubSamples().get(0).getParentContainer().getName());

    ApiInventoryBulkOperationResult containerResults = importResult.getContainerResult();
    assertNotNull(containerResults);
    assertEquals(0, containerResults.getErrorCount());
    assertEquals(5, containerResults.getResults().size());
    ApiContainer firstContainer = (ApiContainer) containerResults.getResults().get(0).getRecord();
    assertEquals("Container1", firstContainer.getName());
    assertEquals(4, firstContainer.getContentSummary().getTotalCount());
    assertEquals(InventoryBulkOperationStatus.COMPLETED, containerResults.getStatus());
  }

  @Test
  public void multipleCsvImportIntoExistingContainer() throws Exception {

    ApiContainer existingContainer =
        containerApiMgr.createNewApiContainer(
            new ApiContainer("testCont", ContainerType.LIST), anyUser);
    assertNotNull(existingContainer);
    assertNotNull(existingContainer.getGlobalId());
    assertEquals(0, existingContainer.getContentSummary().getTotalCount());
    assertTrue(existingContainer.getParentContainer().isWorkbench());

    Integer workbenchInitTotalCount =
        getWorkbenchForUser(anyUser).getContentSummary().getTotalCount();

    // two containers, one inside another, top one in existing workbench container
    byte[] containerCsvContentBytes =
        ("Name,Import Id,Parent Container Import Id, Parent Container Global Id"
                + "\nTestContainer1,c1,, "
                + existingContainer.getGlobalId()
                + "\nTestContainer2,c2,c1,\n")
            .getBytes();
    MockMultipartFile containersMultipartFile =
        new MockMultipartFile("containersFile", "testFile", "text/csv", containerCsvContentBytes);

    // two valid samples, one in pre-existing container
    byte[] sampleCsvContentBytes =
        ("Name,Import Id,Parent Container Import Id, Parent Container Global Id"
                + "\nTestSample1,s1,c2,"
                + "\nTestSample2,s2,,"
                + existingContainer.getGlobalId())
            .getBytes();
    MockMultipartFile samplesMultipartFile =
        new MockMultipartFile("samplesFile", "testFile", "text/csv", sampleCsvContentBytes);

    // two valid subsamples, in imported and pre-existing container
    byte[] subSampleCsvContentBytes =
        ("Name,Parent Sample Id,Parent Container Import Id, Parent Container Global Id"
                + "\nTestSubSample1,s1,c1,"
                + "\nTestSubSample2,s1,,"
                + existingContainer.getGlobalId())
            .getBytes();
    MockMultipartFile subSamplesMultipartFile =
        new MockMultipartFile("subSamplesFile", "testFile", "text/csv", subSampleCsvContentBytes);

    String settingsJson =
        "{ \"sampleSettings\": { \"fieldMappings\": { \"Name\": \"name\", \"Import Id\": \"import"
            + " identifier\",    \"Parent Container Import Id\": \"parent container import id\","
            + " \"Parent Container Global Id\": \"parent container global id\"},\"templateInfo\": {"
            + " \"name\": \"TestTemplate\" } }, \"containerSettings\": { \"fieldMappings\": {"
            + " \"Name\": \"name\", \"Import Id\": \"import identifier\",    \"Parent Container"
            + " Import Id\": \"parent container import id\", \"Parent Container Global Id\":"
            + " \"parent container global id\" } }, \"subSampleSettings\": { \"fieldMappings\": {"
            + " \"Name\": \"name\", \"Parent Sample Id\": \"parent sample import id\",     "
            + " \"Parent Container Import Id\": \"parent container import id\", \"Parent Container"
            + " Global Id\": \"parent container global id\"} } }";

    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(containersMultipartFile)
                    .file(samplesMultipartFile)
                    .file(subSamplesMultipartFile)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();

    ApiInventoryImportResult importResult =
        getFromJsonResponseBody(result, ApiInventoryImportResult.class);
    assertNotNull(importResult);
    assertEquals(InventoryBulkOperationStatus.COMPLETED, importResult.getStatus());

    // no default container - everything imported into pre-existing container
    assertNull(importResult.getDefaultContainer());

    // no new elements on workbench
    Integer workbenchFinalTotalCount =
        getWorkbenchForUser(anyUser).getContentSummary().getTotalCount();
    assertEquals(workbenchInitTotalCount, workbenchFinalTotalCount);

    // existing container should have imported content
    existingContainer = containerApiMgr.getApiContainerById(existingContainer.getId(), anyUser);
    assertEquals(3, existingContainer.getContentSummary().getTotalCount());
    assertEquals(1, existingContainer.getContentSummary().getContainerCount());
    assertEquals(2, existingContainer.getContentSummary().getSubSampleCount());
  }

  @Test
  public void multipleCsvFilesPrevalidation() throws Exception {

    String workbenchGlobalId = getWorkbenchForUser(anyUser).getGlobalId();
    assertNotNull(workbenchGlobalId);

    User otherUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithInitialisedContent(otherUser);
    String otherUserWorkbenchGlobalId = getWorkbenchForUser(otherUser).getGlobalId();

    // one valid container, one invalid (no name)
    byte[] containerCsvContentBytes = "Name,Import Id\nTestContainer1, c1\n,c2\n".getBytes();
    MockMultipartFile containersMultipartFile =
        new MockMultipartFile("containersFile", "testFile", "text/csv", containerCsvContentBytes);

    // two valid samples
    byte[] sampleCsvContentBytes = "Name,Import Id\nTestSample1,s1\nTestSample2,s2".getBytes();
    MockMultipartFile samplesMultipartFile =
        new MockMultipartFile("samplesFile", "testFile", "text/csv", sampleCsvContentBytes);

    // two valid subsamples, four invalid (no parent sample id / problems with parent container id)
    byte[] subSampleCsvContentBytes =
        ("Name,Parent Sample Id,Parent Container Import Id, Parent Container Global Id"
                + "\nTestSubSample1,s1,c1,"
                + "\nTestSubSample2,,,"
                + "\nTestSubSample3,s1,,"
                + workbenchGlobalId
                + "\nTestSubSample4,s1,,"
                + otherUserWorkbenchGlobalId
                + "\nTestSubSample5,s1,c0,"
                + "\nTestSubSample6,s1,,FL1"
                + "\nTestSubSample7,s1,,IC0")
            .getBytes();
    MockMultipartFile subSamplesMultipartFile =
        new MockMultipartFile("subSamplesFile", "testFile", "text/csv", subSampleCsvContentBytes);

    String settingsJson =
        "{ \"sampleSettings\": { \"fieldMappings\": { \"Name\": \"name\", \"Import Id\": \"import"
            + " identifier\"}, \"templateInfo\": { \"name\": \"TestTemplate\" } },"
            + " \"containerSettings\": { \"fieldMappings\": { \"Name\": \"name\", \"Import Id\":"
            + " \"import identifier\" } }, \"subSampleSettings\": { \"fieldMappings\": { \"Name\":"
            + " \"name\", \"Parent Sample Id\": \"parent sample import id\",      \"Parent"
            + " Container Import Id\": \"parent container import id\", \"Parent Container Global"
            + " Id\": \"parent container global id\"} } }";
    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(containersMultipartFile)
                    .file(samplesMultipartFile)
                    .file(subSamplesMultipartFile)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();

    // check reported results
    assertNull(result.getResolvedException());
    ApiInventoryImportResult importResult =
        getFromJsonResponseBody(result, ApiInventoryImportResult.class);
    assertNotNull(importResult);
    assertEquals(InventoryBulkOperationStatus.PREVALIDATION_ERROR, importResult.getStatus());

    // check individual import results
    ApiInventoryImportPartialResult containerResult = importResult.getContainerResult();
    assertNotNull(containerResult);
    assertEquals(InventoryBulkOperationStatus.PREVALIDATION_ERROR, containerResult.getStatus());
    assertEquals(1, containerResult.getErrorCount());
    assertEquals(0, containerResult.getSuccessCount());
    assertEquals(1, containerResult.getSuccessCountBeforeFirstError());
    assertApiErrorContainsMessage(
        containerResult.getResults().get(1).getError(), "name is a required field");

    ApiInventoryImportSampleImportResult sampleResult = importResult.getSampleResult();
    assertNotNull(sampleResult);
    assertEquals(InventoryBulkOperationStatus.PREVALIDATED, sampleResult.getStatus());
    assertTrue(sampleResult.isTemplateCreated());
    assertEquals(0, sampleResult.getErrorCount());
    assertEquals(2, sampleResult.getSuccessCount());

    ApiInventoryImportSubSampleImportResult subSampleResult = importResult.getSubSampleResult();
    assertNotNull(subSampleResult);
    assertEquals(InventoryBulkOperationStatus.PREVALIDATION_ERROR, subSampleResult.getStatus());
    assertEquals(5, subSampleResult.getErrorCount());
    assertEquals(0, subSampleResult.getSuccessCount());
    assertEquals(1, subSampleResult.getSuccessCountBeforeFirstError());
    assertEquals("TestSubSample1", subSampleResult.getResults().get(0).getRecord().getName());
    assertApiErrorContainsMessage(
        subSampleResult.getResults().get(1).getError(),
        "Parent sample import id or global id must be set, but both were empty");
    assertEquals("TestSubSample3", subSampleResult.getResults().get(2).getRecord().getName());
    assertApiErrorContainsMessage(
        subSampleResult.getResults().get(3).getError(),
        "doesn't exist, or user has no permission to move items into it");
    assertApiErrorContainsMessage(
        subSampleResult.getResults().get(4).getError(),
        "Parent container with import id 'c0' could not be found");
    assertApiErrorContainsMessage(
        subSampleResult.getResults().get(5).getError(),
        "'FL1' is not a valid global id of an inventory container");
    assertApiErrorContainsMessage(
        subSampleResult.getResults().get(6).getError(),
        "doesn't exist, or user has no permission to move items into it");
  }

  @Test
  public void checkCsvFileSizeLimits() throws Exception {

    byte[] containerCsvContentBytes = ("Name\n" + "TestSample\n".repeat(501)).getBytes();
    MockMultipartFile containersMultipartFile =
        new MockMultipartFile("containersFile", "testFile", "text/csv", containerCsvContentBytes);
    String settingsJson =
        "{ \"containerSettings\": { \"fieldMappings\": { \"Name\": \"name\" } } }";
    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(containersMultipartFile)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();

    Exception csvSizeException = result.getResolvedException();
    assertNotNull(csvSizeException);
    assertEquals(
        "CSV file is too long, import limit is set to 500 containers.",
        csvSizeException.getMessage());
  }

  private MockMultipartFile getTestCsvFile(String paramName, String fileName)
      throws IOException, FileNotFoundException {
    return new MockMultipartFile(
        paramName, fileName, "text/csv", getTestResourceFileStream("inventory/" + fileName));
  }
}
