package com.researchspace.webapp.integrations.fieldmark;

import static com.researchspace.service.fieldmark.impl.FieldmarkToRSpaceApiConverterTest.getPreBuiltSample;
import static com.researchspace.service.fieldmark.impl.FieldmarkToRSpaceApiConverterTest.getPreBuiltSampleTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.controller.ContainersApiController;
import com.researchspace.api.v1.controller.InventoryFilesApiController;
import com.researchspace.api.v1.controller.SampleTemplatesApiController;
import com.researchspace.api.v1.controller.SamplesApiController;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInventoryFile;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.fieldmark.model.FieldmarkNotebook;
import com.researchspace.model.User;
import com.researchspace.model.dtos.fieldmark.FieldmarkNotebookDTO;
import com.researchspace.service.fieldmark.FieldmarkServiceClientAdapter;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
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

public class FieldmarkApiControllerTest extends SpringTransactionalTest {
  private static final FieldmarkApiImportRequest GOOD_NOTEBOOK_REQ =
      new FieldmarkApiImportRequest("notebookId");
  private static final FieldmarkApiImportRequest WRONG_NOTEBOOK_REQ =
      new FieldmarkApiImportRequest("wrong_notebookId");

  private @InjectMocks FieldmarkApiController fieldmarkApiController;
  private @Mock SampleTemplatesApiController sampleTemplatesApiController;
  private @Mock InventoryFilesApiController inventoryFilesApiController;
  private @Mock SamplesApiController samplesApiController;
  private @Mock ContainersApiController containersApiController;
  private @Mock FieldmarkServiceClientAdapter fieldmarkServiceClientAdapter;
  private @Mock User goodUser;
  private @Mock User wrongUser;
  private BindingResult bindingResult;

  private List<FieldmarkNotebook> notebookList;
  private FieldmarkNotebookDTO notebookDTO;
  private ApiSampleTemplate sampleTemplateRSpace;
  private ApiContainer containerRSpace;
  private ApiSampleWithFullSubSamples sampleRSpace10;
  private final ObjectMapper mapper = new ObjectMapper();

