package com.researchspace.service.fieldmark.impl;

import static com.researchspace.fieldmark.model.utils.FieldmarkUtils.createFilesMap;
import static com.researchspace.service.IntegrationsHandler.FIELDMARK_APP_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.commons.util.StringUtils.isNotBlank;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiInventoryImportSampleParseResult;
import com.researchspace.fieldmark.client.FieldmarkClient;
import com.researchspace.fieldmark.model.FieldmarkDoiIdentifier;
import com.researchspace.fieldmark.model.FieldmarkNotebook;
import com.researchspace.fieldmark.model.FieldmarkNotebookMetadata;
import com.researchspace.fieldmark.model.FieldmarkRecordsCsvExport;
import com.researchspace.fieldmark.model.FieldmarkRecordsJsonExport;
import com.researchspace.fieldmark.model.utils.FieldmarkFileExtractor;
import com.researchspace.fieldmark.model.utils.FieldmarkLocationExtractor;
import com.researchspace.model.User;
import com.researchspace.model.dtos.fieldmark.FieldmarkNotebookDTO;
import com.researchspace.model.dtos.fieldmark.FieldmarkRecordDTO;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.inventory.InventoryImportManager;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

public class FieldmarkServiceClientAdapterTest {

  private static final String GOOD_ACCESS_TOKEN = "API_KEY";
  private static final String WRONG_ACCESS_TOKEN = "WRONG_API_KEY";
  private static final String USERNAME = "userName";
  private static final String WRONG_USERNAME = "wrong_" + USERNAME;
  private static final String GOOD_NOTEBOOK_ID = "notebookId";
  private static final String GOOD_NOTEBOOK_ID_NO_IGSN = "notebookId_NoIgsn";
  private static final String WRONG_NOTEBOOK_ID = "wrong_notebookId";
  private static final String FORM_ID = "Primary";
  private static final String IGSN_CANDIDATE_FIELD_NAME = "IGSN-QR-Code";
  private @InjectMocks FieldmarkServiceClientAdapterImpl adapterUnderTest;
  private @Mock FieldmarkClient fieldmarkClient;
  private @Mock User goodUser;
  private @Mock User wrongUser;
  private @Mock UserConnectionManager userConnectionManager;
  private @Mock UserConnection userConnectionMock;
  private @Mock InventoryImportManager importManager;

  private List<FieldmarkNotebook> notebookList;
  private FieldmarkNotebook notebook;
  private FieldmarkRecordsJsonExport records;
  private Map<String, byte[]> filesInRecords;
  private FieldmarkRecordsCsvExport csvRecords;

