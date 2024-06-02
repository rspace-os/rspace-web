package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.controller.InventoryExportApiController.ApiInventoryExportSettingsPost;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

public class InventoryExportPostValidatorTest extends SpringTransactionalTest {

  @Autowired private InventoryExportPostValidator exportPostValidator;

  @Test
  public void validateRequiredExportPostFields() {

    ApiInventoryExportSettingsPost exportSettings = new ApiInventoryExportSettingsPost();

    // no global ids nor users to export
    Errors e = resetErrorsAndValidate(exportSettings);
    assertEquals(1, e.getErrorCount());
    assertEquals(
        "errors.inventory.export.request.has.no.ids.no.users", e.getAllErrors().get(0).getCode());

    // too many global ids
    exportSettings.setGlobalIds(Collections.nCopies(1001, "SS123"));
    e = resetErrorsAndValidate(exportSettings);
    assertEquals(1, e.getErrorCount());
    assertEquals("Size", e.getAllErrors().get(0).getCode());

    // requested export of both globalIds and users
    exportSettings.setGlobalIds(List.of("SS123", "123"));
    exportSettings.setUsernamesToExport(List.of("dummy"));
    e = resetErrorsAndValidate(exportSettings);
    assertEquals(2, e.getErrorCount());
    assertEquals(
        "errors.inventory.export.request.has.both.ids.and.users",
        e.getAllErrors().get(0).getCode());
    assertEquals(
        "errors.inventory.export.request.invalid.globalIds", e.getAllErrors().get(1).getCode());

    // incorrect exportMode
    exportSettings.setGlobalIds(Collections.nCopies(999, "SS123"));
    exportSettings.setExportMode("UNKNOWN");
    e = resetErrorsAndValidate(exportSettings);
    assertEquals(2, e.getErrorCount());
    assertEquals("Pattern", e.getAllErrors().get(0).getCode());
    assertEquals(
        "errors.inventory.export.request.has.both.ids.and.users",
        e.getAllErrors().get(1).getCode());

    // sanity check with valid request
    exportSettings.setUsernamesToExport(List.of("dummy"));
    exportSettings.setGlobalIds(null);
    exportSettings.setExportMode("FULL");
    e = resetErrorsAndValidate(exportSettings);
    assertEquals(0, e.getErrorCount());
  }

  private Errors resetErrorsAndValidate(ApiInventoryExportSettingsPost exportSettings) {
    Errors e = new BeanPropertyBindingResult(exportSettings, "exportSettings");
    exportPostValidator.validate(exportSettings, e);
    return e;
  }
}