  @Before
  public void setUp() throws IOException, URISyntaxException, BindException {
    MockitoAnnotations.openMocks(this);
    bindingResult = new BeanPropertyBindingResult(null, "fieldmark");

    String json =
        IOUtils.resourceToString(
            "/TestResources/fieldmark/notebooks.json", Charset.defaultCharset());
    notebookList = Arrays.asList(mapper.readValue(json, FieldmarkNotebook[].class));

    when(fieldmarkServiceClientAdapter.getFieldmarkNotebookList(goodUser)).thenReturn(notebookList);
    when(fieldmarkServiceClientAdapter.getFieldmarkNotebookList(wrongUser))
        .thenThrow(
            new HttpServerErrorException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error"));

    json =
        IOUtils.resourceToString(
            "/TestResources/fieldmark/notebookDTO-singleRecord.json", Charset.defaultCharset());
    notebookDTO = mapper.readValue(json, FieldmarkNotebookDTO.class);

    when(fieldmarkServiceClientAdapter.getFieldmarkNotebook(
            goodUser, GOOD_NOTEBOOK_REQ.getNotebookId()))
        .thenReturn(notebookDTO);
    when(fieldmarkServiceClientAdapter.getFieldmarkNotebook(
            wrongUser, GOOD_NOTEBOOK_REQ.getNotebookId()))
        .thenThrow(new IOException("No disk space left"));
    when(fieldmarkServiceClientAdapter.getFieldmarkNotebook(
            goodUser, WRONG_NOTEBOOK_REQ.getNotebookId()))
        .thenThrow(new HttpServerErrorException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

    sampleTemplateRSpace = getPreBuiltSampleTemplate(notebookDTO);

    when(sampleTemplatesApiController.createNewSampleTemplate(any(), any(), any()))
        .thenReturn(sampleTemplateRSpace);

    json =
        IOUtils.resourceToString(
            "/TestResources/fieldmark/api-container-response.json", Charset.defaultCharset());
    containerRSpace = mapper.readValue(json, ApiContainer.class);

    when(containersApiController.createNewContainer(any(), any(), any()))
        .thenReturn(containerRSpace);

    sampleRSpace10 = getPreBuiltSample(notebookDTO);

    when(samplesApiController.createNewSample(any(), any(), any())).thenReturn(sampleRSpace10);

    when(inventoryFilesApiController.uploadFile(any(), any(), any()))
        .thenReturn(new ApiInventoryFile());
  }

  @Test
  public void testGetNotebooksSuccessful() throws BindException, IOException, URISyntaxException {
    List<FieldmarkNotebook> result = fieldmarkApiController.getNotebooks(goodUser);
    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals("1726126204618-rspace-igsn-demo", result.get(0).getNonUniqueProjectId());
  }

  @Test
  public void testGetNotebooksRaisesException() {
    BindException thrown =
        assertThrows(
            BindException.class,
            () -> fieldmarkApiController.getNotebooks(wrongUser),
            "FieldmarkApiController did not throw the exception, but it was needed");
    assertTrue(
        thrown.getMessage().contains("Error fetching notebooks due to the Fieldmark server"));
  }

  @Test
  public void testImportNotebookRaisesIoException() {
    BindException thrown =
        assertThrows(
            BindException.class,
            () ->
                fieldmarkApiController.importNotebook(GOOD_NOTEBOOK_REQ, bindingResult, wrongUser),
            "FieldmarkApiController did not throw the exception, but it was needed");
    assertTrue(
        thrown
            .getMessage()
            .contains(
                "Error importing notebook \""
                    + GOOD_NOTEBOOK_REQ.getNotebookId()
                    + "\" from fieldmark"));
  }

  @Test
  public void testImportNotebookRaisesServerHttpException() throws IOException {
    when(fieldmarkServiceClientAdapter.getFieldmarkNotebook(
            goodUser, GOOD_NOTEBOOK_REQ.getNotebookId()))
        .thenThrow(
            new HttpServerErrorException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error"));
    BindException thrown =
        assertThrows(
            BindException.class,
            () -> fieldmarkApiController.importNotebook(GOOD_NOTEBOOK_REQ, bindingResult, goodUser),
            "FieldmarkApiController did not throw the exception, but it was needed");
    assertTrue(
        thrown
            .getMessage()
            .contains(
                "Error importing notebook \""
                    + GOOD_NOTEBOOK_REQ.getNotebookId()
                    + "\" due to Fieldmark server unavailable"));
  }

  public void testImportNotebookRaisesClientHttpException() {
    BindException thrown =
        assertThrows(
            BindException.class,
            () ->
                fieldmarkApiController.importNotebook(WRONG_NOTEBOOK_REQ, bindingResult, goodUser),
            "FieldmarkApiController did not throw the exception, but it was needed");
    assertTrue(thrown.getMessage().contains("Unauthorized"));
  }

  @Test
  public void testImportNotebookSuccessful() throws BindException, IOException {
    FieldmarkApiImportResult result =
        fieldmarkApiController.importNotebook(GOOD_NOTEBOOK_REQ, bindingResult, goodUser);

    verify(fieldmarkServiceClientAdapter)
        .getFieldmarkNotebook(goodUser, GOOD_NOTEBOOK_REQ.getNotebookId());
    verifyNoMoreInteractions(fieldmarkServiceClientAdapter);
    verify(sampleTemplatesApiController).createNewSampleTemplate(any(), any(), any());
    verifyNoMoreInteractions(sampleTemplatesApiController);
    verify(containersApiController).createNewContainer(any(), any(), any());
    verifyNoMoreInteractions(containersApiController);
    verify(samplesApiController).createNewSample(any(), any(), any());
    verifyNoMoreInteractions(samplesApiController);
    verify(inventoryFilesApiController).uploadFile(any(), any(), any());
    verifyNoMoreInteractions(inventoryFilesApiController);
    assertNotNull(result);
    assertEquals("IC98304", result.getContainerGlobalId());
    assertEquals("IT98304", result.getSampleTemplateGlobalId());
    assertEquals("Container RSpace IGSN Demo - 2024-11-20 18:24:03", result.getContainerName());
    assertEquals(1, result.getSampleGlobalIds().size());
    assertEquals("SA98307", result.getSampleGlobalIds().stream().findFirst().get());
  }
}
