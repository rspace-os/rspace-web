package com.researchspace.webapp.integrations.fieldmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.fieldmark.model.FieldmarkNotebook;
import com.researchspace.fieldmark.model.exception.FieldmarkImportException;
import com.researchspace.model.User;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.fieldmark.FieldmarkServiceManager;
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

  private static final String NOTEBOOK_ID = "notebookId";
  private static final FieldmarkApiImportRequest GOOD_NOTEBOOK_REQ =
      new FieldmarkApiImportRequest(NOTEBOOK_ID, "IgsnFieldName");
  private static final FieldmarkApiImportRequest WRONG_NOTEBOOK_REQ =
      new FieldmarkApiImportRequest("wrong_notebookId");

  private @InjectMocks FieldmarkApiController fieldmarkApiController;
  private @Mock FieldmarkServiceManager fieldmarkServiceManagerImpl;
  private @Mock ApiAvailabilityHandler apiHandler;
  private @Mock User goodUser;
  private @Mock User wrongUser;
  private BindingResult bindingResult;
  private List<FieldmarkNotebook> notebookList;

  FieldmarkApiImportResult importResult;
  private final ObjectMapper mapper = new ObjectMapper();

  @Before
  public void setUp() throws IOException, URISyntaxException, BindException {
    MockitoAnnotations.openMocks(this);
    bindingResult = new BeanPropertyBindingResult(null, "fieldmark");

    ApiContainer container = new ApiContainer();
    container.setId(1L);
    container.setGlobalId("CC1");
    container.setName("container");

    ApiSampleTemplate sampleTemplate = new ApiSampleTemplate();
    sampleTemplate.setId(1L);
    sampleTemplate.setGlobalId("ST1");
    sampleTemplate.setName("sampleTemplate");
    importResult = new FieldmarkApiImportResult(container, sampleTemplate);

    String json =
        IOUtils.resourceToString(
            "/TestResources/fieldmark/notebooks.json", Charset.defaultCharset());
    notebookList = Arrays.asList(mapper.readValue(json, FieldmarkNotebook[].class));

    when(fieldmarkServiceManagerImpl.getFieldmarkNotebookList(goodUser)).thenReturn(notebookList);
    when(fieldmarkServiceManagerImpl.getFieldmarkNotebookList(wrongUser))
        .thenThrow(
            new FieldmarkImportException(
                new HttpServerErrorException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error")));

    when(fieldmarkServiceManagerImpl.importNotebook(GOOD_NOTEBOOK_REQ, goodUser))
        .thenReturn(importResult);
    when(fieldmarkServiceManagerImpl.importNotebook(GOOD_NOTEBOOK_REQ, wrongUser))
        .thenThrow(
            new FieldmarkImportException(
                new HttpServerErrorException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error")));

    when(fieldmarkServiceManagerImpl.getIgsnCandidateFields(goodUser, NOTEBOOK_ID))
        .thenReturn(List.of("fieldName1", "fieldName2"));
    when(fieldmarkServiceManagerImpl.getIgsnCandidateFields(wrongUser, NOTEBOOK_ID))
        .thenThrow(
            new FieldmarkImportException(
                new HttpServerErrorException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error")));
  }

  @Test
  public void testGetNotebooksSuccessful() throws BindException {
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
                    + "\" from Fieldmark"));
  }

  @Test
  public void testImportNotebookRaisesServerHttpException() {
    when(fieldmarkServiceManagerImpl.importNotebook(GOOD_NOTEBOOK_REQ, goodUser))
        .thenThrow(
            new FieldmarkImportException(
                new HttpServerErrorException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error")));
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
                    + "\" from Fieldmark"));
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
  public void testImportNotebookSucceed() throws BindException {
    FieldmarkApiImportResult result =
        fieldmarkApiController.importNotebook(GOOD_NOTEBOOK_REQ, bindingResult, goodUser);

    verify(fieldmarkServiceManagerImpl).importNotebook(GOOD_NOTEBOOK_REQ, goodUser);
    assertNotNull(result);
  }

  @Test
  public void testGetIgsnCandidateFieldsSucceed() throws BindException {
    List<String> result = fieldmarkApiController.getIgsnCandidateFields(NOTEBOOK_ID, goodUser);

    verify(fieldmarkServiceManagerImpl).getIgsnCandidateFields(goodUser, NOTEBOOK_ID);
    assertNotNull(result);
  }

  @Test
  public void testGetIgsnCandidateFieldsRaisesException() {
    BindException thrown =
        assertThrows(
            BindException.class,
            () -> fieldmarkApiController.getIgsnCandidateFields(NOTEBOOK_ID, wrongUser),
            "FieldmarkApiController did not throw the exception, but it was needed");
    assertTrue(
        thrown
            .getMessage()
            .contains(
                "Error creating IGSN candidate fields for notebook \""
                    + GOOD_NOTEBOOK_REQ.getNotebookId()));
  }
}
