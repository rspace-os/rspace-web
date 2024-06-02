package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.controller.InventoryImportApiController.ApiInventoryImportSettingsPost;
import com.researchspace.api.v1.controller.InventoryImportPostFullValidator.ApiInventoryImportPostFull;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;

public class InventoryImportPostFullValidatorTest extends SpringTransactionalTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();

  @Autowired
  @Qualifier("inventoryImportPostFullValidator")
  private InventoryImportPostFullValidator importPostValidator;

  @Mock private MultipartFile dummyMultipartFile;

  @Test
  public void validateContainerImportPost() {

    ApiInventoryImportSettingsPost importSettings = new ApiInventoryImportSettingsPost();
    ApiInventoryImportPostFull fullSettings = new ApiInventoryImportPostFull();
    fullSettings.setImportSettings(importSettings);
    fullSettings
        .getImportSettings()
        .setContainerSettings(new InventoryImportApiController.ApiInventoryImportSettings());
    fullSettings.setContainersFile(dummyMultipartFile);

    // no field mappings
    Errors e = resetErrorsAndValidate(fullSettings);
    assertEquals(1, e.getErrorCount());
    assertEquals("containerSettings.fieldMappings", e.getFieldError().getField());
    assertEquals("errors.inventory.import.fieldMappings.missingName", e.getFieldError().getCode());

    // sanity check with valid request
    Map<String, String> fieldMappings = new HashMap<>();
    fieldMappings.put("name", "name");
    fullSettings.getImportSettings().getContainerSettings().setFieldMappings(fieldMappings);
    e = resetErrorsAndValidate(fullSettings);
    assertEquals(0, e.getErrorCount());
  }

  @Test
  public void validateSampleFieldMappings() {

    ApiInventoryImportSettingsPost importSettings = new ApiInventoryImportSettingsPost();
    importSettings.setSampleSettings(
        new InventoryImportApiController.ApiInventoryImportSamplesSettings());
    importSettings.getSampleSettings().setTemplateInfo(new ApiSampleTemplatePost());

    ApiInventoryImportPostFull fullSettings = new ApiInventoryImportPostFull();
    fullSettings.setImportSettings(importSettings);
    fullSettings.setSamplesFile(dummyMultipartFile);
    Errors e = resetErrorsAndValidate(fullSettings);
    assertEquals(1, e.getErrorCount());
    assertEquals("sampleSettings.fieldMappings", e.getFieldError().getField());
    assertEquals("errors.inventory.import.fieldMappings.missingName", e.getFieldError().getCode());

    HashMap<String, String> fieldMappings = new HashMap<>();
    fieldMappings.put("csvColumn1", "description");
    importSettings.getSampleSettings().setFieldMappings(fieldMappings);
    e = resetErrorsAndValidate(fullSettings);
    assertEquals(1, e.getErrorCount());
    assertEquals("sampleSettings.fieldMappings", e.getFieldError().getField());
    assertEquals("errors.inventory.import.fieldMappings.missingName", e.getFieldError().getCode());

    fieldMappings.put("csvColumn2", "name");
    importSettings.getSampleSettings().setFieldMappings(fieldMappings);
    e = resetErrorsAndValidate(fullSettings);
    assertEquals(0, e.getErrorCount());
  }

  @Test
  public void validateSubSampleImportPost() {

    ApiInventoryImportSettingsPost importSettings = new ApiInventoryImportSettingsPost();
    ApiInventoryImportPostFull fullSettings = new ApiInventoryImportPostFull();
    fullSettings.setImportSettings(importSettings);
    fullSettings
        .getImportSettings()
        .setSubSampleSettings(new InventoryImportApiController.ApiInventoryImportSettings());
    fullSettings.setSubSamplesFile(dummyMultipartFile);

    // no field mappings
    Errors e = resetErrorsAndValidate(fullSettings);
    assertEquals(1, e.getErrorCount());
    assertEquals("subSampleSettings.fieldMappings", e.getFieldError().getField());

    // subsample mappings contain parent sample import id mapping, but no samples csv file provided
    Map<String, String> fieldMappings = new HashMap<>();
    fieldMappings.put("name", "name");
    fieldMappings.put("parent sample import id", "parent sample import id");
    fullSettings.getImportSettings().getSubSampleSettings().setFieldMappings(fieldMappings);
    e = resetErrorsAndValidate(fullSettings);
    assertEquals(1, e.getErrorCount());
    assertEquals("sampleSettings", e.getFieldError().getField());
    assertEquals(
        "errors.inventory.import.subSampleImportRequiresSamplesImport",
        e.getFieldError().getCode());
  }

  private Errors resetErrorsAndValidate(ApiInventoryImportPostFull importSettingsFull) {
    Errors e =
        new BeanPropertyBindingResult(importSettingsFull.getImportSettings(), "importSettings");
    importPostValidator.validate(importSettingsFull, e);
    return e;
  }
}
