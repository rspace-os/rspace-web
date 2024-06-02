package com.researchspace.api.v1.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.InventoryImportApi;
import com.researchspace.api.v1.controller.InventoryImportPostFullValidator.ApiInventoryImportPostFull;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.InventoryBulkOperationStatus;
import com.researchspace.api.v1.model.ApiInventoryImportParseResult;
import com.researchspace.api.v1.model.ApiInventoryImportResult;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.model.User;
import com.researchspace.service.inventory.InventoryImportManager;
import com.researchspace.service.inventory.csvimport.exception.InventoryImportException;
import com.researchspace.service.inventory.csvimport.exception.InventoryImportPrevalidationException;
import com.researchspace.webapp.config.WebConfig;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@ApiController
public class InventoryImportApiController extends BaseApiInventoryController
    implements InventoryImportApi {

  @Autowired private InventoryImportManager importManager;

  @Autowired private InventoryImportPostFullValidator importPostFullValidator;

  @Data
  @NoArgsConstructor
  public static class ApiInventoryImportSettingsPost {

    @JsonProperty("containerSettings")
    private ApiInventoryImportSettings containerSettings;

    @JsonProperty("sampleSettings")
    private ApiInventoryImportSamplesSettings sampleSettings;

    @JsonProperty("subSampleSettings")
    private ApiInventoryImportSettings subSampleSettings;
  }

  @Data
  @NoArgsConstructor
  public static class ApiInventoryImportSettings {

    @JsonProperty("fieldMappings")
    protected Map<String, String> fieldMappings;
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @NoArgsConstructor
  public static class ApiInventoryImportSamplesSettings extends ApiInventoryImportSettings {

    @JsonProperty("templateId")
    private Long templateId;

    @JsonProperty("templateInfo")
    private ApiSampleTemplatePost templateInfo;
  }

  /**
   * Converts json object coming together with uploaded import file. As other custom converters, is
   * registered in {@link WebConfig#mvcConversionService()}
   */
  public static class ApiInventoryImportPostConverter
      implements Converter<String, ApiInventoryImportSettingsPost> {
    @Override
    public ApiInventoryImportSettingsPost convert(String source) {
      try {
        return new ObjectMapper().readValue(source, ApiInventoryImportSettingsPost.class);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public ApiInventoryImportParseResult parseImportFile(
      @RequestPart("file") MultipartFile file,
      @RequestParam(value = "recordType") String recordType,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    ApiInventoryImportParseResult result = null;
    try (InputStream is = file.getInputStream()) {
      switch (recordType) {
        case "SAMPLES":
          result = importManager.parseSamplesCsvFile(file.getOriginalFilename(), is, user);
          break;
        case "SUBSAMPLES":
          result = importManager.parseSubSamplesCsvFile(is, user);
          break;
        case "CONTAINERS":
          result = importManager.parseContainersCsvFile(is, user);
          break;
        default:
          throw new IllegalArgumentException("unrecoginzed fileType: " + recordType);
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("exception: " + e.getMessage(), e);
    }

    return result;
  }

  @Override
  public ApiInventoryImportResult importFromCsv(
      @RequestPart(value = "containersFile", required = false) MultipartFile containersFile,
      @RequestPart(value = "samplesFile", required = false) MultipartFile samplesFile,
      @RequestPart(value = "subSamplesFile", required = false) MultipartFile subSamplesFile,
      @RequestParam("importSettings") ApiInventoryImportSettingsPost importSettings,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    // validate incoming settings
    BindingResult errors = new BeanPropertyBindingResult(importSettings, "importSettings");
    ApiInventoryImportPostFull importPostFull =
        new ApiInventoryImportPostFull(importSettings, containersFile, samplesFile, subSamplesFile);
    inputValidator.validate(importPostFull, importPostFullValidator, errors);
    throwBindExceptionIfErrors(errors);

    ApiInventoryImportResult result;
    try {
      result = importManager.importInventoryCsvFiles(importPostFull, user);
      result.setStatusForAllResults(InventoryBulkOperationStatus.COMPLETED);

    } catch (InventoryImportException iie) {
      result = (ApiInventoryImportResult) iie.getResult();
      if (iie instanceof InventoryImportPrevalidationException) {
        // only set top-level status, so prevalidation status on individual files are preserved
        result.setStatus(InventoryBulkOperationStatus.PREVALIDATION_ERROR);
      } else {
        // everything was reverted
        result.setStatusForAllResults(InventoryBulkOperationStatus.REVERTED_ON_ERROR);
      }

    } catch (IOException | DataIntegrityViolationException e) {
      throw new IllegalStateException(e);
    }

    return result;
  }
}