  @Before
  public void init() throws IOException, URISyntaxException {
    MockitoAnnotations.openMocks(this);
    ObjectMapper mapper = new ObjectMapper();

    String json =
        IOUtils.resourceToString(
            "/TestResources/fieldmark/notebooks.json", Charset.defaultCharset());
    notebookList = Arrays.asList(mapper.readValue(json, FieldmarkNotebook[].class));

    json =
        IOUtils.resourceToString(
            "/TestResources/fieldmark/notebookID.json", Charset.defaultCharset());
    notebook = mapper.readValue(json, FieldmarkNotebook.class);

    json =
        IOUtils.resourceToString("/TestResources/fieldmark/records.json", Charset.defaultCharset());
    records = mapper.readValue(json, FieldmarkRecordsJsonExport.class);

    byte[] zipFile = IOUtils.resourceToByteArray("/TestResources/fieldmark/FieldmarkFile.zip");
    filesInRecords = createFilesMap(GOOD_NOTEBOOK_ID, zipFile);

    byte[] csvFileInByte = IOUtils.resourceToByteArray("/TestResources/fieldmark/notebook.csv");
    csvRecords = new FieldmarkRecordsCsvExport(csvFileInByte);

    when(fieldmarkClient.getNotebooks(GOOD_ACCESS_TOKEN)).thenReturn(notebookList);
    when(fieldmarkClient.getNotebooks(WRONG_ACCESS_TOKEN))
        .thenThrow(
            new HttpServerErrorException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Fieldmark server is down"));
    when(fieldmarkClient.getNotebook(GOOD_ACCESS_TOKEN, GOOD_NOTEBOOK_ID)).thenReturn(notebook);
    when(fieldmarkClient.getNotebook(GOOD_ACCESS_TOKEN, WRONG_NOTEBOOK_ID))
        .thenThrow(new HttpServerErrorException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
    when(fieldmarkClient.getNotebookRecords(GOOD_ACCESS_TOKEN, GOOD_NOTEBOOK_ID))
        .thenReturn(records);
    when(fieldmarkClient.getNotebookRecords(GOOD_ACCESS_TOKEN, GOOD_NOTEBOOK_ID_NO_IGSN))
        .thenReturn(records);
    when(fieldmarkClient.getNotebookFiles(GOOD_ACCESS_TOKEN, GOOD_NOTEBOOK_ID, FORM_ID))
        .thenReturn(filesInRecords);
    when(fieldmarkClient.getNotebookCsv(GOOD_ACCESS_TOKEN, GOOD_NOTEBOOK_ID, FORM_ID))
        .thenReturn(csvRecords);
    when(fieldmarkClient.getNotebookCsv(GOOD_ACCESS_TOKEN, GOOD_NOTEBOOK_ID_NO_IGSN, FORM_ID))
        .thenReturn(csvRecords);

    when(goodUser.getUsername()).thenReturn(USERNAME);
    when(wrongUser.getUsername()).thenReturn(WRONG_USERNAME);

    when(userConnectionManager.findByUserNameProviderName(USERNAME, FIELDMARK_APP_NAME))
        .thenReturn(Optional.of(userConnectionMock));
    when(userConnectionManager.findByUserNameProviderName(WRONG_USERNAME, FIELDMARK_APP_NAME))
        .thenReturn(Optional.empty());
    when(userConnectionMock.getAccessToken()).thenReturn(GOOD_ACCESS_TOKEN);

    ApiInventoryImportSampleParseResult parseCsvResult = new ApiInventoryImportSampleParseResult();
    parseCsvResult.setFieldMappings(Map.of(IGSN_CANDIDATE_FIELD_NAME, "identifier"));
    when(importManager.parseSamplesCsvFile(
            "fieldmarkImport_" + GOOD_NOTEBOOK_ID, csvRecords.getCsvFile(), goodUser))
        .thenReturn(parseCsvResult);
    when(importManager.parseSamplesCsvFile(
            "fieldmarkImport_" + GOOD_NOTEBOOK_ID_NO_IGSN, csvRecords.getCsvFile(), goodUser))
        .thenReturn(new ApiInventoryImportSampleParseResult());
    when(importManager.parseSamplesCsvFile(
            "fieldmarkImport_" + WRONG_NOTEBOOK_ID, csvRecords.getCsvFile(), goodUser))
        .thenThrow(new IOException("Error while parsing CSV"));
  }

  /* testing getFieldmarkNotebookList */

  @Test
  public void testGetFieldmarkNotebookListReturnsNotebooks()
      throws MalformedURLException, URISyntaxException {
    List<FieldmarkNotebook> result = adapterUnderTest.getFieldmarkNotebookList(goodUser);

    verify(userConnectionManager).findByUserNameProviderName(USERNAME, FIELDMARK_APP_NAME);
    verify(fieldmarkClient).getNotebooks(GOOD_ACCESS_TOKEN);
    assertNotNull(result);
    assertEquals(1, result.size());
  }

  @Test
  public void testGetFieldmarkNotebookListFieldmarkServerDown() {
    when(userConnectionMock.getAccessToken()).thenReturn(WRONG_ACCESS_TOKEN);
    HttpServerErrorException thrown =
        assertThrows(
            HttpServerErrorException.class,
            () -> adapterUnderTest.getFieldmarkNotebookList(goodUser),
            "FieldmarkServiceClientAdapterImpl did not throw the exception, but it was needed");
    assertTrue(thrown.getMessage().contains("Fieldmark server is down"));
  }

  @Test
  public void testGetFieldmarkNotebookListAccessTokenNotFound() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> adapterUnderTest.getFieldmarkNotebookList(wrongUser),
            "FieldmarkServiceClientAdapterImpl did not throw the exception, but it was needed");
    assertTrue(thrown.getMessage().contains("No UserConnection exists for"));
  }

