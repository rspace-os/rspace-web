package com.researchspace.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.InventoryBulkOperationStatus;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryImportInstrumentImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportInstrumentParseResult;
import com.researchspace.api.v1.model.ApiInventoryImportPartialResult;
import com.researchspace.api.v1.model.ApiInventoryImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportSampleImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportSampleParseResult;
import com.researchspace.api.v1.model.ApiInventoryImportSubSampleImportResult;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryLinkTargetSummary;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.api.v1.model.ApiJob;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.apiutils.ApiError;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.dao.DigitalObjectIdentifierDao;
import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.SampleSource;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.InventoryImportManager;
import com.researchspace.service.inventory.InventoryLinkManager;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.csvexport.InventoryItemCsvExporter;
import com.researchspace.service.inventory.csvimport.CsvSampleImporter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

  private static final String LINK_FIELD_NAME = "Related items";
  private static final String SAMPLE_WITHOUT_LINK_COLUMN = "sample without link column";
  private static final String CSV_EXPORT_COMMENT_HEADER = "# RSpace Inventory Export";
  private static final String CSV_EXPORTED_CONTENT_SAMPLES = "# Exported content: SAMPLES";

  private static final String CONTAINER_IMPORT_ALL_COLUMNS_CSV = "container_import_all_columns.csv";
  private static final String SAMPLE_IMPORT_INTO_CONTAINERS_CSV =
      "sample_import_into_containers.csv";
  private static final String SUBSAMPLE_IMPORT_INTO_CONTAINERS_CSV =
      "subsample_import_into_containers.csv";

  private User anyUser;
  private String apiKey;

  @Autowired private SamplesApiController samplesController;

  @Autowired private DigitalObjectIdentifierDao doiDao;
  @Autowired private CsvSampleImporter csvSampleImporter;
  @Autowired private InventoryImportManager importApiMgr;
  @Autowired private InventoryLinkManager inventoryLinkManager;
  @Autowired private InventoryLinkDao inventoryLinkDao;
  @Autowired private IPropertyHolder propertyHolder;
  @Mock private ApiAvailabilityHandler apiHandler;

  @BeforeEach
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
    assertThat(templateInfo.getFields()).hasSize(17);
    assertEquals(ApiFieldType.STRING, templateInfo.getFields().get(0).getType());
    assertEquals("_Name", templateInfo.getFields().get(0).getName());
    assertEquals(ApiFieldType.RADIO, templateInfo.getFields().get(3).getType());
    assertThat(templateInfo.getFields().get(3).getDefinition().getOptions())
        .containsExactly("mouse", "rabbit");
    assertThat(parseResult.getFieldNameForColumnName()).hasSize(17);
    assertThat(parseResult.getFieldNameForColumnName()).containsEntry("Name", "_Name");
    assertThat(parseResult.getRadioOptionsForColumn()).hasSize(17);
    assertThat(parseResult.getRadioOptionsForColumn())
        .containsEntry("Dilution", List.of("1:100", "1:100, 1:250", "1:200; 1:500", "1:500"));
    assertThat(parseResult.getQuantityUnitForColumn()).isEmpty(); // no column matches quantity
    assertThat(parseResult.getColumnsWithoutBlankValue()).hasSize(15);
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
    assertThat(templateInfo.getExpiryDate()).hasToString("-999999999-01-01");
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
    assertThat(templateInfo.getFields()).hasSize(12);
    assertEquals(ApiFieldType.STRING, templateInfo.getFields().get(0).getType());
    assertEquals("_Name", templateInfo.getFields().get(0).getName());
    assertEquals(ApiFieldType.TEXT, templateInfo.getFields().get(1).getType());
    assertEquals(ApiFieldType.RADIO, templateInfo.getFields().get(2).getType());
    assertThat(templateInfo.getFields().get(2).getDefinition().getOptions())
        .containsExactly("monoclonal", "polyclonal");
    assertEquals(ApiFieldType.NUMBER, templateInfo.getFields().get(3).getType());
    assertEquals(ApiFieldType.STRING, templateInfo.getFields().get(4).getType());
    assertEquals(ApiFieldType.DATE, templateInfo.getFields().get(5).getType());
    assertEquals(ApiFieldType.TIME, templateInfo.getFields().get(6).getType());
    assertEquals(ApiFieldType.DATE, templateInfo.getFields().get(7).getType());
    assertEquals(ApiFieldType.RADIO, templateInfo.getFields().get(8).getType());
    assertEquals(ApiFieldType.URI, templateInfo.getFields().get(9).getType());
    assertEquals(ApiFieldType.STRING, templateInfo.getFields().get(10).getType());
    assertEquals(ApiFieldType.URI, templateInfo.getFields().get(11).getType());
    assertThat(parseResult.getRadioOptionsForColumn()).hasSize(11);
    assertThat(parseResult.getRadioOptionsForColumn())
        .containsEntry("Creation Time", List.of("14:00", "15:00"));
    assertThat(parseResult.getRadioOptionsForColumn())
        .containsEntry("Best Before", List.of("2030-01-04", "2030-12-22", "2031-12-23"));
    assertThat(parseResult.getQuantityUnitForColumn()).hasSize(2);
    assertThat(parseResult.getQuantityUnitForColumn())
        .containsEntry("Internal id", RSUnitDef.DIMENSIONLESS.getId());
    assertThat(parseResult.getQuantityUnitForColumn())
        .containsEntry("Quantity", RSUnitDef.MILLI_GRAM.getId());
    assertEquals(Integer.valueOf(5), parseResult.getRowsCount());
    assertThat(parseResult.getFieldMappings()).containsEntry("Igsn", "identifier");

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
    assertThat(templateInfo.getExpiryDate()).hasToString("-999999999-01-01");
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
    assertThat(sampleResults.getResults()).hasSize(5);
    ApiSampleWithFullSubSamples firstSample =
        (ApiSampleWithFullSubSamples) sampleResults.getResults().get(0).getRecord();
    assertEquals("Sample1", firstSample.getName());
    assertThat(firstSample.getExpiryDate()).hasToString("2030-12-22");
    assertEquals(SampleSource.VENDOR_SUPPLIED, firstSample.getSampleSource());
    assertEquals("5 mg", firstSample.getQuantity().toQuantityInfo().toPlainString());
    assertEquals(null, firstSample.getFields().get(1).getContent());
    assertThat(firstSample.getFields().get(1).getSelectedOptions()).containsExactly("monoclonal");
    assertEquals("https://researchspace.com", firstSample.getFields().get(5).getContent());
    assertNotNull(firstSample.getIdentifiers());
    assertThat(firstSample.getIdentifiers()).hasSize(1);
    assertEquals("10.82316/k8xy-6y85", firstSample.getIdentifiers().get(0).getDoi());
    assertEquals(InventoryBulkOperationStatus.COMPLETED, sampleResults.getStatus());
  }

  @Test
  public void importComplexTemplateCsvWithoutTemplateCreation() throws Exception {

    SampleTemplate complexTemplate = findComplexSampleTemplate(anyUser);

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

    assertThat(sampleResults.getResults()).hasSize(9);
    ApiSampleWithFullSubSamples firstSample =
        (ApiSampleWithFullSubSamples) sampleResults.getResults().get(0).getRecord();
    assertEquals("TestSample1", firstSample.getName());
    assertEquals("3.14", firstSample.getFields().get(0).getContent());
    ApiSampleWithFullSubSamples ninthSample =
        (ApiSampleWithFullSubSamples) sampleResults.getResults().get(8).getRecord();
    assertEquals("TestSample9", ninthSample.getName());
    assertThat(ninthSample.getFields().get(9).getSelectedOptions()).hasSize(1);
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
    assertThat(error.getErrors()).hasSize(2);
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
    assertThat(error.getErrors()).hasSize(1);
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
    assertThat(error.getErrors()).hasSize(1);
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
        "Unexpected CSV line field count: expected 2 values, but found 3 values.");
    assertApiErrorContainsMessage(
        samplesResult.getResults().get(2).getError(),
        "Unexpected CSV line field count: expected 2 values, but found 1 value.");
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
    assertThat(parseResult.getColumnNames())
        .containsExactly("Name", "Alternative name", "Import identifier", "Parent container");
    assertThat(parseResult.getColumnsWithoutBlankValue())
        .containsExactly("Name", "Alternative name");
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
    assertThat(defaultImportContainerName)
        .as(defaultImportContainerName)
        .startsWith("imported items");
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
    assertThat(containerResults.getResults()).hasSize(5);
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

    SampleTemplate sampleTemplate = findBasicSampleTemplate(anyUser);
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
    assertThat(defaultImportContainerName)
        .as(defaultImportContainerName)
        .startsWith("imported items");
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
    assertThat(createdSample.getSubSamples()).hasSize(2);
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
    assertThat(createdSubSample1.getParentContainer().getName())
        .as(createdSubSample1.getParentContainer().getName())
        .startsWith("imported items");
    ApiSubSample createdSubSample2 =
        (ApiSubSample) subSampleResults.getResults().get(1).getRecord();
    assertNotNull(createdSubSample2.getId());
    assertEquals(preexistingSample.getGlobalId(), createdSubSample2.getSampleInfo().getGlobalId());
    assertTrue(createdSubSample2.getParentContainer().isWorkbench());
  }

  @Test
  public void importMultipleCsvFiles() throws Exception {

    SampleTemplate basicTemplate = findBasicSampleTemplate(anyUser);
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
    assertThat(defaultImportContainerName)
        .as(defaultImportContainerName)
        .startsWith("imported items");
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
    assertThat(containerResults.getResults()).hasSize(5);
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

  /**
   * RSDEV-1354: a link column exported as "RelationType serverUrl/globalId/GID[vN]" is suggested as
   * a Link field on parse and re-imported as a link, even when the target does not exist on this
   * server (a dangling link, rendered by the UI as "No access", indistinguishable from a target
   * that exists but is unreadable - see ADR-0002).
   */
  @Test
  public void parseAndImportSampleCsvWithLinkColumn() throws Exception {
    String serverUrl = propertyHolder.getServerUrl();
    if (serverUrl.endsWith("/")) {
      serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
    }
    String csv =
        "Name,Related\n"
            + "linked sample,IsDerivedFrom "
            + serverUrl
            + "/globalId/SA999999v2\n"
            + "unlinked sample,\n";
    MockMultipartFile parseFile =
        new MockMultipartFile(
            "file", "links.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/parseFile"))
                    .file(parseFile)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("recordType", "SAMPLES")
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());
    ApiInventoryImportSampleParseResult parseResult =
        getFromJsonResponseBody(result, ApiInventoryImportSampleParseResult.class);
    ApiSampleTemplatePost templateInfo = parseResult.getTemplateInfo();
    assertEquals(ApiFieldType.LINK, templateInfo.getFields().get(1).getType());
    templateInfo.setExpiryDate(null);
    templateInfo.getFields().remove(0); // Name column maps to the sample name

    String settingsJson =
        "{ \"sampleSettings\": { \"fieldMappings\": { \"Name\": \"name\"}, \"templateInfo\": "
            + JacksonUtil.toJson(templateInfo)
            + "} }";
    result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(
                        new MockMultipartFile(
                            "samplesFile",
                            "links.csv",
                            "text/csv",
                            csv.getBytes(StandardCharsets.UTF_8)))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiInventoryImportResult importResult =
        getFromJsonResponseBody(result, ApiInventoryImportResult.class);
    ApiInventoryImportSampleImportResult sampleResults = importResult.getSampleResult();
    assertEquals(InventoryBulkOperationStatus.COMPLETED, sampleResults.getStatus());
    assertEquals(2, sampleResults.getSuccessCount());

    ApiSampleWithFullSubSamples linked =
        (ApiSampleWithFullSubSamples) sampleResults.getResults().get(0).getRecord();
    ApiInventoryEntityField linkField = linked.getFields().get(0);
    assertEquals(ApiFieldType.LINK, linkField.getType());
    ApiInventoryLink link = linkField.getLink();
    assertNotNull(link);
    assertEquals("IsDerivedFrom", link.getRelationType());
    assertEquals("SA999999", link.getTargetGlobalId());
    assertEquals(2L, link.getVersionPin());

    ApiSampleWithFullSubSamples unlinked =
        (ApiSampleWithFullSubSamples) sampleResults.getResults().get(1).getRecord();
    assertNull(unlinked.getFields().get(0).getLink());
  }

  /**
   * RSDEV-1354, the other half of {@link #parseAndImportSampleCsvWithLinkColumn}: that case pins
   * the dangling path, where the target does not exist and so no revision can resolve. This one
   * imports a link whose target is a real, readable record pinned at a real version, and checks the
   * two things only a live target can show: the summary resolves to the target's own name and type,
   * and the stored link captured an actual Envers revision rather than degrading to "latest".
   */
  @Test
  public void parseAndImportSampleCsvWithLinkToExistingVersionedTarget() throws Exception {
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(anyUser);
    String targetGlobalId = "SA" + target.getId();
    String serverUrl = propertyHolder.getServerUrl();
    if (serverUrl.endsWith("/")) {
      serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
    }
    // a newly created sample is at version 1, so v1 is a version that really exists
    String csv =
        "Name,Related\n"
            + "linked sample,IsDerivedFrom "
            + serverUrl
            + "/globalId/"
            + targetGlobalId
            + "v1\n";

    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/parseFile"))
                    .file(
                        new MockMultipartFile(
                            "file", "links.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("recordType", "SAMPLES")
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());
    ApiInventoryImportSampleParseResult parseResult =
        getFromJsonResponseBody(result, ApiInventoryImportSampleParseResult.class);
    ApiSampleTemplatePost templateInfo = parseResult.getTemplateInfo();
    assertEquals(ApiFieldType.LINK, templateInfo.getFields().get(1).getType());
    templateInfo.setExpiryDate(null);
    templateInfo.getFields().remove(0); // Name column maps to the sample name

    String settingsJson =
        "{ \"sampleSettings\": { \"fieldMappings\": { \"Name\": \"name\"}, \"templateInfo\": "
            + JacksonUtil.toJson(templateInfo)
            + "} }";
    result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(
                        new MockMultipartFile(
                            "samplesFile",
                            "links.csv",
                            "text/csv",
                            csv.getBytes(StandardCharsets.UTF_8)))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiInventoryImportResult importResult =
        getFromJsonResponseBody(result, ApiInventoryImportResult.class);
    ApiInventoryImportSampleImportResult sampleResults = importResult.getSampleResult();
    assertEquals(InventoryBulkOperationStatus.COMPLETED, sampleResults.getStatus());
    assertEquals(1, sampleResults.getSuccessCount());

    ApiSampleWithFullSubSamples linked =
        (ApiSampleWithFullSubSamples) sampleResults.getResults().get(0).getRecord();
    ApiInventoryLink link = linked.getFields().get(0).getLink();
    assertNotNull(link);
    assertEquals(targetGlobalId, link.getTargetGlobalId());
    assertEquals(1L, link.getVersionPin());

    // the target is live and readable, so the card shows its name and type rather than the
    // redacted "No access" summary an unresolvable target produces
    ApiInventoryLinkTargetSummary summary =
        inventoryLinkManager.getTargetSummary(targetGlobalId, anyUser);
    assertEquals(target.getName(), summary.getName());
    assertEquals("SAMPLE", summary.getType());
    assertTrue(summary.isReadable());
    assertFalse(summary.isDeleted());

    // the pin resolved to a real audit revision. A dangling import stores null here, meaning
    // "resolve latest at read time", so a null would mean the pin never bound to anything.
    Long storedRevision =
        doInTransaction(
            () -> {
              List<InventoryLinkField> referencing =
                  inventoryLinkDao.findReferencingStructuredLinkFields(
                      GlobalIdPrefix.SA, target.getId());
              assertEquals(1, referencing.size());
              return referencing.get(0).getLink().getTargetRevisionId();
            });
    assertNotNull(storedRevision, "a pin to a version that exists must capture its revision");
  }

  /**
   * RSDEV-1354: a CSV link whose target exists but is not readable by the importer imports rather
   * than failing its row. Distinguishing "gone" from "not yours" is the disclosure ADR-0002
   * prevents, so import cannot treat the two differently; the summary redacts the target for the
   * importer and the owner's referencing-items query still finds the link.
   */
  @Test
  public void parseAndImportSampleCsvWithLinkToUnreadableTarget() throws Exception {
    User targetOwner = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(targetOwner, "unreadable target");
    String targetGlobalId = "SA" + target.getId();
    logoutAndLoginAs(anyUser);

    String serverUrl = propertyHolder.getServerUrl();
    if (serverUrl.endsWith("/")) {
      serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
    }
    String csv =
        "Name,Related\n"
            + "linked sample,IsDerivedFrom "
            + serverUrl
            + "/globalId/"
            + targetGlobalId
            + "\n";

    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/parseFile"))
                    .file(
                        new MockMultipartFile(
                            "file", "links.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("recordType", "SAMPLES")
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());
    ApiInventoryImportSampleParseResult parseResult =
        getFromJsonResponseBody(result, ApiInventoryImportSampleParseResult.class);
    ApiSampleTemplatePost templateInfo = parseResult.getTemplateInfo();
    assertEquals(ApiFieldType.LINK, templateInfo.getFields().get(1).getType());
    templateInfo.setExpiryDate(null);
    templateInfo.getFields().remove(0); // Name column maps to the sample name

    String settingsJson =
        "{ \"sampleSettings\": { \"fieldMappings\": { \"Name\": \"name\"}, \"templateInfo\": "
            + JacksonUtil.toJson(templateInfo)
            + "} }";
    result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(
                        new MockMultipartFile(
                            "samplesFile",
                            "links.csv",
                            "text/csv",
                            csv.getBytes(StandardCharsets.UTF_8)))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiInventoryImportResult importResult =
        getFromJsonResponseBody(result, ApiInventoryImportResult.class);
    ApiInventoryImportSampleImportResult sampleResults = importResult.getSampleResult();
    assertEquals(InventoryBulkOperationStatus.COMPLETED, sampleResults.getStatus());
    assertEquals(1, sampleResults.getSuccessCount());

    ApiSampleWithFullSubSamples linked =
        (ApiSampleWithFullSubSamples) sampleResults.getResults().get(0).getRecord();
    ApiInventoryLink link = linked.getFields().get(0).getLink();
    assertNotNull(link, "a link to an unreadable target must still be stored");
    assertEquals(targetGlobalId, link.getTargetGlobalId());

    // the importer's summary of that target is the redacted one: "No access", not "Target deleted"
    ApiInventoryLinkTargetSummary summary =
        inventoryLinkManager.getTargetSummary(targetGlobalId, anyUser);
    assertEquals(targetGlobalId, summary.getGlobalId());
    assertFalse(summary.isReadable());
    assertFalse(summary.isDeleted(), "an unreadable target must not be reported as deleted");
    assertNull(summary.getName());
    assertNull(summary.getType());

    // the owner's referencing-items view still resolves the inbound link without error
    doInTransaction(
        () -> {
          List<InventoryLinkField> referencing =
              inventoryLinkDao.findReferencingStructuredLinkFields(
                  GlobalIdPrefix.SA, target.getId());
          assertEquals(1, referencing.size());
          return null;
        });
  }

  /**
   * RSDEV-1354: instruments persist through a different path from samples (instrument template
   * creation, then {@code InstrumentEntityApiManagerImpl}), so a link that survives the sample
   * import says nothing about this one. Guards against a link being populated on the parsed DTO and
   * then silently dropped on the way to the database for instruments only.
   */
  @Test
  public void parseAndImportInstrumentCsvWithLinkColumn() throws Exception {
    String serverUrl = propertyHolder.getServerUrl();
    if (serverUrl.endsWith("/")) {
      serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
    }
    String csv =
        "Name,Related\n"
            + "linked instrument,IsDerivedFrom "
            + serverUrl
            + "/globalId/SA999999v2\n"
            + "unlinked instrument,\n";

    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/parseFile"))
                    .file(
                        new MockMultipartFile(
                            "file",
                            "instrumentLinks.csv",
                            "text/csv",
                            csv.getBytes(StandardCharsets.UTF_8)))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("recordType", "INSTRUMENTS")
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());
    ApiInventoryImportInstrumentParseResult parseResult =
        getFromJsonResponseBody(result, ApiInventoryImportInstrumentParseResult.class);
    ApiInstrumentTemplatePost templateInfo = parseResult.getTemplateInfo();
    assertEquals(ApiFieldType.LINK, templateInfo.getFields().get(1).getType());
    templateInfo.getFields().remove(0); // Name column maps to the instrument name

    String settingsJson =
        "{ \"instrumentSettings\": { \"fieldMappings\": { \"Name\": \"name\"},"
            + " \"templateInfo\": "
            + JacksonUtil.toJson(templateInfo)
            + "} }";
    result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(
                        new MockMultipartFile(
                            "instrumentsFile",
                            "instrumentLinks.csv",
                            "text/csv",
                            csv.getBytes(StandardCharsets.UTF_8)))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiInventoryImportResult importResult =
        getFromJsonResponseBody(result, ApiInventoryImportResult.class);
    ApiInventoryImportInstrumentImportResult instrumentResults = importResult.getInstrumentResult();
    assertEquals(InventoryBulkOperationStatus.COMPLETED, instrumentResults.getStatus());
    assertEquals(2, instrumentResults.getSuccessCount());

    ApiInstrument linked = (ApiInstrument) instrumentResults.getResults().get(0).getRecord();
    ApiInventoryEntityField linkField = linked.getFields().get(0);
    assertEquals(ApiFieldType.LINK, linkField.getType());
    ApiInventoryLink link = linkField.getLink();
    assertNotNull(link);
    assertEquals("IsDerivedFrom", link.getRelationType());
    assertEquals("SA999999", link.getTargetGlobalId());
    assertEquals(2L, link.getVersionPin());

    ApiInstrument unlinked = (ApiInstrument) instrumentResults.getResults().get(1).getRecord();
    assertNull(unlinked.getFields().get(0).getLink());
  }

  /**
   * RSDEV-1354: a multi-record CSV export gives every row the union of all exported records'
   * columns, filling the columns a row does not have with the exporter's {@code #N/A} sentinel, so
   * a per-template link column carries that sentinel on every row belonging to another template.
   * Re-importing the exporter's own output must still suggest the column as a Link field and must
   * leave the sentinel rows linkless instead of failing them. Before the fix the column was
   * inferred as a String because the sentinel is not a parseable link, and forcing a Link template
   * made every sentinel row fail with a link parse error.
   */
  @Test
  public void exportedMultiRowSampleCsvWithSentinelLinkColumnReimportsAsLinks() throws Exception {
    ApiSampleWithFullSubSamples linkTarget = createBasicSampleForUser(anyUser, "link target");
    ApiSampleWithFullSubSamples linkedSample = createSampleWithLinkTo(linkTarget);
    ApiSampleWithFullSubSamples otherTemplateSample =
        createBasicSampleForUser(anyUser, SAMPLE_WITHOUT_LINK_COLUMN);

    String samplesCsv = exportSamplesSectionAsCsv(linkedSample, otherTemplateSample);
    List<String[]> exportedRows = parseCsvRows(samplesCsv);
    String[] header = exportedRows.get(0);
    int linkColumnIndex = indexOfColumnStartingWith(header, LINK_FIELD_NAME);
    int nameColumnIndex = indexOfColumnStartingWith(header, "Name");
    String[] rowWithoutLinkColumn =
        exportedRows.stream()
            .filter(row -> SAMPLE_WITHOUT_LINK_COLUMN.equals(row[nameColumnIndex]))
            .findFirst()
            .orElseThrow();
    assertEquals(
        InventoryItemCsvExporter.CSV_VALUE_UNAVAILABLE_ITEM_PROPERTY,
        rowWithoutLinkColumn[linkColumnIndex],
        "the export should fill the other template's link column with its sentinel");

    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/parseFile"))
                    .file(
                        new MockMultipartFile(
                            "file",
                            "exportedSamples.csv",
                            "text/csv",
                            samplesCsv.getBytes(StandardCharsets.UTF_8)))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("recordType", "SAMPLES")
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());
    ApiInventoryImportSampleParseResult parseResult =
        getFromJsonResponseBody(result, ApiInventoryImportSampleParseResult.class);
    ApiSampleTemplatePost templateInfo = parseResult.getTemplateInfo();
    assertEquals(
        ApiFieldType.LINK,
        templateInfo.getFields().get(linkColumnIndex).getType(),
        "a link column with sentinel rows should still be suggested as a Link field");

    templateInfo.setExpiryDate(null);
    templateInfo.getFields().remove(nameColumnIndex);
    String settingsJson =
        "{ \"sampleSettings\": { \"fieldMappings\": { \"Name\": \"name\"}, \"templateInfo\": "
            + JacksonUtil.toJson(templateInfo)
            + "} }";
    result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(
                        new MockMultipartFile(
                            "samplesFile",
                            "exportedSamples.csv",
                            "text/csv",
                            samplesCsv.getBytes(StandardCharsets.UTF_8)))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());

    ApiInventoryImportResult importResult =
        getFromJsonResponseBody(result, ApiInventoryImportResult.class);
    ApiInventoryImportSampleImportResult sampleResults = importResult.getSampleResult();
    assertEquals(InventoryBulkOperationStatus.COMPLETED, sampleResults.getStatus());
    assertEquals(2, sampleResults.getSuccessCount());

    ApiInventoryEntityField reimportedLink =
        linkFieldOfImportedSampleNamed(sampleResults, linkedSample.getName());
    assertEquals(ApiFieldType.LINK, reimportedLink.getType());
    assertNotNull(reimportedLink.getLink(), "the exported link should survive the round trip");
    assertEquals("References", reimportedLink.getLink().getRelationType());
    assertEquals(linkTarget.getGlobalId(), reimportedLink.getLink().getTargetGlobalId());

    ApiInventoryEntityField sentinelLink =
        linkFieldOfImportedSampleNamed(sampleResults, SAMPLE_WITHOUT_LINK_COLUMN);
    assertEquals(ApiFieldType.LINK, sentinelLink.getType());
    assertNull(sentinelLink.getLink(), "a sentinel cell means no link, not a malformed one");
  }

  /**
   * RSDEV-1354: CSV import deliberately does not check that a link target exists, so a crafted file
   * can name the Global ID the sample it is creating is itself about to be given. The write-path
   * self-link rejection cannot see that, because link fields are applied before the row exists and
   * so before the record has an id; {@code assertNoSelfLinkAfterSave} is the backstop, and this is
   * it firing through the real import endpoint.
   *
   * <p>The id an import will hand out cannot be read off anything beforehand, so it is calibrated
   * rather than guessed: two identical throwaway imports show how many Sample ids one import of
   * this shape consumes (its generated template plus its sample), and the third import is given
   * that step applied once more as its link target. A wrong prediction makes the import succeed,
   * which fails this test rather than passing it silently.
   */
  @Test
  public void sampleCsvLinkingToItsOwnFutureGlobalIdIsRejected() throws Exception {
    String serverUrl = propertyHolder.getServerUrl();
    if (serverUrl.endsWith("/")) {
      serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
    }
    String probeCsv =
        "Name,Related\nprobe sample,IsDerivedFrom " + serverUrl + "/globalId/SA999999\n";
    String settingsJson = buildLinkColumnSampleImportSettings(probeCsv);

    ApiInventoryImportSampleImportResult firstProbe = importSamplesCsv(probeCsv, settingsJson);
    assertEquals(InventoryBulkOperationStatus.COMPLETED, firstProbe.getStatus());
    ApiInventoryImportSampleImportResult secondProbe = importSamplesCsv(probeCsv, settingsJson);
    assertEquals(InventoryBulkOperationStatus.COMPLETED, secondProbe.getStatus());
    long firstProbeId = firstProbe.getResults().get(0).getRecord().getId();
    long secondProbeId = secondProbe.getResults().get(0).getRecord().getId();
    long idsPerImport = secondProbeId - firstProbeId;
    long predictedSelfId = secondProbeId + idsPerImport;

    int samplesBefore =
        sampleApiMgr.getSamplesForUser(null, null, null, anyUser).getTotalHits().intValue();
    String selfLinkCsv =
        "Name,Related\nself linking sample,IsDerivedFrom "
            + serverUrl
            + "/globalId/SA"
            + predictedSelfId
            + "\n";

    ApiInventoryImportSampleImportResult selfLinkResult =
        importSamplesCsv(selfLinkCsv, settingsJson);

    assertEquals(InventoryBulkOperationStatus.REVERTED_ON_ERROR, selfLinkResult.getStatus());
    assertEquals(0, selfLinkResult.getSuccessCount());
    assertEquals(1, selfLinkResult.getErrorCount());
    // the API reports the raw message key; the frontend i18n catalogue renders it
    assertApiErrorContainsMessage(
        selfLinkResult.getResults().get(0).getError(),
        "errors.inventory.field.link.selfLinkForbidden");
    assertEquals(
        samplesBefore,
        sampleApiMgr.getSamplesForUser(null, null, null, anyUser).getTotalHits().intValue(),
        "no self-linked sample should survive the import");
  }

  /** Parses the CSV and turns the suggested template into an importFiles settings payload. */
  private String buildLinkColumnSampleImportSettings(String csv) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/parseFile"))
                    .file(
                        new MockMultipartFile(
                            "file", "links.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("recordType", "SAMPLES")
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());
    ApiSampleTemplatePost templateInfo =
        getFromJsonResponseBody(result, ApiInventoryImportSampleParseResult.class)
            .getTemplateInfo();
    assertEquals(ApiFieldType.LINK, templateInfo.getFields().get(1).getType());
    templateInfo.setExpiryDate(null);
    templateInfo.getFields().remove(0); // Name column maps to the sample name
    return "{ \"sampleSettings\": { \"fieldMappings\": { \"Name\": \"name\"}, \"templateInfo\": "
        + JacksonUtil.toJson(templateInfo)
        + "} }";
  }

  private ApiInventoryImportSampleImportResult importSamplesCsv(String csv, String settingsJson)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/import/importFiles"))
                    .file(
                        new MockMultipartFile(
                            "samplesFile",
                            "links.csv",
                            "text/csv",
                            csv.getBytes(StandardCharsets.UTF_8)))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .param("importSettings", settingsJson)
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());
    return getFromJsonResponseBody(result, ApiInventoryImportResult.class).getSampleResult();
  }

  private ApiSampleWithFullSubSamples createSampleWithLinkTo(ApiSampleWithFullSubSamples target)
      throws Exception {
    ApiSampleTemplatePost templatePost = new ApiSampleTemplatePost();
    templatePost.setName("template with link field");
    templatePost.setDefaultUnitId(RSUnitDef.GRAM.getId());
    templatePost.setSampleSource(SampleSource.LAB_CREATED);
    ApiInventoryEntityField linkField = new ApiInventoryEntityField();
    linkField.setName(LINK_FIELD_NAME);
    linkField.setType(ApiFieldType.LINK);
    linkField.setAllowedRelationTypes(List.of("References"));
    templatePost.setFields(List.of(linkField));
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPostWithJSONBody(apiKey, "/sampleTemplates", anyUser, templatePost))
            .andReturn();
    assertNull(result.getResolvedException());
    ApiSampleTemplate template = getFromJsonResponseBody(result, ApiSampleTemplate.class);

    ApiSampleWithFullSubSamples toCreate = new ApiSampleWithFullSubSamples("sample with link");
    toCreate.setTemplateId(template.getId());
    ApiSampleWithFullSubSamples created = sampleApiMgr.createNewApiSample(toCreate, anyUser);

    ApiInventoryLink link = new ApiInventoryLink();
    link.setRelationType("References");
    link.setTargetGlobalId(target.getGlobalId());
    ApiInventoryEntityField fieldUpdate = new ApiInventoryEntityField();
    fieldUpdate.setId(
        sampleApiMgr.getApiSampleById(created.getId(), anyUser).getFields().get(0).getId());
    fieldUpdate.setType(ApiFieldType.LINK);
    fieldUpdate.setLink(link);
    ApiSampleWithFullSubSamples sampleUpdate = new ApiSampleWithFullSubSamples(created.getName());
    sampleUpdate.setId(created.getId());
    sampleUpdate.setFields(List.of(fieldUpdate));
    ApiSample updated = sampleApiMgr.updateApiSample(sampleUpdate, anyUser);
    assertNotNull(updated.getFields().get(0).getLink(), "the sample should start out linked");
    return created;
  }

  private String exportSamplesSectionAsCsv(ApiSampleWithFullSubSamples... samples)
      throws Exception {
    String globalIds =
        Arrays.stream(samples)
            .map(s -> "\"" + s.getGlobalId() + "\"")
            .collect(Collectors.joining(", "));
    MvcResult result =
        mockMvc
            .perform(
                multipart(createUrl(API_VERSION.ONE, "/export"))
                    .param(
                        "exportSettings",
                        "{ \"globalIds\": ["
                            + globalIds
                            + "], \"resultFileType\": \"SINGLE_CSV\" }")
                    .header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());
    ApiJob job = getFromJsonResponseBody(result, ApiJob.class);
    String downloadLink = job.getLinkOfType(ApiLinkItem.ENCLOSURE_REL).get().getLink();
    result =
        mockMvc
            .perform(
                get(downloadLink.substring(downloadLink.indexOf("/api/"))).header("apiKey", apiKey))
            .andReturn();
    assertNull(result.getResolvedException());
    return samplesSectionOf(result.getResponse().getContentAsString());
  }

  private static String samplesSectionOf(String exportedCsv) {
    int samplesContent = exportedCsv.indexOf(CSV_EXPORTED_CONTENT_SAMPLES);
    assertTrue(samplesContent > 0, exportedCsv);
    int sectionStart = exportedCsv.lastIndexOf(CSV_EXPORT_COMMENT_HEADER, samplesContent);
    int nextSectionStart = exportedCsv.indexOf(CSV_EXPORT_COMMENT_HEADER, samplesContent);
    return nextSectionStart < 0
        ? exportedCsv.substring(sectionStart)
        : exportedCsv.substring(sectionStart, nextSectionStart);
  }

  private static List<String[]> parseCsvRows(String csv) throws IOException {
    CsvMapper mapper = new CsvMapper();
    mapper.enable(CsvParser.Feature.WRAP_AS_ARRAY);
    mapper.enable(CsvParser.Feature.SKIP_EMPTY_LINES);
    List<String[]> rows = mapper.readerFor(String[].class).<String[]>readValues(csv).readAll();
    return rows.stream()
        .filter(row -> !row[0].startsWith(InventoryItemCsvExporter.CSV_COMMENT_PREFIX))
        .collect(Collectors.toList());
  }

  private static int indexOfColumnStartingWith(String[] header, String columnNamePrefix) {
    for (int i = 0; i < header.length; i++) {
      if (header[i].startsWith(columnNamePrefix)) {
        return i;
      }
    }
    throw new IllegalStateException(
        "no column starting with '" + columnNamePrefix + "' in " + Arrays.toString(header));
  }

  private static ApiInventoryEntityField linkFieldOfImportedSampleNamed(
      ApiInventoryImportSampleImportResult results, String sampleName) {
    ApiSampleWithFullSubSamples sample =
        results.getResults().stream()
            .map(r -> (ApiSampleWithFullSubSamples) r.getRecord())
            .filter(r -> sampleName.equals(r.getName()))
            .findFirst()
            .orElseThrow();
    return sample.getFields().stream()
        .filter(f -> f.getName().startsWith(LINK_FIELD_NAME))
        .findFirst()
        .orElseThrow();
  }

  private MockMultipartFile getTestCsvFile(String paramName, String fileName)
      throws IOException, FileNotFoundException {
    return new MockMultipartFile(
        paramName, fileName, "text/csv", getTestResourceFileStream("inventory/" + fileName));
  }
}
