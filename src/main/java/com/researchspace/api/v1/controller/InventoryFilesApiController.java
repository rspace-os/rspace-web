package com.researchspace.api.v1.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.InventoryFilesApi;
import com.researchspace.api.v1.model.ApiInventoryFile;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.FileProperty;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.dtos.chemistry.ChemicalExportFormat;
import com.researchspace.model.dtos.chemistry.ChemicalExportType;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.service.chemistry.ChemistryProvider;
import com.researchspace.service.inventory.InventoryFileApiManager;
import com.researchspace.webapp.config.WebConfig;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.controller.RSChemController.ChemEditorInputDto;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@ApiController
public class InventoryFilesApiController extends BaseApiInventoryController
    implements InventoryFilesApi {

  @Autowired private InventoryFileApiManager invFileManager;

  @Autowired private InventoryFilePostValidator invFilePostValidator;

  @Autowired private ChemistryProvider chemistryProvider;

  @Autowired
  private @Qualifier("compositeFileStore") FileStore fileStore;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApiInventoryFilePost {

    @JsonProperty("parentGlobalId")
    private String parentGlobalId;

    @JsonProperty("fileName")
    protected String fileName;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApiInventoryFileImageRequest {

    @JsonProperty("height")
    private Integer height;

    @JsonProperty("width")
    private Integer width;

    @JsonProperty("scale")
    private Double scale;
  }

  /**
   * Converts json object coming together with uploaded attachment file. As other custom converters,
   * is registered in {@link WebConfig#mvcConversionService()}
   */
  public static class ApiInventoryFilePostConverter
      implements Converter<String, ApiInventoryFilePost> {
    @Override
    public ApiInventoryFilePost convert(String source) {
      try {
        return new ObjectMapper().readValue(source, ApiInventoryFilePost.class);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Converts json object {@link ApiInventoryFileImageRequest} for image requests. As other custom
   * converters, is registered in {@link WebConfig#mvcConversionService()}
   */
  public static class ApiInventoryFileImageRequestConverter
      implements Converter<String, ApiInventoryFileImageRequest> {
    @Override
    public ApiInventoryFileImageRequest convert(String source) {
      try {
        return new ObjectMapper().readValue(source, ApiInventoryFileImageRequest.class);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public ApiInventoryFile getFileById(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {

    InventoryFile invFile = doGetInventoryFile(id, user);
    return getPopulatedApiFile(invFile);
  }

  private ApiInventoryFile getPopulatedApiFile(InventoryFile invFile) {
    ApiInventoryFile apiFile = new ApiInventoryFile(invFile);
    addInventoryFileLink(apiFile);
    return apiFile;
  }

  private InventoryFile doGetInventoryFile(Long id, User user) {
    InventoryFile invFile = invFileManager.getInventoryFileById(id, user);
    if (invFile == null) {
      throw new NotFoundException(createNotFoundMessage("Inventory File", id));
    }
    return invFile;
  }

  @Override
  public void getFileBytes(
      @PathVariable Long id,
      @RequestAttribute(name = "user") User user,
      HttpServletResponse response)
      throws IOException {

    InventoryFile invFile = doGetInventoryFile(id, user);
    response.setContentType(invFile.getContentMimeType());
    response.setHeader(
        "Content-Disposition", "attachment; filename=\"" + invFile.getFileName() + "\"");
    InputStream resourceStream = fileStore.retrieve(invFile.getFileProperty()).get();
    try (InputStream is = resourceStream;
        ServletOutputStream out = response.getOutputStream()) {
      IOUtils.copy(is, out);
    }
  }

  @Override
  public void getImageBytes(
      @PathVariable Long id,
      @RequestParam("imageParams") ApiInventoryFileImageRequest imageParams,
      @RequestAttribute(name = "user") User user,
      HttpServletResponse response)
      throws IOException, URISyntaxException {
    InventoryFile inventoryFile = doGetInventoryFile(id, user);
    // Have to do this manually currently as uploading a file defaults to general file type
    if (chemistryProvider.getSupportedFileTypes().contains(inventoryFile.getExtension())) {
      inventoryFile.setFileType(InventoryFile.InventoryFileType.CHEMICAL);
    }
    if (isChemicalFile(inventoryFile)) {
      response.setContentType(MediaType.IMAGE_PNG_VALUE);
      response.setHeader(
          "Content-Disposition", "attachment; filename=\"" + inventoryFile.getFileName() + "\"");
      try (InputStream is = getChemicalImage(inventoryFile, imageParams);
          ServletOutputStream out = response.getOutputStream()) {
        IOUtils.copy(is, out);
      }
    } else {
      throw new UnsupportedOperationException("Getting images for general files not supported yet");
    }
  }

  private boolean isChemicalFile(InventoryFile file) {
    return file.getFileType().equals(InventoryFile.InventoryFileType.CHEMICAL);
  }

  private InputStream getChemicalImage(
      InventoryFile inventoryFile, ApiInventoryFileImageRequest imageRequest)
      throws URISyntaxException, IOException {
    return generateChemImage(imageRequest, inventoryFile.getFileProperty());
  }

  private ByteArrayInputStream generateChemImage(
      ApiInventoryFileImageRequest imageRequest, FileProperty fileProperty)
      throws IOException, URISyntaxException {
    String chemString =
        chemistryProvider.convert(new File(new URI(fileProperty.getAbsolutePathUri())));
    ChemicalExportFormat format =
        new ChemicalExportFormat(
            ChemicalExportType.PNG, imageRequest.width, imageRequest.height, imageRequest.scale);
    try {
      byte[] chemistryExportResponse = chemistryProvider.exportToImage(chemString, format);
      return new ByteArrayInputStream(chemistryExportResponse);
    } catch (Exception e) {
      throw new InternalServerErrorException(e.getMessage());
    }
  }

  @Override
  public AjaxReturnObject<ChemEditorInputDto> getChemFileDto(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) throws IOException {

    InventoryFile invFile = doGetInventoryFile(id, user);
    String chemString = chemistryProvider.convert(fileStore.findFile(invFile.getFileProperty()));

    RSChemElement chem =
        RSChemElement.builder()
            .chemElements(chemistryProvider.convert(chemString))
            .chemElementsFormat(ChemElementsFormat.MRV)
            .build();

    return new AjaxReturnObject<ChemEditorInputDto>(
        new ChemEditorInputDto(null, chem.getChemElements(), chem.getChemElementsFormat()), null);
  }

  @Override
  public ApiInventoryFile uploadFile(
      @RequestPart("file") MultipartFile file,
      @RequestParam("fileSettings") ApiInventoryFilePost settings,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    // validate incoming settings
    BindingResult errors = new BeanPropertyBindingResult(settings, "fileSettings");
    inputValidator.validate(settings, invFilePostValidator, errors);
    throwBindExceptionIfErrors(errors);

    String fileName = settings.getFileName();
    if (fileName == null) {
      fileName = file.getOriginalFilename();
    }
    GlobalIdentifier parentInvRecordGlobalId = new GlobalIdentifier(settings.getParentGlobalId());
    assertUserCanEditInventoryRecord(parentInvRecordGlobalId, user);

    InventoryFile result;
    try (InputStream is = file.getInputStream()) {
      result =
          invFileManager.attachNewInventoryFileToInventoryRecord(
              parentInvRecordGlobalId, fileName, is, user);
    } catch (IOException ioe) {
      throw new IllegalStateException(ioe);
    }

    return getPopulatedApiFile(result);
  }

  @Override
  public ApiInventoryFile deleteFile(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    InventoryFile file = invFileManager.markInventoryFileAsDeleted(id, user);
    return getPopulatedApiFile(file);
  }

  @Override
  public ResponseEntity<byte[]> getImageByContentsHash(
      @PathVariable String contentsHash, @RequestAttribute(name = "user") User user)
      throws IOException {
    return doImageResponse(
        user, () -> invFileManager.getFilePropertyByContentsHash(contentsHash, user));
  }
}