  /* testing getFieldmarkNotebook */

  @Test
  public void testGetFieldmarkNotebook() throws IOException {
    FieldmarkNotebookDTO resultUnderTest =
        adapterUnderTest.getFieldmarkNotebook(
            goodUser, GOOD_NOTEBOOK_ID, IGSN_CANDIDATE_FIELD_NAME);

    verify(userConnectionManager).findByUserNameProviderName(USERNAME, FIELDMARK_APP_NAME);
    verify(fieldmarkClient).getNotebook(GOOD_ACCESS_TOKEN, GOOD_NOTEBOOK_ID);
    verify(fieldmarkClient).getNotebookRecords(GOOD_ACCESS_TOKEN, GOOD_NOTEBOOK_ID);
    verify(fieldmarkClient).getNotebookFiles(GOOD_ACCESS_TOKEN, GOOD_NOTEBOOK_ID, FORM_ID);
    verify(fieldmarkClient).getNotebookCsv(GOOD_ACCESS_TOKEN, GOOD_NOTEBOOK_ID, FORM_ID);

    // check Notebook
    assertNotNull(resultUnderTest);
    assertEquals("1726126204618-rspace-igsn-demo", resultUnderTest.getId());
    assertEquals("RSpace IGSN Demo", resultUnderTest.getName());
    assertTrue(isNotBlank(resultUnderTest.getTimestamp()));
    String notebookDTOTimestamp = resultUnderTest.getTimestamp();

    // Check Metadata is properly filled
    FieldmarkNotebookMetadata metadataUnderTest = resultUnderTest.getMetadata();
    assertNotNull(metadataUnderTest);
    assertEquals("123", metadataUnderTest.getAge());
    assertEquals("Large", metadataUnderTest.getSize());
    assertFalse(metadataUnderTest.getIsPublic());
    assertFalse(metadataUnderTest.getIsRequest());
    assertEquals("Fieldmark", metadataUnderTest.getLeadInstitution());
    assertEquals("RSpace IGSN Demo", metadataUnderTest.getName());
    assertEquals("1.1", metadataUnderTest.getNotebookVersion());
    assertEquals(
        "Demonstration notebook to help develop an export pipeline from Fieldmark to RSpace.",
        metadataUnderTest.getPreDescription());
    assertEquals("Steve Cassidy", metadataUnderTest.getProjectLead());
    assertEquals("New", metadataUnderTest.getProjectStatus());
    assertEquals("1.0", metadataUnderTest.getSchemaVersion());
    assertEquals("true", metadataUnderTest.getShowQRCodeButton());
    assertEquals("1726126204618-rspace-igsn-demo", metadataUnderTest.getProjectId());

    // check records and fields
    Map<String, FieldmarkRecordDTO> recordsUnderTest = resultUnderTest.getRecords();
    assertNotNull(recordsUnderTest);
    assertEquals(3, recordsUnderTest.size());

    // record 1
    assertTrue(recordsUnderTest.containsKey("rec-5eb53c21-d7f8-41a7-a8b5-4900e46cf8e0"));
    FieldmarkRecordDTO currentRecord =
        recordsUnderTest.get("rec-5eb53c21-d7f8-41a7-a8b5-4900e46cf8e0");
    assertEquals("rec-5eb53c21-d7f8-41a7-a8b5-4900e46cf8e0", currentRecord.getRecordId());
    assertEquals("Sample-12-00009", currentRecord.getIdentifier());
    assertEquals(notebookDTOTimestamp, currentRecord.getTimestamp());
    assertEquals(11, currentRecord.getFields().size());
    assertEquals("00009", currentRecord.getField("Field-ID").getFieldValue());
    assertEquals(
        "Sample-12-00009", currentRecord.getField("hridPrimary-Next-Section").getFieldValue());
    assertEquals(12, currentRecord.getField("Survey-Number").getFieldValue());
    assertEquals(
        new FieldmarkDoiIdentifier("1"),
        currentRecord.getField(IGSN_CANDIDATE_FIELD_NAME).getFieldValue());
    assertNull(currentRecord.getField("Sample-Location").getFieldValue());
    assertEquals("Roma", currentRecord.getField("New-Text-Field").getFieldValue());
    assertEquals(
        "https://doi.org/10.82316/rzqc-0n83",
        currentRecord.getField("Item-Description").getFieldValue());
    assertEquals(
        "Sample-Photograph/Sample-12-00009-Sample-Photograph.jpg",
        ((FieldmarkFileExtractor) currentRecord.getField("Sample-Photograph")).getFileName());
    assertEquals(byte[].class, currentRecord.getField("Sample-Photograph").getFieldType());
    assertEquals(18, currentRecord.getField("Length-mm").getFieldValue());
    assertEquals(88, currentRecord.getField("Width-mm").getFieldValue());
    assertEquals(5, currentRecord.getField("Thickness-mm").getFieldValue());

    // record 2
    assertTrue(recordsUnderTest.containsKey("rec-b189e6ec-b760-4b44-8789-0ddc35d7cde2"));
    currentRecord = recordsUnderTest.get("rec-b189e6ec-b760-4b44-8789-0ddc35d7cde2");
    assertEquals("rec-b189e6ec-b760-4b44-8789-0ddc35d7cde2", currentRecord.getRecordId());
    assertEquals("Sample-63-00050", currentRecord.getIdentifier());
    assertEquals(notebookDTOTimestamp, currentRecord.getTimestamp());
    assertEquals(11, currentRecord.getFields().size());
    assertEquals("00050", currentRecord.getField("Field-ID").getFieldValue());
    assertEquals(
        "Sample-63-00050", currentRecord.getField("hridPrimary-Next-Section").getFieldValue());
    assertEquals(63, currentRecord.getField("Survey-Number").getFieldValue());
    assertEquals(
        new FieldmarkDoiIdentifier("1"),
        currentRecord.getField(IGSN_CANDIDATE_FIELD_NAME).getFieldValue());
    assertEquals(
        "14.036364239998946",
        ((FieldmarkLocationExtractor) currentRecord.getField("Sample-Location"))
            .getLatitudeStringValue());
    assertEquals(
        "42.250294200498814",
        ((FieldmarkLocationExtractor) currentRecord.getField("Sample-Location"))
            .getLongitudeStringValue());
    assertEquals("homeS", currentRecord.getField("New-Text-Field").getFieldValue());
    assertEquals("10.82316/8vmr-be22", currentRecord.getField("Item-Description").getFieldValue());
    assertEquals(
        "Sample-Photograph/Sample-63-00050-Sample-Photograph.jpg",
        ((FieldmarkFileExtractor) currentRecord.getField("Sample-Photograph")).getFileName());
    assertEquals(byte[].class, currentRecord.getField("Sample-Photograph").getFieldType());
    assertEquals(50, currentRecord.getField("Length-mm").getFieldValue());
    assertEquals(22, currentRecord.getField("Width-mm").getFieldValue());
    assertEquals(56, currentRecord.getField("Thickness-mm").getFieldValue());

    // record 3
    assertTrue(recordsUnderTest.containsKey("rec-e881323c-d3cb-4393-9784-07b86585675b"));
    currentRecord = recordsUnderTest.get("rec-e881323c-d3cb-4393-9784-07b86585675b");
    assertEquals("rec-e881323c-d3cb-4393-9784-07b86585675b", currentRecord.getRecordId());
    assertEquals("Sample-1-00008", currentRecord.getIdentifier());
    assertEquals(notebookDTOTimestamp, currentRecord.getTimestamp());
    assertEquals(11, currentRecord.getFields().size());
    assertEquals("00008", currentRecord.getField("Field-ID").getFieldValue());
    assertEquals(
        "Sample-1-00008", currentRecord.getField("hridPrimary-Next-Section").getFieldValue());
    assertEquals(1, currentRecord.getField("Survey-Number").getFieldValue());
    assertEquals(
        "1",
        ((FieldmarkDoiIdentifier) currentRecord.getField("IGSN-QR-Code").getFieldValue())
            .getDoiIdentifier());
    assertNull(currentRecord.getField("Sample-Location").getFieldValue());
    assertEquals(
        "Glasgow or PAISLEY (Scotland)", currentRecord.getField("New-Text-Field").getFieldValue());
    assertEquals(
        "doi.org/10.82316/731r-r253", currentRecord.getField("Item-Description").getFieldValue());
    assertEquals(
        "Sample-Photograph/Sample-1-00008-Sample-Photograph.jpg",
        ((FieldmarkFileExtractor) currentRecord.getField("Sample-Photograph")).getFileName());
    assertEquals(byte[].class, currentRecord.getField("Sample-Photograph").getFieldType());
    assertEquals(22, currentRecord.getField("Length-mm").getFieldValue());
    assertEquals(54, currentRecord.getField("Width-mm").getFieldValue());
    assertEquals(32, currentRecord.getField("Thickness-mm").getFieldValue());
  }

