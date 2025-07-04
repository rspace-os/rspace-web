package com.researchspace.service.fieldmark.impl;

import static com.researchspace.service.fieldmark.impl.FieldmarkToRSpaceApiConverterTest.getPreBuiltSample;
import static com.researchspace.service.fieldmark.impl.FieldmarkToRSpaceApiConverterTest.getPreBuiltSampleTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.controller.ContainerApiPostValidator;
import com.researchspace.api.v1.controller.InventoryFilePostValidator;
import com.researchspace.api.v1.controller.SampleApiPostFullValidator;
import com.researchspace.api.v1.controller.SampleApiPostValidator;
import com.researchspace.api.v1.controller.SampleTemplatePostValidator;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.fieldmark.model.FieldmarkNotebook;
import com.researchspace.fieldmark.model.exception.FieldmarkImportException;
import com.researchspace.fieldmark.model.utils.FieldmarkDoiIdentifierExtractor;
import com.researchspace.model.User;
import com.researchspace.model.dtos.IControllerInputValidator;
import com.researchspace.model.dtos.fieldmark.FieldmarkNotebookDTO;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.fieldmark.FieldmarkServiceClientAdapter;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.InventoryFileApiManager;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.integrations.fieldmark.FieldmarkApiImportRequest;
import com.researchspace.webapp.integrations.fieldmark.FieldmarkApiImportResult;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.naming.InvalidNameException;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.client.HttpServerErrorException;

public class FieldmarkServiceManagerTest extends SpringTransactionalTest {

  private static final String NOTEBOOK_ID = "notebookId";
  private static final String GOOD_IDENTIFIER_FIELD_NAME = "IGSN-QR-Code";
  private static final String WRONG_IDENTIFIER_FIELD_NAME = "IdenfifierFieldName_wrong";
  private static String IDENTIFIER_VALUE;
  private static final FieldmarkApiImportRequest GOOD_NOTEBOOK_REQ =
      new FieldmarkApiImportRequest(NOTEBOOK_ID, GOOD_IDENTIFIER_FIELD_NAME);
  private static final FieldmarkApiImportRequest WRONG_NOTEBOOK_REQ =
      new FieldmarkApiImportRequest("wrong_notebookId", WRONG_IDENTIFIER_FIELD_NAME);
  private static final String FIELD_GLOBAL_IDENTIFIER = "SA98307";
  private static final String RECORD_ID = "rec-ae48f602-c9c4-4e9e-ae3b-6ecf65706e87";

  private @InjectMocks FieldmarkServiceManagerImpl fieldmarkServiceManagerImpl;
  private @Mock FieldmarkServiceClientAdapter fieldmarkServiceClientAdapter;
  private @Mock ApiAvailabilityHandler apiHandler;
  private @Mock SampleTemplatePostValidator sampleTemplatePostValidator;
  private @Mock ContainerApiPostValidator apiContainerPostValidator;
  private @Mock IControllerInputValidator inputValidator;
  private @Mock SampleApiPostValidator sampleApiPostValidator;
  private @Mock SampleApiPostFullValidator sampleApiPostFullValidator;
  private @Mock InventoryFilePostValidator invFilePostValidator;

  private @Mock InventoryIdentifierApiManager inventoryIdentifierApiManager;
  private @Mock InventoryFileApiManager inventoryFileManager;
  private @Mock ContainerApiManager containerApiMgr;
  private @Mock SampleApiManager sampleApiMgr;
  private @Mock User goodUser;
  private @Mock User wrongUser;
  private BindingResult bindingResult;

  private List<FieldmarkNotebook> notebookList;
  private FieldmarkNotebookDTO notebookDTO;
  private ApiSampleTemplate sampleTemplateRSpace;
  private ApiContainer containerRSpace;
  private ApiSampleWithFullSubSamples sampleRSpace10;
  private final ObjectMapper mapper = new ObjectMapper();
  private final String DOI_1 = "10.12345/nico-test1";
  private final String DOI_2 = "10.12345/nico-test2";
  private final ApiInventoryDOI DOI_TEST_1 = new ApiInventoryDOI();
  private final ApiInventoryDOI DOI_TEST_2 = new ApiInventoryDOI();

