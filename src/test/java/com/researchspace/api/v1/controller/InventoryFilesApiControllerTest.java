package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.controller.InventoryFilesApiController.ApiInventoryFileImageRequest;
import com.researchspace.api.v1.controller.InventoryFilesApiController.ApiInventoryFilePost;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryFile;
import com.researchspace.api.v1.model.ApiSampleField;
import com.researchspace.api.v1.model.ApiSampleInfo;
import com.researchspace.api.v1.model.ApiSampleWithoutSubSamples;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ChemicalExportFormat;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.InventoryFile.InventoryFileType;
import com.researchspace.service.chemistry.ChemistryProvider;
import com.researchspace.service.impl.ContentInitializerForDevRunManager;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.controller.RSChemController.ChemEditorInputDto;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import javax.ws.rs.InternalServerErrorException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

public class InventoryFilesApiControllerTest extends SpringTransactionalTest {
  @Mock ChemistryProvider chemistryProvider;

  @Autowired @InjectMocks private InventoryFilesApiController invFilesApi;

  @Autowired private ContainerApiManager containerMgr;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.openMocks(this);
  }

  @Test
  public void retrieveDefaultDevRunContainers() throws Exception {
    User exampleContentUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithExampleContent(exampleContentUser);
    logoutAndLoginAs(exampleContentUser);

    // retrieve top containers list
    ISearchResults<ApiContainerInfo> userContainers =
        containerMgr.getTopContainersForUser(
            PaginationCriteria.createDefaultForClass(Container.class),
            null,
            null,
            exampleContentUser);
    assertEquals(2, userContainers.getTotalHits().intValue());
    // find image container and its attachment
    ApiContainerInfo imageContainer = userContainers.getResults().get(1);
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_IMAGE_CONTAINER_NAME,
        imageContainer.getName());
    assertEquals(1, imageContainer.getAttachments().size());
    ApiInventoryFile defaultAttachment = imageContainer.getAttachments().get(0);
    assertEquals(
        ContentInitializerForDevRunManager.EXAMPLE_TOP_IMAGE_CONTAINER_ATTACHMENT_NAME,
        defaultAttachment.getName());

    // retrieve attachment by id
    ApiInventoryFile retrievedDefaultFile =
        invFilesApi.getFileById(defaultAttachment.getId(), exampleContentUser);
    assertEquals(imageContainer.getGlobalId(), retrievedDefaultFile.getParentGlobalId());
    assertEquals(defaultAttachment.getName(), retrievedDefaultFile.getName());
    assertEquals(InventoryFileType.GENERAL, retrievedDefaultFile.getType());
    assertEquals(47L, retrievedDefaultFile.getSize());
    assertEquals("txt", retrievedDefaultFile.getExtension());
    assertEquals("text/plain", retrievedDefaultFile.getContentMimeType());

    // retrieve bytes of the attachment
    MockHttpServletResponse resp = new MockHttpServletResponse();
    invFilesApi.getFileBytes(defaultAttachment.getId(), exampleContentUser, resp);

    byte[] content = resp.getContentAsByteArray();
    assertNotNull(content);
    assertEquals(47, content.length);
  }

  @Test
  public void uploadDeleteInventoryAttachment() throws Exception {

    User user = createInitAndLoginAnyUser();
    ApiSampleInfo apiSample = createBasicSampleForUser(user);
    assertEquals(0, apiSample.getAttachments().size());

    MockMultipartFile mockFile = createAnyMultipartFile();
    ApiInventoryFilePost settings = new ApiInventoryFilePost();
    settings.setParentGlobalId(apiSample.getGlobalId());

    ApiInventoryFile uploadedFile = invFilesApi.uploadFile(mockFile, settings, user);
    assertNotNull(uploadedFile);
    assertEquals(apiSample.getGlobalId(), uploadedFile.getParentGlobalId());
    assertEquals("afile.dat", uploadedFile.getName());
    assertEquals(InventoryFileType.GENERAL, uploadedFile.getType());
    assertEquals(5, uploadedFile.getSize());
    assertEquals("dat", uploadedFile.getExtension());
    assertEquals("application/octet-stream", uploadedFile.getContentMimeType());
    assertFalse(uploadedFile.getDeleted());

    apiSample = sampleApiMgr.getApiSampleById(apiSample.getId(), user);
    assertEquals(1, apiSample.getAttachments().size());

    ApiInventoryFile deletedFile = invFilesApi.deleteFile(uploadedFile.getId(), user);
    assertNotNull(deletedFile);

    apiSample = sampleApiMgr.getApiSampleById(apiSample.getId(), user);
    assertEquals(0, apiSample.getAttachments().size());
  }

  @Test
  public void uploadDeleteSampleFieldAttachment() throws Exception {

    User user = createInitAndLoginAnyUser();
    ApiSampleWithoutSubSamples apiSample = createComplexSampleForUser(user);
    ApiSampleField attachmentField = apiSample.getFields().get(6);
    assertEquals(ApiFieldType.ATTACHMENT, attachmentField.getType());
    assertNull(attachmentField.getAttachment());

    MockMultipartFile mockFile = createAnyMultipartFile();
    ApiInventoryFilePost settings = new ApiInventoryFilePost();
    settings.setParentGlobalId(attachmentField.getGlobalId());

    ApiInventoryFile uploadedFile = invFilesApi.uploadFile(mockFile, settings, user);
    assertNotNull(uploadedFile);
    assertEquals(attachmentField.getGlobalId(), uploadedFile.getParentGlobalId());
    assertEquals("afile.dat", uploadedFile.getName());
    assertEquals(InventoryFileType.GENERAL, uploadedFile.getType());
    assertEquals(5, uploadedFile.getSize());
    assertEquals("dat", uploadedFile.getExtension());
    assertEquals("application/octet-stream", uploadedFile.getContentMimeType());
    assertFalse(uploadedFile.getDeleted());

    apiSample = sampleApiMgr.getApiSampleById(apiSample.getId(), user);
    attachmentField = apiSample.getFields().get(6);
    assertNotNull(attachmentField.getAttachment());
    assertEquals(uploadedFile.getId(), attachmentField.getAttachment().getId());

    ApiInventoryFile deletedFile = invFilesApi.deleteFile(uploadedFile.getId(), user);
    assertNotNull(deletedFile);

    apiSample = sampleApiMgr.getApiSampleById(apiSample.getId(), user);
    attachmentField = apiSample.getFields().get(6);
    assertNull(attachmentField.getAttachment());
  }

  @Test
  public void uploadChemicalAndRetrieveImageAndChemDtoSuccess() throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleInfo apiSample = createBasicSampleForUser(user);
    assertEquals(0, apiSample.getAttachments().size());
    ApiInventoryFile uploadedFile = createAndUploadChemistryFile(user, apiSample);

    mockSuccessChemistryWeb();

    MockHttpServletResponse resp = new MockHttpServletResponse();
    invFilesApi.getImageBytes(
        uploadedFile.getId(), new ApiInventoryFileImageRequest(1, 1, 1.0), user, resp);
    assertArrayEquals(new byte[] {1, 2, 3}, resp.getContentAsByteArray());

    resp = new MockHttpServletResponse();
    AjaxReturnObject<ChemEditorInputDto> chemDto =
        invFilesApi.getChemFileDto(uploadedFile.getId(), user);
    assertTrue(chemDto.isSuccess());
    assertEquals("123chemStringConverted", chemDto.getData().getChemElements());
  }

  @Test()
  public void uploadChemicalAndRetrieveImageBadRequest() throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleInfo apiSample = createBasicSampleForUser(user);
    ApiInventoryFile uploadedFile = createAndUploadChemistryFile(user, apiSample);
    mockErrorChemistryWeb();
    MockHttpServletResponse resp = new MockHttpServletResponse();
    assertThrows(
        InternalServerErrorException.class,
        () ->
            invFilesApi.getImageBytes(
                uploadedFile.getId(), new ApiInventoryFileImageRequest(), user, resp));
  }

  private ApiInventoryFile createAndUploadChemistryFile(User user, ApiSampleInfo apiSample)
      throws Exception {

    MockMultipartFile mockFile = getChemicalMockFile();
    ApiInventoryFilePost settings = new ApiInventoryFilePost();
    settings.setParentGlobalId(apiSample.getGlobalId());

    ApiInventoryFile uploadedFile = invFilesApi.uploadFile(mockFile, settings, user);
    assertNotNull(uploadedFile);
    assertEquals(apiSample.getGlobalId(), uploadedFile.getParentGlobalId());
    assertEquals("Amfetamine.mol", uploadedFile.getName());
    assertEquals(InventoryFileType.GENERAL, uploadedFile.getType());
    assertEquals("mol", uploadedFile.getExtension());
    assertEquals("application/octet-stream", uploadedFile.getContentMimeType());
    assertFalse(uploadedFile.getDeleted());

    return uploadedFile;
  }

  private void mockErrorChemistryWeb() throws IOException {
    when(chemistryProvider.convert(any(File.class))).thenReturn("123chemString");
    when(chemistryProvider.getSupportedFileTypes()).thenReturn(Collections.singletonList("mol"));
    when(chemistryProvider.exportToImage(anyString(), anyString(), any(ChemicalExportFormat.class)))
        .thenThrow(RuntimeException.class);
  }

  private void mockSuccessChemistryWeb() throws IOException {
    when(chemistryProvider.getSupportedFileTypes()).thenReturn(Collections.singletonList("mol"));
    when(chemistryProvider.exportToImage(anyString(), anyString(), any(ChemicalExportFormat.class)))
        .thenReturn(new byte[] {1, 2, 3});
    when(chemistryProvider.convert(any(File.class))).thenReturn("123chemString");
    when(chemistryProvider.convertToDefaultFormat("123chemString", "mol"))
        .thenReturn("123chemStringConverted");
  }
}
