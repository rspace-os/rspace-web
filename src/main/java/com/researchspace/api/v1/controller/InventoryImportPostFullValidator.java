package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.controller.InventoryImportApiController.ApiInventoryImportSamplesSettings;
import com.researchspace.api.v1.controller.InventoryImportApiController.ApiInventoryImportSettings;
import com.researchspace.api.v1.controller.InventoryImportApiController.ApiInventoryImportSettingsPost;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.multipart.MultipartFile;

@Component
public class InventoryImportPostFullValidator implements Validator {

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApiInventoryImportPostFull {
    private ApiInventoryImportSettingsPost importSettings;
    private MultipartFile containersFile;
    private MultipartFile samplesFile;
    private MultipartFile subSamplesFile;
  }

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiInventoryImportPostFull.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiInventoryImportSettingsPost inventorySettings =
        ((ApiInventoryImportPostFull) target).getImportSettings();

    ApiInventoryImportSettings containerSettings = inventorySettings.getContainerSettings();
    if (containerSettings != null) {
      if (containerSettings.getFieldMappings() == null
          || !containerSettings.getFieldMappings().containsValue("name")) {
        errors.rejectValue(
            "containerSettings.fieldMappings", "errors.inventory.import.fieldMappings.missingName");
      }
    }

    ApiInventoryImportSamplesSettings sampleSettings = inventorySettings.getSampleSettings();
    if (sampleSettings != null) {
      if (sampleSettings.getTemplateInfo() == null) {
        errors.rejectValue(
            "sampleSettings.templateInfo", "errors.inventory.import.templateInfo.empty");
      }
      if (sampleSettings.getFieldMappings() == null
          || !sampleSettings.getFieldMappings().containsValue("name")) {
        errors.rejectValue(
            "sampleSettings.fieldMappings", "errors.inventory.import.fieldMappings.missingName");
      }
    }

    ApiInventoryImportSettings subSampleSettings = inventorySettings.getSubSampleSettings();
    if (subSampleSettings != null) {
      if (subSampleSettings.getFieldMappings() == null
          || !subSampleSettings.getFieldMappings().containsValue("name")) {
        errors.rejectValue(
            "subSampleSettings.fieldMappings", "errors.inventory.import.fieldMappings.missingName");
      } else {
        if (!subSampleSettings.getFieldMappings().containsValue("parent sample import id")
            && !subSampleSettings.getFieldMappings().containsValue("parent sample global id")) {
          errors.rejectValue(
              "sampleSettings",
              "errors.inventory.import.subSampleImportRequiresParentSampleMapping");
        }
        if (sampleSettings == null
            && subSampleSettings.getFieldMappings().containsValue("parent sample import id")) {
          errors.rejectValue(
              "sampleSettings", "errors.inventory.import.subSampleImportRequiresSamplesImport");
        }
      }
    }
  }
}