  @Before
  public void setUp() throws IOException, URISyntaxException, BindException, InvalidNameException {
    MockitoAnnotations.openMocks(this);
    bindingResult = new BeanPropertyBindingResult(null, "fieldmark");
    DOI_TEST_1.setId(1L);
    DOI_TEST_1.setDoi(DOI_1);
    DOI_TEST_2.setId(2L);
    DOI_TEST_2.setDoi(DOI_2);
    String json =
        IOUtils.resourceToString(
            "/TestResources/fieldmark/notebooks.json", Charset.defaultCharset());
    notebookList = Arrays.asList(mapper.readValue(json, FieldmarkNotebook[].class));

    when(fieldmarkServiceClientAdapter.getFieldmarkNotebookList(goodUser)).thenReturn(notebookList);
    when(fieldmarkServiceClientAdapter.getFieldmarkNotebookList(wrongUser))
        .thenThrow(
            new FieldmarkImportException(
                new HttpServerErrorException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error")));

    json =
        IOUtils.resourceToString(
            "/TestResources/fieldmark/notebookDTO-singleRecord.json", Charset.defaultCharset());
    notebookDTO = mapper.readValue(json, FieldmarkNotebookDTO.class);
    notebookDTO.setDoiIdentifierFieldName(GOOD_IDENTIFIER_FIELD_NAME);
    IDENTIFIER_VALUE =
        (String)
            notebookDTO
                .getRecord(RECORD_ID)
                .getFields()
                .get(GOOD_IDENTIFIER_FIELD_NAME)
                .getFieldValue();
    notebookDTO
        .getRecord(RECORD_ID)
        .getFields()
        .put(GOOD_IDENTIFIER_FIELD_NAME, new FieldmarkDoiIdentifierExtractor(IDENTIFIER_VALUE));

    when(fieldmarkServiceClientAdapter.getFieldmarkNotebook(
            goodUser, GOOD_NOTEBOOK_REQ.getNotebookId(), GOOD_IDENTIFIER_FIELD_NAME))
        .thenReturn(notebookDTO);
    when(fieldmarkServiceClientAdapter.getFieldmarkNotebook(
            wrongUser, GOOD_NOTEBOOK_REQ.getNotebookId(), GOOD_IDENTIFIER_FIELD_NAME))
        .thenThrow(new IOException("No disk space left"));
    when(fieldmarkServiceClientAdapter.getFieldmarkNotebook(
            goodUser, WRONG_NOTEBOOK_REQ.getNotebookId(), WRONG_IDENTIFIER_FIELD_NAME))
        .thenThrow(new HttpServerErrorException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

    sampleTemplateRSpace = getPreBuiltSampleTemplate(notebookDTO);

    when(sampleApiMgr.createSampleTemplate(any(), any())).thenReturn(sampleTemplateRSpace);

    json =
        IOUtils.resourceToString(
            "/TestResources/fieldmark/api-container-response.json", Charset.defaultCharset());
    containerRSpace = mapper.readValue(json, ApiContainer.class);

    when(containerApiMgr.createNewApiContainer(any(), any())).thenReturn(containerRSpace);

    sampleRSpace10 = getPreBuiltSample(notebookDTO);

    when(sampleApiMgr.createNewApiSample(any(), any())).thenReturn(sampleRSpace10);

    when(inventoryIdentifierApiManager.findIdentifiers(
            "draft", false, IDENTIFIER_VALUE, false, goodUser))
        .thenReturn(List.of(DOI_TEST_1));
    when(inventoryIdentifierApiManager.findIdentifiers(
            "draft", false, WRONG_IDENTIFIER_FIELD_NAME, false, goodUser))
        .thenReturn(Collections.EMPTY_LIST);
    when(inventoryIdentifierApiManager.findIdentifiers(
            "draft", false, IDENTIFIER_VALUE, false, wrongUser))
        .thenThrow(new HttpServerErrorException(HttpStatus.UNAUTHORIZED));
    when(inventoryIdentifierApiManager.findIdentifiers(
            "draft", false, WRONG_IDENTIFIER_FIELD_NAME, false, wrongUser))
        .thenReturn(List.of(DOI_TEST_2));
  }

  @Test
  public void testGetNotebooksSuccessful() {
    List<FieldmarkNotebook> result = fieldmarkServiceManagerImpl.getFieldmarkNotebookList(goodUser);
    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals("1726126204618-rspace-igsn-demo", result.get(0).getNonUniqueProjectId());
  }

  @Test
  public void testGetNotebookListRaisesException() {
    FieldmarkImportException thrown =
        assertThrows(
            FieldmarkImportException.class,
            () -> fieldmarkServiceManagerImpl.getFieldmarkNotebookList(wrongUser),
            "FieldmarkServiceManager did not throw the exception, but it was needed");
    assertTrue(thrown.getMessage().contains("Internal Server Error"));
  }

  @Test
  public void testImportNotebookRaisesIoException() {
    FieldmarkImportException thrown =
        assertThrows(
            FieldmarkImportException.class,
            () -> fieldmarkServiceManagerImpl.importNotebook(GOOD_NOTEBOOK_REQ, wrongUser),
            "FieldmarkServiceManager did not throw the exception, but it was needed");
    assertTrue(
        thrown
            .getMessage()
            .contains(
                "The notebookID \""
                    + GOOD_NOTEBOOK_REQ.getNotebookId()
                    + "\" has not being imported"));
    assertTrue(thrown.getMessage().contains("No disk space left"));
  }

  @Test
  public void testImportNotebookRaisesServerHttpException() throws IOException {
    when(fieldmarkServiceClientAdapter.getFieldmarkNotebook(
            goodUser, GOOD_NOTEBOOK_REQ.getNotebookId(), GOOD_IDENTIFIER_FIELD_NAME))
        .thenThrow(
            new HttpServerErrorException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error"));
    FieldmarkImportException thrown =
        assertThrows(
            FieldmarkImportException.class,
            () -> fieldmarkServiceManagerImpl.importNotebook(GOOD_NOTEBOOK_REQ, goodUser),
            "FieldmarkServiceManager did not throw the exception, but it was needed");

    assertTrue(thrown.getMessage().contains("The notebook cannot be fetched"));
    assertTrue(thrown.getMessage().contains("Internal Server Error"));
  }

  public void testImportNotebookRaisesClientHttpException() {
    BindException thrown =
        assertThrows(
            BindException.class,
            () -> fieldmarkServiceManagerImpl.importNotebook(WRONG_NOTEBOOK_REQ, goodUser),
            "FieldmarkServiceManager did not throw the exception, but it was needed");
    assertTrue(thrown.getMessage().contains("Unauthorized"));
  }

  @Test
  public void testImportNotebookSuccessful() throws IOException, InvalidNameException {
    FieldmarkApiImportResult result =
        fieldmarkServiceManagerImpl.importNotebook(GOOD_NOTEBOOK_REQ, goodUser);

    verify(fieldmarkServiceClientAdapter)
        .getFieldmarkNotebook(
            goodUser, GOOD_NOTEBOOK_REQ.getNotebookId(), GOOD_IDENTIFIER_FIELD_NAME);

    verify(sampleTemplatePostValidator).validate(any(), any());
    verify(sampleApiMgr).createSampleTemplate(any(), any());

    verify(apiContainerPostValidator).validate(any(), any());
    verify(containerApiMgr).createNewApiContainer(any(), any());

    verify(inputValidator).validate(any(), any(SampleApiPostValidator.class), any());
    verify(inputValidator).validate(any(), any(SampleApiPostFullValidator.class), any());
    verify(inputValidator).validate(any(), any(InventoryFilePostValidator.class), any());
    verify(sampleApiMgr).createNewApiSample(any(), any());
    verify(sampleApiMgr).assertUserCanEditSampleField(any(), any());

    verify(apiHandler).assertInventoryAndDataciteEnabled(goodUser);
    verify(inventoryIdentifierApiManager)
        .findIdentifiers("draft", false, IDENTIFIER_VALUE, false, goodUser);
    verify(inventoryFileManager)
        .attachNewInventoryFileToInventoryRecord(any(), any(), any(), any());

    assertNotNull(result);
    assertEquals("IC98304", result.getContainerGlobalId());
    assertEquals("IT98304", result.getSampleTemplateGlobalId());
    assertEquals("Container RSpace IGSN Demo - 2024-11-20 18:24:03", result.getContainerName());
    assertEquals(1, result.getSampleGlobalIds().size());
    assertEquals(FIELD_GLOBAL_IDENTIFIER, result.getSampleGlobalIds().stream().findFirst().get());
  }
}