  @Test
  public void testGetFieldmarkNotebookWithAccessTokenNotFound() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> adapterUnderTest.getFieldmarkNotebook(wrongUser, GOOD_NOTEBOOK_ID, null),
            "FieldmarkServiceClientAdapterImpl did not throw the exception, but it was needed");
    assertTrue(thrown.getMessage().contains("No UserConnection exists for"));
  }

  @Test
  public void testGetFieldmarkNotebookWrongNotebookId() {
    HttpServerErrorException thrown =
        assertThrows(
            HttpServerErrorException.class,
            () -> adapterUnderTest.getFieldmarkNotebook(goodUser, WRONG_NOTEBOOK_ID, null),
            "FieldmarkServiceClientAdapterImpl did not throw the exception, but it was needed");
    assertTrue(thrown.getMessage().contains("Unauthorized"));

    verify(userConnectionManager).findByUserNameProviderName(USERNAME, FIELDMARK_APP_NAME);
    verify(fieldmarkClient).getNotebook(GOOD_ACCESS_TOKEN, WRONG_NOTEBOOK_ID);
    verifyNoMoreInteractions(fieldmarkClient);
  }

  /*  testing getIgsnCandidateFields */
  @Test
  public void testGetIgsnCandidateFieldsSucceedWithOneField() throws IOException {
    List<String> result = adapterUnderTest.getIgsnCandidateFields(goodUser, GOOD_NOTEBOOK_ID);

    verify(userConnectionManager).findByUserNameProviderName(USERNAME, FIELDMARK_APP_NAME);
    verify(fieldmarkClient).getNotebookRecords(GOOD_ACCESS_TOKEN, GOOD_NOTEBOOK_ID);
    verify(fieldmarkClient).getNotebookCsv(GOOD_ACCESS_TOKEN, GOOD_NOTEBOOK_ID, FORM_ID);
    verify(importManager)
        .parseSamplesCsvFile(
            "fieldmarkImport_" + GOOD_NOTEBOOK_ID, csvRecords.getCsvFile(), goodUser);
    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals(IGSN_CANDIDATE_FIELD_NAME, result.get(0));
  }

  @Test
  public void testGetIgsnCandidateFieldsSucceedWithNoFields() throws IOException {
    List<String> result =
        adapterUnderTest.getIgsnCandidateFields(goodUser, GOOD_NOTEBOOK_ID_NO_IGSN);

    verify(userConnectionManager).findByUserNameProviderName(USERNAME, FIELDMARK_APP_NAME);
    verify(fieldmarkClient).getNotebookRecords(GOOD_ACCESS_TOKEN, GOOD_NOTEBOOK_ID_NO_IGSN);
    verify(fieldmarkClient).getNotebookCsv(GOOD_ACCESS_TOKEN, GOOD_NOTEBOOK_ID_NO_IGSN, FORM_ID);
    verify(importManager)
        .parseSamplesCsvFile(
            "fieldmarkImport_" + GOOD_NOTEBOOK_ID_NO_IGSN, csvRecords.getCsvFile(), goodUser);
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }
}
